package edu.whut.cs.bi.biz.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import edu.whut.cs.bi.biz.service.impl.ReportServiceImpl;
import io.minio.*;
import org.apache.commons.io.IOUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;


/**
 * 检测报告Controller
 *
 * @author wanzheng
 */
@Controller
@RequestMapping("/biz/report")
public class ReportController extends BaseController {
    private String prefix = "biz/report";


    private int tableCounter = 1; // 添加表格计数器
    // 新增一个Map存储病害与图片序号的映射关系
    Map<Long, List<String>> diseaseImageRefs = new HashMap<>();  // key: 病害ID, value: 图片序号列表

    @Autowired
    private IReportService reportService;

    @Autowired
    private IReportTemplateService reportTemplateService;

    @Autowired
    private IReportDataService reportDataService;

    @Autowired
    private IPropertyService propertyService;

    @Autowired
    IBuildingService buildingService;

    @Autowired
    IFileMapService iFileMapService;

    @Autowired
    DiseaseController diseaseController;

    @Autowired
    AttachmentService attachmentService;

    @Autowired
    ITaskService taskService;

    @Autowired
    IDiseaseService diseaseService;

    @Autowired
    BuildingMapper buildingMapper;

    @Autowired
    BiObjectMapper biObjectMapper;

    @Autowired
    private ReportServiceImpl reportServiceImpl;

    @Autowired
    private MinioConfig minioConfig;
    @Autowired
    private FileMapServiceImpl fileMapServiceImpl;
    @Autowired
    private MinioClient minioClient;

    @RequiresPermissions("biz:report:view")
    @GetMapping()
    public String report() {
        return prefix + "/report";
    }

    @GetMapping("/export/{id}")
    public void exportTaskReport(@PathVariable("id") Long taskId, HttpServletResponse response) {
        try {
            // 1. 查询数据
            Task task = taskService.selectTaskById(taskId);
            Building building = buildingService.selectBuildingById(task.getBuildingId());
            Disease disease = new Disease();
            disease.setBuildingId(task.getBuildingId());
            disease.setProjectId(task.getProjectId());
            List<Disease> properties = diseaseService.selectDiseaseListForApi(disease);
            List<BiObject> biObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());
            // System.out.println(building.getRootObjectId());
            // biObjects.forEach(e -> {
            // System.out.println(e.getId() + " " + e.getName());
            // });
            // 2. 找到树的根节点
            BiObject root = biObjectMapper.selectBiObjectById(building.getRootObjectId());

            // 3. 创建Word文档
            XWPFDocument doc = new XWPFDocument();

            Map<Long, List<Disease>> level3DiseaseMap = new LinkedHashMap<>();
            collectDiseases(root, biObjects, properties, 1, level3DiseaseMap);

            // 4. 递归写入树结构和病害信息
            writeBiObjectTreeToWord(doc, root, biObjects, level3DiseaseMap, "4.1", 1);
            // 5. 导出Word
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            // 后端修改：对文件名进行URL编码
            String fileName = URLEncoder.encode(building.getName() + "病害报告.docx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            doc.write(response.getOutputStream());
            doc.close();
        } catch (Exception e) {
            logger.error("导出Word文档失败：", e);
            throw new RuntimeException("导出Word文档失败：" + e.getMessage());
        }
    }

