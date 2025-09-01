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
import edu.whut.cs.bi.biz.domain.vo.Disease2ReportSummaryAiVO;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.Convert2VO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
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


    @Value("${springAi_Rag.endpoint}")
    private String SpringAiUrl;
    @Autowired
    private DiseaseMapper diseaseMapper;

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
     * @param reportId   报告ID
     * @param buildingId 建筑物ID
     * @return 生成的文件路径
     */
    @Override
    public String generateReportDocument(Long reportId, Long buildingId,Long projectId) {
        InputStream templateStream = null;
        FileOutputStream out = null;
        XWPFDocument document = null;
        File outputFile = null;

        try {
            // 查询报告信息
            Report report = reportMapper.selectReportById(reportId);
            if (report == null) {
                return null;
            }

            // 查询建筑物信息
            Building building = null;
            if (buildingId != null) {
                building = buildingService.selectBuildingById(buildingId);
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


            // 查询报告数据
            ReportData queryParam = new ReportData();
            queryParam.setReportId(reportId);
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
            if (buildingId != null) {
                insertBridgeImages(document, buildingId);
            }
            log.info("桥梁照片处理结束");
//            debugAllStyles(document);

            // 处理第三章外观检测结果
            if (building != null) {
                BiObject biObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
                List<BiObject> biObjects = new ArrayList<>();
                biObjects.add(biObject);
                processChapter3(document, biObjects, building,projectId);
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
                            handleImagePlaceholder(document, key, value);
                        } catch (Exception e) {
                            log.error("处理图片占位符出错: key={}, value={}, error={}", key, value, e.getMessage());
                        }
                    } else {
                        // 文本类型（默认）
                        replaceText(document, key, value);
                    }
                } catch (Exception e) {
                    log.error("替换占位符出错: key={}, value={}, type={}, error={}", key, value, type, e.getMessage());
                }
            }

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
     * @param reportId   报告ID
     * @param buildingId 建筑物ID
     * @param projectId  项目ID
     */
    @Async("reportTaskExecutor")
    public void generateReportDocumentAsync(Long reportId, Long buildingId, Long projectId) {
        try {
            // 更新报告状态为生成中
            Report updateReport = new Report();
            updateReport.setId(reportId);
            updateReport.setStatus(2); // 生成中
            updateReport.setUpdateBy("system");
            updateReport.setUpdateTime(new Date());
            reportMapper.updateReport(updateReport);

            // 生成报告文档
            log.info("开始生成报告");
            String minioId = generateReportDocument(reportId, buildingId, projectId);
            log.info("生成报告结束");

            // 更新报告状态为已生成并保存MinioID
            updateReport = new Report();
            updateReport.setId(reportId);
            updateReport.setStatus(1); // 已生成
            updateReport.setMinioId(Long.valueOf(minioId));
            updateReport.setUpdateBy(ShiroUtils.getLoginName());
            updateReport.setUpdateTime(new Date());
            reportMapper.updateReport(updateReport);
        } catch (Exception e) {
            log.error("异步生成报告文档失败", e);
            // 更新报告状态为生成失败
            Report updateReport = new Report();
            updateReport.setId(reportId);
            updateReport.setStatus(3); // 生成失败
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
                // 查找包含占位符的run
                for (int i = 0; i < runs.size(); i++) {
                    XWPFRun run = runs.get(i);
                    String text = run.getText(0);
                    if (text != null && text.contains(oldText)) {
                        // 保留原有的字体样式和大小
                        String replacedText = text.replace(oldText, newText);
                        run.setText(replacedText, 0);
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
                            for (int i = 0; i < runs.size(); i++) {
                                XWPFRun run = runs.get(i);
                                String text = run.getText(0);
                                if (text != null && text.contains(oldText)) {
                                    // 保留原有的字体样式和大小
                                    String replacedText = text.replace(oldText, newText);
                                    run.setText(replacedText, 0);
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
     * @param document     Word文档
     * @param placeholder  占位符（包含${}）
     * @param imageFileName 图片文件名
     * @throws Exception   异常
     */
    private void replaceImageInDocument(XWPFDocument document, String placeholder, String imageFileName) throws Exception {
        replaceImageInDocument(document, placeholder, imageFileName, null,false);
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
    private void processChapter3(XWPFDocument document, List<BiObject> subBridges, Building building ,Long projectId) throws Exception {


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

        // 提前收集所有病害的图片序号
        Map<Long, List<String>> diseaseImageRefs = new HashMap<>(); // key: 病害ID, value: 图片序号列表

        for (Disease d : nodeDiseases) {
            List<String> imageNumbers = new ArrayList<>();
            List<Map<String, Object>> images = diseaseController.getDiseaseImage(d.getId());
            if (images != null) {
                for (Map<String, Object> img : images) {
                    if (Boolean.TRUE.equals(img.get("isImage"))) {
                        // 修改图片序号格式为 "图3-1" 格式（移除多余的序号）
                        imageNumbers.add("图3-" + chapterImageCounter.getAndIncrement());
                    }
                }
            }
            diseaseImageRefs.put(d.getId(), imageNumbers);
        }

        // 如果存在病害信息，则生成介绍段落和表格
        if (!nodeDiseases.isEmpty() && level == 3) {
            String tableNumber = "3." + chapter3TableCounter.getAndIncrement();
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

            XWPFRun runTableRef = tableRefPara.createRun();
            runTableRef.setText("具体检测结果见下表 " + tableNumber + ":");

            // 添加表格标题 - 使用标准格式
            addTableCaption(document, node.getName(), tableNumber, cursor);

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

            // 设置表格居中对齐
            CTJc jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
            jc.setVal(STJc.CENTER);

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
                            cellR.setText(d.getType() != null ? d.getType() : "/");
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
                            // 获取该病害对应的所有图片序号
                            List<String> refs = diseaseImageRefs.getOrDefault(d.getId(), new ArrayList<>());
                            cellR.setText(String.join(",", refs));
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
     * 添加表格标题的方法
     *
     * @param cursor 指定插入位置的游标，如果为null则追加到文档末尾
     */
    private void addTableCaption(XWPFDocument document, String nodeName, String tableNumber, XmlCursor cursor) {
        XWPFParagraph tableNumPara;
        if (cursor != null) {
            tableNumPara = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            tableNumPara = document.createParagraph();
        }
        tableNumPara.setAlignment(ParagraphAlignment.CENTER);

        // 尝试使用Word内置的Caption样式
        try {
            tableNumPara.setStyle("12");
        } catch (Exception e) {
            // 如果Caption样式不存在，手动设置格式
            CTPPr ppr = tableNumPara.getCTP().getPPr();
            if (ppr == null) {
                ppr = tableNumPara.getCTP().addNewPPr();
            }

            // 设置段落间距
            CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
            spacing.setAfter(BigInteger.valueOf(120));
            spacing.setBefore(BigInteger.valueOf(120));
            spacing.setLine(BigInteger.valueOf(240));
            spacing.setLineRule(STLineSpacingRule.AUTO);

            // 设置段落居中
            CTJc jc = ppr.isSetJc() ? ppr.getJc() : ppr.addNewJc();
            jc.setVal(STJc.CENTER);
        }

        XWPFRun runTableNum = tableNumPara.createRun();
        runTableNum.setText("表 " + tableNumber + " " + nodeName + "检测结果表");

        // 设置11号字体（表标题标准字号）
        runTableNum.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(22));
        runTableNum.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(22));

        runTableNum.setFontFamily("黑体");
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

            // 获取该病害的图片序号
            List<String> imageRefs = diseaseImageRefs.getOrDefault(d.getId(), new ArrayList<>());
            int refIndex = 0;

            for (Map<String, Object> img : images) {
                if (Boolean.TRUE.equals(img.get("isImage"))) {
                    // 提取图片URL和文件名
                    String url = (String) img.get("url");
                    String newName = url.substring(url.lastIndexOf("/") + 1);

                    // 使用已收集的图片序号
                    if (refIndex >= imageRefs.size()) {
                        continue; // 跳过没有序号的图片
                    }

                    String imageTitle = imageRefs.get(refIndex);
                    refIndex++;

                    // 添加组件名和病害类型
                    String componentName = d.getComponent() != null ? d.getComponent().getName() : "";
                    String imageDesc = componentName + d.getType();
                    imageTitle += " " + imageDesc;

                    // 将图片信息添加到列表
                    allImages.add(Pair.of(newName, imageTitle));
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

        // 设置表格居中对齐
        CTJc jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJc.CENTER);

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
                String title = imageInfo.getRight();

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
                }

                // 在标题单元格中添加标题
                XWPFParagraph titlePara = titleCell.getParagraphs().get(0);
                titlePara.setStyle("12");
                XWPFRun titleRun = titlePara.createRun();
                titleRun.setText(title);
                titleRun.setFontFamily("黑体");

                // 设置标题字体大小为10.5磅
                titleRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21)); // 10.5pt = 21 half-points
                titleRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));

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
    private void handleImagePlaceholder(XWPFDocument document, String key, String value) throws Exception {
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
        if ("${chapter-1-2-bridgeLayoutPlan}".equals(key)) {
            imageTitle = "图 1-1 桥型布置图";
        } else if ("${chapter-1-2-bridgeStandardCrossSection}".equals(key)) {
            imageTitle = "图 1-2 横断面布置图";
        }

        // 判断是否为封面图片，需要特殊处理
        boolean isCoverImage = "${cover-image}".equals(key);

        if (imageIds.length == 1) {
            // 单张图片
            String imageId = imageIds[0];
            FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(imageId));
            if (fileMap != null) {
                // 替换图片，并添加标题
                replaceImageInDocument(document, key, fileMap.getNewName(), imageTitle, isCoverImage);
            } else {
                log.warn("未找到图片文件: imageId={}", imageId);
            }
        } else {
            // 多张图片，需要创建表格来展示
            insertMultipleImagesTable(document, key, imageIds, imageTitle);
        }
    }

    /**
     * 在Word文档中插入多张图片的表格
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
     * 在表格单元格中处理多张图片
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
     * 在普通段落中处理多张图片
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
     * 替换文档中的图片
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

}