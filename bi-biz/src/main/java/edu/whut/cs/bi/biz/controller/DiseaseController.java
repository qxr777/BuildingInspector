package edu.whut.cs.bi.biz.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import edu.whut.cs.bi.biz.domain.enums.AreaIdentifierEnums;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jetbrains.annotations.NotNull;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 病害Controller
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
@Controller
@Slf4j
@RequestMapping("/biz/disease")
public class DiseaseController extends BaseController {
    private String prefix = "biz/disease";

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private ITaskService taskService;

    @Resource
    private IBiObjectService biObjectService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private MinioConfig minioConfig;

    @Resource
    private IBiTemplateObjectService biTemplateObjectService;

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Resource
    private ReadFileService readFileService;
    @Autowired
    private BuildingMapper buildingMapper;

    // 初始化OkHttp客户端（设置超时，避免下载卡死）
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // 连接超时10秒
            .readTimeout(15, TimeUnit.SECONDS)    // 读取超时15秒
            .build();

    @RequiresPermissions("biz:disease:view")
    @GetMapping()
    public String disease() {
        return prefix + "/disease";
    }

    /**
     * 查询病害列表
     */
    @RequiresPermissions("biz:disease:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Disease disease) {
        List<Disease> diseaseList = diseaseService.selectDiseaseList(disease);
        return getDataTable(diseaseList);
    }

    @RequiresPermissions("biz:disease:export")
    @Log(title = "病害", businessType = BusinessType.EXPORT)
    @PostMapping("/exportApparentReport")
    public void exportApparentReport(Disease disease, HttpServletResponse response) throws IOException {
        List<Disease> diseaseList = diseaseService.selectDiseaseListForTask(disease);

        // -------------------------- 步骤1：生成Word文档 --------------------------
        XWPFDocument document = new XWPFDocument();

        // 1.1 设置文档标题
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        String buildingName = "未知建筑";
        Building building = buildingMapper.selectBuildingById(disease.getBuildingId());
        if (building != null && org.apache.shiro.util.StringUtils.hasText(building.getName())) {
            buildingName = building.getName();
        }
        titleRun.setText("构件表观病害检查记录表");
        titleRun.setFontFamily("黑体");
        titleRun.setFontSize(14);
        titleRun.setBold(false);
        titleRun.addBreak(); // 换行

// 1.2 创建表格
        int tableRows = diseaseList.size() + 1; // 数据行 + 表头行
        int tableCols = 8;
        XWPFTable table = document.createTable(tableRows, tableCols);

        CTTblPr tblPr = table.getCTTbl().getTblPr();
        CTTblWidth tblWidth = tblPr.addNewTblW();
        tblWidth.setType(STTblWidth.PCT); // 按百分比设置
        tblWidth.setW(BigInteger.valueOf(5300)); // （5000=100%）

// 1.3 设置表头和列宽
        String[] headers = {"序号", "构件名称", "病害位置", "病害类型", "病害参数", "标度", "病害照片", "发展趋势"};
        XWPFTableRow headerRow = table.getRow(0);

// 列宽分配比例
        int[] columnWidths = {350, 600, 700, 600, 1100, 350, 650, 650};

        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            cell.setText(headers[i]);
            // 设置表头单元格对齐方式
            for (XWPFParagraph p : cell.getParagraphs()) {
                p.setAlignment(ParagraphAlignment.CENTER);
            }

            // 设置列宽
            CTTcPr tcPr = cell.getCTTc().addNewTcPr();
            CTTblWidth colWidth = tcPr.addNewTcW();
            colWidth.setType(STTblWidth.PCT); // 按百分比设置
            colWidth.setW(BigInteger.valueOf(columnWidths[i]));
        }

