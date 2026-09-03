package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.domain.temp.ComponentDiseaseType;
import edu.whut.cs.bi.biz.domain.vo.Disease2ReportSummaryAiVO;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.ReportMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.ReportGenerateTools;
import edu.whut.cs.bi.biz.utils.ReportTemplateValueUtils;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTBlip;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static edu.whut.cs.bi.biz.service.impl.ReportServiceImpl.compareCodes;

/**
 * 定期检查多桥报告Service实现。
 *
 * <p>桥梁章节的生成逻辑参照一级单桥报告实现并独立复制而来，之后两边各自演进、互不影响；
 * 定期检查记录表、桥梁卡片、评定表、检测结论等全系统通用服务仍然共用。</p>
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class LineMultiBridgeReportServiceImpl implements ILineMultiBridgeReportService {
    /**
     * 多桥模板的桥梁章节沿用一级梁桥的占位符体系，向底层通用服务传递该类型。
     */
    private static final ReportTemplateTypes BRIDGE_CHAPTER_TEMPLATE_TYPE = ReportTemplateTypes.LEVEL_1_BEAM_BRIDGE;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private ILineMultiBridgeReportDataService lineReportDataService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private IProjectService projectService;

    @Autowired
    private IBuildingService buildingService;

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private IComponentService componentService;

    @Resource
    private IDiseaseService diseaseService;

    @Value("${springAi_Rag.endpoint}")
    private String SpringAiUrl;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private IBiEvaluationService biEvaluationService;

    @Autowired
    private IPropertyService propertyService;

    @Autowired
    private EvaluationTableService evaluationTableService;

    @Autowired
    private ComparisonAnalysisService comparisonAnalysisService;

    @Autowired
    private TestConclusionService testConclusionService;

    @Autowired
    private IBridgeCardService bridgeCardService;

    @Autowired
    private RegularInspectionService regularInspectionService;

    @Resource
    private IBiTemplateObjectService biTemplateObjectService;

    @Override
    public boolean isMultiBridgeTemplate(ReportTemplate template) {
        return template != null && ReportTemplateTypes.isMultiBridge(template.getName());
    }

    @Override
    public List<Task> orderTasksBySelection(List<Long> taskIds, List<Task> tasks) {
        if (taskIds == null || tasks == null) {
            return tasks;
        }
        Map<Long, Task> taskById = tasks.stream()
                .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left));
        return taskIds.stream()
                .map(taskById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public String validateTasks(Report report, List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "多桥定期检查报告至少需要选择一座桥";
        }
        for (Task task : tasks) {
            Building building = task.getBuilding();
            if (building == null) {
                return "任务关联的建筑不存在：任务ID " + task.getId();
            }
            String bridgeName = building.getName() == null ? ("任务ID " + task.getId()) : building.getName();
            if (task.getProjectId() != null && report != null && report.getProjectId() != null
                    && !report.getProjectId().equals(task.getProjectId())) {
                return "所选桥梁不属于当前报告项目：" + bridgeName;
            }
            if (building.getRootPropertyId() == null) {
                return "桥梁【" + bridgeName + "】的桥梁信息卡片不存在,请通过excel导入";
            }
            Property query = new Property();
            query.setId(building.getRootPropertyId());
            List<Property> properties = propertyService.selectPropertyList(query);
            if (properties == null || properties.isEmpty()) {
                return "桥梁【" + bridgeName + "】的桥梁信息卡片不存在,请通过excel导入";
            }
            BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
            if (evaluation == null || evaluation.getSystemLevel() == null) {
                return "桥梁【" + bridgeName + "】未进行评定，请评定后再生成报告";
            }
        }
        return null;
    }

    /**
     * 生成多桥定期检查报告。
     *
     * <p>模板只加载一次。模板中的桥梁章节由 start/end 锚点标记，先在独立 Word 文档
     * 副本中按任务生成，再将生成后的章节按选择顺序插回主文档，从而避免多个桥梁共用
     * 同一份填报数据或相互覆盖占位符。</p>
     */
    @Override
    public String generateReportDocument(Report report, List<Task> tasks, ReportTemplate template) {
        InputStream templateStream = null;
        FileOutputStream out = null;
        File outputFile = null;
        XWPFDocument document = null;

        try {
            if (report == null || template == null || template.getMinioId() == null || tasks == null || tasks.isEmpty()) {
                log.error("多桥报告缺少报告、模板或任务");
                return null;
            }

            FileMap fileMap = fileMapService.selectFileMapById(template.getMinioId());
            if (fileMap == null || fileMap.getNewName() == null) {
                log.error("未找到多桥模板文件");
                return null;
            }

            String fileName = fileMap.getNewName();
            templateStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(fileName.substring(0, 2) + "/" + fileName)
                    .build());
            byte[] templateBytes = readAllBytes(templateStream);

            Project project = report.getProjectId() == null ? null : projectService.selectProjectById(report.getProjectId());
            List<LineReportData> allReportData = lineReportDataService.selectByReportId(report.getId());

            document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
            WordFieldUtils.resetCounters();
            prepareTaskBuildings(tasks, project);
            List<LineBridgeGroup> groups = lineReportDataService.resolveEffectiveGroups(report.getId(), tasks);
            Map<Long, Task> taskById = tasks.stream()
                    .filter(task -> task != null && task.getId() != null)
                    .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left));
            applyLineLevelData(document, allReportData, report, project, tasks.get(0));
            generateBridgeSummary(document, groups, taskById);

            XWPFParagraph masterStart = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-start}");
            XWPFParagraph masterEnd = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-end}");
            if (masterStart == null || masterEnd == null) {
                throw new IllegalStateException("多桥模板缺少桥梁章节 start/end 锚点");
            }

            // 清掉模板中的样例桥章节，只保留锚点和前后公共内容。
            removeBodyElementsBetween(document, masterStart, masterEnd);

            for (LineBridgeGroup group : groups) {
                List<Task> groupTasks = resolveGroupTasks(group, taskById);
                if (groupTasks.isEmpty()) {
                    log.warn("跳过没有子桥的大桥: {}", group == null ? null : group.getName());
                    continue;
                }

                XWPFDocument bridgeDocument = new XWPFDocument(new ByteArrayInputStream(templateBytes));
                try {
                    WordFieldUtils.resetCounters();
                    String groupId = group.getId();
                    List<LineReportData> groupData = allReportData.stream()
                            .filter(data -> groupId != null && groupId.equals(data.getGroupId()))
                            .collect(Collectors.toList());

                    applyGroupIdentity(bridgeDocument, group, groupTasks.get(0).getBuilding(), project);
                    processGroupChapter(bridgeDocument, group, groupTasks, project, groupData, templateBytes);

                    WordFieldUtils.updateAllFields(bridgeDocument);
                    bridgeDocument.getSettings().setUpdateFields();
                    insertBodyElementCopies(document, masterEnd, bridgeDocument,
                            copyBridgeBodyElements(bridgeDocument));
                } finally {
                    bridgeDocument.close();
                }
            }

            // 锚点只用于服务端定位，不能出现在最终报告中。
            removeParagraph(document, masterStart);
            // 删除 masterStart 后位置索引会变化，按占位符重新定位更安全。
            removeParagraphByPlaceholder(document, "${line.bridge-block-end}");
            replaceText(document, "${line.bridge-block-start}", "");
            replaceText(document, "${line.bridge-block-end}", "");
            updateTocPreview(document, groups);
            WordFieldUtils.updateAllFields(document);
            document.getSettings().setUpdateFields();
            applyDocumentHeadingStyles(document);

            outputFile = File.createTempFile("line_multi_bridge_report_" + report.getId(), ".docx");
            out = new FileOutputStream(outputFile);
            document.write(out);
            out.close();

            FileMap reportFileMap = fileMapService.handleFileUploadFromFile(
                    outputFile,
                    report.getName() + "_" + System.currentTimeMillis() + ".docx",
                    ShiroUtils.getLoginName());
            report.setMinioId(Long.valueOf(reportFileMap.getId()));
            report.setStatus(1);
            reportMapper.updateReport(report);
            return reportFileMap.getId().toString();
        } catch (Exception e) {
            log.error("生成多桥报告失败", e);
            return null;
        } finally {
            try {
                if (templateStream != null) {
                    templateStream.close();
                }
                if (out != null) {
                    out.close();
                }
                if (document != null) {
                    document.close();
                }
                if (outputFile != null && outputFile.exists() && !outputFile.delete()) {
                    log.warn("多桥报告临时文件删除失败: {}", outputFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("清理多桥报告资源失败", e);
            }
        }
    }

    /**
     * 处理一座大桥的章节内容，只操作传入的章节文档，不负责下载或上传。
     */
    private void processGroupChapter(XWPFDocument document, LineBridgeGroup group, List<Task> groupTasks,
                                     Project project, List<LineReportData> groupData, byte[] templateBytes) {
        if (document == null || groupTasks == null || groupTasks.isEmpty()) {
            return;
        }
        Task firstTask = groupTasks.get(0);
        try {
            if (project == null && firstTask.getProjectId() != null) {
                project = projectService.selectProjectById(firstTask.getProjectId());
            }
            if (project == null) {
                log.warn("大桥章节未找到项目: groupId={}", group == null ? null : group.getId());
                return;
            }

            List<ReportData> chapterData = groupData == null ? Collections.emptyList() : new ArrayList<>(groupData);
            Map<String, ReportData> dataMap = new HashMap<>();
            for (ReportData data : chapterData) {
                if (data != null && data.getKey() != null) {
                    dataMap.put(data.getKey(), data);
                }
            }

            Calendar calendar = Calendar.getInstance();
            ReportGenerateTools.replaceText(document, "${year}", String.valueOf(calendar.get(Calendar.YEAR)));
            ReportGenerateTools.replaceText(document, "${month}", String.valueOf(calendar.get(Calendar.MONTH) + 1));
            ReportGenerateTools.replaceText(document, "${day}", String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
            String groupName = groupDisplayName(group, firstTask);
            ReportGenerateTools.replaceText(document, "${buildingName}", groupName);
            if (project.getName() != null) {
                ReportGenerateTools.replaceText(document, "${project-name}", project.getName());
            }
            if (project.getDept() != null && project.getDept().getDeptName() != null) {
                ReportGenerateTools.replaceText(document, "${client-unit}", project.getDept().getDeptName());
            }

            testConclusionService.clearDiseaseSummaryCache();

            replaceOverallOverviewInBridgeBlock(document, valueOf(dataMap, "overallOverview"));
            removeTechnicalStandardInBridgeBlock(document);
            insertGroupOverviewImages(document, dataMap);

            XWPFParagraph appendixHeading = findBridgeBlockHeading(document, "附表");
            XWPFParagraph blockEnd = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-end}");
            if (appendixHeading != null && blockEnd != null) {
                removeBodyElementsBetween(document, appendixHeading, blockEnd);
            }

            insertHeadingBeforePlaceholder(document, "${testConclusion}", "检测结论", 4);
            insertHeadingBeforePlaceholder(document, "${technicalAdvice}", "技术建议", 4);
            processGroupAutoGeneratedContent(document, groupName, project, groupTasks);
            processGroupUserData(document, chapterData, groupTasks, dataMap, project);
            fillAppendixForGroup(document, groupTasks, project, templateBytes);
            WordFieldUtils.updateAllFields(document);
        } catch (Exception e) {
            log.error("处理大桥章节失败: groupId={}", group == null ? null : group.getId(), e);
        }
    }

    private void prepareTaskBuildings(List<Task> tasks, Project project) {
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            if (task == null) {
                continue;
            }
            task.setProject(project);
            if (task.getBuilding() == null && task.getBuildingId() != null) {
                task.setBuilding(buildingService.selectBuildingById(task.getBuildingId()));
            }
        }
    }

    private List<Task> resolveGroupTasks(LineBridgeGroup group, Map<Long, Task> taskById) {
        if (group == null || group.getTaskIds() == null || taskById == null) {
            return Collections.emptyList();
        }
        return group.getTaskIds().stream()
                .map(taskById::get)
                .filter(task -> task != null && task.getBuilding() != null)
                .collect(Collectors.toList());
    }

    private String groupDisplayName(LineBridgeGroup group, Task fallbackTask) {
        if (group != null && group.getName() != null && !group.getName().trim().isEmpty()) {
            return group.getName().trim();
        }
        if (fallbackTask != null && fallbackTask.getBuilding() != null) {
            return safeText(fallbackTask.getBuilding().getName());
        }
        return "桥梁";
    }

    /**
     * 3.1 整体概况改为填报正文，不再用桥梁卡片自动拼句。
     */
    private void replaceOverallOverviewInBridgeBlock(XWPFDocument document, String overview) {
        XWPFParagraph heading = findBridgeBlockHeading(document, "整体概况");
        if (heading == null) {
            if (!isBlank(overview)) {
                ReportGenerateTools.replaceText(document, "${overallOverview}", overview);
            }
            return;
        }
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int headingIndex = paragraphs.indexOf(heading);
        if (headingIndex < 0 || headingIndex + 1 >= paragraphs.size()) {
            return;
        }
        XWPFParagraph nextParagraph = paragraphs.get(headingIndex + 1);
        String nextText = safeParagraphText(nextParagraph);
        if (nextText.contains("${桥梁名称}") || nextText.contains("${overallOverview}")
                || nextText.contains("位于") || isBlank(nextText)) {
            clearParagraph(nextParagraph);
            if (!isBlank(overview)) {
                XWPFRun run = nextParagraph.createRun();
                run.setText(overview);
                applyBodyFormat(nextParagraph, true);
            }
        }
        ReportGenerateTools.replaceText(document, "${overallOverview}", isBlank(overview) ? "" : overview);
    }

    /**
     * 技术标准整节去掉，不放进 3.1，也不挪到第一章。
     */
    private void removeTechnicalStandardInBridgeBlock(XWPFDocument document) {
        XWPFParagraph heading = findBridgeBlockHeading(document, "技术标准");
        if (heading != null) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            int headingIndex = paragraphs.indexOf(heading);
            XWPFParagraph valueParagraph = headingIndex >= 0 && headingIndex + 1 < paragraphs.size()
                    ? paragraphs.get(headingIndex + 1) : null;
            if (valueParagraph != null && safeParagraphText(valueParagraph).contains("${standard}")) {
                removeParagraph(document, valueParagraph);
            }
            removeParagraph(document, heading);
        }
        replaceText(document, "${standard}", "");
    }

    private XWPFParagraph findBridgeBlockHeading(XWPFDocument document, String headingText) {
        boolean inBridgeBlock = false;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = safeParagraphText(paragraph);
            if (text.contains("${line.bridge-block-start}")) {
                inBridgeBlock = true;
                continue;
            }
            if (text.contains("${line.bridge-block-end}")) {
                break;
            }
            if (inBridgeBlock && headingText.equals(text)) {
                return paragraph;
            }
        }
        return null;
    }

    private String valueOf(Map<String, ReportData> dataMap, String key) {
        ReportData data = dataMap == null ? null : dataMap.get(key);
        return data == null ? null : data.getValue();
    }

    private String safeParagraphText(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return "";
        }
        String text = paragraph.getText();
        return text == null ? "" : text.trim();
    }

    /**
     * 填充线路级内容：封面、项目信息以及第一章由用户填报的线路级数据。
     */
    private void applyLineLevelData(XWPFDocument document, List<LineReportData> reportDataList,
                                    Report report, Project project, Task firstTask) {
        Calendar calendar = Calendar.getInstance();
        replaceText(document, "${year}", String.valueOf(calendar.get(Calendar.YEAR)));
        replaceText(document, "${month}", String.valueOf(calendar.get(Calendar.MONTH) + 1));
        replaceText(document, "${day}", String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        replaceText(document, "${line-report-title}", report == null ? "" : safeText(report.getName()));
        if (project != null) {
            replaceText(document, "${project-name}", safeText(project.getName()));
            if (project.getDept() != null) {
                replaceText(document, "${client-unit}", safeText(project.getDept().getDeptName()));
                replaceText(document, "${line-client-unit}", safeText(project.getDept().getDeptName()));
            }
        }
        if (firstTask != null && firstTask.getBuilding() != null) {
            Building building = firstTask.getBuilding();
            replaceText(document, "${路线名称}", safeText(building.getRouteName()));
            replaceText(document, "${路线编号}", safeText(building.getRouteCode()));
        }
        if (reportDataList == null) {
            return;
        }
        for (LineReportData data : reportDataList) {
            if (data.getTaskId() != null || !isBlank(data.getGroupId()) || data.getKey() == null) {
                continue;
            }
            if (LineBridgeGroup.DATA_KEY.equals(data.getKey())) {
                continue;
            }
            if (data.getType() != null && data.getType() == 1) {
                continue;
            }
            String key = data.getKey();
            String placeholder = key.startsWith("${") ? key : "${" + key + "}";
            replaceText(document, placeholder, safeText(data.getValue()));
        }
    }

    /**
     * 填充一座大桥的身份信息，桥梁章节副本内的同名占位符都指向这一座大桥。
     */
    private void applyGroupIdentity(XWPFDocument document, LineBridgeGroup group, Building firstBuilding, Project project) {
        String groupName = groupDisplayName(group, null);
        if (isBlank(groupName) && firstBuilding != null) {
            groupName = safeText(firstBuilding.getName());
        }
        replaceText(document, "${桥梁名称}", safeText(groupName));
        replaceText(document, "${building-name}", safeText(groupName));
        replaceText(document, "${buildingName}", safeText(groupName));
        if (firstBuilding != null) {
            replaceText(document, "${桥梁编号}", safeText(firstBuilding.getBuildingCode()));
            replaceText(document, "${路线编号}", safeText(firstBuilding.getRouteCode()));
            replaceText(document, "${路线名称}", safeText(firstBuilding.getRouteName()));
            replaceText(document, "${桥位桩号}", safeText(firstBuilding.getBridgePileNumber()));
        }
        if (project != null) {
            replaceText(document, "${project-name}", safeText(project.getName()));
            if (project.getDept() != null) {
                replaceText(document, "${client-unit}", safeText(project.getDept().getDeptName()));
            }
        }
    }

    /**
     * 填充单座桥梁的身份信息，桥梁章节副本内的同名占位符都指向这一座桥。
     */
    private void applyBridgeIdentity(XWPFDocument document, Building building, Project project) {
        replaceText(document, "${桥梁名称}", safeText(building.getName()));
        replaceText(document, "${桥梁编号}", safeText(building.getBuildingCode()));
        replaceText(document, "${路线编号}", safeText(building.getRouteCode()));
        replaceText(document, "${路线名称}", safeText(building.getRouteName()));
        replaceText(document, "${桥位桩号}", safeText(building.getBridgePileNumber()));
        replaceText(document, "${building-name}", safeText(building.getName()));
        replaceText(document, "${buildingName}", safeText(building.getName()));
        if (project != null) {
            replaceText(document, "${project-name}", safeText(project.getName()));
            if (project.getDept() != null) {
                replaceText(document, "${client-unit}", safeText(project.getDept().getDeptName()));
            }
        }
    }

    /**
     * 生成第二章：按大桥出标题（2.2 的下一级，样式 4），其下按子桥出部件划分及构件数量表。
     */
    private void generateBridgeSummary(XWPFDocument document, List<LineBridgeGroup> groups, Map<Long, Task> taskById) {
        XWPFParagraph placeholder = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-summary}");
        if (placeholder == null) {
            log.warn("多桥模板未找到桥梁汇总占位符");
            return;
        }
        XmlCursor cursor = placeholder.getCTP().newCursor();
        AtomicInteger chapter2TableCounter = new AtomicInteger(1);

        for (LineBridgeGroup group : groups) {
            List<Task> groupTasks = resolveGroupTasks(group, taskById);
            if (groupTasks.isEmpty()) {
                continue;
            }
            XWPFParagraph groupTitle = document.insertNewParagraph(cursor);
            groupTitle.setStyle("4");
            groupTitle.createRun().setText(groupDisplayName(group, groupTasks.get(0)));
            applyHeadingFormat(groupTitle, 4);
            cursor.toNextToken();

            XWPFParagraph componentIntro = document.insertNewParagraph(cursor);
            componentIntro.createRun().setText("各桥梁部件划分及构件数量如下表所示：");
            applyBodyFormat(componentIntro, true);
            cursor.toNextToken();

            for (Task task : groupTasks) {
                Building building = task.getBuilding();
                if (building == null || building.getRootObjectId() == null) {
                    continue;
                }
                BiObject rootObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
                if (rootObject == null) {
                    log.warn("多桥第二章未找到桥梁结构树: taskId={}, rootObjectId={}",
                            task.getId(), building.getRootObjectId());
                    continue;
                }
                List<BiObject> allObjects = biObjectMapper.selectChildrenById(rootObject.getId());
                for (BiObject tableRoot : resolveComponentTableRoots(rootObject, allObjects)) {
                    String tableName = Objects.equals(tableRoot.getId(), rootObject.getId())
                            ? safeText(building.getName()) : safeText(tableRoot.getName());
                    WordFieldUtils.createTableCaptionWithCounter(
                            document, tableName + "桥梁部件划分及构件数量表",
                            cursor, 2, chapter2TableCounter, 21, 360, false, 0);
                    generateComponentTable(document, cursor, collectComponentStructure(tableRoot, allObjects));
                }
            }
        }
        clearParagraph(placeholder);
    }

    /**
     * 更新母版中原有目录的桥梁名称缓存。
     *
     * <p>POI 不会计算 TOC 域，因此先把母版中 3 个样例桥名替换成前 3 座大桥名；
     * 真正的页码和超过 3 座大桥的目录项在 Word 打开时由 updateFields 自动刷新。</p>
     */
    private void updateTocPreview(XWPFDocument document, List<LineBridgeGroup> groups) {
        String[] placeholders = {"${桥梁名称}", "${line.bridge.2.name}", "${line.bridge.3.name}"};
        for (int i = 0; i < placeholders.length; i++) {
            XWPFParagraph paragraph = ReportGenerateTools.findParagraphByPlaceholder(document, placeholders[i]);
            if (paragraph == null) {
                continue;
            }
            if (groups != null && i < groups.size() && groups.get(i) != null
                    && !isBlank(groups.get(i).getName())) {
                ReportGenerateTools.replaceTextInParagraphs(
                        Collections.singletonList(paragraph),
                        placeholders[i],
                        safeText(groups.get(i).getName()),
                        "目录预览");
            } else {
                clearParagraph(paragraph);
            }
        }
    }

    /**
     * 删除两个锚点段落之间的全部内容，只保留锚点本身。
     */
    private void removeBodyElementsBetween(XWPFDocument document, XWPFParagraph start, XWPFParagraph end) {
        int startIndex = document.getPosOfParagraph(start);
        int endIndex = document.getPosOfParagraph(end);
        if (startIndex < 0 || endIndex < 0 || startIndex >= endIndex) {
            throw new IllegalStateException("多桥模板锚点顺序错误");
        }
        while (endIndex - startIndex > 1) {
            document.removeBodyElement(startIndex + 1);
            endIndex--;
        }
    }

    /**
     * 取出桥梁副本中两个锚点之间生成好的章节内容。
     */
    private List<IBodyElement> copyBridgeBodyElements(XWPFDocument document) {
        XWPFParagraph start = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-start}");
        XWPFParagraph end = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-end}");
        if (start == null || end == null) {
            throw new IllegalStateException("桥梁副本缺少章节锚点");
        }
        int startIndex = document.getPosOfParagraph(start);
        int endIndex = document.getPosOfParagraph(end);
        List<IBodyElement> elements = new ArrayList<>();
        for (int i = startIndex + 1; i < endIndex; i++) {
            elements.add(document.getBodyElements().get(i));
        }
        return elements;
    }

    /**
     * 将桥梁副本文档中的章节插入主文档。
     *
     * <p>图片在 docx 中不是段落 XML 的一部分：段落内的 {@code a:blip} 通过 rId 引用
     * 文档关系中的媒体文件。仅复制 CTP/CTTbl 会保留原副本的 rId，导致主文档找不到图片。
     * 因此在写入主文档前，需要把每个图片关系重新绑定到主文档。</p>
     */
    private void insertBodyElementCopies(XWPFDocument target, XWPFParagraph before,
                                         XWPFDocument sourceDocument, List<IBodyElement> elements) throws Exception {
        XmlCursor cursor = before.getCTP().newCursor();
        Map<String, String> pictureRelationIds = new HashMap<>();
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph source = (XWPFParagraph) element;
                CTP copiedParagraph = (CTP) source.getCTP().copy();
                rebindPictureRelationships(sourceDocument, target, copiedParagraph, pictureRelationIds);
                XWPFParagraph inserted = target.insertNewParagraph(cursor);
                inserted.getCTP().set(copiedParagraph);
            } else if (element instanceof XWPFTable) {
                XWPFTable source = (XWPFTable) element;
                CTTbl copiedTable = (CTTbl) source.getCTTbl().copy();
                rebindPictureRelationships(sourceDocument, target, copiedTable, pictureRelationIds);
                XWPFTable inserted = target.insertNewTbl(cursor);
                inserted.getCTTbl().set(copiedTable);
            }
            cursor.toNextToken();
        }
    }

    /**
     * 将已复制 XML 中的图片 rId 改为主文档的图片关系 ID。
     */
    private void rebindPictureRelationships(XWPFDocument sourceDocument, XWPFDocument targetDocument,
                                            XmlObject copiedElement,
                                            Map<String, String> pictureRelationIds) throws Exception {
        XmlObject[] blipNodes = copiedElement.selectPath(
                "declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main' .//a:blip");
        for (XmlObject blipNode : blipNodes) {
            CTBlip blip = (CTBlip) blipNode;
            String sourceRelationId = blip.getEmbed();
            if (sourceRelationId == null || sourceRelationId.isBlank()) {
                continue;
            }

            String targetRelationId = pictureRelationIds.get(sourceRelationId);
            if (targetRelationId == null) {
                XWPFPictureData pictureData = sourceDocument.getPictureDataByID(sourceRelationId);
                if (pictureData == null) {
                    log.warn("桥梁章节图片关系不存在，保留原引用: rId={}", sourceRelationId);
                    continue;
                }
                targetRelationId = targetDocument.addPictureData(
                        pictureData.getData(), pictureData.getPictureType());
                pictureRelationIds.put(sourceRelationId, targetRelationId);
            }
            blip.setEmbed(targetRelationId);
        }
    }

    private void removeParagraph(XWPFDocument document, XWPFParagraph paragraph) {
        int position = document.getPosOfParagraph(paragraph);
        if (position >= 0) {
            document.removeBodyElement(position);
        }
    }

    private void removeParagraphByPlaceholder(XWPFDocument document, String placeholder) {
        XWPFParagraph paragraph = ReportGenerateTools.findParagraphByPlaceholder(document, placeholder);
        if (paragraph != null) {
            removeParagraph(document, paragraph);
        }
    }

    /**
     * 在占位符段落前插入小节标题。模板里已有同名标题时不再重复插入。
     */
    private void insertHeadingBeforePlaceholder(XWPFDocument document, String placeholder, String title, int headingLevel) {
        XWPFParagraph target = ReportGenerateTools.findParagraphByPlaceholder(document, placeholder);
        if (target == null) {
            return;
        }
        int position = document.getPosOfParagraph(target);
        if (position > 0) {
            IBodyElement previous = document.getBodyElements().get(position - 1);
            if (previous instanceof XWPFParagraph) {
                String previousText = ((XWPFParagraph) previous).getText();
                if (previousText != null && previousText.replace(".", "").replace(" ", "").contains(title)) {
                    applyHeadingFormat((XWPFParagraph) previous, headingLevel);
                    return;
                }
            }
        }
        XmlCursor cursor = target.getCTP().newCursor();
        XWPFParagraph heading = document.insertNewParagraph(cursor);
        heading.setStyle(String.valueOf(headingLevel));
        heading.createRun().setText(title);
        applyHeadingFormat(heading, headingLevel);
    }

    /**
     * 替换占位符。除正文、表格、页眉页脚外，还覆盖文本框等普通段落 API 遍历不到的文本节点。
     */
    private void replaceText(XWPFDocument document, String oldText, String newText) {
        String replacement = newText == null ? "" : newText;
        ReportGenerateTools.replaceText(document, oldText, replacement);
        replaceTextInXmlTextNodes(document.getDocument(), oldText, replacement);
    }

    private void replaceTextInXmlTextNodes(XmlObject root, String oldText, String newText) {
        if (root == null || oldText == null || oldText.isEmpty()) {
            return;
        }
        try {
            XmlObject[] textNodes = root.selectPath(
                    "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' .//w:t");
            for (XmlObject textNode : textNodes) {
                XmlCursor cursor = textNode.newCursor();
                String text = cursor.getTextValue();
                if (text != null && text.contains(oldText)) {
                    cursor.setTextValue(text.replace(oldText, newText));
                }
            }
        } catch (Exception e) {
            log.warn("替换XML文本节点占位符失败: {}", e.getMessage());
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void clearParagraph(XWPFParagraph paragraph) {
        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }
    }

    // ==================== 以下桥梁章节逻辑参照一级单桥报告实现，独立维护 ====================

    /**
     * 处理大桥章节中由系统自动生成的内容
     */
    private void processGroupAutoGeneratedContent(XWPFDocument document, String groupName,
                                                  Project project, List<Task> groupTasks) {
        try {
            processAppearanceCheck(document, groupTasks, project.getId());
            log.info("外观检测结果生成完成");
        } catch (Exception e) {
            log.error("处理外观检测结果出错: error={}", e.getMessage(), e);
            ReportGenerateTools.replaceText(document, "${appearanceInspectionResults}", "【外观检测结果生成失败，请联系管理员】");
        }

        try {
            handleEvaluationResults(document, "${evaluationResults}", groupName, groupTasks);
            log.info("技术状况评定生成完成");
        } catch (Exception e) {
            log.error("处理技术状况评定出错: error={}", e.getMessage(), e);
            ReportGenerateTools.replaceText(document, "${evaluationResults}", "【技术状况评定生成失败，请联系管理员】");
        }

        try {
            handleComparisonAnalysis(document, "${comparativeAnalysisOfEvaluationResults}", groupTasks);
            log.info("近年评定结果对比生成完成");
        } catch (Exception e) {
            log.error("处理近年评定结果对比出错: error={}", e.getMessage(), e);
            ReportGenerateTools.replaceText(document, "${comparativeAnalysisOfEvaluationResults}", "【近年评定结果对比生成失败，请联系管理员】");
        }

        try {
            handleTestConclusion(document, "${testConclusion}", groupTasks, groupName);
            handleTestConclusionBridge(document, "${testConclusionBridge}", groupTasks);
        } catch (Exception e) {
            log.error("处理检测结论出错: error={}", e.getMessage());
            ReportGenerateTools.replaceText(document, "${testConclusion}", "检测结论数据获取失败");
            ReportGenerateTools.replaceText(document, "${testConclusionBridge}", "检测结论详情数据获取失败");
        }
    }

    /**
     * 处理第三章外观检测结果。
     *
     * <p>模板里 3.2「外观检测结果」是二级（样式 2），其下一级是样式 4（与 3.1.1 整体概况同级）。
     * 每座子桥占这一级（3.2.1、3.2.2）；原先的上部/下部/桥面系及构件整体再降一级。</p>
     */
    private void processAppearanceCheck(XWPFDocument document, List<Task> groupTasks, Long projectId) throws Exception {
        XWPFParagraph placeholderParagraph = null;
        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getText().contains("${appearanceInspectionResults}")) {
                placeholderParagraph = paragraph;
                break;
            }
        }
        if (placeholderParagraph == null) {
            return;
        }

        XmlCursor cursor = placeholderParagraph.getCTP().newCursor();
        AtomicInteger chapter3ImageCounter = new AtomicInteger(1);
        AtomicInteger chapter3TableCounter = new AtomicInteger(1);

        for (Task task : groupTasks) {
            Building building = task.getBuilding();
            if (building == null || building.getRootObjectId() == null) {
                continue;
            }
            BiObject rootBiObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
            if (rootBiObject == null) {
                continue;
            }
            List<BiObject> allObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());

            XWPFParagraph subBridgeTitle = document.insertNewParagraph(cursor);
            subBridgeTitle.setStyle("4");
            subBridgeTitle.createRun().setText(safeText(building.getName()));
            applyHeadingFormat(subBridgeTitle, 4);
            cursor.toNextToken();

            Disease queryParam = new Disease();
            queryParam.setBuildingId(building.getId());
            queryParam.setProjectId(projectId);
            List<Disease> subBridgeDiseases = diseaseMapper.selectDiseaseList(queryParam);

            List<BiObject> appearanceRoots = resolveComponentTableRoots(rootBiObject, allObjects);
            for (BiObject appearanceRoot : appearanceRoots) {
                Map<Long, List<Disease>> bridgeDiseaseMap = new LinkedHashMap<>();
                collectDiseases(appearanceRoot, appearanceRoot, allObjects, subBridgeDiseases, bridgeDiseaseMap);
                boolean skipRootHeading = appearanceRoots.size() == 1
                        && Objects.equals(appearanceRoot.getId(), rootBiObject.getId());
                if (skipRootHeading) {
                    List<BiObject> structures = listComponentChildren(appearanceRoot.getId(), allObjects).stream()
                            .filter(child -> COMPONENT_STRUCTURE_NAMES.contains(child.getName()))
                            .collect(Collectors.toList());
                    if (structures.isEmpty()) {
                        structures = listComponentChildren(appearanceRoot.getId(), allObjects);
                    }
                    // 子桥标题已写出。从上部结构按 level=2 往下写，部件仍是 level=3，
                    // 检测结果表只在 level==3 导出；若从上部结构当 level=1 写，病害会落到空心板等下一级，全部变成「未见明显病害」。
                    for (BiObject structure : structures) {
                        writeBiObjectTreeToWord(document, structure, allObjects, bridgeDiseaseMap,
                                "3.2", 2, chapter3ImageCounter, chapter3TableCounter, cursor, 3,
                                safeText(building.getName()));
                    }
                } else {
                    writeBiObjectTreeToWord(document, appearanceRoot, allObjects, bridgeDiseaseMap,
                            "3.2", 1, chapter3ImageCounter, chapter3TableCounter, cursor, 4,
                            safeText(building.getName()));
                }
            }
        }

        placeholderParagraph.removeRun(0);
        if (placeholderParagraph.getRuns().size() == 0) {
            for (int i = 0; i < document.getParagraphs().size(); i++) {
                if (document.getParagraphs().get(i) == placeholderParagraph) {
                    document.removeBodyElement(i);
                    break;
                }
            }
        }
    }

    /**
     * 收集病害数据，参考ReportControllertest.java中的collectDiseases方法
     */
    private void collectDiseases(BiObject node, BiObject appearanceRoot, List<BiObject> allNodes,
                                 List<Disease> properties, Map<Long, List<Disease>> map) {
        // 找到当前节点所属的 level3 祖先（如自身就是 level3 则返回自身）
        BiObject level3 = findLevel3Ancestor(node, appearanceRoot, allNodes);

        // 把当前节点的病害挂到 level3 名下
        List<Disease> self = properties.stream()
                .filter(d -> d.getBiObjectId() != null && node.getId().equals(d.getBiObjectId()))
                .collect(Collectors.toList());
        map.computeIfAbsent(level3.getId(), k -> new ArrayList<>()).addAll(self);

        // 继续向下收集
        List<BiObject> children = allNodes.stream()
                .filter(o -> node.getId().equals(o.getParentId()))
                .collect(Collectors.toList());
        for (BiObject child : children) {
            collectDiseases(child, appearanceRoot, allNodes, properties, map);
        }
    }

    /**
     * 已维修、未找到的病害不写入检测结果表，也不导出对应照片。
     */
    private static boolean isExcludedFromInspectionResultTable(Disease disease) {
        if (disease == null || disease.getDevelopmentTrend() == null) {
            return false;
        }
        String trend = disease.getDevelopmentTrend().trim();
        return "已维修".equals(trend) || "未找到".equals(trend);
    }

    /**
     * 递归写入树结构和病害信息
     *
     * @param document   Word文档
     * @param node       当前节点
     * @param allNodes   所有节点
     * @param diseaseMap 病害映射
     * @param prefix     章节前缀
     * @param level      当前层级
     * @param cursor     XML游标，指定内容插入位置，如果为null则追加到文档末尾
     * @throws Exception 异常
     */

    private void writeBiObjectTreeToWord(XWPFDocument document, BiObject node, List<BiObject> allNodes,
                                         Map<Long, List<Disease>> diseaseMap, String prefix, int level,
                                         AtomicInteger chapterImageCounter, AtomicInteger chapter3TableCounter,
                                         XmlCursor cursor, int baseHeadingLevel, String bridgeName) throws Exception {
        if (level > 3) {
            return; // 不再写标题，也不再递归写标题
        }
        if (node.getName().equals("其他") || node.getName().equals("附属设施")) {
            return;
        }

        // 写目录标题，根据是否有游标决定在哪里创建段落
        XWPFParagraph p;
        if (cursor != null) {
            p = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            p = document.createParagraph();
        }
        int actualHeadingLevel = baseHeadingLevel + level;
        String headingStyle = String.valueOf(Math.min(actualHeadingLevel, 9));
        p.setStyle(headingStyle);

        // 设置段落左对齐
        p.setAlignment(ParagraphAlignment.LEFT);

        // 设置缩进 - 根据需要选择一种方式
        CTPPr ppr = p.getCTP().getPPr();
        if (ppr == null) ppr = p.getCTP().addNewPPr();
        CTInd ind = ppr.isSetInd() ? ppr.getInd() : ppr.addNewInd();

        ind.setFirstLine(BigInteger.valueOf(0));
        ind.setLeft(BigInteger.valueOf(0));

        // 创建标题运行
        XWPFRun run = p.createRun();
        run.setText(node.getName());
        applyHeadingFormat(p, actualHeadingLevel);

        // 写病害信息；已维修、未找到不进检测结果表，对应照片也不导出
        List<Disease> nodeDiseases = diseaseMap.getOrDefault(node.getId(), List.of()).stream()
                .filter(d -> !isExcludedFromInspectionResultTable(d))
                .collect(Collectors.toList());

        // 查询所有组件
        List<Long> componentIds = nodeDiseases.stream()
                .map(Disease::getComponentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Component> components = componentService.selectComponentsByIds(componentIds);

        Map<Long, Component> componentMap = components.stream()
                .collect(Collectors.toMap(Component::getId, c -> c));
        // 给病害 按照构件编号 排序
        nodeDiseases.sort((d1, d2) -> {

            // -----------------------
            // 按 Component.code 排
            // -----------------------
            Component c1 = componentMap.get(d1.getComponentId());
            Component c2 = componentMap.get(d2.getComponentId());

            String code1 = (c1 != null ? c1.getCode() : "");
            String code2 = (c2 != null ? c2.getCode() : "");

            return compareCodes(code1, code2);
        });

        // 提前收集所有病害的图片书签信息
        Map<Long, List<String>> diseaseImageRefs = new HashMap<>(); // key: 病害ID, value: 图片书签列表

        for (Disease d : nodeDiseases) {
            List<String> imageBookmarks = new ArrayList<>();
            List<Map<String, Object>> images = ReportGenerateTools.getDiseaseImage(d.getId());
            if (images != null) {
                for (Map<String, Object> img : images) {
                    if (Boolean.TRUE.equals(img.get("isImage"))) {
                        // 为每张病害图片创建书签，暂时先创建占位符
                        // 实际的图片标题域将在插入图片时创建
                        String componentName = "";
                        Component component = componentService.selectComponentById(d.getComponentId());
                        if (component != null) {
                            componentName = component.getName();
                        }
                        // 照片 图注 的 描述 不需要带病害规范号。
                        String type = d.getType().substring(d.getType().lastIndexOf("#") + 1);
                        String imageDesc = componentName + d.getPosition() + type;
                        // 使用第3章编号规则
                        String bookmark = "Figure_Chapter3_" + UUID.randomUUID() + "_" + chapterImageCounter.getAndIncrement();
                        imageBookmarks.add(bookmark + "|" + imageDesc); // 书签名和描述用|分隔
                    }
                }
            }
            diseaseImageRefs.put(d.getId(), imageBookmarks);
        }

        // 如果存在病害信息，则生成介绍段落和表格
        if (!nodeDiseases.isEmpty() && level == 3) {
            // 现在域本身包含完整格式，不需要额外的序号映射
            // 创建介绍段落，使用游标
            XWPFParagraph introPara;
            if (cursor != null) {
                introPara = document.insertNewParagraph(cursor);
                cursor.toNextToken();
            } else {
                introPara = document.createParagraph();
            }

            // Part 1: 加粗的开头部分
            XWPFRun runBold = introPara.createRun();
            runBold.setText("经检查，" + node.getName() + " 主要病害为:");
            runBold.setBold(true);
            applyBodyFormat(introPara, true);

            // Part 2: 生成病害小结

            log.info("开始生成病害小结");
            String diseaseString = "";
            try {
                diseaseString = getDiseaseSummary(nodeDiseases);
                diseaseString = normalizeDiseaseSummary(diseaseString, node.getName());
            } catch (Exception e) {
                log.error("ai小结病害失败，使用原始病害拼接兜底: node={}, url={}{}",
                        node.getName(), SpringAiUrl, "/api-ai/diseaseSummary", e);
            }
            if (diseaseString == null || diseaseString.trim().isEmpty()) {
                diseaseString = ReportTemplateValueUtils.buildDiseaseSummary(nodeDiseases);
                log.info("病害小结使用本地拼接: node={}, text={}", node.getName(), diseaseString);
            }

            testConclusionService.cacheDiseaseSummary(node.getId(), diseaseString);

            log.info("生成结束");

            String[] lines = diseaseString.split("\\r?\\n");
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                XWPFParagraph diseasePara;
                if (cursor != null) {
                    diseasePara = document.insertNewParagraph(cursor);
                    cursor.toNextToken();
                } else {
                    diseasePara = document.createParagraph();
                }
                XWPFRun runItem = diseasePara.createRun();
                runItem.setText(line.trim());
                applyBodyFormat(diseasePara, true);
            }

            // Part 3: 表格引用部分
            XWPFParagraph tableRefPara;
            if (cursor != null) {
                tableRefPara = document.insertNewParagraph(cursor);
                cursor.toNextToken();
            } else {
                tableRefPara = document.createParagraph();
            }

            String tableTitle = (isBlank(bridgeName) ? "" : bridgeName) + node.getName() + "结构检测结果表";
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(
                    document, tableTitle, cursor, 3, chapter3TableCounter, 21, 360, false, 0);

            WordFieldUtils.createChapterTableReference(tableRefPara, tableBookmark, "具体检测结果见下表", ":");
            applyBodyFormat(tableRefPara, true);

            // 创建表格
            XWPFTable table;
            if (cursor != null) {
                table = document.insertNewTbl(cursor);
                cursor.toNextToken();
                // 初始化表格结构
                for (int i = 0; i < 8; i++) {
                    if (i == 0) {
                        table.getRow(0).getCell(i);
                    } else {
                        table.getRow(0).addNewTableCell();
                    }
                }
            } else {
                table = document.createTable(1, 8);
            }

            // 设置表格边框
            CTTblPr tblPr = table.getCTTbl().getTblPr();
            if (tblPr == null) {
                tblPr = table.getCTTbl().addNewTblPr();
            }
            CTTblBorders borders = tblPr.addNewTblBorders();
            borders.addNewBottom().setVal(STBorder.SINGLE);
            borders.addNewLeft().setVal(STBorder.SINGLE);
            borders.addNewRight().setVal(STBorder.SINGLE);
            borders.addNewTop().setVal(STBorder.SINGLE);
            borders.addNewInsideH().setVal(STBorder.SINGLE);
            borders.addNewInsideV().setVal(STBorder.SINGLE);

            CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
            jc.setVal(STJcTable.CENTER);

            // 设置表格宽度为页面宽度（关键修改）
            CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
            tblWidth.setW(BigInteger.valueOf(9534));
            tblWidth.setType(STTblWidth.DXA);

            // 设置表头
            XWPFTableRow headerRow = table.getRow(0);

            // 表头文本数组
            String[] headers = {"序号", "缺损位置", "缺损类型", "数量", "病害描述", "评定类别 (1~5)", "发展趋势", "照片"};

            // 修改列宽比例，确保总和不超过页面宽度
            Double[] columnWidthRatios = {0.08, 0.14, 0.14, 0.08, 0.26, 0.10, 0.08, 0.12};
            int totalWidth = 9534;

            CTTblLayoutType tblLayout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
            tblLayout.setType(STTblLayoutType.FIXED);

            // 设置每列
            for (int i = 0; i < headers.length; i++) {
                XWPFTableCell cell = headerRow.getCell(i);

                // 清除内容（更安全的清除方式）
                for (int j = cell.getParagraphs().size() - 1; j >= 0; j--) {
                    cell.removeParagraph(j);
                }

                // 设置文本样式
                XWPFParagraph paragraph = cell.addParagraph();
                ReportGenerateTools.setSingleLineSpacing(paragraph);  // 设置单倍行距
                paragraph.setAlignment(ParagraphAlignment.CENTER);

                XWPFRun run1 = paragraph.createRun();
                run1.setText(headers[i]);
                run1.setBold(true);

                // 设置中英文字体和字号
                ReportGenerateTools.setMixedFontFamily(run1, 21);  // 10.5pt

                // 设置列宽
                CTTc cttc = cell.getCTTc();
                CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();

                if (tcPr.isSetTcW()) {
                    tcPr.unsetTcW(); // 关键：去掉默认宽度
                }

                // 计算每列的实际宽度
                int columnWidth = (int) Math.round(totalWidth * columnWidthRatios[i]);
                CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                tcW.setW(BigInteger.valueOf(columnWidth));
                tcW.setType(STTblWidth.DXA);

                // 防止内容换行（可选）
                tcPr.addNewNoWrap();
                // 垂直居中 12.16 修改
                if (!tcPr.isSetVAlign()) {
                    tcPr.addNewVAlign().setVal(STVerticalJc.CENTER);
                }
            }

            // 设置标题行在跨页时重复显示
            ReportGenerateTools.setTableHeaderRepeat(headerRow);

            // 填充数据行
            int seqNum = 1;
            for (Disease d : nodeDiseases) {
                XWPFTableRow dataRow = table.createRow();

                // 为数据行的每个单元格设置相同的宽度
                for (int i = 0; i < headers.length; i++) {
                    XWPFTableCell cell = dataRow.getCell(i);

                    // 设置单元格宽度与表头一致
                    CTTc cttc = cell.getCTTc();
                    CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();
                    int columnWidth = (int) Math.round(totalWidth * columnWidthRatios[i]);
                    CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                    tcW.setW(BigInteger.valueOf(columnWidth));
                    tcW.setType(STTblWidth.DXA);

                    // 设置单元格内容居中
                    XWPFParagraph cellP = cell.getParagraphs().get(0);
                    ReportGenerateTools.setSingleLineSpacing(cellP);  // 设置单倍行距
                    cellP.setAlignment(ParagraphAlignment.CENTER);

                    // 垂直居中 12.16修改。
                    CTTcPr curTcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr()
                            : cell.getCTTc().addNewTcPr();
                    if (!curTcPr.isSetVAlign()) {
                        curTcPr.addNewVAlign().setVal(STVerticalJc.CENTER);
                    }

                    // 设置文本内容
                    XWPFRun cellR = cellP.createRun();

                    // 设置中英文字体和字号
                    ReportGenerateTools.setMixedFontFamily(cellR, 21);  // 10.5pt
                    Component component = componentService.selectComponentById(d.getComponentId());
                    switch (i) {
                        case 0:
                            cellR.setText(String.valueOf(seqNum++));
                            break;
                        case 1:
                            cellR.setText(component != null ? component.getName() : "/");
                            break;
                        case 2:
                            // 12.25 修改 ,额外处理裂缝类型
                            cellR.setText(ReportGenerateTools.reportDiseaseTypeNameIfCrack(d) != null ? ReportGenerateTools.reportDiseaseTypeNameIfCrack(d) : "/");
                            break;
                        case 3:
                            cellR.setText(d.getQuantity() > 0 ? String.valueOf(d.getQuantity()) : "/");
                            break;
                        case 4:
                            cellR.setText(d.getDescription() != null ? d.getDescription() : "/");
                            break;
                        case 5:
                            cellR.setText(d.getLevel() > 0 ? String.valueOf(d.getLevel()) : "/");
                            break;
                        case 6:
                            cellR.setText(d.getDevelopmentTrend());
                            break;
                        case 7:
                            // 获取该病害对应的所有图片书签信息
                            List<String> refs = diseaseImageRefs.getOrDefault(d.getId(), new ArrayList<>());
                            // 从书签信息中提取书签名，创建图片引用域
                            if (!refs.isEmpty()) {
                                // 清除现有内容
                                cellP.removeRun(cellP.getRuns().size() - 1);

                                for (int j = 0; j < refs.size(); j++) {
                                    String[] parts = refs.get(j).split("\\|");
                                    String bookmarkName = parts[0];

                                    if (j > 0) {
                                        XWPFRun commaRun = cellP.createRun();
                                        commaRun.setText(",");
                                        ReportGenerateTools.setMixedFontFamily(commaRun, 21);  // 10.5pt
                                    }

                                    // 创建章节格式的图片引用域，添加"图"字前缀
                                    WordFieldUtils.createChapterFigureReference(cellP, bookmarkName, "图", "");
                                }
                            } else {
                                cellR.setText("/");
                            }
                            break;
                    }
                }
            }

            // 在表格下方插入病害图片
            if (!nodeDiseases.isEmpty()) {
                insertDiseaseImagesWithStreaming(document, nodeDiseases, diseaseImageRefs, cursor);
            }
        } else if (level == 3) {
            // 没有病害信息，显示未见明显病害的说明
            XWPFParagraph noDiseasePara;
            if (cursor != null) {
                noDiseasePara = document.insertNewParagraph(cursor);
                cursor.toNextToken();
            } else {
                noDiseasePara = document.createParagraph();
            }

            // 设置1.5倍行距
            CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
            spacing.setLine(BigInteger.valueOf(360)); // 1.5倍行距

            // 添加文本内容
            XWPFRun noDiseaseRun = noDiseasePara.createRun();
            noDiseaseRun.setText("经检查，" + node.getName() + "未见明显病害。");
            applyBodyFormat(noDiseasePara, true);

            // 插入当前结构的现状照片
            try {
                insertBiObjectStatusImages(document, node, chapterImageCounter, cursor);
            } catch (Exception e) {
                log.error("插入BiObject现状照片失败", e);
            }
        }

        // 按结构树 orderNum 出标题，与评定部件顺序一致：上部结构→下部结构→桥面系，
        // 以及承重构件、一般构件、支座、桥墩、桥台等，不能按名称拼音排。
        List<BiObject> children = ReportTemplateValueUtils.sortedReportChildren(allNodes, node.getId());

        int idx = 1;
        for (BiObject child : children) {
            writeBiObjectTreeToWord(document, child, allNodes, diseaseMap, prefix + "." + idx, level + 1, chapterImageCounter, chapter3TableCounter, cursor, baseHeadingLevel, bridgeName);
            idx++;
        }
    }


    public String getDiseaseSummary(List<Disease> diseases) throws JsonProcessingException {
        // 瘦身
        List<Disease2ReportSummaryAiVO> less = Disease2ReportSummaryAiVO.convert(diseases);
        // 序列化为JSON字符串
        ObjectMapper mapper = new ObjectMapper();
        String diseasesJson = mapper.writeValueAsString(less);
        // 发送POST请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(diseasesJson, headers);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.postForObject(SpringAiUrl + "/api-ai" + "/diseaseSummary", request, String.class);
        return response;
    }

    private String normalizeDiseaseSummary(String diseaseSummary, String nodeName) {
        if (diseaseSummary == null) {
            return "";
        }

        String[] lines = diseaseSummary.replace("\r\n", "\n").replace("\r", "\n").split("\\n+");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String cleanedLine = line.trim().replaceFirst("^\\d+[）).、，,]\\s*", "");
            if (cleanedLine.isEmpty()) {
                continue;
            }
            builder.append(cleanedLine);
        }
        String normalized = builder.toString();

        if (nodeName != null) {
            String[] prefixes = {
                    nodeName + "：",
                    nodeName + ":",
                    nodeName + "，",
                    nodeName + ","
            };
            for (String prefix : prefixes) {
                if (normalized.startsWith(prefix)) {
                    normalized = normalized.substring(prefix.length());
                    break;
                }
            }
        }

        return normalized.trim();
    }


    /**
     * 找到第三层祖先节点（相对外观检测出表根，例如主桥/引桥）。
     */
    private BiObject findLevel3Ancestor(BiObject node, BiObject appearanceRoot, List<BiObject> allNodes) {
        BiObject cur = node;
        int lv = getRelativeLevel(cur, appearanceRoot, allNodes);
        while (lv > 3 && cur.getParentId() != null
                && (appearanceRoot == null || !cur.getId().equals(appearanceRoot.getId()))) {
            BiObject finalCur = cur;
            BiObject parent = allNodes.stream()
                    .filter(o -> o.getId().equals(finalCur.getParentId()))
                    .findFirst()
                    .orElse(null);
            if (parent == null && appearanceRoot != null && appearanceRoot.getId().equals(cur.getParentId())) {
                parent = appearanceRoot;
            }
            if (parent == null) {
                break;
            }
            cur = parent;
            lv--;
        }
        return cur;
    }

    /**
     * 相对出表根的层级：根=1，上部结构=2，部件=3。
     */
    private int getRelativeLevel(BiObject node, BiObject ancestor, List<BiObject> allNodes) {
        if (node == null) {
            return 0;
        }
        if (ancestor != null && node.getId().equals(ancestor.getId())) {
            return 1;
        }
        Map<Long, BiObject> byId = allNodes.stream()
                .filter(object -> object.getId() != null)
                .collect(Collectors.toMap(BiObject::getId, object -> object, (left, right) -> left));
        if (ancestor != null && ancestor.getId() != null) {
            byId.put(ancestor.getId(), ancestor);
        }
        int level = 1;
        BiObject current = node;
        while (current != null && (ancestor == null || !current.getId().equals(ancestor.getId()))) {
            Long parentId = current.getParentId();
            if (parentId == null) {
                break;
            }
            BiObject parent = byId.get(parentId);
            if (parent == null) {
                break;
            }
            current = parent;
            level++;
            if (level > 20) {
                break;
            }
        }
        return level;
    }

    /**
     * 获取节点层级
     */
    private int getLevel(BiObject node, List<BiObject> allNodes) {
        int level = 2;
        BiObject p = node;
        while (p.getParentId() != null) {
            BiObject finalP = p;
            p = allNodes.stream()
                    .filter(o -> o.getId().equals(finalP.getParentId()))
                    .findFirst()
                    .orElse(null);
            if (p != null) level++;
            else break;
        }
        return level;
    }


    /**
     * 处理评定结果：大桥总述 + 每座子桥一段 + 按子桥顺序出表。
     */
    private void handleEvaluationResults(XWPFDocument document, String key, String groupName, List<Task> groupTasks) {
        try {
            log.info("开始处理评定结果, key: {}", key);
            StringBuilder content = new StringBuilder();
            content.append("依据《公路桥梁技术状况评定标准》（JTG/T H21-2011）规定评定方法，")
                    .append(groupName)
                    .append("的技术状况评定结果如下：\n");

            Map<Long, BiEvaluation> evaluationMap = new LinkedHashMap<>();
            Integer minSystemLevel = null;
            for (Task task : groupTasks) {
                BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
                Building building = task.getBuilding();
                String bridgeName = building != null && building.getName() != null ? building.getName() : "桥梁";
                if (evaluation == null) {
                    content.append("未找到").append(bridgeName).append("的评定结果。\n");
                    continue;
                }
                evaluationMap.put(task.getId(), evaluation);
                if (evaluation.getSystemLevel() != null
                        && (minSystemLevel == null || evaluation.getSystemLevel() > minSystemLevel)) {
                    minSystemLevel = evaluation.getSystemLevel();
                }
                content.append(bridgeName)
                        .append("上部结构技术状况评分为")
                        .append(formatScore(evaluation.getSuperstructureScore()))
                        .append("分，等级为")
                        .append(evaluation.getSuperstructureLevel())
                        .append("类；下部结构技术状况评分为")
                        .append(formatScore(evaluation.getSubstructureScore()))
                        .append("分，等级为")
                        .append(evaluation.getSubstructureLevel())
                        .append("类；桥面系技术状况评分为")
                        .append(formatScore(evaluation.getDeckSystemScore()))
                        .append("分，等级为")
                        .append(evaluation.getDeckSystemLevel())
                        .append("类；全桥技术状况评分为")
                        .append(formatScore(evaluation.getSystemScore()))
                        .append("分，评定为")
                        .append(evaluation.getSystemLevel())
                        .append("类桥梁。\n");
            }
            if (minSystemLevel != null) {
                content.append("全桥技术状况评定按照评定单元最低分进行评定，因此评定为")
                        .append(minSystemLevel)
                        .append("类。\n");
            }
            content.append("技术状况评定记录和具体评分见下表所示。");

            XWPFParagraph paragraph = insertEvaluationContent(document, key, content.toString());
            for (Task task : groupTasks) {
                BiEvaluation evaluation = evaluationMap.get(task.getId());
                if (evaluation == null) {
                    continue;
                }
                Building building = task.getBuilding();
                String bridgeName = building != null && building.getName() != null ? building.getName() : "桥梁";
                evaluationTableService.generateEvaluationTableAfterParagraph(
                        document, paragraph, building, evaluation, bridgeName);
            }
            log.info("评定结果和表格处理完成");
        } catch (Exception e) {
            log.error("处理评定结果失败: key={}, error={}", key, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 插入评定文字内容到文档中
     *
     * @param document 文档对象
     * @param key      占位符
     */
    private XWPFParagraph insertEvaluationContent(XWPFDocument document, String key, String content) {
        try {
            // 查找占位符位置
            XWPFParagraph targetParagraph = null;
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph paragraph = paragraphs.get(i);
                String text = paragraph.getText();
                if (text != null && text.contains(key)) {
                    targetParagraph = paragraph;
                    break;
                }
            }

            if (targetParagraph == null) {
                log.warn("未找到占位符: {}", key);
                return null;
            }

            // 清空占位符段落的所有Run
            while (targetParagraph.getRuns().size() > 0) {
                targetParagraph.removeRun(0);
            }

            String[] lines = content.split("\n");
            XWPFParagraph lastParagraph = targetParagraph;
            boolean firstFilled = false;
            XmlCursor cursor = null;
            try {
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    XWPFParagraph paragraph;
                    if (!firstFilled) {
                        paragraph = targetParagraph;
                        firstFilled = true;
                    } else {
                        if (cursor == null) {
                            cursor = lastParagraph.getCTP().newCursor();
                            if (!cursor.toNextSibling()) {
                                cursor.toEndToken();
                                cursor.toNextToken();
                            }
                        }
                        paragraph = document.insertNewParagraph(cursor);
                        cursor.toNextToken();
                    }
                    XWPFRun textRun = paragraph.createRun();
                    textRun.setText(line);
                    applyBodyFormat(paragraph, true);
                    lastParagraph = paragraph;
                }
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            log.info("评定内容插入成功: {}, 插入{}行内容", key, lines.length);

            return lastParagraph;

        } catch (Exception e) {
            log.error("插入评定内容失败: key={}", key, e);
            throw e;
        }
    }


    /**
     * 格式化分数，保留一位小数
     *
     * @param score 分数
     * @return 格式化后的分数字符串
     */
    private String formatScore(java.math.BigDecimal score) {
        if (score == null) {
            return "0.0";
        }
        return String.format("%.1f", score.doubleValue());
    }


    /**
     * 处理大桥章节中用户填写的数据（照片已按大桥上传处理）
     */
    private void processGroupUserData(XWPFDocument document, List<ReportData> reportDataList,
                                      List<Task> groupTasks, Map<String, ReportData> dataMap,
                                      Project project) {
        Task firstTask = groupTasks == null || groupTasks.isEmpty() ? null : groupTasks.get(0);
        Building firstBuilding = firstTask == null ? null : firstTask.getBuilding();
        for (ReportData data : reportDataList) {
            String key = data.getKey();
            String value = data.getValue();
            Integer type = data.getType();
            try {
                if (key != null && (key.contains("leftFront") || key.contains("rightFront") ||
                        key.contains("leftSide") || key.contains("rightSide"))) {
                    continue;
                }
                if ("overallOverview".equals(key) || LineBridgeGroup.DATA_KEY.equals(key)) {
                    continue;
                }
                if (isSurroundingEnvironmentKey(key)) {
                    continue;
                }

                if (type == 0) {
                    if (key != null && key.contains("focusDiseases")) {
                        try {
                            List<ComponentDiseaseType> combinations = parseChooseDiseaseJson(value);
                            if (!combinations.isEmpty()) {
                                generateGroupFocusOnDiseases(document, combinations, groupTasks, project);
                            } else {
                                ReportGenerateTools.replaceText(document, "${focusOnDiseases}", "无重点关注病害");
                            }
                        } catch (Exception e) {
                            log.error("处理大桥病害数据出错: key={}, value={}, error={}", key, value, e.getMessage(), e);
                            ReportGenerateTools.replaceText(document, "${focusOnDiseases}", "【病害数据处理失败，请联系管理员】");
                        }
                    } else {
                        String placeholder = (key != null && key.startsWith("${")) ? key : "${" + key + "}";
                        ReportGenerateTools.replaceText(document, placeholder, value != null ? value : "");
                    }
                }
            } catch (Exception e) {
                log.error("处理字段出错: key={}, type={}, error={}", key, type, e.getMessage(), e);
            }
        }

        try {
            ReportData environmentData = findSurroundingEnvironmentData(dataMap);
            generateBridgeSiteEnvironment(document, groupTasks,
                    environmentData == null ? null : environmentData.getValue());
        } catch (Exception e) {
            log.error("生成桥址周边环境调查内容失败: buildingId={}",
                    firstBuilding == null ? null : firstBuilding.getId(), e);
        }
    }

    /**
     * “桥址周边环境”在历史数据中由两部分组成：
     * <ul>
     *     <li>桥梁信息卡属性树中的“环境条件”，用于报告概述；</li>
     *     <li>本次任务下与桥下空间、跨江/河流、电线、周边建筑等有关的现场记录，
     *     其照片作为 disease 附件保存。</li>
     * </ul>
     */
    private void generateBridgeSiteEnvironment(XWPFDocument document, List<Task> groupTasks,
                                               String manualSummary) throws Exception {
        if (document == null || groupTasks == null || groupTasks.isEmpty()) {
            return;
        }

        XWPFParagraph anchor = findSurroundingEnvironmentAnchor(document);
        if (anchor == null) {
            log.warn("多桥模板未找到“周边环境调查”锚点，跳过桥址周边环境导出");
            return;
        }

        List<Disease> environmentRecords = new ArrayList<>();
        for (Task task : groupTasks) {
            if (task.getBuilding() != null) {
                environmentRecords.addAll(loadBridgeSiteEnvironmentRecords(task.getBuilding(), task));
            }
        }
        Building firstBuilding = groupTasks.get(0).getBuilding();
        String propertySummary = firstBuilding == null ? null : loadEnvironmentCondition(firstBuilding);
        String summary = firstNonBlank(manualSummary, propertySummary, buildEnvironmentSummary(environmentRecords));

        if (isBlank(summary) && environmentRecords.isEmpty()) {
            clearParagraph(anchor);
            return;
        }

        XmlCursor cursor = anchor.getCTP().newCursor();
        if (!isBlank(summary)) {
            XWPFParagraph summaryParagraph = document.insertNewParagraph(cursor);
            cursor.toNextToken();
            applyBodyFormat(summaryParagraph, true);
            summaryParagraph.createRun().setText(normalizeEnvironmentSummary(summary));
        }

        if (!environmentRecords.isEmpty()) {
            Map<Long, List<String>> imageReferences = collectEnvironmentImageReferences(environmentRecords);
            WordFieldUtils.createTableCaption(document, "周边环境检查结果一览表", cursor, 3);
            XWPFTable table = document.insertNewTbl(cursor);
            cursor.toNextToken();
            populateBridgeSiteEnvironmentTable(table, environmentRecords, imageReferences);

            insertDiseaseImagesWithStreaming(document, environmentRecords, imageReferences, cursor);
        }

        clearParagraph(anchor);
    }

    private XWPFParagraph findSurroundingEnvironmentAnchor(XWPFDocument document) {
        XWPFParagraph placeholder = ReportGenerateTools.findParagraphByPlaceholder(document, "${surroundingEnvironment}");
        if (placeholder != null) {
            return placeholder;
        }

        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText();
            if (text != null && text.replaceAll("\\s+", "").contains("周边环境调查")) {
                // 当前资源模板在标题后已有一个空白段落；把它作为插入锚点，避免把内容插到标题前。
                if (i + 1 < paragraphs.size()) {
                    XWPFParagraph nextParagraph = paragraphs.get(i + 1);
                    String nextText = nextParagraph.getText();
                    if (isBlank(nextText) || nextText.contains("${surroundingEnvironment}")) {
                        return nextParagraph;
                    }
                }
                log.warn("“周边环境调查”标题后未预留空白段落，跳过自动插入以保护后续模板内容");
                return null;
            }
        }
        return null;
    }

    private List<Disease> loadBridgeSiteEnvironmentRecords(Building building, Task task) {
        Disease query = new Disease();
        query.setBuildingId(building.getId());
        query.setTaskId(task.getId());
        List<Disease> diseases = diseaseMapper.selectDiseaseList(query);
        if (diseases == null || diseases.isEmpty()) {
            return Collections.emptyList();
        }
        return diseases.stream()
                .filter(this::isBridgeSiteEnvironmentRecord)
                .sorted(Comparator.comparing(Disease::getPosition, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Disease::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    private boolean isBridgeSiteEnvironmentRecord(Disease disease) {
        if (disease == null) {
            return false;
        }
        String text = String.join(" ", safeText(disease.getPosition()), safeText(disease.getType()),
                safeText(disease.getDescription()), safeText(disease.getBiObjectName()),
                disease.getDiseaseType() == null ? "" : safeText(disease.getDiseaseType().getName()));
        return text.contains("桥下") || text.contains("跨江") || text.contains("河流") || text.contains("水沟")
                || text.contains("电线") || text.contains("电缆") || text.contains("建筑") || text.contains("房屋")
                || text.contains("周边") || text.contains("环境") || text.contains("违建") || text.contains("空间占用");
    }

    private String loadEnvironmentCondition(Building building) {
        if (building.getRootPropertyId() == null) {
            return null;
        }
        try {
            return findPropertyValue(propertyService.selectPropertyTree(building.getRootPropertyId()), "环境条件");
        } catch (Exception e) {
            log.warn("读取桥梁环境条件属性失败: buildingId={}", building.getId(), e);
            return null;
        }
    }

    private String findPropertyValue(Property property, String propertyName) {
        if (property == null) {
            return null;
        }
        if (propertyName.equals(property.getName()) && !isBlank(property.getValue())) {
            return property.getValue();
        }
        if (property.getChildren() != null) {
            for (Property child : property.getChildren()) {
                String value = findPropertyValue(child, propertyName);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String buildEnvironmentSummary(List<Disease> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        String descriptions = records.stream()
                .map(this::environmentDescription)
                .filter(value -> !isBlank(value))
                .distinct()
                .collect(Collectors.joining("；"));
        return isBlank(descriptions) ? null : "经现场检查，" + descriptions + "。";
    }

    private String normalizeEnvironmentSummary(String summary) {
        String normalized = summary.trim();
        return normalized.endsWith("。") || normalized.endsWith("；") ? normalized : normalized + "。";
    }

    private void populateBridgeSiteEnvironmentTable(XWPFTable table, List<Disease> records,
                                                    Map<Long, List<String>> imageReferences) {
        int rowCount = records.size() + 1;
        XWPFTableRow firstRow = table.getRow(0);
        while (firstRow.getTableCells().size() < 5) {
            firstRow.createCell();
        }
        for (int i = 1; i < rowCount; i++) {
            XWPFTableRow row = table.createRow();
            while (row.getTableCells().size() < 5) {
                row.createCell();
            }
        }
        applyTableGrid(table, 5);
        applyBridgeSiteEnvironmentTableLayout(table);

        String[] headers = {"序号", "桥梁部位", "周边环境情况描述", "照片编号", "备注"};
        for (int i = 0; i < headers.length; i++) {
            setTableCell(table.getRow(0), i, headers[i], true);
        }

        for (int i = 0; i < records.size(); i++) {
            Disease record = records.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setTableCell(row, 0, String.valueOf(i + 1));
            setTableCell(row, 1, environmentLocation(record));
            setTableCell(row, 2, environmentDescription(record), false, ParagraphAlignment.LEFT);
            setEnvironmentPhotoReferenceCell(row, 3, imageReferences.get(record.getId()));
            setTableCell(row, 4, "/");
        }
    }

    private void applyBridgeSiteEnvironmentTableLayout(XWPFTable table) {
        final int[] widths = {550, 1450, 3950, 1350, 1012};
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }
        CTTblWidth tableWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tableWidth.setW(BigInteger.valueOf(Arrays.stream(widths).sum()));
        tableWidth.setType(STTblWidth.DXA);

        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int width : widths) {
            grid.addNewGridCol().setW(BigInteger.valueOf(width));
        }
        for (XWPFTableRow row : table.getRows()) {
            for (int col = 0; col < widths.length; col++) {
                XWPFTableCell cell = row.getCell(col);
                CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTTblWidth cellWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                cellWidth.setW(BigInteger.valueOf(widths[col]));
                cellWidth.setType(STTblWidth.DXA);
            }
        }
    }

    private Map<Long, List<String>> collectEnvironmentImageReferences(List<Disease> records) {
        Map<Long, List<String>> references = new HashMap<>();
        for (Disease record : records) {
            List<String> itemReferences = new ArrayList<>();
            List<Map<String, Object>> images = ReportGenerateTools.getDiseaseImage(record.getId());
            int imageIndex = 1;
            for (Map<String, Object> image : images) {
                if (Boolean.TRUE.equals(image.get("isImage"))) {
                    String bookmark = "Figure_Environment_" + record.getId() + "_" + imageIndex++;
                    itemReferences.add(bookmark + "|" + buildEnvironmentImageCaption(record));
                }
            }
            references.put(record.getId(), itemReferences);
        }
        return references;
    }

    private void setEnvironmentPhotoReferenceCell(XWPFTableRow row, int cellIndex, List<String> references) {
        if (references == null || references.isEmpty()) {
            setTableCell(row, cellIndex, "/");
            return;
        }
        setTableCell(row, cellIndex, "");
        XWPFParagraph paragraph = row.getCell(cellIndex).getParagraphs().get(0);
        for (int i = 0; i < references.size(); i++) {
            if (i > 0) {
                paragraph.createRun().setText("、");
            }
            String bookmark = references.get(i).split("\\|", 2)[0];
            WordFieldUtils.createChapterFigureReference(paragraph, bookmark, "图 ", "");
        }
    }

    private String environmentLocation(Disease record) {
        return firstNonBlank(record.getPosition(), record.getBiObjectName(), "桥址周边");
    }

    private String environmentDescription(Disease record) {
        return firstNonBlank(record.getDescription(), record.getType(),
                record.getDiseaseType() == null ? null : record.getDiseaseType().getName(), "现场检查记录");
    }

    private String buildEnvironmentImageCaption(Disease record) {
        return environmentLocation(record) + environmentDescription(record);
    }

    private ReportData findSurroundingEnvironmentData(Map<String, ReportData> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            return null;
        }
        for (ReportData data : dataMap.values()) {
            if (data != null && isSurroundingEnvironmentKey(data.getKey())) {
                return data;
            }
        }
        return null;
    }

    private boolean isSurroundingEnvironmentKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("${", "").replace("}", "").trim();
        return "surroundingEnvironment".equals(normalized);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }


    /**
     * 生成大桥重点关注病害内容，病害来自该大桥下全部子桥。
     */
    private void generateGroupFocusOnDiseases(XWPFDocument document, List<ComponentDiseaseType> combinations,
                                              List<Task> groupTasks, Project project) {
        try {
            StringBuilder content = new StringBuilder();
            List<Long> componentIds = combinations.stream()
                    .map(ComponentDiseaseType::getComponentId)
                    .distinct()
                    .collect(Collectors.toList());

            List<Disease> allDiseases = new ArrayList<>();
            Integer year = project == null ? null : project.getYear();
            for (Task task : groupTasks) {
                if (task.getBuilding() == null) {
                    continue;
                }
                List<Disease> diseases = diseaseMapper.selectDiseaseComponentData(
                        componentIds, task.getBuilding().getId(), year);
                if (diseases != null) {
                    allDiseases.addAll(diseases);
                }
            }

            Map<Long, Map<Long, List<Disease>>> groupedMap = allDiseases.stream()
                    .collect(Collectors.groupingBy(
                            Disease::getBiObjectId,
                            Collectors.groupingBy(Disease::getDiseaseTypeId)
                    ));
            int index = 1;
            for (Long biObjectId : groupedMap.keySet()) {
                Map<Long, List<Disease>> diseaseMap = groupedMap.get(biObjectId);
                BiObject tempBiObject = biObjectMapper.selectBiObjectById(biObjectId);
                for (Long diseaseTypeId : diseaseMap.keySet()) {
                    List<Disease> diseases = diseaseMap.get(diseaseTypeId);
                    Disease disease = diseases.get(0);
                    content.append(index).append(")");
                    content.append(tempBiObject.getName());
                    content.append(disease.getType().substring(disease.getType().lastIndexOf('#') + 1));
                    content.append("\n");
                    content.append("成因分析：\n");
                    content.append(getDiseaseCause(disease));
                    content.append("\n");
                    index++;
                }
            }

            ReportGenerateTools.replaceText(document, "${focusOnDiseases}", content.toString().trim());
        } catch (Exception e) {
            log.error("生成大桥重点关注病害内容失败", e);
            ReportGenerateTools.replaceText(document, "${focusOnDiseases}", "【病害分析生成失败】");
        }
    }

    /**
     * 对于单个病害 生成 成因分析
     */
    private String getDiseaseCause(Disease disease) {
        CauseQuery causeQuery = new CauseQuery();
        Building building = buildingService.selectBuildingById(disease.getBuildingId());
        BiObject biObject = biObjectMapper.selectBiObjectById(disease.getBiObjectId());
        BiObject biObject_building = biObjectMapper.selectBiObjectById(building.getRootObjectId());
        BiTemplateObject biTemplateObject = biTemplateObjectService.selectBiTemplateObjectById(biObject_building.getTemplateObjectId());
        causeQuery.setTemplate(biTemplateObject.getName());
        causeQuery.setObject(biObject.getName());
        causeQuery.setParentObject(biObject.getParentName());
        causeQuery.setDescription(disease.getDescription());
        causeQuery.setPosition(disease.getPosition());
        causeQuery.setType(disease.getType());
        return diseaseService.getCauseAnalysis(causeQuery);
    }

    /**
     * 3.1 四张照片来自填报页上传，不再自动取桥梁卡片 newfront/newside。
     */
    private void insertGroupOverviewImages(XWPFDocument document, Map<String, ReportData> dataMap) throws Exception {
        String leftFront = resolveUploadedImageName(dataMap, "leftFront");
        String rightFront = resolveUploadedImageName(dataMap, "rightFront");
        String leftSide = resolveUploadedImageName(dataMap, "leftSide");
        String rightSide = resolveUploadedImageName(dataMap, "rightSide");

        replaceOverviewImage(document, "%{leftFront}", leftFront);
        replaceOverviewImage(document, "%{rightFront}", rightFront);
        replaceOverviewImage(document, "%{leftSide}", leftSide);
        replaceOverviewImage(document, "%{rightSide}", rightSide);
        replaceOverviewImage(document, "${桥梁正面照}", leftFront);
        replaceOverviewImage(document, "${桥梁正面照1}", rightFront != null ? rightFront : leftFront);
        replaceOverviewImage(document, "${桥梁立面照}", leftSide);
        replaceOverviewImage(document, "${桥梁立面照1}", rightSide != null ? rightSide : leftSide);
    }

    private void replaceOverviewImage(XWPFDocument document, String placeholder, String imageFileName) throws Exception {
        if (!isBlank(imageFileName)) {
            ReportGenerateTools.replaceImageInDocument(document, placeholder, imageFileName, null, false);
        } else {
            ReportGenerateTools.replaceText(document, placeholder, "");
        }
    }

    private String resolveUploadedImageName(Map<String, ReportData> dataMap, String key) {
        ReportData data = dataMap == null ? null : dataMap.get(key);
        if (data == null || isBlank(data.getValue())) {
            return null;
        }
        String firstId = data.getValue().split(",")[0].trim();
        if (firstId.isEmpty()) {
            return null;
        }
        try {
            FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(firstId));
            return fileMap == null ? null : fileMap.getNewName();
        } catch (NumberFormatException e) {
            log.warn("概况照片ID无效: key={}, value={}", key, firstId);
            return null;
        }
    }


    /**
     * 一座大桥一张近年评定对比表，行是该大桥下全部子桥。
     */
    private void handleComparisonAnalysis(XWPFDocument document, String key, List<Task> groupTasks) {
        try {
            log.info("开始处理比较分析, key: {}, 子桥数量: {}", key, groupTasks == null ? 0 : groupTasks.size());
            XWPFParagraph targetParagraph = null;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && text.contains(key)) {
                    targetParagraph = paragraph;
                    break;
                }
            }
            if (targetParagraph == null) {
                log.warn("未找到占位符: {}", key);
                return;
            }
            comparisonAnalysisService.generateMultiBridgeComparisonAnalysisTable(document, targetParagraph, groupTasks);
            log.info("比较分析处理完成");
        } catch (Exception e) {
            log.error("处理比较分析失败: key={}, error={}", key, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 解析JSON数据
     *
     * @param jsonValue JSON字符串
     * @return 构件病害类型组合列表
     */
    private List<ComponentDiseaseType> parseChooseDiseaseJson(String jsonValue) {
        List<ComponentDiseaseType> combinations = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, List<Integer>>> selectedData = objectMapper.readValue(jsonValue, List.class);

            for (Map<String, List<Integer>> item : selectedData) {
                for (Map.Entry<String, List<Integer>> entry : item.entrySet()) {
                    Long componentId = Long.parseLong(entry.getKey());
                    List<Integer> diseaseTypeIds = entry.getValue();

                    for (Integer diseaseTypeId : diseaseTypeIds) {
                        combinations.add(new ComponentDiseaseType(null, componentId, diseaseTypeId.longValue()));
                    }
                }
            }

            log.info("解析JSON数据成功，共{}个构件病害类型组合", combinations.size());
        } catch (Exception e) {
            log.error("解析第七章JSON数据失败: {}", jsonValue, e);
        }
        return combinations;
    }


    /**
     * 插入一个空行：单倍行距，段前段后为 0。用于检测结果表与图片之间的换行。
     */
    private void insertSingleLineBreak(XWPFDocument document, XmlCursor cursor) {
        XWPFParagraph paragraph;
        if (cursor != null) {
            paragraph = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            paragraph = document.createParagraph();
        }
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr == null) {
            ppr = paragraph.getCTP().addNewPPr();
        }
        if (ppr.isSetInd()) {
            ppr.unsetInd();
        }
        CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(240));
        spacing.setLineRule(STLineSpacingRule.AUTO);
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
        if (spacing.isSetBeforeLines()) {
            spacing.unsetBeforeLines();
        }
        if (spacing.isSetAfterLines()) {
            spacing.unsetAfterLines();
        }
    }

    /**
     * 使用流式处理方式插入病害图片
     *
     * @param document         Word文档
     * @param diseases         病害列表
     * @param diseaseImageRefs 已收集的病害图片序号映射
     * @param cursor           XML游标，指定内容插入位置，如果为null则追加到文档末尾
     * @throws Exception 异常
     */
    private void insertDiseaseImagesWithStreaming(XWPFDocument document, List<Disease> diseases,
                                                  Map<Long, List<String>> diseaseImageRefs, XmlCursor cursor) throws Exception {
        // 收集所有图片信息
        List<Pair<String, String>> allImages = new ArrayList<>(); // <图片文件名, 标题>

        // 逐个处理每个病害的图片
        for (Disease d : diseases) {
            List<Map<String, Object>> images = ReportGenerateTools.getDiseaseImage(d.getId());
            if (images == null || images.isEmpty()) {
                continue;
            }

            // 获取该病害的图片书签信息
            List<String> imageRefs = diseaseImageRefs.getOrDefault(d.getId(), new ArrayList<>());
            int refIndex = 0;

            for (Map<String, Object> img : images) {
                if (Boolean.TRUE.equals(img.get("isImage"))) {
                    // 提取图片URL和文件名
                    String url = (String) img.get("url");
                    String newName = url.substring(url.lastIndexOf("/") + 1);

                    // 使用已收集的图片书签信息
                    if (refIndex >= imageRefs.size()) {
                        continue; // 跳过没有书签的图片
                    }

                    String[] parts = imageRefs.get(refIndex).split("\\|");
                    String bookmarkName = parts[0];
                    String imageDesc = parts.length > 1 ? parts[1] : "";
                    refIndex++;

                    // 将图片信息添加到列表，包含书签名
                    allImages.add(Pair.of(newName, bookmarkName + "|" + imageDesc));
                }
            }
        }

        if (allImages.isEmpty()) {
            return;
        }

        // 检测结果表与图片之间空一行：单倍行距，段前段后为 0
        insertSingleLineBreak(document, cursor);

        // 计算需要的行数：每行放2张图片，需要图片行+标题行
        int imagesPerRow = 2;
        int totalImageRows = (int) Math.ceil((double) allImages.size() / imagesPerRow);
        int totalRows = totalImageRows * 2; // 每组图片需要两行：图片行+标题行

        // 创建表格
        XWPFTable table;
        if (cursor != null) {
            table = document.insertNewTbl(cursor);
            cursor.toNextToken();

            // 初始化第一行：确保有2列
            XWPFTableRow row0 = table.getRow(0);
            row0.getCell(0);
            row0.addNewTableCell(); // 添加第二列

            // 创建其余行
            for (int i = 1; i < totalRows; i++) {
                XWPFTableRow newRow = table.createRow();

                // 确保新行有且仅有2列
                int currentCells = newRow.getTableCells().size();
                if (currentCells < 2) {
                    for (int j = currentCells; j < 2; j++) {
                        newRow.addNewTableCell();
                    }
                } else if (currentCells > 2) {
                    for (int j = currentCells - 1; j >= 2; j--) {
                        newRow.removeCell(j);
                    }
                }
            }
        } else {
            table = document.createTable(totalRows, 2);
        }

        // 设置表格样式
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // 设置表格宽度为页面宽度的100%，避免右侧留白
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(9534)); // 使用100%宽度
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格边框样式
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();

        // 设置外边框
        CTBorder topBorder = borders.addNewTop();
        topBorder.setVal(STBorder.NONE);

        CTBorder bottomBorder = borders.addNewBottom();
        bottomBorder.setVal(STBorder.NONE);

        CTBorder leftBorder = borders.addNewLeft();
        leftBorder.setVal(STBorder.NONE);

        CTBorder rightBorder = borders.addNewRight();
        rightBorder.setVal(STBorder.NONE);

        // 设置内部边框
        CTBorder insideH = borders.addNewInsideH();
        insideH.setVal(STBorder.NONE);

        CTBorder insideV = borders.addNewInsideV();
        insideV.setVal(STBorder.NONE);

        CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJcTable.CENTER);

        // 设置列宽相等（每列占50%宽度）
        int cellWidth = 4767;

        // 设置所有单元格的样式
        for (int row = 0; row < totalRows; row++) {
            for (int col = 0; col < 2; col++) {
                XWPFTableCell cell = table.getRow(row).getCell(col);
                CTTc ctTc = cell.getCTTc();
                CTTcPr tcPr = ctTc.isSetTcPr() ? ctTc.getTcPr() : ctTc.addNewTcPr();

                // 设置单元格宽度
                CTTblWidth cellWidthObj = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                cellWidthObj.setW(BigInteger.valueOf(cellWidth));
                cellWidthObj.setType(STTblWidth.DXA);

                // 设置单元格垂直居中
                CTVerticalJc vJc = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
                vJc.setVal(STVerticalJc.CENTER);

                // 清除默认段落
                if (cell.getParagraphs().size() > 0) {
                    for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
                        cell.removeParagraph(i);
                    }
                }

                // 添加新段落，居中对齐
                XWPFParagraph para = cell.addParagraph();
                ReportGenerateTools.setSingleLineSpacing(para);  // 设置单倍行距
                para.setAlignment(ParagraphAlignment.CENTER);
            }
        }

        // 填充图片和标题
        int imageIndex = 0;
        for (int groupIndex = 0; groupIndex < totalImageRows; groupIndex++) {
            int imageRowIndex = groupIndex * 2;     // 图片行索引
            int titleRowIndex = groupIndex * 2 + 1; // 标题行索引

            // 在当前图片行填充最多2张图片
            for (int col = 0; col < 2 && imageIndex < allImages.size(); col++) {
                Pair<String, String> imageInfo = allImages.get(imageIndex);
                String fileName = imageInfo.getLeft();
                String titleInfo = imageInfo.getRight();

                // 解析书签名和描述
                String[] parts = titleInfo.split("\\|");
                String bookmarkName = parts[0];
                String imageDesc = parts.length > 1 ? parts[1] : "";

                // 获取图片单元格和标题单元格
                XWPFTableCell imageCell = table.getRow(imageRowIndex).getCell(col);
                XWPFTableCell titleCell = table.getRow(titleRowIndex).getCell(col);

                // 在图片单元格中添加图片
                XWPFParagraph imagePara = imageCell.getParagraphs().get(0);
                XWPFRun imageRun = imagePara.createRun();

                log.info("开始插入图片：{}", fileName);
                try (InputStream imageStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(fileName.substring(0, 2) + "/" + fileName)
                                .build())) {

                    // 统一图片大小
//                    int imgWidth = 7 * 360000;
                    // 11.11 修改 ， 所有图片 除了附表 和 封面 ，统一 8 cm x 6 cm
                    int imgWidth = 8 * 360000;
                    int imgHeight = 6 * 360000;

                    imageRun.addPicture(
                            imageStream,
                            XWPFDocument.PICTURE_TYPE_JPEG,
                            "disease.jpg",
                            imgWidth,
                            imgHeight
                    );
                    log.info("插入图片结束：{}", fileName);
                } catch (Exception e) {
                    log.error("插入病害图片失败", e);
                    continue;
                }

                // 在标题单元格中添加图片标题域
                XWPFParagraph titlePara = titleCell.getParagraphs().get(0);
                titlePara.setAlignment(ParagraphAlignment.CENTER);

                // 清除现有内容
                while (titlePara.getRuns().size() > 0) {
                    titlePara.removeRun(0);
                }

                // 在现有段落中创建图片标题域，使用第3章编号和指定的书签名
                WordFieldUtils.createFigureCaptionInParagraph(titlePara, imageDesc, 3, bookmarkName, 240, 0);

                imageIndex++;
            }

            // 如果当前行只有一张图片，需要清空第二列的内容
            if (imageIndex == allImages.size() && (imageIndex - 1) % 2 == 0) {
                // 最后一张图片在第一列，第二列保持空白
                XWPFTableCell emptyImageCell = table.getRow(imageRowIndex).getCell(1);
                XWPFTableCell emptyTitleCell = table.getRow(titleRowIndex).getCell(1);

                // 确保空单元格有段落但无内容
                if (emptyImageCell.getParagraphs().isEmpty()) {
                    emptyImageCell.addParagraph();
                }
                if (emptyTitleCell.getParagraphs().isEmpty()) {
                    emptyTitleCell.addParagraph();
                }
            }
        }

        // 在表格后添加空段落
        XWPFParagraph spacerAfter;
        if (cursor != null) {
            spacerAfter = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            spacerAfter = document.createParagraph();
        }
        spacerAfter.setSpacingAfter(300);
    }


    /**
     * 插入BiObject的现状照片（type=8）
     *
     * @param document            Word文档
     * @param biObject            BiObject对象
     * @param chapterImageCounter 图片计数器，用于生成图片编号
     * @param cursor              XML游标，指定内容插入位置，如果为null则追加到文档末尾
     * @throws Exception 异常
     */
    private void insertBiObjectStatusImages(XWPFDocument document, BiObject biObject,
                                            AtomicInteger chapterImageCounter, XmlCursor cursor) throws Exception {
        // 收集所有现状照片信息
        List<Pair<String, String>> allImages = new ArrayList<>(); // <图片文件名, 标题>

        // 获取BiObject的现状照片（type=8）
        try {
            List<FileMap> photoList = fileMapService.selectBiObjectPhotoList(biObject.getId());
            if (photoList == null || photoList.isEmpty()) {
                return;
            }

            // 为每张照片生成书签名和标题
            for (FileMap fileMap : photoList) {
                String newName = fileMap.getNewName();
                // 生成书签名
                String bookmarkName = "fig_status_" + chapterImageCounter.getAndIncrement();
                // 获取照片备注作为描述，如果没有则使用默认描述
                String imageDesc = fileMap.getAttachmentRemark() != null && !fileMap.getAttachmentRemark().isEmpty()
                        ? fileMap.getAttachmentRemark()
                        : biObject.getName() + "现状照片";

                // 将图片信息添加到列表，包含书签名和描述
                allImages.add(Pair.of(newName, bookmarkName + "|" + imageDesc));
            }
        } catch (Exception e) {
            log.error("获取BiObject现状照片失败", e);
            return;
        }

        if (allImages.isEmpty()) {
            return;
        }

        // 检测结果表与图片之间空一行：单倍行距，段前段后为 0
        insertSingleLineBreak(document, cursor);

        // 计算需要的行数：每行放2张图片，需要图片行+标题行
        int imagesPerRow = 2;
        int totalImageRows = (int) Math.ceil((double) allImages.size() / imagesPerRow);
        int totalRows = totalImageRows * 2; // 每组图片需要两行：图片行+标题行

        // 创建表格
        XWPFTable table;
        if (cursor != null) {
            table = document.insertNewTbl(cursor);
            cursor.toNextToken();

            // 初始化第一行：确保有2列
            XWPFTableRow row0 = table.getRow(0);
            row0.getCell(0);
            row0.addNewTableCell(); // 添加第二列

            // 创建其余行
            for (int i = 1; i < totalRows; i++) {
                XWPFTableRow newRow = table.createRow();

                // 确保新行有且仅有2列
                int currentCells = newRow.getTableCells().size();
                if (currentCells < 2) {
                    for (int j = currentCells; j < 2; j++) {
                        newRow.addNewTableCell();
                    }
                } else if (currentCells > 2) {
                    for (int j = currentCells - 1; j >= 2; j--) {
                        newRow.removeCell(j);
                    }
                }
            }
        } else {
            table = document.createTable(totalRows, 2);
        }

        // 设置表格样式
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // 设置表格宽度为页面宽度的100%，避免右侧留白
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(9534)); // 使用100%宽度
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格边框样式
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();

        // 设置外边框
        CTBorder topBorder = borders.addNewTop();
        topBorder.setVal(STBorder.NONE);

        CTBorder bottomBorder = borders.addNewBottom();
        bottomBorder.setVal(STBorder.NONE);

        CTBorder leftBorder = borders.addNewLeft();
        leftBorder.setVal(STBorder.NONE);

        CTBorder rightBorder = borders.addNewRight();
        rightBorder.setVal(STBorder.NONE);

        // 设置内部边框
        CTBorder insideH = borders.addNewInsideH();
        insideH.setVal(STBorder.NONE);

        CTBorder insideV = borders.addNewInsideV();
        insideV.setVal(STBorder.NONE);

        CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJcTable.CENTER);

        // 设置列宽相等（每列占50%宽度）
        int cellWidth = 4767;

        // 设置所有单元格的样式
        for (int row = 0; row < totalRows; row++) {
            for (int col = 0; col < 2; col++) {
                XWPFTableCell cell = table.getRow(row).getCell(col);
                CTTc ctTc = cell.getCTTc();
                CTTcPr tcPr = ctTc.isSetTcPr() ? ctTc.getTcPr() : ctTc.addNewTcPr();

                // 设置单元格宽度
                CTTblWidth cellWidthObj = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                cellWidthObj.setW(BigInteger.valueOf(cellWidth));
                cellWidthObj.setType(STTblWidth.DXA);

                // 设置单元格垂直居中
                CTVerticalJc vJc = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
                vJc.setVal(STVerticalJc.CENTER);

                // 清除默认段落
                if (cell.getParagraphs().size() > 0) {
                    for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
                        cell.removeParagraph(i);
                    }
                }

                // 添加新段落，居中对齐
                XWPFParagraph para = cell.addParagraph();
                ReportGenerateTools.setSingleLineSpacing(para);  // 设置单倍行距
                para.setAlignment(ParagraphAlignment.CENTER);
            }
        }

        // 填充图片和标题
        int imageIndex = 0;
        for (int groupIndex = 0; groupIndex < totalImageRows; groupIndex++) {
            int imageRowIndex = groupIndex * 2;     // 图片行索引
            int titleRowIndex = groupIndex * 2 + 1; // 标题行索引

            // 在当前图片行填充最多2张图片
            for (int col = 0; col < 2 && imageIndex < allImages.size(); col++) {
                Pair<String, String> imageInfo = allImages.get(imageIndex);
                String fileName = imageInfo.getLeft();
                String titleInfo = imageInfo.getRight();

                // 解析书签名和描述
                String[] parts = titleInfo.split("\\|");
                String bookmarkName = parts[0];
                String imageDesc = parts.length > 1 ? parts[1] : "";

                // 获取图片单元格和标题单元格
                XWPFTableCell imageCell = table.getRow(imageRowIndex).getCell(col);
                XWPFTableCell titleCell = table.getRow(titleRowIndex).getCell(col);

                // 在图片单元格中添加图片
                XWPFParagraph imagePara = imageCell.getParagraphs().get(0);
                XWPFRun imageRun = imagePara.createRun();

                log.info("开始插入现状照片：{}", fileName);
                try (InputStream imageStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(fileName.substring(0, 2) + "/" + fileName)
                                .build())) {

                    // 统一图片大小
                    int imgWidth = 8 * 360000;
                    int imgHeight = 6 * 360000;

                    imageRun.addPicture(
                            imageStream,
                            XWPFDocument.PICTURE_TYPE_JPEG,
                            "status.jpg",
                            imgWidth,
                            imgHeight
                    );
                    log.info("插入现状照片结束：{}", fileName);
                } catch (Exception e) {
                    log.error("插入现状照片失败", e);
                    continue;
                }

                // 在标题单元格中添加图片标题域
                XWPFParagraph titlePara = titleCell.getParagraphs().get(0);
                titlePara.setAlignment(ParagraphAlignment.CENTER);

                // 清除现有内容
                while (titlePara.getRuns().size() > 0) {
                    titlePara.removeRun(0);
                }

                // 在现有段落中创建图片标题域，使用第3章编号和指定的书签名
                WordFieldUtils.createFigureCaptionInParagraph(titlePara, imageDesc, 3, bookmarkName, 240, 0);

                imageIndex++;
            }

            // 如果当前行只有一张图片，需要清空第二列的内容
            if (imageIndex == allImages.size() && (imageIndex - 1) % 2 == 0) {
                // 最后一张图片在第一列，第二列保持空白
                XWPFTableCell emptyImageCell = table.getRow(imageRowIndex).getCell(1);
                XWPFTableCell emptyTitleCell = table.getRow(titleRowIndex).getCell(1);

                // 确保空单元格有段落但无内容
                if (emptyImageCell.getParagraphs().isEmpty()) {
                    emptyImageCell.addParagraph();
                }
                if (emptyTitleCell.getParagraphs().isEmpty()) {
                    emptyTitleCell.addParagraph();
                }
            }
        }

        // 在表格后添加空段落
        XWPFParagraph spacerAfter;
        if (cursor != null) {
            spacerAfter = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            spacerAfter = document.createParagraph();
        }
        spacerAfter.setSpacingAfter(300);
        spacerAfter.setSpacingBefore(300);
    }

    /**
     * 处理检测结论
     */
    private void handleTestConclusion(XWPFDocument document, String key, List<Task> groupTasks, String groupName) {
        try {
            log.info("开始处理检测结论, key: {}, 子桥数量: {}", key, groupTasks.size());
            XWPFParagraph targetParagraph = ReportGenerateTools.findParagraphByPlaceholder(document, key);
            if (targetParagraph == null) {
                log.warn("未找到检测结论占位符: {}", key);
                return;
            }
            Map<Long, BiEvaluation> biEvaluationMap = new HashMap<>();
            Integer minSystemLevel = null;
            for (Task task : groupTasks) {
                BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
                if (evaluation == null) {
                    continue;
                }
                biEvaluationMap.put(task.getId(), evaluation);
                if (evaluation.getSystemLevel() != null
                        && (minSystemLevel == null || evaluation.getSystemLevel() > minSystemLevel)) {
                    minSystemLevel = evaluation.getSystemLevel();
                }
            }
            testConclusionService.handleTestConclusion(document, targetParagraph, groupTasks, groupName,
                    biEvaluationMap, minSystemLevel);
            log.info("检测结论处理完成");
        } catch (Exception e) {
            log.error("处理检测结论失败: key={}, error={}", key, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 处理检测结论桥梁详情
     */
    private void handleTestConclusionBridge(XWPFDocument document, String key, List<Task> groupTasks) {
        try {
            log.info("开始处理检测结论桥梁详情, key: {}, 子桥数量: {}", key, groupTasks.size());
            XWPFParagraph targetParagraph = ReportGenerateTools.findParagraphByPlaceholder(document, key);
            if (targetParagraph == null) {
                log.warn("未找到检测结论桥梁详情占位符: {}", key);
                return;
            }
            testConclusionService.handleTestConclusionBridge(document, targetParagraph, groupTasks);
            log.info("检测结论桥梁详情处理完成");
        } catch (Exception e) {
            log.error("处理检测结论桥梁详情失败: key={}", key, e);
            throw e;
        }
    }

    private enum AppendixPart {
        BRIDGE_CARD,
        INSPECTION_TABLE
    }

    /**
     * 附表先按子桥顺序出全部桥梁卡片，再按同一顺序出全部定期检查记录表。
     */
    private void fillAppendixForGroup(XWPFDocument document, List<Task> groupTasks,
                                      Project project, byte[] templateBytes) {
        if (document == null || groupTasks == null || groupTasks.isEmpty() || templateBytes == null) {
            return;
        }
        XWPFParagraph appendixHeading = findBridgeBlockHeading(document, "附表");
        XWPFParagraph blockEnd = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-end}");
        if (appendixHeading == null || blockEnd == null) {
            log.warn("大桥章节未找到附表标题或结束锚点，跳过附表生成");
            return;
        }
        for (Task task : groupTasks) {
            insertAppendixPart(document, blockEnd, templateBytes, project, task, AppendixPart.BRIDGE_CARD);
        }
        for (Task task : groupTasks) {
            insertAppendixPart(document, blockEnd, templateBytes, project, task, AppendixPart.INSPECTION_TABLE);
        }
    }

    private void insertAppendixPart(XWPFDocument document, XWPFParagraph blockEnd, byte[] templateBytes,
                                    Project project, Task task, AppendixPart part) {
        Building building = task == null ? null : task.getBuilding();
        if (building == null) {
            return;
        }
        XWPFDocument appendixSource = null;
        try {
            appendixSource = new XWPFDocument(new ByteArrayInputStream(templateBytes));
            applyBridgeIdentity(appendixSource, building, project);
            if (part == AppendixPart.INSPECTION_TABLE) {
                regularInspectionService.fillSingleBridgeRegularInspectionTable(
                        appendixSource, building, task, project, BRIDGE_CHAPTER_TEMPLATE_TYPE);
            } else {
                BiEvaluation evaluation = task.getId() == null
                        ? null : biEvaluationService.selectBiEvaluationByTaskId(task.getId());
                bridgeCardService.processBridgeCardData(
                        appendixSource, building, BRIDGE_CHAPTER_TEMPLATE_TYPE,
                        evaluation == null ? null : evaluation.getSystemLevel());
            }
            insertBodyElementCopies(document, blockEnd, appendixSource,
                    copyAppendixBodyElements(appendixSource, part));
        } catch (Exception e) {
            log.warn("大桥附表生成失败: taskId={}, part={}", task.getId(), part, e);
        } finally {
            if (appendixSource != null) {
                try {
                    appendixSource.close();
                } catch (IOException e) {
                    log.warn("关闭附表临时文档失败: taskId={}", task.getId(), e);
                }
            }
        }
    }

    private List<IBodyElement> copyAppendixBodyElements(XWPFDocument document, AppendixPart part) {
        XWPFParagraph appendixHeading = findBridgeBlockHeading(document, "附表");
        XWPFParagraph end = ReportGenerateTools.findParagraphByPlaceholder(document, "${line.bridge-block-end}");
        if (appendixHeading == null || end == null) {
            return Collections.emptyList();
        }
        int startIndex = document.getPosOfParagraph(appendixHeading);
        int endIndex = document.getPosOfParagraph(end);
        int splitIndex = indexOfAppendixInspectionTable(document, startIndex, endIndex);
        int from;
        int to;
        if (splitIndex < 0) {
            if (part != AppendixPart.BRIDGE_CARD) {
                return Collections.emptyList();
            }
            from = startIndex + 1;
            to = endIndex;
        } else if (part == AppendixPart.BRIDGE_CARD) {
            from = startIndex + 1;
            to = splitIndex;
        } else {
            from = splitIndex;
            to = endIndex;
        }
        List<IBodyElement> elements = new ArrayList<>();
        for (int i = from; i < to; i++) {
            elements.add(document.getBodyElements().get(i));
        }
        return elements;
    }

    private int indexOfAppendixInspectionTable(XWPFDocument document, int startIndex, int endIndex) {
        for (int i = startIndex + 1; i < endIndex; i++) {
            IBodyElement element = document.getBodyElements().get(i);
            if (!(element instanceof XWPFParagraph)) {
                continue;
            }
            String text = safeParagraphText((XWPFParagraph) element).replace(" ", "");
            if (text.contains("定期检查记录表")) {
                return i;
            }
        }
        return -1;
    }

    // ==================== 以下表格工具参照组合桥报告实现，独立维护 ====================

    private static final Set<String> COMPONENT_STRUCTURE_NAMES = Set.of("上部结构", "下部结构", "桥面系");
    private static final Set<String> SKIPPED_COMPONENT_NODE_NAMES = Set.of("其他", "附属设施");

    /**
     * 部件表一行：按评定部件出表，锥坡/护坡这类复合部件可拆成子行。
     */
    private static final class ComponentTableRow {
        private final String structureName;
        private final String groupName;
        private final String componentName;
        private final String countText;
        private final String remarkText;

        private ComponentTableRow(String structureName, String groupName, String componentName,
                                  String countText, String remarkText) {
            this.structureName = structureName;
            this.groupName = groupName;
            this.componentName = componentName;
            this.countText = countText;
            this.remarkText = remarkText;
        }

        private boolean nested() {
            return groupName != null && !groupName.isEmpty();
        }
    }

    /**
     * 一张部件表对应一个出表根节点：普通桥是桥梁本身，组合桥则是主桥/引桥。
     */
    private List<BiObject> resolveComponentTableRoots(BiObject root, List<BiObject> allObjects) {
        List<BiObject> children = listComponentChildren(root.getId(), allObjects);
        if (children.stream().anyMatch(child -> COMPONENT_STRUCTURE_NAMES.contains(child.getName()))) {
            return List.of(root);
        }
        List<BiObject> subBridges = children.stream()
                .filter(child -> listComponentChildren(child.getId(), allObjects).stream()
                        .anyMatch(grand -> COMPONENT_STRUCTURE_NAMES.contains(grand.getName())))
                .collect(Collectors.toList());
        return subBridges.isEmpty() ? List.of(root) : subBridges;
    }

    /**
     * 收集评定部件，按上部结构/下部结构/桥面系分组；不展开到节段、孔等构件。
     */
    private Map<String, List<ComponentTableRow>> collectComponentStructure(BiObject tableRoot, List<BiObject> allObjects) {
        Map<String, List<ComponentTableRow>> structureMap = new LinkedHashMap<>();
        for (BiObject structure : listComponentChildren(tableRoot.getId(), allObjects)) {
            List<ComponentTableRow> rows = new ArrayList<>();
            for (BiObject component : listComponentChildren(structure.getId(), allObjects)) {
                List<BiObject> nestedChildren = nestedComponentChildren(component, allObjects);
                if (nestedChildren.isEmpty()) {
                    rows.add(new ComponentTableRow(structure.getName(), null, component.getName(),
                            formatComponentCount(resolveComponentCount(component, allObjects)),
                            formatComponentRemark(component.getRemark())));
                } else {
                    for (BiObject child : nestedChildren) {
                        rows.add(new ComponentTableRow(structure.getName(), component.getName(), child.getName(),
                                formatComponentCount(resolveComponentCount(child, allObjects)),
                                formatComponentRemark(child.getRemark())));
                    }
                }
            }
            if (!rows.isEmpty()) {
                structureMap.put(structure.getName(), rows);
            }
        }
        return structureMap;
    }

    private List<BiObject> listComponentChildren(Long parentId, List<BiObject> allObjects) {
        if (parentId == null || allObjects == null) {
            return List.of();
        }
        return allObjects.stream()
                .filter(object -> parentId.equals(object.getParentId()))
                .filter(object -> !isSkippedComponentNode(object))
                .sorted(Comparator.comparing(BiObject::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private boolean isSkippedComponentNode(BiObject object) {
        return object == null || object.getName() == null || SKIPPED_COMPONENT_NODE_NAMES.contains(object.getName());
    }

    /**
     * 仅当子节点名称正好是父节点顿号拆分结果时展开，例如「锥坡、护坡」→ 锥坡 / 护坡。
     * 上部承重构件下的节段、孔跨不会展开。
     */
    private List<BiObject> nestedComponentChildren(BiObject parent, List<BiObject> allObjects) {
        if (parent == null || parent.getName() == null || !parent.getName().contains("、")) {
            return List.of();
        }
        List<BiObject> children = listComponentChildren(parent.getId(), allObjects);
        if (children.size() < 2) {
            return List.of();
        }
        Set<String> parts = Arrays.stream(parent.getName().split("、"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (parts.size() < 2 || !children.stream().allMatch(child -> parts.contains(child.getName()))) {
            return List.of();
        }
        return children;
    }

    /**
     * 上部承重构件这类评定部件本身往往没有 count，改为累加下级已填写的构件数量。
     * 子树里都没有 count 时返回 0，表格显示为「/」，不能把结构树占位节点个数当成构件数。
     */
    private Integer resolveComponentCount(BiObject node, List<BiObject> allObjects) {
        if (node == null) {
            return 0;
        }
        if (node.getCount() != null && node.getCount() > 0) {
            return node.getCount();
        }
        List<BiObject> children = listComponentChildren(node.getId(), allObjects);
        if (children.isEmpty()) {
            return node.getCount();
        }
        int sum = 0;
        for (BiObject child : children) {
            Integer childCount = resolveComponentCount(child, allObjects);
            if (childCount != null && childCount > 0) {
                sum += childCount;
            }
        }
        return sum;
    }

    private String formatComponentCount(Integer count) {
        if (count == null || count <= 0) {
            return "/";
        }
        return String.valueOf(count);
    }

    private String formatComponentRemark(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            return "/";
        }
        return remark.trim();
    }

    /**
     * 生成部件划分及构件数量表格。桥梁部件列预留两格，供「锥坡、护坡」左右拆分。
     */
    private void generateComponentTable(XWPFDocument document, XmlCursor cursor,
                                        Map<String, List<ComponentTableRow>> structureMap) {
        int totalRows = 1;
        for (List<ComponentTableRow> rows : structureMap.values()) {
            totalRows += rows.size();
        }

        XWPFTable table;
        if (cursor != null) {
            table = document.insertNewTbl(cursor);
            cursor.toNextToken();
        } else {
            table = document.createTable();
        }

        int columnCount = 6;
        for (int i = 0; i < totalRows; i++) {
            XWPFTableRow row = table.getRow(i);
            if (row == null) {
                row = table.createRow();
            }
            for (int j = 0; j < columnCount; j++) {
                if (row.getCell(j) == null) {
                    row.createCell();
                }
            }
        }

        applyTableGrid(table, columnCount);

        XWPFTableRow headerRow = table.getRow(0);
        setTableCell(headerRow, 0, "序号", true);
        setTableCell(headerRow, 1, "桥梁结构", true);
        setTableCell(headerRow, 2, "桥梁部件", true);
        setTableCell(headerRow, 3, "", true);
        setTableCell(headerRow, 4, "构件数量", true);
        setTableCell(headerRow, 5, "说明", true);
        mergeHorizontalCells(table, 0, 2, 3);

        int rowIndex = 1;
        int sequenceNum = 1;
        List<int[]> structureMerges = new ArrayList<>();
        List<int[]> groupMerges = new ArrayList<>();

        for (Map.Entry<String, List<ComponentTableRow>> entry : structureMap.entrySet()) {
            List<ComponentTableRow> rows = entry.getValue();
            int structureStart = rowIndex;
            int groupStart = -1;
            String currentGroup = null;
            for (ComponentTableRow item : rows) {
                XWPFTableRow row = table.getRow(rowIndex);
                setTableCell(row, 0, String.valueOf(sequenceNum));
                setTableCell(row, 1, item.structureName);
                if (item.nested()) {
                    setTableCell(row, 2, item.groupName);
                    setTableCell(row, 3, item.componentName);
                    if (!Objects.equals(currentGroup, item.groupName)) {
                        if (groupStart >= 0 && rowIndex - 1 > groupStart) {
                            groupMerges.add(new int[]{groupStart, rowIndex - 1});
                        }
                        groupStart = rowIndex;
                        currentGroup = item.groupName;
                    }
                } else {
                    if (groupStart >= 0 && rowIndex - 1 > groupStart) {
                        groupMerges.add(new int[]{groupStart, rowIndex - 1});
                    }
                    groupStart = -1;
                    currentGroup = null;
                    setTableCell(row, 2, item.componentName);
                    setTableCell(row, 3, "");
                    mergeHorizontalCells(table, rowIndex, 2, 3);
                }
                setTableCell(row, 4, item.countText);
                setTableCell(row, 5, item.remarkText, false,
                        "/".equals(item.remarkText) ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
                rowIndex++;
                sequenceNum++;
            }
            if (groupStart >= 0 && rowIndex - 1 > groupStart) {
                groupMerges.add(new int[]{groupStart, rowIndex - 1});
            }
            if (rows.size() > 1) {
                structureMerges.add(new int[]{structureStart, rowIndex - 1});
            }
        }

        for (int[] range : structureMerges) {
            mergeVerticalCells(table, range[0], range[1], 1);
        }
        for (int[] range : groupMerges) {
            mergeVerticalCells(table, range[0], range[1], 2);
        }
    }

    /**
     * 垂直合并单元格
     *
     * @param table    表格
     * @param startRow 起始行
     * @param endRow   结束行
     * @param col      列索引
     */
    private void mergeVerticalCells(XWPFTable table, int startRow, int endRow, int col) {
        if (startRow >= endRow) {
            return;
        }

        // 获取起始单元格
        XWPFTableCell startCell = table.getRow(startRow).getCell(col);
        if (startCell == null) {
            return;
        }

        // 设置合并：从startRow到endRow
        for (int i = startRow + 1; i <= endRow; i++) {
            XWPFTableCell cell = table.getRow(i).getCell(col);
            if (cell != null) {
                // 设置垂直合并
                cell.getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.CONTINUE);
            }
        }

        // 设置起始单元格的垂直合并为开始
        CTTcPr tcPr = startCell.getCTTc().getTcPr();
        if (tcPr == null) {
            tcPr = startCell.getCTTc().addNewTcPr();
        }
        CTVMerge vMerge = tcPr.isSetVMerge() ? tcPr.getVMerge() : tcPr.addNewVMerge();
        vMerge.setVal(STMerge.RESTART);
    }

    /**
     * 横向合并单元格。
     */
    private void mergeHorizontalCells(XWPFTable table, int row, int startCol, int endCol) {
        if (startCol >= endCol) {
            return;
        }
        XWPFTableRow tableRow = table.getRow(row);
        if (tableRow == null) {
            return;
        }
        for (int col = startCol; col <= endCol; col++) {
            XWPFTableCell cell = tableRow.getCell(col);
            if (cell == null) {
                continue;
            }
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }
            CTHMerge hMerge = tcPr.isSetHMerge() ? tcPr.getHMerge() : tcPr.addNewHMerge();
            hMerge.setVal(col == startCol ? STMerge.RESTART : STMerge.CONTINUE);
        }
    }

    /**
     * 全文标题样式：二级四号黑体，三级及以下小四黑体，段前段后各 0.5 行、1.5 倍行距。
     */
    private void applyDocumentHeadingStyles(XWPFDocument document) {
        if (document == null) {
            return;
        }
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (isCaptionParagraph(paragraph)) {
                continue;
            }
            int level = resolveHeadingLevel(paragraph);
            if (level >= 2) {
                applyHeadingFormat(paragraph, level);
            }
        }
    }

    /**
     * 表题、图题在模板里常带 outlineLvl，不能当成正文标题去改成左对齐。
     */
    private boolean isCaptionParagraph(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return false;
        }
        String text = safeParagraphText(paragraph).replace(" ", "");
        if (text.contains("桥梁基本状况卡片") || text.contains("定期检查记录表")
                || text.startsWith("表") || text.startsWith("图")) {
            return true;
        }
        try {
            CTPPr ppr = paragraph.getCTP().getPPr();
            if (ppr != null && ppr.isSetJc() && ppr.getJc().getVal() == STJc.CENTER) {
                return true;
            }
        } catch (Exception ignored) {
            // 按普通段落处理
        }
        String style = paragraph.getStyle();
        return "13".equals(style) || (style != null && style.toLowerCase().contains("caption"));
    }

    private int resolveHeadingLevel(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return 0;
        }
        String style = paragraph.getStyle();
        if (style != null) {
            String trimmed = style.trim();
            if (trimmed.matches("\\d+")) {
                int numeric = Integer.parseInt(trimmed);
                if (numeric >= 1 && numeric <= 9) {
                    return numeric;
                }
            }
            String normalized = trimmed.replace(" ", "").toLowerCase();
            if (normalized.startsWith("heading")) {
                try {
                    return Integer.parseInt(normalized.substring("heading".length()));
                } catch (NumberFormatException ignored) {
                    // 不是 HeadingN 样式
                }
            }
            if (trimmed.startsWith("标题")) {
                String number = trimmed.substring("标题".length()).trim();
                try {
                    return Integer.parseInt(number);
                } catch (NumberFormatException ignored) {
                    // 不是「标题 N」样式
                }
            }
        }
        try {
            CTPPr ppr = paragraph.getCTP().getPPr();
            if (ppr != null && ppr.isSetOutlineLvl() && ppr.getOutlineLvl().getVal() != null) {
                return ppr.getOutlineLvl().getVal().intValue() + 1;
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    /**
     * 二级：四号黑体；三级及以下：小四黑体。段前段后 0.5 行，1.5 倍行距。
     */
    private void applyHeadingFormat(XWPFParagraph paragraph, int headingLevel) {
        if (paragraph == null || headingLevel < 2) {
            return;
        }
        applyParagraphLineSpacing(paragraph, true);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        // 模板二级（3.1/3.2，样式 2～3）四号黑体；三级及以下（3.1.1/3.2.1，样式 4+）小四黑体。
        int fontHalfPoints = headingLevel <= 3 ? 28 : 24;
        for (XWPFRun run : paragraph.getRuns()) {
            applyHeadingRun(run, fontHalfPoints);
        }
    }

    /**
     * 正文：小四宋体、1.5 倍行距，可选首行缩进 2 字符。
     */
    private void applyBodyFormat(XWPFParagraph paragraph, boolean firstLineIndent) {
        if (paragraph == null) {
            return;
        }
        applyParagraphLineSpacing(paragraph, false);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr == null) {
            ppr = paragraph.getCTP().addNewPPr();
        }
        CTInd ind = ppr.isSetInd() ? ppr.getInd() : ppr.addNewInd();
        if (firstLineIndent) {
            ind.setFirstLine(BigInteger.valueOf(480));
        }
        for (XWPFRun run : paragraph.getRuns()) {
            boolean bold = Boolean.TRUE.equals(run.isBold());
            ReportGenerateTools.setMixedFontFamily(run, 24);
            run.setBold(bold);
        }
    }

    private void applyParagraphLineSpacing(XWPFParagraph paragraph, boolean halfLineBeforeAfter) {
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr == null) {
            ppr = paragraph.getCTP().addNewPPr();
        }
        CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(360));
        spacing.setLineRule(STLineSpacingRule.AUTO);
        if (halfLineBeforeAfter) {
            spacing.setBeforeLines(BigInteger.valueOf(50));
            spacing.setAfterLines(BigInteger.valueOf(50));
        }
    }

    private void applyHeadingRun(XWPFRun run, int fontHalfPoints) {
        run.setBold(true);
        run.setFontFamily("黑体");
        CTRPr rpr = run.getCTR().addNewRPr();
        CTFonts fonts = rpr.addNewRFonts();
        fonts.setAscii("黑体");
        fonts.setHAnsi("黑体");
        fonts.setEastAsia("黑体");
        rpr.addNewSz().setVal(BigInteger.valueOf(fontHalfPoints));
        rpr.addNewSzCs().setVal(BigInteger.valueOf(fontHalfPoints));
        rpr.addNewB().setVal(true);
    }

    /**
     * 表格全框线、居中、按列数铺满宽度。
     */
    private void applyTableGrid(XWPFTable table, int columnCount) {
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }
        if (tblPr.isSetTblBorders()) {
            tblPr.unsetTblBorders();
        }
        CTTblBorders borders = tblPr.addNewTblBorders();
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);
        if (columnCount > 0) {
            XWPFTableRow firstRow = table.getRow(0);
            if (firstRow != null) {
                for (int i = firstRow.getTableCells().size(); i < columnCount; i++) {
                    firstRow.createCell();
                }
            }
        }
    }

    /**
     * 设置表格单元格内容（五号宋体，1倍行距）
     */
    private void setTableCell(XWPFTableRow row, int cellIndex, String text) {
        setTableCell(row, cellIndex, text, false, ParagraphAlignment.CENTER);
    }

    /**
     * 设置表格单元格内容
     *
     * @param row       表格行
     * @param cellIndex 单元格索引
     * @param text      文本内容
     * @param isHeader  是否为表头（表头宋体加粗，数据宋体）
     */
    private void setTableCell(XWPFTableRow row, int cellIndex, String text, boolean isHeader) {
        setTableCell(row, cellIndex, text, isHeader, ParagraphAlignment.CENTER);
    }

    private void setTableCell(XWPFTableRow row, int cellIndex, String text, boolean isHeader,
                              ParagraphAlignment alignment) {
        XWPFTableCell cell = row.getCell(cellIndex);
        if (cell == null) {
            cell = row.createCell();
        }

        // 清空单元格内容
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }

        // 创建段落
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(alignment == null ? ParagraphAlignment.CENTER : alignment);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

        // 设置1倍行距
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr == null) ppr = paragraph.getCTP().addNewPPr();
        CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(240));
        spacing.setLineRule(STLineSpacingRule.AUTO);

        // 创建文本运行（五号宋体）
        XWPFRun run = paragraph.createRun();
        run.setText(text != null ? text : "");
        run.setFontSize(10.5);
        run.setBold(isHeader);
        ReportGenerateTools.setMixedFontFamily(run, 21);
        if (isHeader) {
            run.setBold(true);
        }
    }
}