    private void collectDiseases(BiObject node,
                                 List<BiObject> allNodes,
                                 List<Disease> properties,
                                 int level,
                                 Map<Long, List<Disease>> map) {

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

    private void writeBiObjectTreeToWord(XWPFDocument doc, BiObject node, List<BiObject> allNodes,
                                         Map<Long, List<Disease>> properties, String prefix, int level) throws JsonProcessingException {
        if (level > 3) {
            return; // 不再写标题，也不再递归写标题
        }
        // 写目录标题
        XWPFParagraph p = doc.createParagraph();
        // 设置为标题样式
        if (level == 1) {
            p.setStyle("Heading1");
        } else if (level == 2) {
            p.setStyle("Heading2");
        } else if (level == 3) {
            p.setStyle("Heading3");
        } else {
            p.setStyle("Heading4");
        }

        // 设置大纲级别（模仿示例代码）
        CTPPr ppr = p.getCTP().getPPr();
        if (ppr == null)
            ppr = p.getCTP().addNewPPr();
        // Word 大纲级别从 0 开始，对应 Heading 1
        if (level <= 9) { // Word 支持的大纲级别通常到 9
            ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
        } else { // 超过 9 级，设置为正文级别
            ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(9));
        }

        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setColor("000000"); // 黑色
        run.setFontSize(12 + Math.max(0, 4 - level)); // 层级越深字号越小
        run.setText(prefix + " " + node.getName());

        // 写病害信息
        List<Disease> nodeDiseases = properties.getOrDefault(node.getId(), List.of());
        String tableNumber = "4." + tableCounter;
        // 提前收集所有病害的图片序号
        diseaseImageRefs.clear();  // 清空上一节点的数据
        int imageSeq = 1;
        for (Disease d : nodeDiseases) {
            List<String> imageNumbers = new ArrayList<>();
            List<Map<String, Object>> images = diseaseController.getDiseaseImage(d.getId());
            if (images != null) {
                for (Map<String, Object> img : images) {
                    if (Boolean.TRUE.equals(img.get("isImage"))) {
                        // 生成图片序号 "图4.1-1" 格式
                        imageNumbers.add("图" + tableNumber + "-" + imageSeq);
                        imageSeq++;
                    }
                }
            }
            diseaseImageRefs.put(d.getId(), imageNumbers);
        }


        // 如果存在病害信息，则生成介绍段落和表格
        if (!nodeDiseases.isEmpty()) {
            // 创建介绍段落
            XWPFParagraph introPara = doc.createParagraph();

            // Part 1: 加粗的开头部分
            XWPFRun runBold = introPara.createRun();
            runBold.setText("经检查，" + node.getName() + " 主要病害为:");
            runBold.setBold(true);
            runBold.setFontSize(12); // 设置字号与后面一致

            // Part 2: 生成病害小结
            String diseaseString = reportService.getDiseaseSummary(nodeDiseases);
            // 按行分割字符串并创建多个段落
            String[] lines = diseaseString.split("\\r?\\n"); // 支持Windows(\r\n)和Unix(\n)换行符

            for (String line : lines) {
                // 创建新的段落并设置首行缩进
                XWPFParagraph diseasePara = doc.createParagraph();
                CTPPr diseasePpr = diseasePara.getCTP().getPPr();
                if (diseasePpr == null) {
                    diseasePpr = diseasePara.getCTP().addNewPPr();
                }
                CTInd ind = diseasePpr.isSetInd() ? diseasePpr.getInd() : diseasePpr.addNewInd();
                ind.setFirstLine(BigInteger.valueOf(480)); // 设置首行缩进为480（约2个字符）

                XWPFRun runItem = diseasePara.createRun();
                runItem.setText(line); // 写入单行内容
                runItem.setFontSize(12); // 设置字号
            }
//      int summarySeqNum = 1;
//      for (Disease d : nodeDiseases) {
//        // 创建新的段落并设置首行缩进
//        XWPFParagraph diseasePara = doc.createParagraph();
//        CTPPr diseasePpr = diseasePara.getCTP().getPPr();
//        if (diseasePpr == null) {
//          diseasePpr = diseasePara.getCTP().addNewPPr();
//        }
//        CTInd ind = diseasePpr.isSetInd() ? diseasePpr.getInd() : diseasePpr.addNewInd();
//        ind.setFirstLine(BigInteger.valueOf(480)); // 设置首行缩进为480（约2个字符）
//
//        XWPFRun runItem = diseasePara.createRun();
//        runItem.setText(summarySeqNum++ + "） "
//            + (d.getComponent() != null ? d.getComponent().getName().split("（")[0] : "/") + " "
//            + (d.getType() != null ? d.getType() : "/") + " "
//            + (d.getQuantity() > 0 ? d.getQuantity() : "/") + " 处; "
//            + (d.getDescription() != null ? d.getDescription() : "/") + "; ");
//        runItem.setFontSize(12); // 设置字号
//      }

            // Part 3: 表格引用部分
            XWPFParagraph tableRefPara = doc.createParagraph();

            // 设置1.5倍行距
            CTPPr ppr1 = tableRefPara.getCTP().getPPr();
            if (ppr1 == null) {
                ppr1 = tableRefPara.getCTP().addNewPPr();
            }
            CTSpacing spacing = ppr1.isSetSpacing() ? ppr1.getSpacing() : ppr1.addNewSpacing();
            spacing.setLine(BigInteger.valueOf(360)); // 1.5倍行距（240 * 1.5 = 360）

            tableNumber = "4." + tableCounter++; // 生成表格编号
            XWPFRun runTableRef = tableRefPara.createRun();
            runTableRef.setText("具体检测结果见下表 " + tableNumber + ":");
            runTableRef.setFontSize(12); // 设置字号

            // 添加表格编号
            XWPFParagraph tableNumPara = doc.createParagraph();
            tableNumPara.setAlignment(ParagraphAlignment.CENTER); // 设置居中对齐
            XWPFRun runTableNum = tableNumPara.createRun();
            runTableNum.setText("表 " + tableNumber);
            runTableNum.setFontSize(12);
            runTableNum.setBold(true);

            // --- 原有的表格创建代码开始 --- //
            XWPFTable table = doc.createTable(1, 8); // 1 row (header), 8 columns

            // 设置表格边框
            CTTblPr tblPr = table.getCTTbl().getTblPr();
            if (tblPr == null)
                tblPr = table.getCTTbl().addNewTblPr();
            CTTblBorders borders = tblPr.addNewTblBorders();
            borders.addNewBottom().setVal(STBorder.SINGLE);
            borders.addNewLeft().setVal(STBorder.SINGLE);
            borders.addNewRight().setVal(STBorder.SINGLE);
            borders.addNewTop().setVal(STBorder.SINGLE);
            borders.addNewInsideH().setVal(STBorder.SINGLE);
            borders.addNewInsideV().setVal(STBorder.SINGLE);

            // 设置表格居中对齐
            CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
            jc.setVal(STJcTable.CENTER);

            // 设置表头
            XWPFTableRow headerRow = table.getRow(0);

            // 表头文本数组
            String[] headers = {"序号", "缺损位置", "缺损类型", "数量", "病害描述", "评定类别 (1~5)", "发展趋势", "照片"};

            // 设置表头样式（加粗 + 居中 + 不换行）
            Double[] num = {1.24, 2.75, 2.28, 1.29, 4.43, 2.0, 2.0, 1.51};

            CTTblLayoutType tblLayout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
            tblLayout.setType(STTblLayoutType.FIXED);

            // 3. 设置每列
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
                run1.setFontSize(11); // 五号字

                // 设置列宽（关键修正）
                CTTc cttc = cell.getCTTc();
                CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();

                // 确保单元格宽度属性存在
                CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                tcW.setW(BigInteger.valueOf((int) Math.round(num[i] * 567)));
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
                    CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                    tcW.setW(BigInteger.valueOf((int) Math.round(num[i] * 567)));
                    tcW.setType(STTblWidth.DXA);
                    // 设置单元格内容居中
                    XWPFParagraph cellP = cell.getParagraphs().get(0);
                    cellP.setAlignment(ParagraphAlignment.CENTER);

                    // 设置文本内容
                    XWPFRun cellR = cellP.createRun();
                    cellR.setFontSize(11);

                    switch (i) {
                        case 0:
                            cellR.setText(String.valueOf(seqNum++));
                            break;
                        case 1:
                            cellR.setText(d.getComponent() != null ? d.getComponent().getName() : "/");
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
                            cellR.setText(d.getDevelopmentTrend() != null && !d.getDevelopmentTrend().isEmpty() ? d.getDevelopmentTrend() : "/");
                            break;
                        case 7:
                            // 获取该病害对应的所有图片序号
                            List<String> refs = diseaseImageRefs.getOrDefault(d.getId(), new ArrayList<>());
                            // 将序号列表转为字符串，如 "图4.1-1,图4.1-2"
                            cellR.setText(String.join(",", refs));
                            break;
                    }
                }
            }
            // 在表格下方插入病害图片（不用表格，每行两个图片+标题，图片和标题均用段落并排）
            if (!nodeDiseases.isEmpty()) {
                imageSeq = 1;
                List<byte[]> imageDatas = new ArrayList<>();
                List<String> imageTitles = new ArrayList<>();
                for (Disease d : nodeDiseases) {
                    List<Map<String, Object>> images = diseaseController.getDiseaseImage(d.getId());
                    if (images != null) {
                        for (Map<String, Object> img : images) {
                            if (Boolean.TRUE.equals(img.get("isImage"))) {
                                String url = (String) img.get("url");
                                String newName = url.substring(url.lastIndexOf("/") + 1);
                                byte[] imageData = iFileMapService.handleFileDownloadByNewName(newName);
                                if (imageData != null && imageData.length > 0) {
                                    imageDatas.add(imageData);
                                    String componentName = d.getComponent().getName();
                                    String imageDesc = componentName.substring(componentName.lastIndexOf("#") + 1) + d.getType();
                                    imageTitles.add("图" + tableNumber + "-" + imageSeq + "  " + imageDesc);
                                    imageSeq++;
                                }
                            }
                        }
                    }
                }
                int total = imageDatas.size();
                for (int i = 0; i < total; i += 2) {
                    // 一行插入两个图片
                    XWPFParagraph imageRow = doc.createParagraph();
                    imageRow.setAlignment(ParagraphAlignment.CENTER);
                    for (int j = 0; j < 2; j++) {
                        if (i + j < total) {
                            XWPFRun imageRun = imageRow.createRun();
                            try {
                                BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageDatas.get(i + j)));
                                int maxWidth = (int) (6 * 37.8); // 6cm
                                int maxHeight = (int) (5 * 37.8); // 5cm
                                int width = originalImage.getWidth();
                                int height = originalImage.getHeight();
                                double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
                                int newWidth = (int) (width * ratio);
                                int newHeight = (int) (height * ratio);
                                java.awt.Image scaled = originalImage.getScaledInstance(newWidth, newHeight,
                                        java.awt.Image.SCALE_SMOOTH);
                                BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                                scaledImage.getGraphics().drawImage(scaled, 0, 0, null);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(scaledImage, "jpg", baos);
                                byte[] scaledImageData = baos.toByteArray();
                                imageRun.addPicture(
                                        new ByteArrayInputStream(scaledImageData),
                                        XWPFDocument.PICTURE_TYPE_JPEG,
                                        "disease.jpg",
                                        6 * 360000,
                                        5 * 360000);
                                imageRun.addTab(); // 图片之间加tab分隔
                            } catch (Exception ex) {
                                logger.error("插入病害图片失败", ex);
                            }
                        }
                    }
                    // 一行插入两个标题
                    XWPFParagraph titleRow = doc.createParagraph();
                    titleRow.setAlignment(ParagraphAlignment.CENTER);
                    for (int j = 0; j < 2; j++) {
                        if (i + j < total) {
                            XWPFRun titleRun = titleRow.createRun();
                            titleRun.setText(imageTitles.get(i + j));
                            titleRun.setFontSize(10);
                            // 标题间 加 tab
                            titleRun.addTab();
                            titleRun.addTab();
                            titleRun.addTab();
                        }
                    }
                }
            }
        }

        // 递归写子节点
        List<BiObject> children = allNodes.stream()
                .filter(obj -> node.getId().equals(obj.getParentId()))
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());
        int idx = 1;
        for (BiObject child : children) {
            writeBiObjectTreeToWord(doc, child, allNodes, properties, prefix + "." + idx, level + 1);
            idx++;
        }
    }

    /**
     * 查询检测报告列表
     */
    @RequiresPermissions("biz:report:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Report report) {
        startPage();
        List<Report> list = reportService.selectReportList(report);
        return getDataTable(list);
    }

    /**
     * 导出检测报告列表
     */
    @RequiresPermissions("biz:report:export")
    @Log(title = "检测报告", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Report report) {
        List<Report> list = reportService.selectReportList(report);
        ExcelUtil<Report> util = new ExcelUtil<Report>(Report.class);
        return util.exportExcel(list, "检测报告数据");
    }

    /**
     * 新增检测报告
     */
    @GetMapping("/add")
    public String add(ModelMap mmap) {
        // 获取状态正常的报告模板列表
        ReportTemplate reportTemplate = new ReportTemplate();
        reportTemplate.setIsActive(0); // 状态正常
        List<ReportTemplate> templates = reportTemplateService.selectReportTemplateList(reportTemplate);
        mmap.put("templates", templates);

        return prefix + "/add";
    }

    /**
     * 新增保存检测报告
     */
    @RequiresPermissions("biz:report:add")
    @Log(title = "检测报告", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Report report) {
        return toAjax(reportService.insertReport(report));
    }

    /**
     * 修改检测报告
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Report report = reportService.selectReportById(id);
        mmap.put("report", report);

        // 获取状态正常的报告模板列表
        ReportTemplate reportTemplate = new ReportTemplate();
        reportTemplate.setIsActive(0); // 状态正常
        List<ReportTemplate> templates = reportTemplateService.selectReportTemplateList(reportTemplate);
        mmap.put("templates", templates);

        return prefix + "/edit";
    }

    /**
     * 修改保存检测报告
     */
    @RequiresPermissions("biz:report:edit")
    @Log(title = "检测报告", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Report report) {
        return toAjax(reportService.updateReport(report));
    }

    /**
     * 删除检测报告
     */
    @RequiresPermissions("biz:report:remove")
    @Log(title = "检测报告", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(reportService.deleteReportByIds(ids));
    }

    /**
     * 获取报告的模板信息
     */
    @RequiresPermissions("biz:report:view")
    @GetMapping("/getTemplateInfo/{id}")
    @ResponseBody
    public AjaxResult getTemplateInfo(@PathVariable("id") Long id) {
        try {
            Report report = reportService.selectReportById(id);
            if (report == null) {
                return AjaxResult.error("报告不存在");
            }

            if (report.getReportTemplateId() == null) {
                return AjaxResult.error("报告未关联模板");
            }

            ReportTemplate template = reportTemplateService.selectReportTemplateById(report.getReportTemplateId());
            if (template == null) {
                return AjaxResult.error("模板不存在");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("templateName", template.getName());
            data.put("templateId", template.getId());

            return AjaxResult.success("获取模板信息成功", data);
        } catch (Exception e) {
            return AjaxResult.error("获取模板信息失败：" + e.getMessage());
        }
    }

    /**
     * 跳转到报告数据填充页面
     * 跳转到 比较大 的报告模板的 填充页面。（复杂桥）
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/fill/{id}")
    public String fill(@PathVariable("id") Long id, ModelMap mmap) {
        Report report = reportService.selectReportById(id);
        mmap.put("report", report);
        return "biz/report_data/fill";
    }

    /**
     * 生成报告
     */
    @RequiresPermissions("biz:report:edit")
    @Log(title = "检测报告", businessType = BusinessType.UPDATE)
    @PostMapping("/generate/{id}")
    @ResponseBody
    public AjaxResult generate(@PathVariable("id") Long id) {
        try {
            // 获取报告信息
            Report report = reportService.selectReportById(id);
            if (report == null) {
                return AjaxResult.error("报告不存在");
            }

            if (report.getStatus() == 2) {
                return AjaxResult.error("报告正在生成中，请稍后再试");
            }

            if (report.getMinioId() != null) {
                fileMapServiceImpl.deleteFileMapById(report.getMinioId());
            }
            // 获取报告关联的任务ID
            String taskIdsStr = report.getTaskIds();
            if (taskIdsStr == null || taskIdsStr.isEmpty()) {
                return AjaxResult.error("报告未关联任务");
            }

            // 只取第一个任务ID
            Long taskId = Long.parseLong(taskIdsStr.split(",")[0]);
            Task task = taskService.selectTaskById(taskId);
            if (task == null) {
                return AjaxResult.error("任务不存在");
            }

            // 异步生成报告
            reportServiceImpl.generateReportDocumentAsync(report, task);

            return AjaxResult.success("报告生成已开始，请稍后刷新页面查看状态");
        } catch (Exception e) {
            logger.error("生成报告失败", e);
            return AjaxResult.error("生成报告失败：" + e.getMessage());
        }
    }

    /**
     * 下载报告
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/download/{id}")
    @ResponseBody
    public void download(@PathVariable String id, HttpServletResponse response) {
        try {
            // 获取报告信息
            Report report = reportService.selectReportById(Long.valueOf(id));
            if (report == null) {
                return;
            }

            // 检查报告状态
            if (report.getStatus() != 1) {
                return;
            }

            // 检查MinioID
            if (report.getMinioId() == null) {
                return;
            }

            // 从数据库查到文件名和存储路径
            FileMap fileMap = fileMapServiceImpl.selectFileMapById(report.getMinioId());
            String prefix = fileMap.getNewName().substring(0, 2);
            String objectName = prefix + "/" + fileMap.getNewName();

            // 从 MinIO 获取流
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build())) {

                // 设置响应头（指定下载文件名）
                String fileName = URLEncoder.encode(report.getName() + ".docx", "UTF-8").replaceAll("\\+", "%20");
                response.setContentType("application/octet-stream");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

                // 写出到浏览器
                IOUtils.copy(in, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (Exception e) {
            throw new RuntimeException("下载失败", e);
        }
    }

    /**
     * 根据建筑ID导出属性树信息到Word文档
     */
    @GetMapping("/exportPropertyWord/{bid}")
    @Log(title = "导出属性Word", businessType = BusinessType.EXPORT)
    public void exportPropertyWord(@PathVariable("bid") Long bid, HttpServletResponse response) throws IOException {
        reportDataService.exportPropertyWord(bid, response);
    }

    /**
     * 克隆检测报告
     */
    @RequiresPermissions("biz:report:add")
    @Log(title = "检测报告", businessType = BusinessType.INSERT)
    @PostMapping("/clone/{id}")
    @ResponseBody
    public AjaxResult clone(@PathVariable("id") Long id) {
        try {
            return toAjax(reportService.cloneReport(id));
        } catch (Exception e) {
            logger.error("克隆报告失败", e);
            return AjaxResult.error("克隆报告失败：" + e.getMessage());
        }
    }

}