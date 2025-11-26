package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.domain.temp.ComponentDiseaseType;
import edu.whut.cs.bi.biz.domain.vo.Disease2ReportSummaryAiVO;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ReportMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.Convert2VO;
import edu.whut.cs.bi.biz.utils.ReportGenerateTools;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static edu.whut.cs.bi.biz.service.impl.ReportServiceImpl.compareCodes;

@Slf4j
@Service
public class Report1LevelSingleBridgeServiceImpl implements Report1LevelSingleBridgeService {
    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private IReportDataService reportDataService;

    @Autowired
    private IReportTemplateService reportTemplateService;

    @Autowired
    private ITemplateVariableService templateVariableService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private IProjectService projectService;

    @Autowired
    private IBuildingService buildingService;

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private FileMapController fileMapController;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private DiseaseController diseaseController;

    @Autowired
    private IComponentService componentService;

    @Autowired
    private DiseaseComparisonService diseaseComparisonService;

    @Resource
    private IDiseaseService diseaseService;


    @Value("${springAi_Rag.endpoint}")
    private String SpringAiUrl;
    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private DiseaseTypeMapper diseaseTypeMapper;

    @Autowired
    private IBiEvaluationService biEvaluationService;

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
    public String generateReportDocument(Report report, Task task, ReportTemplateTypes templateType) {
        Long buildingId = task.getBuildingId();
        InputStream templateStream = null;
        FileOutputStream out = null;
        XWPFDocument document = null;
        File outputFile = null;

        try {
            if (report == null) {
                log.error("报告对象为空");
                return null;
            }

            // 查询建筑物信息
            Building building = null;
            if (buildingId != null) {
                building = buildingService.selectBuildingById(buildingId);
            }
            if (building == null) {
                log.error("未找到指定的建筑物，buildingId: {}", buildingId);
                return null;
            }

            // 查询项目信息
            Project project = null;
            if (report.getProjectId() != null) {
                project = projectService.selectProjectById(report.getProjectId());
            }
            log.info("单桥报告 - 项目: {}, 建筑: {}", project != null ? project.getName() : "无", building.getName());

            // 查询报告模板信息
            ReportTemplate template = reportTemplateService.selectReportTemplateById(report.getReportTemplateId());
            if (template == null || template.getMinioId() == null) {
                log.error("未找到报告模板或模板MinioId为空");
                return null;
            }

            // 通过模板文件ID获取文件信息
            FileMap fileMap = fileMapService.selectFileMapById(template.getMinioId());
            if (fileMap == null) {
                log.error("未找到模板文件映射");
                return null;
            }

            // 从Minio下载模板文件
            String fileName = fileMap.getNewName();
            templateStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(fileName.substring(0, 2) + "/" + fileName)
                            .build()
            );

            // 加载Word文档
            document = new XWPFDocument(templateStream);
            log.info("成功加载单桥模板Word文档");

            // 重置域计数器
            WordFieldUtils.resetCounters();

            // 查询报告数据
            ReportData queryParam = new ReportData();
            queryParam.setReportId(report.getId());
            List<ReportData> reportDataList = reportDataService.selectReportDataList(queryParam);
            log.info("查询到报告数据记录数: {}", reportDataList.size());

            // 创建数据映射（用于快速查找）
            Map<String, ReportData> dataMap = new HashMap<>();
            for (ReportData data : reportDataList) {
                dataMap.put(data.getKey(), data);
            }

            // 获取根对象信息
            BiObject biObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());

            // 1. 替换日期占位符
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            ReportGenerateTools.replaceText(document, "${year}", String.valueOf(year));
            ReportGenerateTools.replaceText(document, "${month}", String.valueOf(month));
            ReportGenerateTools.replaceText(document, "${day}", String.valueOf(day));
            log.info("已替换日期占位符: {}-{}-{}", year, month, day);

            // 2. 替换项目相关信息
            if (project != null) {
                ReportGenerateTools.replaceText(document, "${project-name}", project.getName() != null ? project.getName() : "");
                if (project.getDept() != null && project.getDept().getDeptName() != null) {
                    ReportGenerateTools.replaceText(document, "${client-unit}", project.getDept().getDeptName());
                }
            }