        // 1.4 填充数据，并收集所有图片信息
        int photoSerialNum = 1; // 全局图片序号，用于命名
        Map<String, List<List<String>>> photoUrlToNameMap = new HashMap<>(); // 存储 模板 构件 名称 对应的 图片名 和 url。
        for (int i = 0; i < diseaseList.size(); i++) {
            Disease item = diseaseList.get(i);
            XWPFTableRow dataRow = table.getRow(i + 1);

            // 序号
            dataRow.getCell(0).setText(i + 1 + "");
            // 构件名称
            dataRow.getCell(1).setText(item.getComponent() != null ? item.getComponent().getName() : "");
            // 病害类型
            dataRow.getCell(3).setText(item.getType().substring(item.getType().lastIndexOf("#") + 1));
            //标度
            dataRow.getCell(5).setText(item.getLevel() + "");
            //发展趋势
            dataRow.getCell(7).setText(item.getDevelopmentTrend());
            //病害参数。
            dataRow.getCell(4).setText(item.getDescription());
            List<DiseaseDetail> detailList = item.getDiseaseDetails();
            if (null != detailList && !detailList.isEmpty()) {
                if (detailList.size() == 1) {
                    // 只有一个 detail ，  要么数量为1 ， 要么 超过阈值。
                    DiseaseDetail detail = detailList.get(0);

                    // 病害位置 ， 需要参考系。 距离参考系的距离是米。

                    String location1 = detail.getReference1Location();
                    String location2 = detail.getReference2Location();
                    BigDecimal distanceOfloc1 = detail.getReference1LocationStart();
                    BigDecimal distanceOfloc2 = detail.getReference2LocationStart();
                    StringBuilder sb = new StringBuilder();
                    if (location1 != null && distanceOfloc1 != null) {
                        sb.append("距").append(location1).append(distanceOfloc1.toPlainString()).append("m");
                    }
                    if (location1 != null && location2 != null && distanceOfloc1 != null && distanceOfloc2 != null) {
                        sb.append(",");
                    }
                    if (location2 != null && distanceOfloc2 != null) {
                        sb.append("距").append(location2).append(distanceOfloc2.toPlainString()).append("m");
                    }
                    dataRow.getCell(2).setText(sb.toString());

//                    //  病害参数。
//                    // 数量。
//                    String qutity = "数量：" + item.getQuantity() + item.getUnits();
//                    // 面积 有无 阈值 统一处理。 后面的参数 有无阈值 分开处理。
//                    boolean hasArea = detail.getAreaLength() != null && detail.getAreaWidth() != null;
//                    String area = detail.getAreaIdentifier() != null && hasArea ? detail.getAreaIdentifier() == 2 ? "面积 S总=" : "面积 S均=" : hasArea ? "面积 S=" : "";
//                    area = hasArea ? area + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString() + "㎡" : "";
//                    // 缝宽
//                    String crackWidth = detail.getCrackWidth() != null ? "缝宽 W=" + detail.getCrackWidth().toPlainString() + "mm" : "";
//                    // 长度
//                    String length = detail.getLength1() != null ? "长度 L=" + detail.getLength1().toPlainString() + "m" : "";
//                    // 角度
//                    String angle = detail.getAngle() != null ? "角度：" + detail.getAngle() + "度" : "";
//                    // 高度 / 深度
//                    String heightOrDepth = detail.getHeightDepth() != null ? "高度/深度：" + detail.getHeightDepth().toPlainString() + "m" : "";
//                    // 变形 位移
//                    String deformation = detail.getDeformation() != null ? "变形/位移：" + detail.getDeformation().toPlainString() + "m" : "";
//
//                    // 超 阈值的处理 。
//                    String lengthRange = detail.getLengthRangeStart() != null && detail.getLengthRangeEnd() != null ? "长度 L=" + detail.getLengthRangeStart().toPlainString() + "-" + detail.getLengthRangeEnd().toPlainString() + "m" : "";
//                    String heightDepthRange = detail.getHeightDepthRangeStart() != null && detail.getHeightDepthRangeEnd() != null ? "高度/深度：" + detail.getHeightDepthRangeStart().toPlainString() + "-" + detail.getHeightDepthRangeEnd().toPlainString() + "m" : "";
//                    String crackWidthRange = detail.getCrackWidthRangeStart() != null && detail.getCrackWidthRangeEnd() != null ? "缝宽 W=" + detail.getCrackWidthRangeStart().toPlainString() + "-" + detail.getCrackWidthRangeEnd().toPlainString() + "mm" : "";
//                    String deformationRange = detail.getDeformationRangeStart() != null && detail.getDeformationRangeEnd() != null ? "位移/变形：" + detail.getDeformationRangeStart().toPlainString() + "-" + detail.getDeformationRangeEnd().toPlainString() + "m" : "";
//                    String angleRange = detail.getAngleRangeStart() != null && detail.getAngleRangeEnd() != null ? "角度：" + detail.getAngleRangeStart().toPlainString() + "-" + detail.getAngleRangeEnd().toPlainString() + "度" : "";
//
//                    sb = new StringBuilder();
//                    // 长度
//                    sb.append(length != "" ? length + "," : length);
//                    sb.append(lengthRange != "" ? lengthRange + "," : lengthRange);
//                    //缝宽
//                    sb.append(crackWidth != "" ? crackWidth + "," : crackWidth);
//                    sb.append(crackWidthRange != "" ? crackWidthRange + "," : crackWidthRange);
//                    //角度
//                    sb.append(angle != "" ? angle + "," : angle);
//                    sb.append(angleRange != "" ? angleRange + "," : angleRange);
//                    //变形位移
//                    sb.append(deformation != "" ? deformation + "," : deformation);
//                    sb.append(deformationRange != "" ? deformationRange + "," : deformationRange);
//                    //高度/深度
//                    sb.append(heightOrDepth != "" ? heightOrDepth + "," : heightOrDepth);
//                    sb.append(heightDepthRange != "" ? heightDepthRange + "," : heightDepthRange);
//                    //面积
//                    sb.append(area != "" ? area + "," : area);
//                    //数量
//                    sb.append(qutity);
                } else {
                    // 病害位置
                    StringBuilder sb_position = new StringBuilder();
//                    // 病害参数
//                    StringBuilder sb_info = new StringBuilder();
                    for (int index = 0; index < detailList.size(); index++) {
                        DiseaseDetail detail = detailList.get(index);
                        String start = "(" + (index + 1) + ")";
                        sb_position.append(start);
//                        sb_info.append(start);
                        String location1 = detail.getReference1Location();
                        String location2 = detail.getReference2Location();
                        BigDecimal distanceOfloc1 = detail.getReference1LocationStart();
                        BigDecimal distanceOfloc2 = detail.getReference2LocationStart();
                        if (location1 != null && distanceOfloc1 != null) {
                            sb_position.append("距").append(location1).append(distanceOfloc1.toPlainString()).append("m");
                        }
                        if (location1 != null && location2 != null && distanceOfloc1 != null && distanceOfloc2 != null) {
                            sb_position.append(",");
                        }
                        if (location2 != null && distanceOfloc2 != null) {
                            sb_position.append("距").append(location2).append(distanceOfloc2.toPlainString()).append("m");
                        }
//
//                        //  病害参数。
//                        // 数量。
//                        String qutity = "数量：" + item.getQuantity() + item.getUnits();
//                        // 面积 有无 阈值 统一处理。 后面的参数 有无阈值 分开处理。
//                        boolean hasArea = detail.getAreaLength() != null && detail.getAreaWidth() != null;
//                        String area = hasArea ? "面积 S=" : "";
//                        area = hasArea ? area + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString() + "㎡" : "";
//                        // 缝宽
//                        String crackWidth = detail.getCrackWidth() != null ? "缝宽 W=" + detail.getCrackWidth().toPlainString() + "mm" : "";
//                        // 长度
//                        String length = detail.getLength1() != null ? "长度 L=" + detail.getLength1().toPlainString() + "m" : "";
//                        // 角度
//                        String angle = detail.getAngle() != null ? "角度：" + detail.getAngle() + "度" : "";
//                        // 高度 / 深度
//                        String heightOrDepth = detail.getHeightDepth() != null ? "高度/深度：" + detail.getHeightDepth().toPlainString() + "m" : "";
//                        // 变形 位移
//                        String deformation = detail.getDeformation() != null ? "变形/位移：" + detail.getDeformation().toPlainString() + "m" : "";
//                        // 长度
//                        sb_info.append(length != "" ? length + "," : length);
//                        //缝宽
//                        sb_info.append(crackWidth != "" ? crackWidth + "," : crackWidth);
//                        //角度
//                        sb_info.append(angle != "" ? angle + "," : angle);
//                        //变形位移
//                        sb_info.append(deformation != "" ? deformation + "," : deformation);
//                        //高度/深度
//                        sb_info.append(heightOrDepth != "" ? heightOrDepth + "," : heightOrDepth);
//                        //面积
//                        sb_info.append(area != "" ? area + "," : area);
//                        //数量
//                        sb_info.append(qutity);
                    }
                    dataRow.getCell(2).setText(sb_position.toString());
//                    dataRow.getCell(4).setText(sb_info.toString());
                }
            }

            // --- 处理图片：只填写图片名称，不插入图片 ---
            List<String> diseaseImages = item.getImages();
            if (diseaseImages != null && !diseaseImages.isEmpty()) {
                StringBuilder photoNamesSb = new StringBuilder();
                for (String imgUrl : diseaseImages) {
                    if (imgUrl == null || imgUrl.trim().isEmpty()) continue;
                    String photoFileName = String.format("图%d", photoSerialNum);
                    List<String> imgNameAndUrl = new ArrayList<>();
                    imgNameAndUrl.add(photoFileName);
                    imgNameAndUrl.add(imgUrl);
                    String parentObjectName = item.getComponent().getParentObjectName();
                    List<List<String>> list = photoUrlToNameMap.get(parentObjectName);
                    if (null == list) {
                        list = new ArrayList<>();
                    }
                    list.add(imgNameAndUrl);
                    photoUrlToNameMap.put(parentObjectName, list);
                    photoSerialNum++;
                    if (photoNamesSb.length() > 0) {
                        photoNamesSb.append(", ");
                    }
                    photoNamesSb.append(photoFileName);
                }
                dataRow.getCell(6).setText(photoNamesSb.toString());
            }
        }
        // -------------------------- 新增：统一设置所有单元格字体 --------------------------
        // 遍历表格所有行
        for (XWPFTableRow row : table.getRows()) {

            // 遍历行中所有单元格
            for (XWPFTableCell cell : row.getTableCells()) {
                // ========== 新增：设置垂直居中 ==========
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                // 遍历单元格内所有段落
                for (XWPFParagraph para : cell.getParagraphs()) {
                    // ========== 新增：设置水平居中 ==========
                    para.setAlignment(ParagraphAlignment.CENTER);
                    // 遍历段落内所有文本运行（Run）
                    for (XWPFRun run : para.getRuns()) {
                        // 设置字体为宋体
                        run.setFontFamily("宋体");
                        // 设置字号为五号（10.5磅）
                        run.setFontSize(10.5);
                    }
                }
            }
        }

