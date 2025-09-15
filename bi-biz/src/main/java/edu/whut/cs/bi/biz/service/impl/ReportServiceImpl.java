package edu.whut.cs.bi.biz.service.impl;

import java.util.*;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.domain.temp.ComponentDiseaseAnalysis;
import edu.whut.cs.bi.biz.domain.temp.ComponentDiseaseType;
import edu.whut.cs.bi.biz.domain.vo.Disease2ReportSummaryAiVO;
import edu.whut.cs.bi.biz.domain.vo.DiseaseComparisonData;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.Convert2VO;
import edu.whut.cs.bi.biz.utils.DiseaseComparisonTableUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.whut.cs.bi.biz.mapper.ReportMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.ShiroUtils;
import org.springframework.web.client.RestTemplate;

import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;

/**
 * 检测报告Service业务层处理
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class ReportServiceImpl implements IReportService {
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
    private IDiseaseService diseaseService;

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

    /**
     * 查询检测报告
     *
     * @param id 检测报告ID
     * @return 检测报告
     */
    @Override
    public Report selectReportById(Long id) {
        return reportMapper.selectReportById(id);
    }

    /**
     * 查询检测报告列表
     *
     * @param report 检测报告
     * @return 检测报告
     */
    @Override
    public List<Report> selectReportList(Report report) {
        return reportMapper.selectReportList(report);
    }

    /**
     * 新增检测报告
     *
     * @param report 检测报告
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertReport(Report report) {
        report.setCreateBy(ShiroUtils.getLoginName());
        // 设置状态为未生成
        report.setStatus(0);
        // 插入报告记录
        int rows = reportMapper.insertReport(report);
        // 生成报告数据
        if (rows > 0) {
            generateReportData(report.getId());
        }
        return rows;
    }

    /**
     * 修改检测报告
     *
     * @param report 检测报告
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateReport(Report report) {
        // 如果修改了模板，需要重新生成报告数据
        Report oldReport = reportMapper.selectReportById(report.getId());
        report.setUpdateBy(ShiroUtils.getLoginName());
        int rows = reportMapper.updateReport(report);
        if (rows > 0 && oldReport != null && !oldReport.getReportTemplateId().equals(report.getReportTemplateId())) {
            // 删除原有报告数据
            reportDataService.deleteReportDataByReportId(report.getId());
            // 重新生成报告数据
            generateReportData(report.getId());
        }
        return rows;
    }

    /**
     * 删除检测报告对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteReportByIds(String ids) {
        // 删除报告数据
        Long[] idArray = Convert.toLongArray(ids);
        for (Long id : idArray) {
            reportDataService.deleteReportDataByReportId(id);
        }
        return reportMapper.deleteReportByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除检测报告信息
     *
     * @param id 检测报告ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteReportById(Long id) {
        // 删除报告数据
        reportDataService.deleteReportDataByReportId(id);
        return reportMapper.deleteReportById(id);
    }

    /**
     * 生成报告数据
     *
     * @param reportId 报告ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateReportData(Long reportId) {
        // 查询报告信息
        Report report = reportMapper.selectReportById(reportId);
        if (report == null) {
            return 0;
        }

        // 查询模板信息
        ReportTemplate template = reportTemplateService.selectReportTemplateById(report.getReportTemplateId());
        if (template == null) {
            return 0;
        }

        // 查询模板变量
        TemplateVariable templateVariable = new TemplateVariable();
        templateVariable.setReportTemplateId(template.getId());
        List<TemplateVariable> templateVariables = templateVariableService.selectTemplateVariableList(templateVariable);

        // 生成报告数据
        List<ReportData> reportDataList = new ArrayList<>();
        String loginName = ShiroUtils.getSysUser().getLoginName();

        for (TemplateVariable variable : templateVariables) {
            ReportData reportData = new ReportData();
            reportData.setReportId(reportId);
            reportData.setKey(variable.getName());
            reportData.setValue(""); // 初始值为空
            reportData.setType(variable.getType());
            reportData.setCreateBy(loginName);
            reportDataList.add(reportData);
        }

        // 批量插入报告数据
        if (reportDataList.size() > 0) {
            return reportDataService.batchInsertReportData(reportDataList);
        }

        return 0;
    }

    /**
     * 生成报告文档
     *
     * @param report   报告ID
     * @param buildingId 建筑物ID
     * @return 生成的文件路径
     */
    @Override
    public String generateReportDocument(Report report, Long buildingId, Long projectId,Long taskId) {
        InputStream templateStream = null;
        FileOutputStream out = null;
        XWPFDocument document = null;
        File outputFile = null;

        try {
            if (report == null) {
                return null;
            }

            // 查询建筑物信息
            Building building;
            if (buildingId != null) {
                building = buildingService.selectBuildingById(buildingId);
            } else {
                return null;
            }

            if (building == null) {
                log.info("未找到指定的建筑物");
                return null;
            }

            // 查询项目信息
            Project project = null;
            if (report.getProjectId() != null) {
                project = projectService.selectProjectById(report.getProjectId());
            }
            log.info("project: {}", project);

            // 查询报告模板信息
            ReportTemplate template = reportTemplateService.selectReportTemplateById(report.getReportTemplateId());
            if (template == null || template.getMinioId() == null) {
                return null;
            }

            // 通过模板文件ID获取文件信息
            FileMap fileMap = fileMapService.selectFileMapById(template.getMinioId());
            if (fileMap == null) {
                return null;
            }

            // 拼接Minio下载地址
            String s = fileMap.getNewName();

            // 从Minio下载模板文件
            templateStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(s.substring(0, 2) + "/" + s)
                            .build()
            );

            // 加载Word文档
            document = new XWPFDocument(templateStream);

            // 重置域计数器（开始新文档）
            WordFieldUtils.resetCounters();

            // 查询报告数据
            ReportData queryParam = new ReportData();
            queryParam.setReportId(report.getId());
            List<ReportData> reportDataList = reportDataService.selectReportDataList(queryParam);

            // 创建数据映射
            Map<String, String> dataMap = new HashMap<>();
            for (ReportData data : reportDataList) {
                dataMap.put(data.getKey(), data.getValue());
            }

            // 获取当前日期
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            // 替换日期相关占位符
            replaceText(document, "${year}", String.valueOf(year));
            replaceText(document, "${month}", String.valueOf(month));
            replaceText(document, "${day}", String.valueOf(day));

            // 替换项目相关信息
            if (project != null) {
                // 项目名称
                replaceText(document, "${project-name}", project.getName());

                // 委托单位 - 从项目的dept中获取
                if (project.getDept() != null && project.getDept().getDeptName() != null) {
                    replaceText(document, "${client-unit}", project.getDept().getDeptName());
                }
            }

            // 处理桥梁图片
            insertBridgeImages(document, buildingId);
            log.info("桥梁照片处理结束");
//            debugAllStyles(document);

            // 处理第三章外观检测结果
            BiObject biObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
            List<BiObject> biObjects = new ArrayList<>();
            biObjects.add(biObject);
            processChapter3(document, biObjects, building, projectId);

            // 处理第八章评定结果（不依赖ReportData）
            try {
                handleChapter8EvaluationResults(document, "${chapter-8-evaluationResults}", building, taskId);
            } catch (Exception e) {
                log.error("处理第八章评定结果出错: error={}", e.getMessage());
                // 如果处理失败，降级为普通文本替换
                replaceText(document, "${chapter-8-evaluationResults}", "评定结果数据获取失败");
            }

            // 替换其他占位符
            for (ReportData data : reportDataList) {
                String key = data.getKey();
                String value = data.getValue();
                Integer type = data.getType();

                if (value == null || value.isEmpty()) {
                    continue;
                }

                try {
                    // 根据类型处理不同的数据
                    if (type != null && type == 1) {
                        // 图片类型
                        try {
                            // 处理图片
                            handleImagePlaceholder(document, key, value, building);
                        } catch (Exception e) {
                            log.error("处理图片占位符出错: key={}, value={}, error={}", key, value, e.getMessage());
                        }
                    } else {
                        // 文本类型（默认）
                        // 检查是否是第七章的特殊字段，需要生成表格
                        if (key.contains("${chapter-7-1-focusOnDiseases}") || key.contains("${chapter-7-2-analysisOfTheCausesOfMajorDiseases}")) {
                            try {
                                handleChapter7DiseaseTable(document, key, value, building, project, biObject);
                            } catch (Exception e) {
                                log.error("处理第七章病害表格出错: key={}, value={}, error={}", key, value, e.getMessage());
                                // 如果处理失败，降级为普通文本替换
                                replaceText(document, key, value);
                            }
                        } else {
                            replaceText(document, key, value);
                        }
                    }
                } catch (Exception e) {
                    log.error("替换占位符出错: key={}, value={}, type={}, error={}", key, value, type, e.getMessage());
                }
            }

            // 更新文档中的所有域
            WordFieldUtils.updateAllFields(document);

            // 创建临时文件保存生成的文档
            String fileName = report.getName() + "_" + ".docx";
            outputFile = File.createTempFile("report_" + report.getId(), ".docx");
            out = new FileOutputStream(outputFile);
            document.write(out);
            out.close();

            // 上传到MinIO
            FileMap reportFileMap = fileMapService.handleFileUploadFromFile(outputFile, fileName, ShiroUtils.getLoginName());

            // 更新报告状态和MinioId已生成
            report.setStatus(1);
            report.setMinioId(Long.valueOf(reportFileMap.getId()));
            reportMapper.updateReport(report);

            return reportFileMap.getId().toString();
        } catch (Exception e) {
            log.error("生成报告报错：{}", e.getMessage());
            return null;
        } finally {
            // 关闭资源
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
                // 删除临时文件
                if (outputFile != null && outputFile.exists()) {
                    outputFile.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 异步生成报告文档
     *
     * @param report   报告ID
     * @param buildingId 建筑物ID
     * @param projectId  项目ID
     */
    @Async("reportTaskExecutor")
    public void generateReportDocumentAsync(Report report, Long buildingId, Long projectId,Long taskId) {
        try {
            // 更新报告状态为生成中
            Report updateReport = new Report();
            updateReport.setId(report.getId());
            updateReport.setStatus(2);
            updateReport.setUpdateBy(ShiroUtils.getLoginName());
            updateReport.setUpdateTime(new Date());
            reportMapper.updateReport(updateReport);

            // 生成报告文档
            log.info("开始生成报告");
            String minioId = generateReportDocument(report, buildingId, projectId,taskId);
            log.info("生成报告结束");

            // 更新报告状态为已生成并保存MinioID
            updateReport = new Report();
            updateReport.setId(report.getId());
            updateReport.setStatus(1);
            updateReport.setMinioId(Long.valueOf(minioId));
            updateReport.setUpdateBy(ShiroUtils.getLoginName());
            updateReport.setUpdateTime(new Date());
            reportMapper.updateReport(updateReport);
        } catch (Exception e) {
            log.error("异步生成报告文档失败", e);
            // 更新报告状态为生成失败
            Report updateReport = new Report();
            updateReport.setId(report.getId());
            updateReport.setStatus(3);
            updateReport.setUpdateBy(ShiroUtils.getLoginName());
            updateReport.setUpdateTime(new Date());
            reportMapper.updateReport(updateReport);
        }
    }

    /**
     * 替换Word文档中的文本
     *
     * @param document Word文档
     * @param oldText  要替换的文本
     * @param newText  新文本
     */
    private void replaceText(XWPFDocument document, String oldText, String newText) {
        if (newText == null) {
            newText = "";
        }

        // 替换段落中的文本
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String paragraphText = paragraph.getText();
            if (paragraphText != null && paragraphText.contains(oldText)) {
                List<XWPFRun> runs = paragraph.getRuns();

                // 先尝试简单替换（单个run包含完整占位符的情况）
                boolean replaced = false;
                for (int i = 0; i < runs.size(); i++) {
                    XWPFRun run = runs.get(i);
                    String text = run.getText(0);
                    if (text != null && text.contains(oldText)) {
                        // 保留原有的字体样式和大小
                        String replacedText = text.replace(oldText, newText);
                        run.setText(replacedText, 0);
                        replaced = true;
                        break;
                    }
                }

                // 如果简单替换失败，处理占位符分散在多个run的情况
                if (!replaced && runs.size() > 1) {
                    // 查找占位符的开始和结束位置
                    int startRunIndex = -1;
                    int endRunIndex = -1;
                    StringBuilder fullText = new StringBuilder();

                    // 寻找占位符的开始和结束位置
                    for (int i = 0; i < runs.size(); i++) {
                        String text = runs.get(i).getText(0);
                        if (text == null) continue;

                        fullText.append(text);
                        // 记录可能的起始位置
                        if (startRunIndex == -1 && text.contains("${")) {
                            startRunIndex = i;
                        }

                        // 检查到目前为止的文本是否包含完整占位符
                        if (fullText.toString().contains(oldText)) {
                            endRunIndex = i;
                            break;
                        }
                    }

                    // 如果找到了完整的占位符
                    if (startRunIndex != -1 && endRunIndex != -1) {
                        log.info("发现分散占位符: {} 从run[{}]到run[{}]", oldText, startRunIndex, endRunIndex);

                        // 将第一个run设置为替换后的文本
                        runs.get(startRunIndex).setText(newText, 0);

                        // 清空其他包含占位符的run
                        for (int i = startRunIndex + 1; i <= endRunIndex; i++) {
                            runs.get(i).setText("", 0);
                        }
                    }
                }
            }
        }

        // 替换表格中的文本
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        String paragraphText = paragraph.getText();
                        if (paragraphText != null && paragraphText.contains(oldText)) {
                            List<XWPFRun> runs = paragraph.getRuns();

                            // 先尝试简单替换（单个run包含完整占位符的情况）
                            boolean replaced = false;
                            for (int i = 0; i < runs.size(); i++) {
                                XWPFRun run = runs.get(i);
                                String text = run.getText(0);
                                if (text != null && text.contains(oldText)) {
                                    // 保留原有的字体样式和大小
                                    String replacedText = text.replace(oldText, newText);
                                    run.setText(replacedText, 0);
                                    replaced = true;
                                    break;
                                }
                            }

                            // 如果简单替换失败，处理占位符分散在多个run的情况
                            if (!replaced && runs.size() > 1) {
                                // 查找占位符的开始和结束位置
                                int startRunIndex = -1;
                                int endRunIndex = -1;
                                StringBuilder fullText = new StringBuilder();

                                // 寻找占位符的开始和结束位置
                                for (int i = 0; i < runs.size(); i++) {
                                    String text = runs.get(i).getText(0);
                                    if (text == null) continue;

                                    fullText.append(text);
                                    // 记录可能的起始位置
                                    if (startRunIndex == -1 && text.contains("${")) {
                                        startRunIndex = i;
                                    }

                                    // 检查到目前为止的文本是否包含完整占位符
                                    if (fullText.toString().contains(oldText)) {
                                        endRunIndex = i;
                                        break;
                                    }
                                }

                                // 如果找到了完整的占位符
                                if (startRunIndex != -1 && endRunIndex != -1) {
                                    log.info("发现表格中的分散占位符: {} 从run[{}]到run[{}]", oldText, startRunIndex, endRunIndex);

                                    // 将第一个run设置为替换后的文本
                                    runs.get(startRunIndex).setText(newText, 0);

                                    // 清空其他包含占位符的run
                                    for (int i = startRunIndex + 1; i <= endRunIndex; i++) {
                                        runs.get(i).setText("", 0);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 处理桥梁图片
     *
     * @param document   Word文档
     * @param buildingId 建筑物ID
     * @throws Exception 异常
     */
    private void insertBridgeImages(XWPFDocument document, Long buildingId) throws Exception {
        // 获取桥梁图片
        List<FileMap> images = fileMapController.getImageMaps(buildingId, "newfront", "newside");

        // 分类存储图片
        List<String> frontImagesList = new ArrayList<>();
        List<String> sideImagesList = new ArrayList<>();

        for (FileMap image : images) {
            String[] parts = image.getOldName().split("_");
            if (parts.length > 1 && "newfront".equals(parts[1])) {
                frontImagesList.add(image.getNewName());
            } else if (parts.length > 1 && "newside".equals(parts[1])) {
                sideImagesList.add(image.getNewName());
            }
        }

        // 替换左右正面照
        if (!frontImagesList.isEmpty()) {
            replaceImageInDocument(document, "${chapter-1-2-leftFront}", frontImagesList.get(0));
            replaceImageInDocument(document, "${chapter-1-2-rightFront}", frontImagesList.size() > 1 ? frontImagesList.get(1) : frontImagesList.get(0));
        } else {
            // 清除占位符
            replaceImageInDocument(document, "${chapter-1-2-leftFront}", null);
            replaceImageInDocument(document, "${chapter-1-2-rightFront}", null);
        }

        // 替换左右立面照
        if (!sideImagesList.isEmpty()) {
            replaceImageInDocument(document, "${chapter-1-2-leftSide}", sideImagesList.get(0));
            replaceImageInDocument(document, "${chapter-1-2-rightSide}", sideImagesList.size() > 1 ? sideImagesList.get(1) : sideImagesList.get(0));
        } else {
            // 清除占位符
            replaceImageInDocument(document, "${chapter-1-2-leftSide}", null);
            replaceImageInDocument(document, "${chapter-1-2-rightSide}", null);
        }
    }


    /**
     * 在Word文档中替换占位符为图片（无标题版本）
     *
     * @param document      Word文档
     * @param placeholder   占位符（包含${}）
     * @param imageFileName 图片文件名
     * @throws Exception 异常
     */
    private void replaceImageInDocument(XWPFDocument document, String placeholder, String imageFileName) throws Exception {
        replaceImageInDocument(document, placeholder, imageFileName, null, false);
    }

    private void clearParagraph(XWPFParagraph paragraph) {
        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }
    }


    /**
     * 处理第三章外观检测结果
     *
     * @param document   Word文档
     * @param subBridges 建筑物信息
     * @throws Exception 异常
     */
    private void processChapter3(XWPFDocument document, List<BiObject> subBridges, Building building, Long projectId) throws Exception {


        // 获取所有子部件
        List<BiObject> allObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());

        // 找到占位符所在的段落
        XWPFParagraph placeholderParagraph = null;

        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getText().contains("${chapter-3-appearanceInspectionResults}")) {
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

        // 为每个子桥生成病害对比表格
        generateDiseaseComparisonTables(document, subBridges, building, projectId, cursor, chapterNum, subChapterNum, chapter3TableCounter);

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

        // 提前收集所有病害的图片书签信息
        Map<Long, List<String>> diseaseImageRefs = new HashMap<>(); // key: 病害ID, value: 图片书签列表

        for (Disease d : nodeDiseases) {
            List<String> imageBookmarks = new ArrayList<>();
            List<Map<String, Object>> images = diseaseController.getDiseaseImage(d.getId());
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
                        String imageDesc = componentName + d.getType();
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
            String diseaseString = getDiseaseSummary(nodeDiseases);
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
            String tableBookmark = addTableCaption(document, node.getName(), cursor, chapter3TableCounter);

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
                paragraph.setAlignment(ParagraphAlignment.CENTER);

                XWPFRun run1 = paragraph.createRun();
                run1.setText(headers[i]);
                run1.setBold(true);

                // 设置10.5字号的解决方案
                run1.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21));
                run1.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));

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
                    cellP.setAlignment(ParagraphAlignment.CENTER);

                    // 设置文本内容
                    XWPFRun cellR = cellP.createRun();

                    // 设置10.5字号
                    cellR.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21)); // 10.5pt = 21 half-points
                    cellR.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21)); // 复杂脚本的字号
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
                            cellR.setText("/");
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
                                        commaRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21));
                                        commaRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));
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
        }

        // 递归写子节点
        List<BiObject> children = allNodes.stream()
                .filter(obj -> node.getId().equals(obj.getParentId()))
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());

        int idx = 1;
        for (BiObject child : children) {
            writeBiObjectTreeToWord(document, child, allNodes, diseaseMap, prefix + "." + idx, level + 1, chapterImageCounter, chapter3TableCounter, cursor, baseHeadingLevel);
            idx++;
        }
    }


    /**
     * 添加表格标题的方法（使用域）
     *
     * @param cursor 指定插入位置的游标，如果为null则追加到文档末尾
     * @return 返回表格的书签名，用于后续引用
     */
    private String addTableCaption(XWPFDocument document, String nodeName, XmlCursor cursor, AtomicInteger chapter3TableCounter) {
        String titleText = nodeName + "检测结果表";
        // 第三章的表格使用章节编号，传递表格计数器用于正确的序号生成
        return WordFieldUtils.createTableCaptionWithCounter(document, titleText, cursor, 3, chapter3TableCounter);
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
            List<Map<String, Object>> images = diseaseController.getDiseaseImage(d.getId());
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

            // 初始化表格结构
            XWPFTableRow row0 = table.getRow(0);
            row0.getCell(0);
            row0.addNewTableCell(); // 添加第二列

            // 创建其余行
            for (int i = 1; i < totalRows; i++) {
                XWPFTableRow newRow = table.createRow();
                newRow.getCell(0);
                newRow.addNewTableCell();
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
        tblWidth.setW(BigInteger.valueOf(10000)); // 使用100%宽度
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格边框样式
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();

        // 设置外边框
        CTBorder topBorder = borders.addNewTop();
        topBorder.setVal(STBorder.DASHED);
        topBorder.setSz(BigInteger.valueOf(6)); // 设置边框宽度

        CTBorder bottomBorder = borders.addNewBottom();
        bottomBorder.setVal(STBorder.DASHED);
        bottomBorder.setSz(BigInteger.valueOf(6));

        CTBorder leftBorder = borders.addNewLeft();
        leftBorder.setVal(STBorder.DASHED);
        leftBorder.setSz(BigInteger.valueOf(6));

        CTBorder rightBorder = borders.addNewRight();
        rightBorder.setVal(STBorder.DASHED);
        rightBorder.setSz(BigInteger.valueOf(6));

        // 设置内部边框
        CTBorder insideH = borders.addNewInsideH();
        insideH.setVal(STBorder.DASHED);
        insideH.setSz(BigInteger.valueOf(6));

        CTBorder insideV = borders.addNewInsideV();
        insideV.setVal(STBorder.DASHED);
        insideV.setSz(BigInteger.valueOf(6));

        CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJcTable.CENTER);

        // 设置列宽相等（每列占50%宽度）
        int cellWidth = 5000; // 10000 / 2 = 5000

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
                    int imgWidth = 7 * 360000;
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
     * 处理图片占位符
     *
     * @param document Word文档
     * @param key      占位符的key
     * @param value    图片的MinIO ID，可能是逗号分隔的多个ID
     * @throws Exception 异常
     */
    private void handleImagePlaceholder(XWPFDocument document, String key, String value, Building building) throws Exception {
        if (value == null || value.isEmpty()) {
            return;
        }

        // 检查是否有多个图片ID
        String[] imageIds = value.split(",");
        // 过滤空的imageId
        imageIds = Arrays.stream(imageIds)
                .filter(id -> id != null && !id.trim().isEmpty())
                .map(String::trim)
                .toArray(String[]::new);

        if (imageIds.length == 0) {
            return;
        }

        // 为特定的key设置特殊标题
        String imageTitle = null;
        String titleText = null;
        Integer chapterNumber = null;
        if ("${chapter-1-2-bridgeLayoutPlan}".equals(key)) {
            titleText = "桥型布置图";
            chapterNumber = 1;
        } else if ("${chapter-1-2-bridgeStandardCrossSection}".equals(key)) {
            titleText = "横断面布置图";
            chapterNumber = 1;
        } else if ("${chapter-4-1-layoutOfMeasuringPoints}".equals(key)) {
            // 构建桥梁测点布置示意图标题：桥梁名称 + "测点布置示意图"
            String bridgeName = building != null && building.getName() != null ? building.getName() : "桥梁";
            titleText = bridgeName + "测点布置示意图";
            chapterNumber = 4;
        }

        // 判断是否为封面图片，需要特殊处理
        boolean isCoverImage = "${cover-image}".equals(key);

        if (imageIds.length == 1) {
            // 单张图片
            String imageId = imageIds[0];
            FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(imageId));
            if (fileMap != null) {
                // 替换图片，并添加标题域
                replaceImageInDocumentWithField(document, key, fileMap.getNewName(), titleText, chapterNumber, isCoverImage);
            } else {
                log.warn("未找到图片文件: imageId={}", imageId);
            }
        } else {
            // 多张图片，需要创建表格来展示
            insertMultipleImagesTableWithField(document, key, imageIds, titleText, chapterNumber);
        }
    }

    /**
     * 在Word文档中插入多张图片的表格（使用域）
     *
     * @param document      Word文档
     * @param key           占位符的key（不包含${}）
     * @param imageIds      图片的MinIO ID数组
     * @param titleText     图片标题文本，可以为null
     * @param chapterNumber 章节号，可以为null
     * @throws Exception 异常
     */
    private void insertMultipleImagesTableWithField(XWPFDocument document, String key, String[] imageIds, String titleText, Integer chapterNumber) throws Exception {
        if (imageIds == null || imageIds.length == 0) return;

        // 查找占位符段落
        XWPFParagraph placeholderParagraph = null;
        int placeholderIndex = -1;
        XWPFTableCell placeholderCell = null;
        boolean isInTable = false;

        // 在段落中查找
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            if (paragraph.getText().contains(key)) {
                placeholderParagraph = paragraph;
                placeholderIndex = i;
                break;
            }
        }

        // 如果在段落中没找到，尝试在表格中查找
        if (placeholderParagraph == null) {
            outerLoop:
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            if (paragraph.getText().contains(key)) {
                                placeholderParagraph = paragraph;
                                placeholderCell = cell;
                                isInTable = true;
                                break outerLoop;
                            }
                        }
                    }
                }
            }
        }

        if (placeholderParagraph == null) {
            log.warn("未找到占位符: {}", key);
            return;
        }

        try {
            if (isInTable) {
                // 在表格单元格中处理多张图片
                handleMultipleImagesInCellWithField(document, placeholderCell, placeholderParagraph, imageIds, titleText, chapterNumber);
            } else {
                // 在普通段落中处理多张图片
                handleMultipleImagesInParagraphWithField(document, placeholderParagraph, placeholderIndex, imageIds, titleText, chapterNumber);
            }
        } catch (Exception e) {
            log.error("插入多张图片失败，占位符: {}, 错误: {}", key, e.getMessage(), e);
            // 异常时恢复占位符
            if (!placeholderParagraph.getText().contains(key)) {
                clearParagraph(placeholderParagraph);
                XWPFRun run = placeholderParagraph.createRun();
                run.setText(key);
            }
            throw e;
        }
    }

    /**
     * 在Word文档中插入多张图片的表格（旧版本，保留兼容性）
     *
     * @param document   Word文档
     * @param key        占位符的key（不包含${}）
     * @param imageIds   图片的MinIO ID数组
     * @param imageTitle 图片标题，可以为null
     * @throws Exception 异常
     */
    private void insertMultipleImagesTable(XWPFDocument document, String key, String[] imageIds, String imageTitle) throws Exception {
        if (imageIds == null || imageIds.length == 0) return;

        // 查找占位符段落
        XWPFParagraph placeholderParagraph = null;
        int placeholderIndex = -1;
        XWPFTableCell placeholderCell = null;
        boolean isInTable = false;

        // 在段落中查找
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            if (paragraph.getText().contains(key)) {
                placeholderParagraph = paragraph;
                placeholderIndex = i;
                break;
            }
        }

        // 如果在段落中没找到，尝试在表格中查找
        if (placeholderParagraph == null) {
            outerLoop:
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            if (paragraph.getText().contains(key)) {
                                placeholderParagraph = paragraph;
                                placeholderCell = cell;
                                isInTable = true;
                                break outerLoop;
                            }
                        }
                    }
                }
            }
        }

        if (placeholderParagraph == null) {
            log.warn("未找到占位符: {}", key);
            return;
        }

        try {
            if (isInTable) {
                // 在表格单元格中处理多张图片
                handleMultipleImagesInCell(placeholderCell, placeholderParagraph, imageIds, imageTitle);
            } else {
                // 在普通段落中处理多张图片
                handleMultipleImagesInParagraph(document, placeholderParagraph, placeholderIndex, imageIds, imageTitle);
            }
        } catch (Exception e) {
            log.error("插入多张图片失败，占位符: {}, 错误: {}", key, e.getMessage(), e);
            // 异常时恢复占位符
            if (!placeholderParagraph.getText().contains(key)) {
                clearParagraph(placeholderParagraph);
                XWPFRun run = placeholderParagraph.createRun();
                run.setText(key);
            }
            throw e;
        }
    }

    /**
     * 在表格单元格中处理多张图片（使用域）
     */
    private void handleMultipleImagesInCellWithField(XWPFDocument document, XWPFTableCell cell, XWPFParagraph placeholderParagraph, String[] imageIds, String titleText, Integer chapterNumber) throws Exception {
        // 清除占位符
        clearParagraph(placeholderParagraph);
        placeholderParagraph.setAlignment(ParagraphAlignment.CENTER);

        // 添加所有图片
        for (String imageId : imageIds) {
            if (imageId == null || imageId.trim().isEmpty()) continue;

            try {
                FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(imageId.trim()));
                if (fileMap != null) {
                    XWPFParagraph imgParagraph = cell.addParagraph();
                    imgParagraph.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun run = imgParagraph.createRun();

                    // 从MinIO获取图片
                    try (InputStream imageStream = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(minioConfig.getBucketName())
                                    .object(fileMap.getNewName().substring(0, 2) + "/" + fileMap.getNewName())
                                    .build()
                    )) {
                        // 表格中的图片使用较小尺寸
                        run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, fileMap.getNewName(),
                                Units.toEMU(200), Units.toEMU(150));
                    }
                } else {
                    log.warn("未找到图片文件: imageId={}", imageId);
                }
            } catch (Exception e) {
                log.error("处理图片出错: imageId={}, error={}", imageId, e.getMessage());
            }
        }

        // 如果有特定标题，在所有图片之后添加标题域
        if (titleText != null && !titleText.isEmpty()) {
            XWPFParagraph titleParagraph = cell.addParagraph();

            // 在现有段落中创建图片标题域
            WordFieldUtils.createFigureCaptionInParagraph(titleParagraph, titleText, chapterNumber, null);
        }
    }

    /**
     * 在表格单元格中处理多张图片（旧版本，保留兼容性）
     */
    private void handleMultipleImagesInCell(XWPFTableCell cell, XWPFParagraph placeholderParagraph, String[] imageIds, String imageTitle) throws Exception {
        // 清除占位符
        clearParagraph(placeholderParagraph);
        placeholderParagraph.setAlignment(ParagraphAlignment.CENTER);

        // 添加所有图片
        for (String imageId : imageIds) {
            if (imageId == null || imageId.trim().isEmpty()) continue;

            try {
                FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(imageId.trim()));
                if (fileMap != null) {
                    XWPFParagraph imgParagraph = cell.addParagraph();
                    imgParagraph.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun run = imgParagraph.createRun();

                    // 从MinIO获取图片
                    try (InputStream imageStream = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(minioConfig.getBucketName())
                                    .object(fileMap.getNewName().substring(0, 2) + "/" + fileMap.getNewName())
                                    .build()
                    )) {
                        // 表格中的图片使用较小尺寸
                        run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, fileMap.getNewName(),
                                Units.toEMU(200), Units.toEMU(150));
                    }
                } else {
                    log.warn("未找到图片文件: imageId={}", imageId);
                }
            } catch (Exception e) {
                log.error("处理图片出错: imageId={}, error={}", imageId, e.getMessage());
            }
        }

        // 如果有特定标题，在所有图片之后添加标题段落
        if (imageTitle != null && !imageTitle.isEmpty()) {
            XWPFParagraph titleParagraph = cell.addParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            titleParagraph.setStyle("12");
            titleParagraph.setSpacingBefore(100);
            titleParagraph.setSpacingAfter(200);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(imageTitle);
        }
    }

    /**
     * 在普通段落中处理多张图片（使用域）
     */
    private void handleMultipleImagesInParagraphWithField(XWPFDocument document, XWPFParagraph placeholderParagraph,
                                                          int placeholderIndex, String[] imageIds, String titleText, Integer chapterNumber) throws Exception {
        // 清除占位符段落的内容，但保留段落本身
        clearParagraph(placeholderParagraph);
        placeholderParagraph.setAlignment(ParagraphAlignment.CENTER);

        // 记录成功插入的段落，用于异常时清理
        List<XWPFParagraph> insertedParagraphs = new ArrayList<>();

        try {
            // 在占位符段落中插入第一张图片
            String firstImageId = imageIds[0];
            FileMap firstFileMap = fileMapService.selectFileMapById(Long.valueOf(firstImageId));
            if (firstFileMap != null) {
                XWPFRun run = placeholderParagraph.createRun();

                // 从MinIO获取图片
                try (InputStream imageStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(firstFileMap.getNewName().substring(0, 2) + "/" + firstFileMap.getNewName())
                                .build()
                )) {
                    // 添加图片到段落
                    run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, firstFileMap.getNewName(),
                            Units.toEMU(400), Units.toEMU(300));
                }
            }

            // 插入剩余图片到新段落
            int currentIndex = placeholderIndex;
            for (int i = 1; i < imageIds.length; i++) {
                String imageId = imageIds[i];
                if (imageId == null || imageId.trim().isEmpty()) continue;

                FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(imageId.trim()));
                if (fileMap != null) {
                    // 在指定位置插入新段落，而不是在末尾创建后移动
                    XWPFParagraph newParagraph = document.insertNewParagraph(placeholderParagraph.getCTP().newCursor());
                    newParagraph.setAlignment(ParagraphAlignment.CENTER);
                    insertedParagraphs.add(newParagraph);

                    XWPFRun run = newParagraph.createRun();

                    // 从MinIO获取图片
                    try (InputStream imageStream = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(minioConfig.getBucketName())
                                    .object(fileMap.getNewName().substring(0, 2) + "/" + fileMap.getNewName())
                                    .build()
                    )) {
                        // 添加图片到段落
                        run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, fileMap.getNewName(),
                                Units.toEMU(400), Units.toEMU(300));
                    }
                    currentIndex++;
                } else {
                    log.warn("未找到图片文件: imageId={}", imageId);
                }
            }

            // 添加标题域
            if (titleText != null && !titleText.isEmpty()) {
                XWPFParagraph titleParagraph = document.insertNewParagraph(
                        insertedParagraphs.isEmpty() ?
                                placeholderParagraph.getCTP().newCursor() :
                                insertedParagraphs.get(insertedParagraphs.size() - 1).getCTP().newCursor()
                );

                // 在现有段落中创建图片标题域
                WordFieldUtils.createFigureCaptionInParagraph(titleParagraph, titleText, chapterNumber, null);
                insertedParagraphs.add(titleParagraph);
            }

        } catch (Exception e) {
            // 异常时清理已插入的段落
            for (XWPFParagraph p : insertedParagraphs) {
                try {
                    int pos = document.getPosOfParagraph(p);
                    if (pos >= 0) {
                        document.removeBodyElement(pos);
                    }
                } catch (Exception cleanupEx) {
                    log.error("清理段落失败", cleanupEx);
                }
            }
            throw e;
        }
    }

    /**
     * 在普通段落中处理多张图片（旧版本，保留兼容性）
     */
    private void handleMultipleImagesInParagraph(XWPFDocument document, XWPFParagraph placeholderParagraph,
                                                 int placeholderIndex, String[] imageIds, String imageTitle) throws Exception {
        // 清除占位符段落的内容，但保留段落本身
        clearParagraph(placeholderParagraph);
        placeholderParagraph.setAlignment(ParagraphAlignment.CENTER);

        // 记录成功插入的段落，用于异常时清理
        List<XWPFParagraph> insertedParagraphs = new ArrayList<>();

        try {
            // 在占位符段落中插入第一张图片
            String firstImageId = imageIds[0];
            FileMap firstFileMap = fileMapService.selectFileMapById(Long.valueOf(firstImageId));
            if (firstFileMap != null) {
                XWPFRun run = placeholderParagraph.createRun();

                // 从MinIO获取图片
                try (InputStream imageStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(firstFileMap.getNewName().substring(0, 2) + "/" + firstFileMap.getNewName())
                                .build()
                )) {
                    // 添加图片到段落
                    run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, firstFileMap.getNewName(),
                            Units.toEMU(400), Units.toEMU(300));
                }
            }

            // 插入剩余图片到新段落
            int currentIndex = placeholderIndex;
            for (int i = 1; i < imageIds.length; i++) {
                String imageId = imageIds[i];
                if (imageId == null || imageId.trim().isEmpty()) continue;

                FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(imageId.trim()));
                if (fileMap != null) {
                    // 在指定位置插入新段落，而不是在末尾创建后移动
                    XWPFParagraph newParagraph = document.insertNewParagraph(placeholderParagraph.getCTP().newCursor());
                    newParagraph.setAlignment(ParagraphAlignment.CENTER);
                    insertedParagraphs.add(newParagraph);

                    XWPFRun run = newParagraph.createRun();

                    // 从MinIO获取图片
                    try (InputStream imageStream = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(minioConfig.getBucketName())
                                    .object(fileMap.getNewName().substring(0, 2) + "/" + fileMap.getNewName())
                                    .build()
                    )) {
                        // 添加图片到段落
                        run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, fileMap.getNewName(),
                                Units.toEMU(400), Units.toEMU(300));
                    }
                    currentIndex++;
                } else {
                    log.warn("未找到图片文件: imageId={}", imageId);
                }
            }

            // 添加标题段落
            if (imageTitle != null && !imageTitle.isEmpty()) {
                XWPFParagraph titleParagraph = document.insertNewParagraph(
                        insertedParagraphs.isEmpty() ?
                                placeholderParagraph.getCTP().newCursor() :
                                insertedParagraphs.get(insertedParagraphs.size() - 1).getCTP().newCursor()
                );
                titleParagraph.setAlignment(ParagraphAlignment.CENTER);
                titleParagraph.setStyle("12");
                titleParagraph.setSpacingBefore(100);
                titleParagraph.setSpacingAfter(200);
                XWPFRun titleRun = titleParagraph.createRun();
                titleRun.setText(imageTitle);
                insertedParagraphs.add(titleParagraph);
            }

        } catch (Exception e) {
            // 异常时清理已插入的段落
            for (XWPFParagraph p : insertedParagraphs) {
                try {
                    int pos = document.getPosOfParagraph(p);
                    if (pos >= 0) {
                        document.removeBodyElement(pos);
                    }
                } catch (Exception cleanupEx) {
                    log.error("清理段落失败", cleanupEx);
                }
            }
            throw e;
        }
    }

    /**
     * 替换文档中的图片（使用域）
     */
    private void replaceImageInDocumentWithField(XWPFDocument document, String placeholder, String imageFileName,
                                                 String titleText, Integer chapterNumber, boolean isCoverImage) throws Exception {
        String imageUrl = null;
        if (imageFileName != null) {
            imageUrl = imageFileName.substring(imageFileName.lastIndexOf('/') + 1);
        }

        // 替换段落中的图片
        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getText().contains(placeholder)) {
                try {
                    // 清除占位符段落的内容，但保留段落本身
                    clearParagraph(paragraph);

                    // 设置段落居中对齐
                    paragraph.setAlignment(ParagraphAlignment.CENTER);

                    // 封面图片特殊处理
                    if (isCoverImage) {
                        // 封面图片设置特殊属性
                        paragraph.setSpacingBefore(0);
                        paragraph.setSpacingAfter(0);
                        paragraph.setSpacingBetween(1.0);
                    }

                    if (imageUrl != null) {
                        try (InputStream imageStream = minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(minioConfig.getBucketName())
                                        .object(imageUrl.substring(0, 2) + "/" + imageUrl)
                                        .build()
                        )) {
                            XWPFRun run = paragraph.createRun();

                            // 根据是否为封面图片设置不同尺寸
                            int width, height;
                            if (isCoverImage) {
                                // 封面图片使用更大尺寸，并设置为内联图片
                                width = Units.toEMU(400);
                                height = Units.toEMU(300);
                            } else {
                                width = Units.toEMU(400);
                                height = Units.toEMU(300);
                            }

                            run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, placeholder, width, height);
                        }
                    }

                    // 如果有标题且不是封面图片，添加标题域
                    if (titleText != null && !titleText.isEmpty() && !isCoverImage) {
                        // 使用insertNewParagraph在指定位置插入，避免在文档末尾创建
                        XWPFParagraph titleParagraph = document.insertNewParagraph(paragraph.getCTP().newCursor());

                        // 在现有段落中创建图片标题域
                        WordFieldUtils.createFigureCaptionInParagraph(titleParagraph, titleText, chapterNumber, null);
                    }

                    return;

                } catch (Exception e) {
                    log.error("替换图片失败，占位符: {}, 错误: {}", placeholder, e.getMessage(), e);
                    // 异常时恢复占位符
                    clearParagraph(paragraph);
                    XWPFRun run = paragraph.createRun();
                    run.setText(placeholder);
                    throw e;
                }
            }
        }

        // 替换表格中的图片
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        if (paragraph.getText().contains(placeholder)) {
                            try {
                                // 清除占位符
                                clearParagraph(paragraph);

                                // 设置段落居中对齐
                                paragraph.setAlignment(ParagraphAlignment.CENTER);

                                if (imageUrl != null) {
                                    try (InputStream imageStream = minioClient.getObject(
                                            GetObjectArgs.builder()
                                                    .bucket(minioConfig.getBucketName())
                                                    .object(imageUrl.substring(0, 2) + "/" + imageUrl)
                                                    .build()
                                    )) {
                                        XWPFRun run = paragraph.createRun();
                                        // 表格中的图片使用较小尺寸
                                        run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, placeholder,
                                                Units.toEMU(200), Units.toEMU(150));
                                    }
                                }

                                // 如果有标题，在图片之后添加标题域
                                if (titleText != null && !titleText.isEmpty()) {
                                    XWPFParagraph titleParagraph = cell.addParagraph();

                                    // 在现有段落中创建图片标题域
                                    WordFieldUtils.createFigureCaptionInParagraph(titleParagraph, titleText, chapterNumber, null);
                                }

                                return;

                            } catch (Exception e) {
                                log.error("替换表格中图片失败，占位符: {}, 错误: {}", placeholder, e.getMessage(), e);
                                // 异常时恢复占位符
                                clearParagraph(paragraph);
                                XWPFRun run = paragraph.createRun();
                                run.setText(placeholder);
                            }
                        }
                    }
                }
            }
        }

        log.warn("未找到占位符在文档中: {}", placeholder);
    }

    /**
     * 替换文档中的图片（旧版本，保留兼容性）
     */
    private void replaceImageInDocument(XWPFDocument document, String placeholder, String imageFileName,
                                        String imageTitle, boolean isCoverImage) throws Exception {
        String imageUrl = null;
        if (imageFileName != null) {
            imageUrl = imageFileName.substring(imageFileName.lastIndexOf('/') + 1);
        }

        // 替换段落中的图片
        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getText().contains(placeholder)) {
                try {
                    // 清除占位符段落的内容，但保留段落本身
                    clearParagraph(paragraph);

                    // 设置段落居中对齐
                    paragraph.setAlignment(ParagraphAlignment.CENTER);

                    // 封面图片特殊处理
                    if (isCoverImage) {
                        // 封面图片设置特殊属性
                        paragraph.setSpacingBefore(0);
                        paragraph.setSpacingAfter(0);
                        paragraph.setSpacingBetween(1.0);
                    }

                    if (imageUrl != null) {
                        try (InputStream imageStream = minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(minioConfig.getBucketName())
                                        .object(imageUrl.substring(0, 2) + "/" + imageUrl)
                                        .build()
                        )) {
                            XWPFRun run = paragraph.createRun();

                            // 根据是否为封面图片设置不同尺寸
                            int width, height;
                            if (isCoverImage) {
                                // 封面图片使用更大尺寸，并设置为内联图片
                                width = Units.toEMU(400);
                                height = Units.toEMU(300);
                            } else {
                                width = Units.toEMU(400);
                                height = Units.toEMU(300);
                            }

                            run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, placeholder, width, height);
                        }
                    }

                    // 如果有标题且不是封面图片，添加标题段落
                    if (imageTitle != null && !imageTitle.isEmpty() && !isCoverImage) {
                        // 使用insertNewParagraph在指定位置插入，避免在文档末尾创建
                        XWPFParagraph titleParagraph = document.insertNewParagraph(paragraph.getCTP().newCursor());
                        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
                        titleParagraph.setStyle("12");
                        titleParagraph.setSpacingBefore(100);
                        titleParagraph.setSpacingAfter(200);
                        XWPFRun titleRun = titleParagraph.createRun();
                        titleRun.setText(imageTitle);
                    }

                    return;

                } catch (Exception e) {
                    log.error("替换图片失败，占位符: {}, 错误: {}", placeholder, e.getMessage(), e);
                    // 异常时恢复占位符
                    clearParagraph(paragraph);
                    XWPFRun run = paragraph.createRun();
                    run.setText(placeholder);
                    throw e;
                }
            }
        }

        // 替换表格中的图片
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        if (paragraph.getText().contains(placeholder)) {
                            try {
                                // 清除占位符
                                clearParagraph(paragraph);

                                // 设置段落居中对齐
                                paragraph.setAlignment(ParagraphAlignment.CENTER);

                                if (imageUrl != null) {
                                    try (InputStream imageStream = minioClient.getObject(
                                            GetObjectArgs.builder()
                                                    .bucket(minioConfig.getBucketName())
                                                    .object(imageUrl.substring(0, 2) + "/" + imageUrl)
                                                    .build()
                                    )) {
                                        XWPFRun run = paragraph.createRun();
                                        // 表格中的图片使用较小尺寸
                                        run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, placeholder,
                                                Units.toEMU(200), Units.toEMU(150));
                                    }
                                }

                                // 如果有标题，在图片之后添加标题段落
                                if (imageTitle != null && !imageTitle.isEmpty()) {
                                    XWPFParagraph titleParagraph = cell.addParagraph();
                                    titleParagraph.setAlignment(ParagraphAlignment.CENTER);
                                    titleParagraph.setStyle("12");
                                    titleParagraph.setSpacingBefore(100);
                                    titleParagraph.setSpacingAfter(200);
                                    XWPFRun titleRun = titleParagraph.createRun();
                                    titleRun.setText(imageTitle);
                                }

                                return;

                            } catch (Exception e) {
                                log.error("替换表格中图片失败，占位符: {}, 错误: {}", placeholder, e.getMessage(), e);
                                // 异常时恢复占位符
                                clearParagraph(paragraph);
                                XWPFRun run = paragraph.createRun();
                                run.setText(placeholder);
                            }
                        }
                    }
                }
            }
        }

        log.warn("未找到占位符在文档中: {}", placeholder);
    }

    private void debugAllStyles(XWPFDocument document) {
        XWPFStyles styles = document.getStyles();
        if (styles == null) {
            System.out.println("文档没有样式信息！");
            return;
        }

        System.out.println("=== 调试样式信息 ===");

        // 1. 遍历文档中所有段落，打印段落使用的样式
        System.out.println("=== 段落使用样式 ===");
        for (XWPFParagraph para : document.getParagraphs()) {
            String styleId = para.getStyle();
            if (styleId != null) {
                XWPFStyle style = styles.getStyle(styleId);
                String styleName = style != null ? style.getName() : "未知";
                System.out.println("段落: " + para.getText() + " | StyleID: " + styleId + " | Name: " + styleName);
            }
        }

        // 2. 遍历文档中所有运行，打印运行使用的字符样式
        System.out.println("=== 运行使用字符样式 ===");
        for (XWPFParagraph para : document.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                String styleId = run.getStyle();
                if (styleId != null) {
                    XWPFStyle style = styles.getStyle(styleId);
                    String styleName = style != null ? style.getName() : "未知";
                    System.out.println("运行文本: " + run.text() + " | StyleID: " + styleId + " | Name: " + styleName);
                }
            }
        }

        // 3. 测试常见题注样式
        System.out.println("=== 常见题注样式 ===");
        String[] commonCaptionStyles = {"Caption", "题注", "图题", "FigureCaption"};
        for (String testStyle : commonCaptionStyles) {
            XWPFStyle style = styles.getStyle(testStyle);
            if (style != null) {
                System.out.println("找到样式: " + testStyle + " -> " + style.getName());
            } else {
                System.out.println("未找到样式: " + testStyle);
            }
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
     * 从书签名中提取章节内序号
     *
     * @param bookmarkName 书签名，如"Figure_Chapter3_1"
     * @return 章节内序号
     */
    private int extractSequenceFromBookmark(String bookmarkName) {
        if (bookmarkName.startsWith("Figure_Chapter")) {
            String[] parts = bookmarkName.split("_");
            if (parts.length >= 3) {
                try {
                    int globalSeq = Integer.parseInt(parts[2]);
                    // 简化处理：假设每个节点的图片从1开始编号
                    // 这里可以根据实际需要调整计算逻辑
                    return globalSeq;
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
        }
        return 1;
    }

    /**
     * 为每个子桥生成病害对比表格
     *
     * @param document           Word文档
     * @param subBridges         子桥列表
     * @param building           建筑物信息
     * @param projectId          项目ID
     * @param cursor             插入位置游标
     * @param chapterNum         章节号
     * @param startSubChapterNum 起始子章节号
     * @param tableCounter       表格计数器
     */
    private void generateDiseaseComparisonTables(XWPFDocument document, List<BiObject> subBridges,
                                                 Building building, Long projectId, XmlCursor cursor,
                                                 Integer chapterNum, Integer startSubChapterNum,
                                                 AtomicInteger tableCounter) {
        try {
            int currentSubChapterNum = startSubChapterNum;

            for (BiObject subBridge : subBridges) {
                // 生成病害对比数据
                List<DiseaseComparisonData> comparisonData = diseaseComparisonService.generateComparisonData(subBridge, projectId, building.getId());

                if (!comparisonData.isEmpty()) {
                    // 创建新的游标
                    XmlCursor subCursor = cursor.newCursor();

                    // 创建章节标题段落 - 使用与现有代码一致的方式
                    XWPFParagraph p;
                    if (subCursor != null) {
                        p = document.insertNewParagraph(subCursor);
                        subCursor.toNextToken();
                    } else {
                        p = document.createParagraph();
                    }

                    // 设置标题样式 - 使用第3级标题
                    p.setStyle("3");
                    p.setAlignment(ParagraphAlignment.LEFT);

                    // 设置缩进 - 根据需要选择一种方式
                    CTPPr ppr = p.getCTP().getPPr();
                    if (ppr == null) ppr = p.getCTP().addNewPPr();
                    CTInd ind = ppr.isSetInd() ? ppr.getInd() : ppr.addNewInd();
                    ind.setFirstLine(BigInteger.valueOf(0));
                    ind.setLeft(BigInteger.valueOf(0));

                    // 创建标题运行
                    XWPFRun run = p.createRun();
                    run.setText("与上一次检查病害变化情况分析");
                    run.setBold(false);
                    run.setFontFamily("黑体");

                    // 生成病害对比表格
                    String tableBookmark = DiseaseComparisonTableUtils.createDiseaseComparisonTable(
                            document,
                            comparisonData,
                            subCursor,
                            tableCounter,
                            chapterNum,
                            currentSubChapterNum,
                            subBridge.getName()
                    );

                    currentSubChapterNum++;
                }
            }
        } catch (Exception e) {
            log.error("生成病害对比表格时发生错误", e);
        }
    }

    /**
     * 处理第七章病害表格
     *
     * @param document Word文档
     * @param key      占位符key
     * @param value    JSON数据
     * @param building 建筑物信息
     * @param project  项目信息
     */
    private void handleChapter7DiseaseTable(XWPFDocument document, String key, String value, Building building, Project project, BiObject biObject) {
        try {
            log.info("开始处理第七章病害表格, key: {}, value: {}", key, value);

            // 解析JSON数据
            List<ComponentDiseaseType> combinations = parseChapter7Json(value);
            if (combinations.isEmpty()) {
                log.warn("第七章JSON数据为空或解析失败: {}", value);
                replaceText(document, key, "无数据");
                return;
            }

            if (key.contains("${chapter-7-1-focusOnDiseases}")) {
                // 重点关注病害 - 生成表格
                List<Map<String, Object>> tableData = generateChapter7TableData(combinations, building, project, biObject);
                if (tableData.isEmpty()) {
                    log.warn("第七章表格数据为空");
                    replaceText(document, key, "无数据");
                    return;
                }

                String tableTitle = "重点关注病害汇总表";
                insertChapter7Table(document, key, tableTitle, tableData);
                log.info("第七章重点关注病害表格处理完成, 生成{}行数据", tableData.size());
            } else if (key.contains("${chapter-7-2-analysisOfTheCausesOfMajorDiseases}")){
                // 主要病害成因分析 - 按结构分类分别处理
                generateAndInsertChapter7AnalysisContent(combinations, building, project, biObject, document);
                log.info("第七章病害成因分析处理完成");
            }

        } catch (Exception e) {
            log.error("处理第七章病害表格失败: key={}, value={}", key, value, e);
            throw e;
        }
    }

    /**
     * 解析第七章JSON数据
     *
     * @param jsonValue JSON字符串
     * @return 构件病害类型组合列表
     */
    private List<ComponentDiseaseType> parseChapter7Json(String jsonValue) {
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
     * 生成第七章表格数据
     *
     * @param combinations 构件病害类型组合
     * @param building     建筑物信息
     * @param project      项目信息
     * @return 表格数据
     */
    private List<Map<String, Object>> generateChapter7TableData(List<ComponentDiseaseType> combinations,
                                                                Building building, Project project, BiObject biObject) {
        List<Map<String, Object>> tableData = new ArrayList<>();

        try {
            // 提取所有构件ID
            List<Long> componentIds = combinations.stream()
                    .map(ComponentDiseaseType::getComponentId)
                    .distinct()
                    .collect(Collectors.toList());

            // 批量查询病害数据
            List<Disease> allDiseases = diseaseMapper.selectDiseaseComponentData(
                    componentIds, building.getId(), project.getYear());

            // 按构件ID+病害类型ID分组
            Map<String, List<Disease>> groupedDiseases = allDiseases.stream()
                    .collect(Collectors.groupingBy(disease ->
                            disease.getBiObjectId() + "_" + disease.getDiseaseTypeId()));

            // 批量查询构件信息
            List<BiObject> components = biObjectMapper.selectBiObjectsByIds(new ArrayList<>(componentIds));
            Map<Long, BiObject> componentMap = components.stream()
                    .collect(Collectors.toMap(BiObject::getId, component -> component));

            // 生成表格数据
            int index = 1;
            for (ComponentDiseaseType combination : combinations) {
                String key = combination.getComponentId() + "_" + combination.getDiseaseTypeId();
                List<Disease> diseaseList = groupedDiseases.get(key);

                if (diseaseList != null && !diseaseList.isEmpty()) {
                    Disease firstDisease = diseaseList.get(0);
                    BiObject component = componentMap.get(combination.getComponentId());

                    if (component != null && firstDisease.getDiseaseType() != null) {
                        Map<String, Object> rowData = new HashMap<>();
                        rowData.put("序号", index++);
                        rowData.put("桥梁名称", biObject.getName());
                        rowData.put("缺损位置", component.getName());
                        rowData.put("缺损类型", firstDisease.getDiseaseType().getName());
                        rowData.put("病害描述", generateDiseaseDescription(diseaseList));

                        tableData.add(rowData);
                    }
                }
            }

            log.info("生成第七章表格数据成功，共{}行", tableData.size());
        } catch (Exception e) {
            log.error("生成第七章表格数据失败", e);
        }

        return tableData;
    }

    /**
     * 生成病害描述
     *
     * @param diseases 病害列表
     * @return 病害描述
     */
    private String generateDiseaseDescription(List<Disease> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            return "";
        }

        int count = diseases.size();
        double totalLength = 0;
        double maxWidth = 0;
        double totalArea = 0;

        for (Disease disease : diseases) {
            List<DiseaseDetail> details = disease.getDiseaseDetails();
            if (details != null) {
                for (DiseaseDetail detail : details) {
                    if (detail.getLength1() != null) {
                        totalLength += detail.getLength1().doubleValue();
                    }
                    if (detail.getCrackWidth() != null) {
                        maxWidth = Math.max(maxWidth, detail.getCrackWidth().doubleValue());
                    }
                    if (detail.getAreaLength() != null && detail.getAreaWidth() != null) {
                        totalArea += detail.getAreaLength().doubleValue() * detail.getAreaWidth().doubleValue();
                    }
                    if(detail.getWidthRangeEnd()!=null) {
                        maxWidth = Math.max(maxWidth, detail.getWidthRangeEnd().doubleValue());
                    }
                    if(detail.getLengthRangeStart()!=null && detail.getLengthRangeEnd()!=null) {
                        totalLength += ((detail.getLengthRangeEnd().doubleValue()-detail.getLengthRangeStart().doubleValue())/2) * disease.getQuantity();
                        break;
                    }
                }
            }
        }
        // 生成汇总描述
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("共计%d条 ", count));
        if(totalLength > 0) {
            sb.append(String.format("总长度%.0fcm, ", totalLength * 100));
        }
        if(maxWidth > 0) {
            sb.append(String.format("最大缝宽%.2fmm, ", maxWidth));
        }
        if(totalArea > 0) {
            sb.append(String.format("面积%.4f㎡, ", totalArea));
        }
        return sb.toString();
    }


    /**
     * 插入第七章表格
     *
     * @param document   Word文档
     * @param key        占位符
     * @param tableTitle 表格标题
     * @param tableData  表格数据
     */
    private void insertChapter7Table(XWPFDocument document, String key, String tableTitle, List<Map<String, Object>> tableData) {
        try {
            // 查找占位符位置
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            XWPFParagraph targetParagraph = null;

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
                return;
            }

            // 获取插入位置的游标
            XmlCursor cursor = targetParagraph.getCTP().newCursor();

            // 清空占位符段落的所有Run
            while (targetParagraph.getRuns().size() > 0) {
                targetParagraph.removeRun(0);
            }

            // 创建第七章表格计数器（如果不存在）
            AtomicInteger chapter7TableCounter = new AtomicInteger(1);

            // 使用现有方法创建表格标题
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(
                    document, tableTitle, cursor, 7, chapter7TableCounter);

            // 创建表格
            XWPFTable table = document.insertNewTbl(cursor);

            // 设置表头
            XWPFTableRow headerRow = table.getRow(0);
            String[] headers = {"序号", "桥梁名称", "缺损位置", "缺损类型", "病害描述"};

            // 设置第一个单元格
            XWPFTableCell firstCell = headerRow.getCell(0);
            firstCell.removeParagraph(0);
            XWPFParagraph firstParagraph = firstCell.addParagraph();
            firstParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun firstRun = firstParagraph.createRun();
            firstRun.setText(headers[0]);
            firstRun.setBold(true);
            firstRun.setFontSize(10);

            // 添加其余单元格并设置样式
            for (int i = 1; i < headers.length; i++) {
                XWPFTableCell cell = headerRow.addNewTableCell();
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = paragraph.createRun();
                run.setText(headers[i]);
                run.setBold(true);
                run.setFontSize(10);
            }

            // 填充数据行
            for (Map<String, Object> rowData : tableData) {
                XWPFTableRow dataRow = table.createRow();
                String[] cellTexts = {
                        String.valueOf(rowData.get("序号")),
                        (String) rowData.get("桥梁名称"),
                        (String) rowData.get("缺损位置"),
                        (String) rowData.get("缺损类型"),
                        (String) rowData.get("病害描述")
                };

                // 设置每个单元格的文本和样式
                for (int i = 0; i < cellTexts.length; i++) {
                    XWPFTableCell cell = dataRow.getCell(i);
                    cell.removeParagraph(0);
                    XWPFParagraph paragraph = cell.addParagraph();
                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun run = paragraph.createRun();
                    run.setText(cellTexts[i]);
                    run.setFontSize(10);
                }
            }

            // 设置表格边框和宽度
            table.setWidth("100%");

        } catch (Exception e) {
            log.error("插入第七章表格失败: key={}, title={}", key, tableTitle, e);
            throw e;
        }
    }

    /**
     * 生成并插入第七章病害成因分析内容
     *
     * @param combinations 构件病害类型组合
     * @param building     建筑物信息
     * @param project      项目信息
     * @param biObject     根对象
     * @param document     Word文档
     */
    private void generateAndInsertChapter7AnalysisContent(List<ComponentDiseaseType> combinations, Building building, Project project, BiObject biObject, XWPFDocument document) {
        try {
            // 收集所有构件ID
            Set<Long> componentIds = combinations.stream()
                    .map(ComponentDiseaseType::getComponentId)
                    .collect(Collectors.toSet());

            // 批量查询构件信息
            List<BiObject> components = biObjectMapper.selectBiObjectsByIds(new ArrayList<>(componentIds));
            Map<Long, BiObject> componentMap = components.stream()
                    .collect(Collectors.toMap(BiObject::getId, component -> component));

            // 批量获取结构分类信息
            Map<Long, String> componentStructureTypeMap = batchGetStructureTypes(components);

            // 收集所有病害类型ID并批量查询
            Set<Long> diseaseTypeIds = combinations.stream()
                    .map(ComponentDiseaseType::getDiseaseTypeId)
                    .collect(Collectors.toSet());
            List<DiseaseType> diseaseTypes = diseaseTypeMapper.selectDiseaseTypeListByIds(new ArrayList<>(diseaseTypeIds));
            Map<Long, DiseaseType> diseaseTypeMap = diseaseTypes.stream()
                    .collect(Collectors.toMap(DiseaseType::getId, diseaseType -> diseaseType));

            // 按结构分类分组
            Map<String, List<ComponentDiseaseAnalysis>> structureGroups = new LinkedHashMap<>();

            for (ComponentDiseaseType combination : combinations) {
                BiObject component = componentMap.get(combination.getComponentId());
                if (component == null) continue;

                // 从缓存中获取结构分类
                String structureType = componentStructureTypeMap.get(combination.getComponentId());
                if (structureType == null) {
                    structureType = "other";
                }

                // 从缓存中获取病害类型信息
                DiseaseType diseaseType = diseaseTypeMap.get(combination.getDiseaseTypeId());
                if (diseaseType == null) continue;

                // 创建分析对象
                ComponentDiseaseAnalysis analysis = new ComponentDiseaseAnalysis();
                analysis.setComponentId(combination.getComponentId());
                analysis.setComponentName(component.getName());
                analysis.setBridgeName(biObject.getName());
                analysis.setDiseaseTypeId(combination.getDiseaseTypeId());
                analysis.setDiseaseTypeName(diseaseType.getName());
                analysis.setStructureType(structureType);

                // 添加到对应的结构分组
                structureGroups.computeIfAbsent(structureType, k -> new ArrayList<>()).add(analysis);
            }

            // 为每个结构分类生成内容并替换对应占位符
            generateStructureAnalysisContent(structureGroups.get("superstructure"), document, "${chapter-7-2-1-superstructure}");
            generateStructureAnalysisContent(structureGroups.get("substructure"), document, "${chapter-7-2-2-substructure}");
            generateStructureAnalysisContent(structureGroups.get("deckSystem"), document, "${chapter-7-2-3-deckSystem}");

        } catch (Exception e) {
            log.error("生成第七章成因分析内容失败", e);
        }
    }

    /**
     * 生成单个结构分类的分析内容并替换占位符
     *
     * @param analyses 该结构分类的分析数据
     * @param document Word文档
     * @param placeholder 占位符
     */
    private void generateStructureAnalysisContent(List<ComponentDiseaseAnalysis> analyses, XWPFDocument document, String placeholder) {
        try {
            if (analyses == null || analyses.isEmpty()) {
                replaceText(document, placeholder, "无数据");
                return;
            }

            StringBuilder content = new StringBuilder();

            // 按构件分组，合并同一构件的多个病害类型
            Map<Long, List<ComponentDiseaseAnalysis>> componentGroups = analyses.stream()
                    .collect(Collectors.groupingBy(ComponentDiseaseAnalysis::getComponentId));

            int index = 1;
            for (List<ComponentDiseaseAnalysis> componentAnalyses : componentGroups.values()) {
                ComponentDiseaseAnalysis first = componentAnalyses.get(0);

                // 生成标题：桥梁名 + 构件名 + 病害类型列表
                String diseaseTypes = componentAnalyses.stream()
                        .map(ComponentDiseaseAnalysis::getDiseaseTypeName)
                        .collect(Collectors.joining("、"));

                content.append(String.format("（%d）%s%s%s\n",
                        index++, first.getBridgeName(), first.getComponentName(), diseaseTypes));

                // 生成成因分析
                List<Long> diseaseTypeIds = componentAnalyses.stream()
                        .map(ComponentDiseaseAnalysis::getDiseaseTypeId)
                        .collect(Collectors.toList());

                String causeAnalysis = getCauseAnalysis(first.getComponentId(), diseaseTypeIds);
                content.append("成因分析：").append(causeAnalysis).append("\n\n");
            }

            String finalContent = content.toString().trim();
            insertChapter7AnalysisText(document, placeholder, finalContent);
            log.info("结构分类内容生成完成: {}, 生成{}组数据", placeholder, componentGroups.size());

        } catch (Exception e) {
            log.error("生成结构分类分析内容失败: placeholder={}", placeholder, e);
            replaceText(document, placeholder, "数据处理失败");
        }
    }

    /**
     * 批量获取构件的结构分类
     *
     * @param components 构件列表
     * @return 构件ID到结构分类的映射
     */
    private Map<Long, String> batchGetStructureTypes(List<BiObject> components) {
        Map<Long, String> resultMap = new HashMap<>();

        try {
            // 收集所有需要查询的结构分类节点ID
            Set<Long> structureNodeIds = new HashSet<>();
            Map<Long, Long> componentToStructureNodeMap = new HashMap<>();

            for (BiObject component : components) {
                // 通过ancestors字段获取层级关系
                String ancestors = component.getAncestors();
                if (ancestors == null || ancestors.isEmpty()) {
                    resultMap.put(component.getId(), "other");
                    continue;
                }

                // 解析祖先节点ID列表，例如：0,1,101,1011
                String[] ancestorIds = ancestors.split(",");
                if (ancestorIds.length < 3) {
                    // 至少需要3层：根节点,桥梁节点,结构分类节点
                    resultMap.put(component.getId(), "other");
                    continue;
                }

                // 获取倒数第二个节点ID（结构分类节点）
                String structureNodeIdStr = ancestorIds[ancestorIds.length - 2].trim();
                if (structureNodeIdStr.isEmpty()) {
                    resultMap.put(component.getId(), "other");
                    continue;
                }

                try {
                    Long structureNodeId = Long.parseLong(structureNodeIdStr);
                    structureNodeIds.add(structureNodeId);
                    componentToStructureNodeMap.put(component.getId(), structureNodeId);
                } catch (NumberFormatException e) {
                    log.warn("解析结构分类节点ID失败: {}", structureNodeIdStr);
                    resultMap.put(component.getId(), "other");
                }
            }

            // 批量查询所有结构分类节点
            if (!structureNodeIds.isEmpty()) {
                List<BiObject> structureNodes = biObjectMapper.selectBiObjectsByIds(new ArrayList<>(structureNodeIds));
                Map<Long, BiObject> structureNodeMap = structureNodes.stream()
                        .collect(Collectors.toMap(BiObject::getId, node -> node));

                // 为每个构件确定结构分类
                for (BiObject component : components) {
                    if (resultMap.containsKey(component.getId())) {
                        continue; // 已经处理过的跳过
                    }

                    Long structureNodeId = componentToStructureNodeMap.get(component.getId());
                    if (structureNodeId == null) {
                        resultMap.put(component.getId(), "other");
                        continue;
                    }

                    BiObject structureNode = structureNodeMap.get(structureNodeId);
                    if (structureNode == null) {
                        log.warn("未找到结构分类节点: {}", structureNodeId);
                        resultMap.put(component.getId(), "other");
                        continue;
                    }

                    // 根据结构分类节点名称判断分类
                    String structureType = determineStructureType(structureNode, component);
                    resultMap.put(component.getId(), structureType);

                    log.debug("构件结构分类确定 - 构件: {} (ID: {}), 结构节点: {} (ID: {}), 分类: {}",
                            component.getName(), component.getId(),
                            structureNode.getName(), structureNodeId, structureType);
                }
            }

        } catch (Exception e) {
            log.error("批量获取构件结构分类失败", e);
            // 异常情况下为所有构件设置默认分类
            for (BiObject component : components) {
                if (!resultMap.containsKey(component.getId())) {
                    resultMap.put(component.getId(), "other");
                }
            }
        }

        return resultMap;
    }

    /**
     * 根据结构分类节点和构件信息确定结构分类
     *
     * @param structureNode 结构分类节点
     * @param component 构件对象
     * @return 结构分类
     */
    private String determineStructureType(BiObject structureNode, BiObject component) {
        String structureName = structureNode.getName().toLowerCase();

        // 直接匹配结构分类关键词
        if (structureName.contains("上部结构") || structureName.contains("主体结构") ||
                structureName.contains("上部") || structureName.contains("主体")) {
            return "superstructure";
        } else if (structureName.contains("下部结构") || structureName.contains("基础结构") ||
                structureName.contains("下部") || structureName.contains("基础")) {
            return "substructure";
        } else if (structureName.contains("桥面系") || structureName.contains("桥面结构") ||
                structureName.contains("桥面") || structureName.contains("附属")) {
            return "deckSystem";
        }

        return "other";
    }

    /**
     * 获取成因分析
     *
     * @param componentId    构件ID
     * @param diseaseTypeIds 病害类型ID列表
     * @return 成因分析文本
     */
    private String getCauseAnalysis(Long componentId, List<Long> diseaseTypeIds) {
        // 目前固定返回"测试"，后续可以根据实际需求实现具体的分析逻辑
        return "测试";
    }

    /**
     * 插入第七章成因分析文本
     *
     * @param document 文档对象
     * @param key      占位符
     * @param content  分析内容
     */
    private void insertChapter7AnalysisText(XWPFDocument document, String key, String content) {
        try {
            // 查找占位符位置
            XWPFParagraph targetParagraph = null;
            int targetParagraphIndex = -1;
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph paragraph = paragraphs.get(i);
                String text = paragraph.getText();
                if (text != null && text.contains(key)) {
                    targetParagraph = paragraph;
                    targetParagraphIndex = i;
                    break;
                }
            }

            if (targetParagraph == null) {
                log.warn("未找到占位符: {}", key);
                return;
            }

            // 清空占位符段落的所有Run
            while (targetParagraph.getRuns().size() > 0) {
                targetParagraph.removeRun(0);
            }

            // 按行分割内容并插入
            String[] lines = content.split("\n");
            int currentPosition = targetParagraphIndex; // 跟踪当前插入位置

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                XWPFParagraph paragraph;
                XWPFRun run;

                if (i == 0) {
                    // 第一行使用现有段落
                    paragraph = targetParagraph;
                    run = paragraph.createRun();
                } else {
                    // 后续行在当前位置的下一行插入新段落
                    currentPosition++; // 每次插入后位置递增
                    paragraph = insertParagraphAtPosition(document, currentPosition);
                    run = paragraph.createRun();
                }

                // 添加Tab缩进并设置文本
                run.setText("\t" + line);
                run.setFontSize(12);
                run.setFontFamily("宋体");

                // 判断是否需要加粗（以（数字）开头的行）
                if (line.matches("^（\\d+）.*")) {
                    run.setBold(true);
                }
            }

            log.info("第七章成因分析文本插入成功: {}, 插入{}行内容", key, lines.length);

        } catch (Exception e) {
            log.error("插入第七章成因分析文本失败: key={}", key, e);
        }
    }

    /**
     * 在指定位置插入新段落
     *
     * @param document 文档对象
     * @param position 插入位置索引
     * @return 新创建的段落
     */
    private XWPFParagraph insertParagraphAtPosition(XWPFDocument document, int position) {
        try {
            // 获取文档的body
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body = document.getDocument().getBody();

            // 获取当前段落数组
            java.util.List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP> pArray = body.getPList();

            // 检查位置是否有效
            if (position >= 0 && position <= pArray.size()) {
                // 在指定位置插入新段落的XML对象
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP newCTP = body.insertNewP(position);

                // 创建XWPFParagraph包装器，不需要手动添加到列表
                // POI会自动维护内部列表的一致性
                XWPFParagraph newParagraph = new XWPFParagraph(newCTP, document);

                return newParagraph;
            } else {
                // 位置无效，在末尾创建
                return document.createParagraph();
            }

        } catch (Exception e) {
            log.error("在位置{}插入段落失败", position, e);
            // 如果插入失败，返回在文档末尾创建的段落
            return document.createParagraph();
        }
    }

    /**
     * 处理第八章评定结果
     *
     * @param document Word文档
     * @param key      占位符
     * @param building 建筑物信息
     * @param taskId   任务id
     */
    private void handleChapter8EvaluationResults(XWPFDocument document, String key, Building building, Long taskId) {
        try {
            log.info("开始处理第八章评定结果, key: {}", key);

            // 查询评定结果
            BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(taskId);
            if (evaluation == null) {
                log.warn("未找到任务的评定结果: taskId={}", taskId);
                replaceText(document, key, "未找到评定结果");
                return;
            }

            // 获取桥梁名称
            String bridgeName = building != null && building.getName() != null ? building.getName() : "桥梁";

            // 生成第八章内容
            String chapter8Content = generateChapter8Content(evaluation, bridgeName);

            // 插入四句话到文档中
            XWPFParagraph chapter8Paragraph = insertChapter8Content(document, key, chapter8Content);

            // 调用专门的服务在四句话后生成表格（包含分页符、横向设置和表格）
            evaluationTableService.generateEvaluationTableAfterParagraph(document, chapter8Paragraph, building, evaluation, bridgeName);

            log.info("第八章评定结果和表格处理完成");

        } catch (Exception e) {
            log.error("处理第八章评定结果失败: key={}, error={}", key, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 生成第八章内容
     *
     * @param evaluation 评定结果
     * @param bridgeName 桥梁名称
     * @return 第八章内容
     */
    private String generateChapter8Content(BiEvaluation evaluation, String bridgeName) {
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
     * 插入第八章内容到文档中
     *
     * @param document 文档对象
     * @param key      占位符
     * @param content  第八章内容
     */
    private XWPFParagraph insertChapter8Content(XWPFDocument document, String key, String content) {
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

            log.info("第八章内容插入成功: {}, 插入{}行内容", key, lines.length);

            return targetParagraph;

        } catch (Exception e) {
            log.error("插入第八章内容失败: key={}", key, e);
            throw e;
        }
    }
}