            // 3. 替换建筑物相关信息
            ReportGenerateTools.replaceText(document, "${buildingName}", building.getName() != null ? building.getName() : "");
            log.info("已替换项目和建筑物信息");

            // 清空第十章病害汇总缓存
            testConclusionService.clearDiseaseSummaryCache();

            // 处理 最后的 定期检查记录表
            try {
                // 调用定期检查记录表服务处理占位符
                regularInspectionService.fillSingleBridgeRegularInspectionTable(document, building, task, project, templateType);
            } catch (Exception e) {
                log.error("处理定期检查记录表失败: error={}", e.getMessage(), e);
            }

            // 4. 处理桥梁基本状况卡片数据 和 桥梁概况 数据
            // 11.11  修改 ， 处理桥梁状况卡片 时 顺带 处理 桥梁概况数据。
            try {
                bridgeCardService.processBridgeCardData(document, building, templateType, task);
                log.info("桥梁基本状况卡片处理完成");
            } catch (Exception e) {
                log.error("处理桥梁基本状况卡片出错: error={}", e.getMessage(), e);
            }
            // 5. 先处理自动生成的章节内容（包括从数据库自动获取的照片）
            processSingleBridgeAutoGeneratedContent(document, building, project, task, biObject, dataMap);
            // 6. 再处理用户填写的简单数据
            processSingleBridgeUserData(document, reportDataList, building, dataMap, project, biObject);
            // 10. 更新文档中的所有域（图表自动编号）
            WordFieldUtils.updateAllFields(document);
            log.info("已更新文档域");
            // 11. 保存文档到临时文件
            String docFileName = report.getName() + "_" + System.currentTimeMillis() + ".docx";
            outputFile = File.createTempFile("single_bridge_report_" + report.getId(), ".docx");
            out = new FileOutputStream(outputFile);
            document.write(out);
            out.close();
            log.info("报告文档已保存到临时文件: {}", outputFile.getAbsolutePath());

            // 12. 上传到MinIO
            FileMap reportFileMap = fileMapService.handleFileUploadFromFile(
                    outputFile,
                    docFileName,
                    ShiroUtils.getLoginName()
            );
            log.info("报告文档已上传到MinIO，文件ID: {}", reportFileMap.getId());

            // 13. 更新报告状态
            report.setMinioId(Long.valueOf(reportFileMap.getId()));
            report.setStatus(1); // 已生成
            reportMapper.updateReport(report);