        //--------------------- 在表格下方统一插入所有图片 --------------------------
        // 添加一个分页符，将图片与表格分开
        XWPFParagraph pageBreakPara = document.createParagraph();
        pageBreakPara.createRun().addBreak(BreakType.PAGE);

        // 添加图片标题
        XWPFParagraph imageTitlePara = document.createParagraph();
        imageTitlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun imageTitleRun = imageTitlePara.createRun();
        imageTitleRun.setText("病害现场照片");
        imageTitleRun.setFontSize(14);
        imageTitleRun.setFontFamily("黑体");
        imageTitleRun.setBold(false);
        imageTitleRun.addBreak();

        for (Map.Entry<String, List<List<String>>> entry : photoUrlToNameMap.entrySet()) {
            // 按“一行两张”的格式插入图片
            int totalPhotos = entry.getValue().size();
            List<List<String>> allList = entry.getValue();
            List<String> nameList = allList.stream().map(list -> list.get(0)).toList();
            List<String> urlList = allList.stream().map(list -> list.get(1)).toList();
            int photosPerRow = 2;
            int photoIndex = 0;
            // 先插入 一行 价绍文字。
            XWPFParagraph imageDescPara = document.createParagraph();
            XWPFRun imageDescRun = imageDescPara.createRun();
            imageDescRun.setText(entry.getKey() + "典型病害照片如下所示：");
            imageTitleRun.setFontFamily("宋体");
            imageDescRun.setFontSize(12);
            while (photoIndex < totalPhotos) {
                // 1. 先插入一行图片（仅图片，不带标题）
                XWPFParagraph imageRowPara = document.createParagraph();
                imageRowPara.setAlignment(ParagraphAlignment.LEFT); // 图片左对齐，通过tab控制间距。
                // 存储当前行图片的标题，用于后续插入
                List<String> currentRowTitles = new ArrayList<>();
                for (int i = 0; i < photosPerRow; i++) {
                    if (photoIndex >= totalPhotos) {
                        break; // 图片不足时跳出循环
                    }

                    String imgUrl = urlList.get(photoIndex);
                    String photoName = nameList.get(photoIndex);
                    currentRowTitles.add(photoName); // 记录当前图片标题

                    try (InputStream imgIs = downloadImageByUrl(imgUrl)) {
                        if (imgIs != null) {
                            // 添加图片
                            XWPFRun imageRun = imageRowPara.createRun();
                            // 设置图片宽度为170px 4：3
                            imageRun.addPicture(imgIs, Document.PICTURE_TYPE_JPEG, photoName + ".jpg",
                                    Units.toEMU(170), Units.toEMU(128));
                        } else {
                            XWPFRun imageRun = imageRowPara.createRun();
                            imageRun.setText("[图片下载失败]");
                        }
                    } catch (Exception e) {
                        XWPFRun imageRun = imageRowPara.createRun();
                        imageRun.setText("[图片处理异常]");
                    }
                    // 两张图片之间添加制表符分隔（增加间距）
                    if (i != photosPerRow - 1 && (photoIndex + 1) < totalPhotos) {
                        for (int t = 0; t < 3; t++) { // 多个制表符增加间距
                            imageRowPara.createRun().addTab();
                        }
                    }
                    photoIndex++;
                }

                // 2. 图片行之后，插入对应的标题行（与图片位置一一对应）
                XWPFParagraph titleRowPara = document.createParagraph();
                titleRowPara.setAlignment(ParagraphAlignment.LEFT); // 标题与图片对齐
                if (!currentRowTitles.isEmpty()) {
                    for (int t = 0; t < 4; t++) {
                        titleRowPara.createRun().addTab();
                    }
                }
                for (int i = 0; i < currentRowTitles.size(); i++) {
                    XWPFRun imgTitleRun = titleRowPara.createRun();
                    imgTitleRun.setFontFamily("宋体");
                    imgTitleRun.setFontSize(11);
                    imgTitleRun.setText(currentRowTitles.get(i)); // 设置图片标题

                    // 标题之间保持与图片相同的间距
                    if (i != currentRowTitles.size() - 1) {
                        for (int t = 0; t < 10; t++) { // 标题间距需要更大一些（文字占空间小）
                            titleRowPara.createRun().addTab();
                        }
                    }
                }
            }
        }