            return reportFileMap.getId().toString();

        } catch (Exception e) {
            log.error("生成单桥报告失败", e);
            return null;
        } finally {
            // 清理资源
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
                if (outputFile != null && outputFile.exists()) {
                    boolean deleted = outputFile.delete();
                    if (!deleted) {
                        log.warn("临时文件删除失败: {}", outputFile.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                log.error("清理资源失败", e);
            }
        }
    }


    /**
     * 处理单桥模板自动生成的内容
     */
    private void processSingleBridgeAutoGeneratedContent(XWPFDocument document, Building building,
                                                         Project project, Task task, BiObject biObject,
                                                         Map<String, ReportData> dataMap) {
        // 0. 处理桥梁概况照片（从数据库自动获取，与组合桥同步）
        try {
            insertSingleBridgeImages(document, building.getId());
            log.info("单桥桥梁概况照片处理完成");
        } catch (Exception e) {
            log.error("处理单桥桥梁概况照片出错: error={}", e.getMessage(), e);
            // 照片处理失败不影响其他内容生成，只记录日志
        }

        // 1. 外观检测结果（从数据库自动生成）
        try {
            // 处理第三章外观检测结果
            BiObject rootBiObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
            List<BiObject> biObjects = new ArrayList<>();
            biObjects.add(rootBiObject);
            processAppearanceCheck(document, biObjects, building, project.getId());
            log.info("外观检测结果生成完成");
        } catch (Exception e) {
            log.error("处理外观检测结果出错: error={}", e.getMessage(), e);
            ReportGenerateTools.replaceText(document, "${appearanceInspectionResults}", "【外观检测结果生成失败，请联系管理员】");
        }


        // 2. 技术状况评定（自动生成）
        try {
            handleEvaluationResults(document, "${evaluationResults}", building, task.getId());
            log.info("技术状况评定生成完成");
        } catch (Exception e) {
            log.error("处理技术状况评定出错: error={}", e.getMessage(), e);
            ReportGenerateTools.replaceText(document, "${evaluationResults}", "【技术状况评定生成失败，请联系管理员】");
        }

        // 3.  近两年评定结果对比（自动生成）
        try {
            handleComparisonAnalysis(document, "${comparativeAnalysisOfEvaluationResults}", task, building.getName(), false);
            log.info("近年评定结果对比生成完成");
        } catch (Exception e) {
            log.error("处理近年评定结果对比出错: error={}", e.getMessage(), e);
            ReportGenerateTools.replaceText(document, "${comparativeAnalysisOfEvaluationResults}", "【近年评定结果对比生成失败，请联系管理员】");
        }


        // 4.
        // 处理检测结论（不依赖ReportData）
        try {
            handleTestConclusion(document, "${testConclusion}", task, building.getName());
            handleTestConclusionBridge(document, "${testConclusionBridge}", task, building.getName());
        } catch (Exception e) {
            log.error("处理检测结论出错: error={}", e.getMessage());
            // 如果处理失败，降级为普通文本替换
            ReportGenerateTools.replaceText(document, "${testConclusion}", "检测结论数据获取失败");
            ReportGenerateTools.replaceText(document, "${testConclusionBridge}", "检测结论详情数据获取失败");
        }
    }

    /**
     * 处理第三章外观检测结果
     *
     * @param document   Word文档
     * @param subBridges 建筑物信息
     * @throws Exception 异常
     */
    private void processAppearanceCheck(XWPFDocument document, List<BiObject> subBridges, Building building, Long projectId) throws Exception {


        // 获取所有子部件
        List<BiObject> allObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());

        // 找到占位符所在的段落
        XWPFParagraph placeholderParagraph = null;

        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getText().contains("${appearanceInspectionResults}")) {
                placeholderParagraph = paragraph;
                break;
            }
        }

        // 如果找不到占位符，直接返回
        if (placeholderParagraph == null) {
            return;
        }

        // 获取占位符段落的XML游标，用于指定内容插入位置
        XmlCursor cursor = placeholderParagraph.getCTP().newCursor();

        // 为每个子桥生成章节
        int chapterNum = 3; // 第三章
        int subChapterNum = 1;

        AtomicInteger chapter3ImageCounter = new AtomicInteger(1);
        AtomicInteger chapter3TableCounter = new AtomicInteger(1);

        // 直接在文档中指定位置生成内容
        for (BiObject subBridge : subBridges) {
            XmlCursor subCursor = cursor.newCursor();
            // 查询子桥对应的building
            Building buildingQuery = new Building();
            buildingQuery.setRootObjectId(subBridge.getId());
            List<Building> subBuildingList = buildingService.selectBuildingList(buildingQuery);
            Building subBuilding = subBuildingList.isEmpty() ? null : subBuildingList.get(0);

            // 收集病害数据
            List<Disease> subBridgeDiseases;
            if (subBuilding != null) {
                // 获取子桥的所有病害
                Disease queryParam = new Disease();
                queryParam.setBuildingId(subBuilding.getId());
                queryParam.setProjectId(projectId);
                subBridgeDiseases = diseaseMapper.selectDiseaseList(queryParam);
            } else {
                // 如果找不到子桥对应的building，使用组合桥的病害
                Disease queryParam = new Disease();
                queryParam.setBuildingId(building.getId());
                queryParam.setProjectId(projectId);
                queryParam.setBiObjectId(subBridge.getId()); // 只获取与子桥关联的病害
                subBridgeDiseases = diseaseMapper.selectDiseaseList(queryParam);
            }

            // 为每个子桥收集病害
            Map<Long, List<Disease>> bridgeDiseaseMap = new LinkedHashMap<>();
            collectDiseases(subBridge, allObjects, subBridgeDiseases, 1, bridgeDiseaseMap);

            // 在指定位置生成内容
            String prefix = chapterNum + "." + subChapterNum;

            // 在游标位置生成内容 - 将baseHeadingLevel设为3，使其成为第三章的子章节
            writeBiObjectTreeToWord(document, subBridge, allObjects,
                    bridgeDiseaseMap, prefix, 1, chapter3ImageCounter, chapter3TableCounter, subCursor, 2);

            subChapterNum++;
        }

        // 删除占位符段落
        placeholderParagraph.removeRun(0); // 清除占位符文本
        if (placeholderParagraph.getRuns().size() == 0) {
            // 如果段落为空，找到它的索引并删除
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
    private void collectDiseases(BiObject node, List<BiObject> allNodes, List<Disease> properties,
                                 int level, Map<Long, List<Disease>> map) {
        // 找到当前节点所属的 level3 祖先（如自身就是 level3 则返回自身）
        BiObject level3 = findLevel3Ancestor(node, allNodes);

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
            collectDiseases(child, allNodes, properties, level + 1, map);
        }
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
                                         XmlCursor cursor, int baseHeadingLevel) throws Exception {
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
        run.setBold(false);
        run.setFontFamily("黑体");

        // 写病害信息
        List<Disease> nodeDiseases = diseaseMap.getOrDefault(node.getId(), List.of());

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
                        String imageDesc = componentName + type;
                        // 使用第3章编号规则
                        String bookmark = "Figure_Chapter3_" + chapterImageCounter.getAndIncrement();
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
            runBold.setFontSize(12); // 设置字号与后面一致

            // Part 2: 生成病害小结

            log.info("开始生成病害小结");
            String diseaseString = "";
            try {
                diseaseString = getDiseaseSummary(nodeDiseases);
            } catch (Exception e) {
                log.error("ai小结病害失败，生成报告继续。");
            }


//             缓存病害汇总到第十章服务，供后续复用
            testConclusionService.cacheDiseaseSummary(node.getId(), diseaseString);

            log.info("生成结束");

            // 按行分割字符串并创建多个段落
            String[] lines = diseaseString.split("\\r?\\n");
            for (String line : lines) {
                // 创建新的段落并设置首行缩进
                XWPFParagraph diseasePara;
                if (cursor != null) {
                    diseasePara = document.insertNewParagraph(cursor);
                    cursor.toNextToken();
                } else {
                    diseasePara = document.createParagraph();
                }
                CTPPr diseasePpr = diseasePara.getCTP().getPPr();
                if (diseasePpr == null) {
                    diseasePpr = diseasePara.getCTP().addNewPPr();
                }
                ind = diseasePpr.isSetInd() ? diseasePpr.getInd() : diseasePpr.addNewInd();
                ind.setFirstLine(BigInteger.valueOf(480));

                XWPFRun runItem = diseasePara.createRun();
                runItem.setText(line);
                runItem.setFontSize(12);
            }

            // Part 3: 表格引用部分
            XWPFParagraph tableRefPara;
            if (cursor != null) {
                tableRefPara = document.insertNewParagraph(cursor);
                cursor.toNextToken();
            } else {
                tableRefPara = document.createParagraph();
            }

            // 设置1.5倍行距
            CTPPr ppr1 = tableRefPara.getCTP().getPPr();
            if (ppr1 == null) {
                ppr1 = tableRefPara.getCTP().addNewPPr();
            }
            CTSpacing spacing = ppr1.isSetSpacing() ? ppr1.getSpacing() : ppr1.addNewSpacing();
            spacing.setLine(BigInteger.valueOf(360));

            // 添加表格标题，获取书签名用于引用
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(document, node.getName() + "检测结果表", cursor, 3, chapter3TableCounter);

            // 创建章节格式的表格引用域
            WordFieldUtils.createChapterTableReference(tableRefPara, tableBookmark, "具体检测结果见下表", ":");

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
            Double[] columnWidthRatios = {0.08, 0.18, 0.14, 0.08, 0.26, 0.10, 0.08, 0.08};
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
                            cellR.setText(d.getDiseaseType().getName() != null ? d.getDiseaseType().getName() : "/");
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
            noDiseaseRun.setFontSize(12);

            // 插入当前结构的现状照片
            try {
                insertBiObjectStatusImages(document, node, chapterImageCounter, cursor);
            } catch (Exception e) {
                log.error("插入BiObject现状照片失败", e);
            }
        }

        // 递归写子节点
        List<BiObject> children = allNodes.stream()
                .filter(obj -> node.getId().equals(obj.getParentId()))
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());

        int idx = 1;
        for (BiObject child : children) {
            if ("附属设施".equals(child.getName())) {
                continue;
            }
            writeBiObjectTreeToWord(document, child, allNodes, diseaseMap, prefix + "." + idx, level + 1, chapterImageCounter, chapter3TableCounter, cursor, baseHeadingLevel);
            idx++;
        }
    }


    public String getDiseaseSummary(List<Disease> diseases) throws JsonProcessingException {
        // 瘦身
        List<Disease2ReportSummaryAiVO> less = Convert2VO.copyList(diseases, Disease2ReportSummaryAiVO.class);
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


    /**
     * 找到第三层祖先节点
     */
    private BiObject findLevel3Ancestor(BiObject node, List<BiObject> allNodes) {
        BiObject cur = node;
        int lv = getLevel(cur, allNodes);
        while (lv > 3 && cur.getParentId() != null) {
            BiObject finalCur = cur;
            cur = allNodes.stream()
                    .filter(o -> o.getId().equals(finalCur.getParentId()))
                    .findFirst()
                    .orElse(null);
            lv--;
        }
        return cur;
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
     * 处理评定结果
     *
     * @param document Word文档
     * @param key      占位符
     * @param building 建筑物信息
     * @param taskId   任务id
     */
    private void handleEvaluationResults(XWPFDocument document, String key, Building building, Long taskId) {
        try {
            log.info("开始处理评定结果, key: {}", key);

            // 查询评定结果
            BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(taskId);
            if (evaluation == null) {
                log.warn("未找到任务的评定结果: taskId={}", taskId);
                ReportGenerateTools.replaceText(document, key, "未找到评定结果");
                return;
            }

            // 获取桥梁名称
            String bridgeName = building != null && building.getName() != null ? building.getName() : "桥梁";

            // 生成评定文字内容
            String Content = generateEvaluationContent(evaluation, bridgeName);

            // 插入四句话到文档中
            XWPFParagraph paragraph = insertEvaluationContent(document, key, Content);

            // 调用专门的服务在四句话后生成表格（包含分页符、横向设置和表格）
            evaluationTableService.generateEvaluationTableAfterParagraph(document, paragraph, building, evaluation, bridgeName);

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

            // 设置段落格式
            targetParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);

            // 设置1.5倍行距
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = targetParagraph.getCTP().getPPr();
            if (ppr == null) {
                ppr = targetParagraph.getCTP().addNewPPr();
            }
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
            spacing.setLine(java.math.BigInteger.valueOf(360));

            // 按行分割内容并在同一段落中添加多个Run
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                if (i > 0) {
                    // 不是第一行时，先添加换行
                    XWPFRun breakRun = targetParagraph.createRun();
                    breakRun.addBreak();
                }

                // 创建文本Run
                XWPFRun textRun = targetParagraph.createRun();
                textRun.setText("\t" + line);
                textRun.setFontSize(12);
                textRun.setFontFamily("宋体");
            }

            log.info("评定内容插入成功: {}, 插入{}行内容", key, lines.length);

            return targetParagraph;

        } catch (Exception e) {
            log.error("插入评定内容失败: key={}", key, e);
            throw e;
        }
    }


    /**
     * 生成评定文字内容
     *
     * @param evaluation 评定结果
     * @param bridgeName 桥梁名称
     */
    private String generateEvaluationContent(BiEvaluation evaluation, String bridgeName) {
        StringBuilder content = new StringBuilder();

        // 第一句话：依据《公路桥梁技术状况评定标准》（JTG/T H21-2011）规定评定方法，[桥梁名称]的技术状况评定结果如下：
        content.append("依据《公路桥梁技术状况评定标准》（JTG/T H21-2011）规定评定方法，")
                .append(bridgeName)
                .append("的技术状况评定结果如下：\n");

        // 第二句话：上部结构技术状况评分为xx分，等级为x类；下部结构技术状况评分为xx分，等级为x类；桥面系技术状况评分为xx分，等级为x类；全桥技术状况评分为xx分，评定为x类桥梁。
        content.append("上部结构技术状况评分为")
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

        // 第三句话：全桥技术状况评定按照评定单元最低分进行评定，因此评定为x类。
        content.append("全桥技术状况评定按照评定单元最低分进行评定，因此评定为")
                .append(evaluation.getSystemLevel())
                .append("类。\n");

        // 第四句话：技术状况评定记录和具体评分见表10.1所示。
        content.append("技术状况评定记录和具体评分见表10.1所示。");

        return content.toString();
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
     * 处理单桥模板用户填写的数据（仅处理文本，照片由系统自动处理）
     */
    private void processSingleBridgeUserData(XWPFDocument document, List<ReportData> reportDataList,
                                             Building building, Map<String, ReportData> dataMap, Project project, BiObject biObject) {
        for (ReportData data : reportDataList) {
            String key = data.getKey();
            String value = data.getValue();
            Integer type = data.getType();
            try {
                // 跳过照片相关的字段，照片由系统自动处理
                if (key != null && (key.contains("leftFront") || key.contains("rightFront") ||
                        key.contains("leftSide") || key.contains("rightSide"))) {
                    log.debug("跳过照片字段（由系统自动处理）: {}", key);
                    continue;
                }

                if (type == 0) {
                    // 文本类型
                    // 检查是否是病害选择字段，需要特殊处理
                    if (key != null && key.contains("focusDiseases")) {
                        try {
                            log.info("开始处理单桥病害数据，key: {}, value: {}", key, value);
                            // 解析病害数据
                            List<ComponentDiseaseType> combinations = parseChooseDiseaseJson(value);
                            if (!combinations.isEmpty()) {
                                // 生成重点关注病害内容（包含文字和成因分析）
                                generateSingleBridgeFocusOnDiseases(document, combinations, building, project, biObject);
                                log.info("单桥病害分析生成完成");
                            } else {
                                log.warn("病害数据解析为空");
                                ReportGenerateTools.replaceText(document, "${focusOnDiseases}", "无重点关注病害");
                            }
                        } catch (Exception e) {
                            log.error("处理单桥病害数据出错: key={}, value={}, error={}", key, value, e.getMessage(), e);
                            ReportGenerateTools.replaceText(document, "${focusOnDiseases}", "【病害数据处理失败，请联系管理员】");
                        }
                    } else {
                        // 普通文本字段 - 直接替换
                        String placeholder = "${" + key + "}";
                        ReportGenerateTools.replaceText(document, placeholder, value != null ? value : "");
                        log.debug("替换文本字段: {} = {}", key, value != null ? value : "(空)");
                    }
                }
            } catch (Exception e) {
                log.error("处理字段出错: key={}, type={}, error={}", key, type, e.getMessage(), e);
            }
        }
    }


    /**
     * 生成单桥重点关注病害内容
     */
    private void generateSingleBridgeFocusOnDiseases(XWPFDocument document, List<ComponentDiseaseType> combinations,
                                                     Building building, Project project, BiObject biObject) {
        try {
            StringBuilder content = new StringBuilder();

            // 1. 得到病害
            // 提取所有构件ID
            List<Long> componentIds = combinations.stream()
                    .map(ComponentDiseaseType::getComponentId)
                    .distinct()
                    .collect(Collectors.toList());

            // 批量查询病害数据
            List<Disease> allDiseases = diseaseMapper.selectDiseaseComponentData(
                    componentIds, building.getId(), project.getYear());

            // 2. 生成主要病害的描述和成因分析
            // 按照biObjectId 分类 ， 同时按照 diseaseTypeId 分组。
            // 分组逻辑
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
                    disease.setBuildingId(building.getId());
                    content.append(getDiseaseCause(disease));
                    content.append("\n");
                    index++;
                }

            }

            // 替换占位符
            ReportGenerateTools.replaceText(document, "${focusOnDiseases}", content.toString().trim());

        } catch (Exception e) {
            log.error("生成单桥重点关注病害内容失败", e);
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
     * 处理单桥桥梁概况照片（与组合桥同步）
     *
     * @param document   Word文档
     * @param buildingId 建筑物ID
     * @throws Exception 异常
     */
    private void insertSingleBridgeImages(XWPFDocument document, Long buildingId) throws Exception {
        log.info("开始处理单桥桥梁概况照片，buildingId: {}", buildingId);

        // 获取桥梁图片（与组合桥使用相同的逻辑）
        List<FileMap> images = fileMapController.getImageMaps(buildingId, "newfront", "newside");
        log.info("从数据库获取到 {} 张图片", images != null ? images.size() : 0);

        if (images != null) {
            for (FileMap image : images) {
                log.debug("图片信息: oldName={}, newName={}", image.getOldName(), image.getNewName());
            }
        }

        // 分类存储图片
        List<String> frontImagesList = new ArrayList<>();
        List<String> sideImagesList = new ArrayList<>();

        if (images != null) {
            for (FileMap image : images) {
                String[] parts = image.getOldName().split("_");
                log.debug("解析图片名称: {} -> parts: {}", image.getOldName(), Arrays.toString(parts));

                if (parts.length > 1 && "newfront".equals(parts[1])) {
                    frontImagesList.add(image.getNewName());
                    log.debug("添加正面照: {}", image.getNewName());
                } else if (parts.length > 1 && "newside".equals(parts[1])) {
                    sideImagesList.add(image.getNewName());
                    log.debug("添加立面照: {}", image.getNewName());
                }
            }
        }

        log.info("图片分类结果 - 正面照: {}, 立面照: {}", frontImagesList.size(), sideImagesList.size());

        // 替换单桥模板的桥梁概况照片占位符
        if (!frontImagesList.isEmpty()) {
            log.info("替换正面照占位符");
            ReportGenerateTools.replaceImageInDocument(document, "%{leftFront}", frontImagesList.get(0), null, false);
            ReportGenerateTools.replaceImageInDocument(document, "%{rightFront}", frontImagesList.size() > 1 ? frontImagesList.get(1) : frontImagesList.get(0), null, false);
        } else {
            log.warn("没有找到正面照，清除占位符");
            // 直接替换为空文本，而不是null
            ReportGenerateTools.replaceText(document, "%{leftFront}", "");
            ReportGenerateTools.replaceText(document, "%{rightFront}", "");
        }

        // 替换左右立面照
        if (!sideImagesList.isEmpty()) {
            log.info("替换立面照占位符");
            ReportGenerateTools.replaceImageInDocument(document, "%{leftSide}", sideImagesList.get(0), null, false);
            ReportGenerateTools.replaceImageInDocument(document, "%{rightSide}", sideImagesList.size() > 1 ? sideImagesList.get(1) : sideImagesList.get(0), null, false);
        } else {
            log.warn("没有找到立面照，清除占位符");
            // 直接替换为空文本，而不是null
            ReportGenerateTools.replaceText(document, "%{leftSide}", "");
            ReportGenerateTools.replaceText(document, "%{rightSide}", "");
        }

        log.info("单桥桥梁概况照片处理完成 - 正面照: {}, 立面照: {}", frontImagesList.size(), sideImagesList.size());
    }


    /**
     * 处理 近年评定结果比较分析表格
     *
     * @param document   Word文档
     * @param key        占位符
     * @param task       当前任务ID
     * @param bridgeName 桥梁名称
     */
    private void handleComparisonAnalysis(XWPFDocument document, String key, Task task, String bridgeName, boolean isSingleBridege) {
        try {
            log.info("开始处理比较分析, key: {}, taskId: {}", key, task.getId());

            // 查找占位符位置
            XWPFParagraph targetParagraph = null;
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
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

            // 调用比较分析服务生成表格
            comparisonAnalysisService.generateComparisonAnalysisTable(document, targetParagraph, task, bridgeName, isSingleBridege);

            log.info("比较分析处理完成");

        } catch (Exception e) {
            log.error("处理比较分析失败: key={}, taskId={}, error={}", key, task.getId(), e.getMessage(), e);
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
                        combinations.add(new ComponentDiseaseType(componentId, diseaseTypeId.longValue()));
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

        // 在表格前添加一个空段落，与上方内容分隔
        XWPFParagraph spacerBefore;
        if (cursor != null) {
            spacerBefore = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            spacerBefore = document.createParagraph();
        }
        spacerBefore.setSpacingAfter(300);
        spacerBefore.setSpacingBefore(300);

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
                titlePara.setStyle("12");
                titlePara.setAlignment(ParagraphAlignment.CENTER);

                // 清除现有内容
                while (titlePara.getRuns().size() > 0) {
                    titlePara.removeRun(0);
                }

                // 在现有段落中创建图片标题域，使用第3章编号和指定的书签名
                WordFieldUtils.createFigureCaptionInParagraph(titlePara, imageDesc, 3, bookmarkName);

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

        // 在表格前添加一个空段落，与上方内容分隔
        XWPFParagraph spacerBefore;
        if (cursor != null) {
            spacerBefore = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            spacerBefore = document.createParagraph();
        }
        spacerBefore.setSpacingAfter(300);
        spacerBefore.setSpacingBefore(300);

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
                titlePara.setStyle("12");
                titlePara.setAlignment(ParagraphAlignment.CENTER);

                // 清除现有内容
                while (titlePara.getRuns().size() > 0) {
                    titlePara.removeRun(0);
                }

                // 在现有段落中创建图片标题域，使用第3章编号和指定的书签名
                WordFieldUtils.createFigureCaptionInParagraph(titlePara, imageDesc, 3, bookmarkName);

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
     *
     * @param document   Word文档
     * @param key        占位符
     * @param task       当前任务
     * @param bridgeName 桥梁名称
     */
    private void handleTestConclusion(XWPFDocument document, String key, Task task, String bridgeName) {
        try {
            log.info("开始处理检测结论, key: {}, taskId: {}", key, task.getId());

            // 查找占位符位置
            XWPFParagraph targetParagraph = ReportGenerateTools.findParagraphByPlaceholder(document, key);
            if (targetParagraph == null) {
                log.warn("未找到检测结论占位符: {}", key);
                return;
            }

            // 调用检测结论服务处理检测结论
            testConclusionService.handleTestConclusion(document, targetParagraph, task, bridgeName);

            log.info("检测结论处理完成");

        } catch (Exception e) {
            log.error("处理检测结论失败: key={}, taskId={}, error={}", key, task.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 处理检测结论桥梁详情
     *
     * @param document   Word文档
     * @param key        占位符
     * @param task       当前任务
     * @param bridgeName 桥梁名称
     */
    private void handleTestConclusionBridge(XWPFDocument document, String key, Task task, String bridgeName) {
        try {
            log.info("开始处理检测结论桥梁详情, key: {}, taskId: {}", key, task.getId());

            // 查找占位符位置
            XWPFParagraph targetParagraph = ReportGenerateTools.findParagraphByPlaceholder(document, key);
            if (targetParagraph == null) {
                log.warn("未找到检测结论桥梁详情占位符: {}", key);
                return;
            }

            // 调用检测结论服务处理检测结论桥梁详情
            testConclusionService.handleTestConclusionBridge(document, targetParagraph, task, bridgeName);

            log.info("检测结论桥梁详情处理完成");

        } catch (Exception e) {
            log.error("处理检测结论桥梁详情失败: key={}, taskId={}, error={}", key, task.getId(), e.getMessage(), e);
            throw e;
        }
    }


}