        // -------------------------- 步骤2：直接输出Word文档 --------------------------
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String date = dateTime.format(formatter);
        String docFileName = URLEncoder.encode(buildingName + "-构件表观病害检查记录表-" + date + ".docx", StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + docFileName + "\"");
        response.setHeader("Cache-Control", "no-store, no-cache");

        // 将Word文档写入响应流
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             InputStream fis = new ByteArrayInputStream(baos.toByteArray())) {
            document.write(baos);
            baos.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } finally {
            document.close();
        }

    }

    /**
     * 导出病害列表
     */
    @RequiresPermissions("biz:disease:export")
    @Log(title = "病害", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(Disease disease, HttpServletResponse response) throws IOException {
        List<Disease> originList = diseaseService.selectDiseaseListForTask(disease);
        Map<String, List<Disease>> mapByObjectName = originList.stream().collect(Collectors.groupingBy(Disease::getBiObjectName));
        List<Disease> list = new ArrayList<>();
        mapByObjectName.values().forEach(list::addAll);
        // -------------------------- 步骤1：生成Excel（关联001/002图片名） --------------------------
        //  创建Excel工作簿
        // 用于记录图片序号（全局连续：001、002、003...）
        int photoSerialNum = 1;
        // 存储“图片序号→图片URL”的映射（后续下载用）
        List<String> allPhotoUrls = new ArrayList<>();
        ByteArrayOutputStream excelBaos = new ByteArrayOutputStream();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("病害数据");

        //  创建表头
        Row headerRow = sheet.createRow(0);
//        String[] headers = {"构件编号", "缺损类型", "构件", "缺损位置", "病害描述", "标度", "长度(m)", "宽度(m)", "缝宽(mm)", "高度/深度(m)", "面积(㎡)", "照片名称", "发展趋势", "备注", "病害数量"};
        String[] headers = {"构件编号", "缺损类型", "构件", "缺损位置", "病害描述", "标度", "长度(m)", "宽度(m)", "缝宽(mm)", "高度/深度(m)", "面积(㎡)", "照片名称", "备注", "病害数量"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // 填充数据
        for (int i = 0; i < list.size(); i++) {
            Disease item = list.get(i);
            Row row = sheet.createRow(i + 1);
            List<DiseaseDetail> detailList = null;
            if (!item.getDiseaseDetails().isEmpty()) {
                detailList = item.getDiseaseDetails();
            }

            // 构件编号
            row.createCell(0).setCellValue(item.getComponent().getCode());
            // 病害类型
            row.createCell(1).setCellValue(item.getType());
            // 构件名称
            String componentName = item.getComponent().getName();
            componentName = componentName.substring(componentName.lastIndexOf("#") + 1);
            row.createCell(2).setCellValue(componentName);
            // 位置
            row.createCell(3).setCellValue(item.getPosition());
            // 病害描述
            row.createCell(4).setCellValue(item.getDescription());
            // 标度
            row.createCell(5).setCellValue(item.getLevel());
//            //发展趋势。
//            row.createCell(12).setCellValue(item.getDevelopmentTrend());
            //备注
            if (item.getRemark() != null) {
                row.createCell(12).setCellValue(item.getRemark());
            }
            // 病害数量
            row.createCell(13).setCellValue(item.getQuantity());
            int detail_length = detailList.size();
            if (detail_length == 1) {
                DiseaseDetail detail = detailList.get(0);
                //长度
                if (detail.getLength1() != null) {
                    row.createCell(6).setCellValue(detail.getLength1().toPlainString());
                } else if (detail.getLengthRangeStart() != null && detail.getLengthRangeEnd() != null) {
//                    row.createCell(6).setCellValue(detail.getLengthRangeStart().toPlainString() + "-" + detail.getLengthRangeEnd().toPlainString());
                    row.createCell(6).setCellValue(detail.getLengthRangeStart().add(detail.getLengthRangeEnd()).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //宽度
                if (detail.getWidth() != null) {
                    row.createCell(7).setCellValue(detail.getWidth().toPlainString());
                } else if (detail.getWidthRangeStart() != null && detail.getWidthRangeEnd() != null) {
//                    row.createCell(7).setCellValue(detail.getWidthRangeStart().toPlainString() + "-" + detail.getWidthRangeEnd().toPlainString());
                    row.createCell(7).setCellValue(detail.getWidthRangeStart().add(detail.getWidthRangeEnd()).divide(BigDecimal.valueOf(2)).toPlainString());

                }
                //缝宽
                if (detail.getCrackWidth() != null) {
                    row.createCell(8).setCellValue(detail.getCrackWidth().toPlainString());
                } else if (detail.getCrackWidthRangeStart() != null && detail.getCrackWidthRangeEnd() != null) {
//                    row.createCell(8).setCellValue(detail.getCrackWidthRangeStart().toPlainString() + "-" + detail.getCrackWidthRangeEnd().toPlainString());
                    row.createCell(8).setCellValue(detail.getCrackWidthRangeStart().add(detail.getCrackWidthRangeEnd()).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //高度深度
                if (detail.getHeightDepth() != null) {
                    row.createCell(9).setCellValue(detail.getHeightDepth().toPlainString());
                } else if (detail.getHeightDepthRangeStart() != null && detail.getHeightDepthRangeEnd() != null) {
//                    row.createCell(9).setCellValue(detail.getHeightDepthRangeStart().toPlainString() + "-" + detail.getHeightDepthRangeEnd().toPlainString());
                    row.createCell(9).setCellValue(detail.getHeightDepthRangeStart().add(detail.getHeightDepthRangeEnd()).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //面积
                // 判断 是否 是 均值。
                if (detail.getAreaWidth() != null && detail.getAreaLength() != null) {
                    if (detail.getAreaIdentifier() != null && detail.getAreaIdentifier().equals(AreaIdentifierEnums.AVERAGE.getCode())) {
//                        row.createCell(10).setCellValue("S均:" + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
                        BigDecimal sTarget = detail.getAreaLength().multiply(detail.getAreaWidth());
                        sTarget = sTarget.multiply(BigDecimal.valueOf(item.getQuantity()));
                        MathContext mc = new MathContext(4, RoundingMode.HALF_UP); // 4位有效数字，四舍五入
                        sTarget = sTarget.round(mc);
                        row.createCell(10).setCellValue(sTarget.toPlainString());
                    } else if (detail.getAreaIdentifier() != null && detail.getAreaIdentifier().equals(AreaIdentifierEnums.COUNT.getCode())) {
//                        row.createCell(10).setCellValue("S总:" + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
                        BigDecimal sTarget = detail.getAreaLength().multiply(detail.getAreaWidth());
                        MathContext mc = new MathContext(4, RoundingMode.HALF_UP); // 4位有效数字，四舍五入
                        sTarget = sTarget.round(mc);
                        row.createCell(10).setCellValue(sTarget.toPlainString());
                    } else {
//                        row.createCell(10).setCellValue("S均:" + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
                        BigDecimal sTarget = detail.getAreaLength().multiply(detail.getAreaWidth());
                        MathContext mc = new MathContext(4, RoundingMode.HALF_UP); // 4位有效数字，四舍五入
                        sTarget = sTarget.round(mc);
                        row.createCell(10).setCellValue(sTarget.toPlainString());
                    }
                }
            } else if (detail_length > 1) {
                DiseaseDetail detail = detailList.get(0);
                //长度
                if (detail.getLength1() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getLength1).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
//                    row.createCell(6).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                    row.createCell(6).setCellValue(min.add(max).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //宽度
                if (detail.getWidth() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getWidth).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
//                    row.createCell(7).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                    row.createCell(7).setCellValue(min.add(max).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //缝宽
                if (detail.getCrackWidth() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getCrackWidth).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
//                    row.createCell(8).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                    row.createCell(8).setCellValue(min.add(max).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //高度深度
                if (detail.getHeightDepth() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getHeightDepth).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
//                    row.createCell(9).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                    row.createCell(9).setCellValue(min.add(max).divide(BigDecimal.valueOf(2)).toPlainString());
                }
                //面积
                if (detail.getAreaWidth() != null && detail.getAreaLength() != null) {
                    BigDecimal count = BigDecimal.valueOf(0L);
                    List<BigDecimal> areaWidthList = detailList.stream().map(DiseaseDetail::getAreaWidth).toList();
                    List<BigDecimal> areaLengthList = detailList.stream().map(DiseaseDetail::getAreaLength).toList();
                    for (int index = 0; index < detail_length; index++) {
                        count.add(areaWidthList.get(index).multiply(areaLengthList.get(index)));
                    }
//                    row.createCell(10).setCellValue("S总:" + count.toPlainString());
                    MathContext mc = new MathContext(4, RoundingMode.HALF_UP);
                    count = count.round(mc);
                    row.createCell(10).setCellValue(count.toPlainString());
                }

            }

            //处理照片名称
            List<String> diseaseImages = item.getImages(); // 获取当前病害的图片URL列表
            if (diseaseImages != null && !diseaseImages.isEmpty()) {
                // 拼接当前病害的所有图片名称（如“001.jpg, 002.jpg”）
                StringBuilder photoNames = new StringBuilder();
                for (String imgUrl : diseaseImages) {
                    // 跳过空URL
                    if (imgUrl == null || imgUrl.trim().isEmpty()) continue;
                    // 生成3位序号文件名（001.jpg、002.jpg...）
                    String photoFileName = String.format("%03d.jpg", photoSerialNum);
                    // 记录URL（后续下载用）
                    allPhotoUrls.add(imgUrl);
                    // 拼接图片名到Excel单元格（多个图片用逗号分隔）
                    if (photoNames.length() > 0) photoNames.append(",");
                    photoNames.append(photoFileName);
                    // 序号自增（下一张图用）
                    photoSerialNum++;
                }
                // 将拼接的图片名写入Excel“照片名称”列（第10列，索引从0开始）
                row.createCell(11).setCellValue(photoNames.toString());
            }
        }

        // 调整列宽
        for (int i = 0; i < headers.length; i++) {
            // 先自动计算宽度
            sheet.autoSizeColumn(i);
            // 额外增加 10 个字符的宽度（避免内容紧贴边框）
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, currentWidth + 10 * 256); // 256是POI中一个字符的基准宽度
        }
        workbook.write(excelBaos); // Excel写入字节流
        // 关闭资源
        workbook.close();


        // -------------------------- 步骤2：构建Zip（含Excel+照片文件夹） --------------------------

        // 设置Zip响应头
        response.setContentType("application/zip");
        String buildingName = "未知建筑";
        Building building = buildingMapper.selectBuildingById(disease.getBuildingId());
        if (building != null && hasText(building.getName())) {
            buildingName = building.getName();
        }
        String zipFileName = URLEncoder.encode(buildingName + "病害数据.zip", StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
        response.setHeader("Cache-Control", "no-store, no-cache");

        // 初始化Zip输出流（直接写入响应，无中间文件）
        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream());
             InputStream excelIs = new ByteArrayInputStream(excelBaos.toByteArray())) {

            // 2.1 向Zip添加Excel文件（根目录）
            ZipEntry excelEntry = new ZipEntry("病害清单.xlsx");
            zipOut.putNextEntry(excelEntry);
            byte[] buffer = new byte[1024 * 8]; // 8KB缓冲区
            int len;
            while ((len = excelIs.read(buffer)) != -1) {
                zipOut.write(buffer, 0, len);
            }
            zipOut.closeEntry(); // 关闭Excel条目


            // 2.2 向Zip添加照片文件夹及图片（001.jpg、002.jpg...）
            // 遍历所有图片URL，下载并写入Zip
            for (int i = 0; i < allPhotoUrls.size(); i++) {
                String imgUrl = allPhotoUrls.get(i);
                // 生成图片文件名（与Excel一致：001.jpg、002.jpg...）
                String photoFileName = String.format("%03d.jpg", i + 1);
                // Zip中路径：病害图片/001.jpg（放入单独文件夹）
                String zipPhotoPath = "病害图片/" + photoFileName;
                ZipEntry photoEntry = new ZipEntry(zipPhotoPath);
                zipOut.putNextEntry(photoEntry);

                // 核心：通过URL下载图片到Zip流
                try (InputStream imgIs = downloadImageByUrl(imgUrl)) {
                    if (imgIs == null) {
                        // 图片下载失败，写入提示文本（避免Zip损坏）
                        String errorMsg = "图片下载失败：" + imgUrl;
                        zipOut.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                        System.err.println(errorMsg);
                        zipOut.closeEntry();
                        continue;
                    }
                    // 将图片流写入Zip
                    while ((len = imgIs.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    // 捕获下载异常，避免整个导出失败
                    String errorMsg = "图片处理异常：" + imgUrl + "，原因：" + e.getMessage();
                    zipOut.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                    System.err.println(errorMsg);
                }
                zipOut.closeEntry(); // 关闭当前图片条目
            }

            zipOut.flush(); // 强制刷新，确保所有数据写入响应
        }

    }

    // 原生逻辑：替代StringUtils.hasText(String str)
    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }


    /**
     * 工具方法：通过URL下载图片，返回图片输入流
     *
     * @param imgUrl 图片URL（如http://xxx.com/photos/123.jpg）
     * @return 图片输入流（null表示下载失败）
     */
    private InputStream downloadImageByUrl(String imgUrl) {
        try {
            // 构建HTTP请求
            Request request = new Request.Builder()
                    .url(imgUrl)
                    .build();
            // 发送请求获取响应
            Response response = okHttpClient.newCall(request).execute();
            // 检查响应是否成功（200状态码）
            if (!response.isSuccessful()) {
                System.err.println("图片URL响应失败：" + imgUrl + "，状态码：" + response.code());
                return null;
            }
            // 返回图片输入流（无需关闭，外层try-with-resources会处理）
            return response.body().byteStream();
        } catch (Exception e) {
            System.err.println("图片下载异常：" + imgUrl + "，原因：" + e.getMessage());
            return null;
        }
    }


    /**
     * 新增病害
     */
    @GetMapping(value = {"/add/{taskId}/{biObjectId}"})
    public String add(@PathVariable("taskId") Long taskId, @PathVariable("biObjectId") Long biObjectId, ModelMap mmap) {
        if (taskId != null) {
            mmap.put("task", taskService.selectTaskById(taskId));
        }
        if (biObjectId != null) {
            mmap.put("biObject", biObjectService.selectBiObjectById(biObjectId));
        }

        return prefix + "/add";
    }

    /**
     * 新增保存病害
     */
    @RequiresPermissions("biz:disease:add")
    @Log(title = "病害", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    @Transactional
    public AjaxResult addSave(@Valid Disease disease, @RequestParam(value = "files", required = false) MultipartFile[] files) {
        disease.setCreateBy(ShiroUtils.getLoginName());
        if (files != null && files.length > 0) {
            disease.setAttachmentCount(files.length);
        }

        diseaseService.insertDisease(disease);
        if (files != null && files.length > 0) {
            diseaseService.handleDiseaseAttachment(files, disease.getId(), 1);
        }

        return toAjax(Math.toIntExact(disease.getId()));
    }


    /**
     * 修改病害
     */
    @RequiresPermissions("biz:disease:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Disease disease = diseaseService.selectDiseaseById(id);

        BiObject biObject = disease.getBiObject();
        mmap.put("biObject", biObject);
        if (biObject.getName().equals("其他")) {
            String customPosition = disease.getPosition();
            mmap.put("customPosition", customPosition);
        }

        String imgNoExp = disease.getImgNoExp();
        if (imgNoExp != null) {
            ObjectMapper mapper = new ObjectMapper();

            try {
                List<String> imgs = mapper.readValue(imgNoExp, new TypeReference<List<String>>() {
                });
                disease.setImgNoExp(imgs.stream().collect(Collectors.joining("、")));
            } catch (JsonProcessingException e) {
                log.error("图片格式有误转化失败");
            }

        }

        // 位置
        mmap.put("disease", disease);

        return prefix + "/edit";
    }

    /**
     * 修改保存病害
     */
    @RequiresPermissions("biz:disease:edit")
    @Log(title = "病害", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(
            @Valid Disease disease,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "existingAttachmentIds", required = false) String existingAttachmentIds,
            @RequestParam(value = "deletedAttachmentIds", required = false) String[] deletedAttachmentIds
    ) {
        disease.setUpdateBy(ShiroUtils.getLoginName());
        List<Attachment> attachment = attachmentService.getAttachmentBySubjectId(disease.getId());
        Set<String> existingIdSet = null;
        if (StrUtil.isNotEmpty(existingAttachmentIds)) {
            existingIdSet = StrUtil.split(existingAttachmentIds, ',')
                    .stream()
                    .map(String::trim)
                    .collect(Collectors.toSet());
        } else {
            existingIdSet = new HashSet<>();
        }

        if (CollUtil.isNotEmpty(attachment)) {
            Set<String> finalExistingIdSet = existingIdSet;
            String attachmentIds = attachment.stream()
                    .filter(e -> e.getName().startsWith("disease"))
                    .map(e -> e.getId().toString())
                    .filter(id -> !finalExistingIdSet.contains(id))
                    .collect(Collectors.joining(","));
            attachmentService.deleteAttachmentByIds(attachmentIds);
        }
        diseaseService.handleDiseaseAttachment(files, disease.getId(), 1);
        if (files != null && files.length > 0) {
            disease.setAttachmentCount(files.length + existingIdSet.size());
        }

        return toAjax(diseaseService.updateDisease(disease));
    }

    /**
     * 删除
     */
    @RequiresPermissions("biz:disease:remove")
    @Log(title = "病害", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(diseaseService.deleteDiseaseByIds(ids));
    }

    /**
     * 修改病害
     */
    @RequiresPermissions("biz:disease:list")
    @GetMapping("/showDiseaseDetail/{id}")
    public String showDiseaseDetail(@PathVariable("id") Long id, ModelMap mmap) {
        Disease disease = diseaseService.selectDiseaseById(id);
        mmap.put("disease", disease);
        mmap.put("biObject", disease.getBiObject());

        return prefix + "/detail";
    }

    /**
     * 构件扣分
     */
    @RequiresPermissions("biz:disease:add")
    @GetMapping("/computeDeductPoints")
    @ResponseBody
    public AjaxResult computeDeductPoints(int maxScale, int scale) {

        if (StringUtils.isNull(maxScale) || StringUtils.isNull(scale)) {
            return AjaxResult.error("参数错误");
        }

        return AjaxResult.success(diseaseService.computeDeductPoints(maxScale, scale));
    }


    @GetMapping("/attachments/{id}")
    @ResponseBody  // 添加此注解以返回JSON数据
    public AjaxResult getAttachments(@PathVariable("id") Long id) {
        try {
            // 获取病害对应的附件列表
            List<Map<String, Object>> result = getDiseaseImage(id);

            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("获取附件列表失败：" + e.getMessage());
        }
    }


    /**
     * 获取与指定病害ID关联的附件信息，特别是以 "disease" 开头的图片类附件。
     * 该接口会返回包含附件ID、文件名、访问URL及是否为图片类型的附件列表，
     * 供前端展示病害相关的图片和其他附件信息。
     *
     * @param id 病害的唯一标识
     *           返回的List立面有个map，map.get(“url”)就是病害图片的url，要看一下map.get(”isImage“)是否为true
     */
    @NotNull
    public List<Map<String, Object>> getDiseaseImage(Long id) {
        List<Attachment> attachments = attachmentService.getAttachmentList(id).stream().filter(e -> e.getName().startsWith("disease")).toList();

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Attachment attachment : attachments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attachment.getId());
            map.put("fileName", attachment.getName().split("_")[1]);
            FileMap fileMap = fileMapService.selectFileMapById(attachment.getMinioId());
            if (fileMap == null) continue;
            String s = fileMap.getNewName();
            map.put("url", minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" + s.substring(0, 2) + "/" + s);
            // 根据文件后缀判断是否为图片
            map.put("isImage", isImageFile(attachment.getName()));
            map.put("type", attachment.getType());
            result.add(map);
        }
        return result;
    }

    // 判断文件是否为图片的辅助方法
    private boolean isImageFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String extension = fileName.toLowerCase();
        return extension.endsWith(".jpg") ||
                extension.endsWith(".jpeg") ||
                extension.endsWith(".png") ||
                extension.endsWith(".gif") ||
                extension.endsWith(".bmp");
    }

    @DeleteMapping("/attachment/delete/{fileId}")
    @ResponseBody  // 添加此注解以返回JSON数据
    @Transactional
    public AjaxResult deleteAttachment(@PathVariable("fileId") Long id) {
        Attachment attachment = attachmentMapper.selectById(id);

        Disease disease = diseaseService.selectDiseaseById(attachment.getSubjectId());
        disease.setAttachmentCount(disease.getAttachmentCount() - 1);
        disease.setUpdateBy(ShiroUtils.getLoginName());
        disease.setUpdateTime(DateUtils.getNowDate());
        diseaseMapper.updateDisease(disease);

        attachmentService.deleteAttachmentById(id);

        return AjaxResult.success("success");
    }

    @PostMapping("/causeAnalysis")
    @ResponseBody
    public AjaxResult getCauseAnalysis(@RequestBody CauseQuery causeQuery) {
        // 获取根对象
        BiObject rootObject = biObjectService.selectBiObjectById(causeQuery.getObjectId());
        String[] split = rootObject.getAncestors().split(",");
        if (split.length > 1) {
            rootObject = biObjectService.selectBiObjectById(Long.parseLong(split[1]));
        }
        if (rootObject != null && rootObject.getTemplateObjectId() != null) {
            // 获取模板对象
            BiTemplateObject templateObject = biTemplateObjectService.selectBiTemplateObjectById(rootObject.getTemplateObjectId());
            if (templateObject != null && templateObject.getName() != null) {
                causeQuery.setTemplate(templateObject.getName());
            }
        }
        return AjaxResult.success(diseaseService.getCauseAnalysis(causeQuery));
    }

    @RequiresPermissions("biz:disease:add")
    @GetMapping("/importCBMS")
    public String importCBMS(@RequestParam("taskId") Long taskId, ModelMap mmap) {
        mmap.put("taskId", taskId);
        return prefix + "/importCBMS";
    }

    @RequiresPermissions("biz:disease:add")
    @PostMapping("/upload/CBMSExcel")
    @ResponseBody
    public AjaxResult uploadCBMSExcel(@RequestParam("file") MultipartFile file, @RequestParam("taskId") Long taskId) {
        readFileService.readCBMSDiseaseExcel(file, taskId);

        return AjaxResult.success("上传成功");
    }

    @RequiresPermissions("biz:disease:add")
    @GetMapping("/importHistory")
    public String importHistory(@RequestParam("taskId") Long taskId, ModelMap mmap) {
        mmap.put("taskId", taskId);
        return prefix + "/importHistory";
    }

    @RequiresPermissions("biz:disease:add")
    @Log(title = "病害", businessType = BusinessType.IMPORT)
    @PostMapping("/upload/diseaseHistoryExcel")
    @ResponseBody
    public AjaxResult diseaseHistoryExcel(@RequestParam("file") MultipartFile file, @RequestParam("taskId") Long taskId) {
        readFileService.readDiseaseExcel(file, taskId);

        return AjaxResult.success("上传成功");
    }

    @RequiresPermissions("biz:disease:add")
    @PostMapping("/upload/diseaseHistoryPhotos")
    @ResponseBody
    public AjaxResult diseaseHistoryPhotos(@RequestParam("photos") List<MultipartFile> photos, @RequestParam("taskId") Long taskId) {

        List<String> unmatchedPhotos = readFileService.uploadPictures(photos, taskId);

        if (!unmatchedPhotos.isEmpty()) {
            return AjaxResult.success(unmatchedPhotos);
        }

        return AjaxResult.success();
    }
}
