package edu.whut.cs.bi.biz.utils;

import cn.hutool.core.util.ObjectUtil;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.constants.ReportConstants;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ReportGenerateTools {

    @Autowired
    private MinioClient minioClientAutoWired;

    @Autowired
    private MinioConfig minioConfigAutoWired;

    @Autowired
    private AttachmentService attachmentServiceAutoWired;

    @Autowired
    private IFileMapService fileMapServiceAutoWired;

    private static MinioClient minioClient;

    private static MinioConfig minioConfig;

    private static AttachmentService attachmentService;

    private static IFileMapService fileMapService;

    @PostConstruct
    public void init() {
        // 静态变量初始化后注入。
        minioClient = minioClientAutoWired;
        minioConfig = minioConfigAutoWired;
        attachmentService = attachmentServiceAutoWired;
        fileMapService = fileMapServiceAutoWired;
    }

    /**
     * 替换Word文档中的文本
     * ! 如果一个 paragarph中有很多个 占位符需要替换 ， 可能会有问题。
     *
     * @param document Word文档
     * @param oldText  要替换的文本
     * @param newText  新文本
     */
    public static void replaceText(XWPFDocument document, String oldText, String newText) {
        if (newText == null) {
            newText = "";
        }

        // 替换正文段落中的文本
        replaceTextInParagraphs(document.getParagraphs(), oldText, newText, "正文段落");

        // 替换正文表格中的文本
        replaceTextInTables(document.getTables(), oldText, newText, "正文表格");

        // 替换页眉中的文本
        replaceTextInHeaders(document, oldText, newText);

        // 替换页脚中的文本
        replaceTextInFooters(document, oldText, newText);
    }

    /**
     * 替换段落列表中的文本
     * ! 如果一个 paragarph中有很多个 占位符需要替换 ， 可能会有问题。
     *
     * @param paragraphs 段落列表
     * @param oldText    要替换的文本
     * @param newText    新文本
     * @param location   位置描述（用于日志）
     */
    public static void replaceTextInParagraphs(List<XWPFParagraph> paragraphs, String oldText, String newText, String location) {
        for (XWPFParagraph paragraph : paragraphs) {
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
                        // 使用支持换行符的方法设置文本
                        setTextWithLineBreaks(run, replacedText);
                        replaced = true;
                        log.debug("在{}中替换占位符: {} -> {}", location, oldText, newText);
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
                        log.info("在{}中发现分散占位符: {} 从run[{}]到run[{}]", location, oldText, startRunIndex, endRunIndex);

                        // 将第一个run设置为替换后的文本（使用支持换行符的方法）
                        setTextWithLineBreaks(runs.get(startRunIndex), newText);

                        // 清空其他包含占位符的run
                        for (int i = startRunIndex + 1; i <= endRunIndex; i++) {
                            runs.get(i).setText("", 0);
                        }
                    }
                }
            }
        }
    }

    /**
     * 替换表格列表中的文本
     *
     * @param tables   表格列表
     * @param oldText  要替换的文本
     * @param newText  新文本
     * @param location 位置描述（用于日志）
     */
    public static void replaceTextInTables(List<XWPFTable> tables, String oldText, String newText, String location) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    // 替换表格单元格中的段落文本
                    replaceTextInParagraphs(cell.getParagraphs(), oldText, newText, location + "单元格");

                    // 递归处理嵌套表格
                    if (!cell.getTables().isEmpty()) {
                        replaceTextInTables(cell.getTables(), oldText, newText, location + "嵌套表格");
                    }
                }
            }
        }
    }


    /**
     * 替换页眉中的文本
     *
     * @param document Word文档
     * @param oldText  要替换的文本
     * @param newText  新文本
     */
    public static void replaceTextInHeaders(XWPFDocument document, String oldText, String newText) {
        try {
            List<XWPFHeader> headers = document.getHeaderList();
            if (headers != null && !headers.isEmpty()) {
                log.debug("开始处理页眉，共{}个", headers.size());

                for (int i = 0; i < headers.size(); i++) {
                    XWPFHeader header = headers.get(i);
                    String location = "页眉" + (i + 1);

                    // 替换页眉段落中的文本
                    replaceTextInParagraphs(header.getParagraphs(), oldText, newText, location);

                    // 替换页眉表格中的文本
                    replaceTextInTables(header.getTables(), oldText, newText, location + "表格");
                }

                log.debug("页眉文本替换完成");
            }
        } catch (Exception e) {
            log.warn("处理页眉时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 替换页脚中的文本
     *
     * @param document Word文档
     * @param oldText  要替换的文本
     * @param newText  新文本
     */
    public static void replaceTextInFooters(XWPFDocument document, String oldText, String newText) {
        try {
            List<XWPFFooter> footers = document.getFooterList();
            if (footers != null && !footers.isEmpty()) {
                log.debug("开始处理页脚，共{}个", footers.size());

                for (int i = 0; i < footers.size(); i++) {
                    XWPFFooter footer = footers.get(i);
                    String location = "页脚" + (i + 1);

                    // 替换页脚段落中的文本
                    replaceTextInParagraphs(footer.getParagraphs(), oldText, newText, location);

                    // 替换页脚表格中的文本
                    replaceTextInTables(footer.getTables(), oldText, newText, location + "表格");
                }

                log.debug("页脚文本替换完成");
            }
        } catch (Exception e) {
            log.warn("处理页脚时发生错误: {}", e.getMessage());
        }
    }


    /**
     * 设置Run的文本，支持处理换行符
     * <p>
     * 如果文本中包含换行符（\n或\r\n），会将文本分行，并在每行之间插入换行符。
     * 这样可以在Word中正确显示用户在前端textarea中输入的多行文本。
     * </p>
     *
     * @param run  XWPFRun对象
     * @param text 要设置的文本（可能包含换行符）
     */
    private static void setTextWithLineBreaks(XWPFRun run, String text) {
        if (text == null || text.isEmpty()) {
            run.setText("", 0);
            return;
        }

        // 分割文本（支持Windows和Unix风格的换行符）
        String[] lines = text.split("\\r?\\n");

        if (lines.length == 1) {
            // 没有换行符，直接设置
            run.setText(text, 0);
        } else {
            // 有换行符，需要分行处理
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    // 第一行使用setText设置到run的位置0
                    run.setText(lines[i], 0);
                } else {
                    // 后续行先添加换行符，再添加文本
                    run.addBreak();
                    run.setText(lines[i]);
                }
            }
        }
    }


    /**
     * 替换文档中的图片（旧版本，保留兼容性）
     */
    public static void replaceImageInDocument(XWPFDocument document, String placeholder, String imageFileName,
                                              String imageTitle, boolean isCoverImage) throws Exception {
        String imageUrl = null;
        if (imageFileName != null) {
            imageUrl = imageFileName.substring(imageFileName.lastIndexOf('/') + 1);
        }

        // 替换段落中的图片
        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            String paragraphText = paragraph.getText();

            // 先检查段落文本中是否包含占位符
            if (paragraphText.contains(placeholder)) {
                log.info("在普通段落中找到占位符: {}, 段落文本: {}", placeholder, paragraphText);

                // 合并段落中的runs，解决Word分割占位符的问题
                mergeParagraphRuns(paragraph, placeholder);
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
//                                width = Units.toEMU(400);
//                                height = Units.toEMU(300);
                                // 11.11 修改 ， 固定大小为 8cm x 6cm
                                width = Units.toEMU(8 * 567);   // 8 cm
                                height = Units.toEMU(6 * 567);  // 6 cm
                            }

                            run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_JPEG, placeholder, width, height);
                        }
                    } else {
                        // 当图片为空时，清除占位符，不显示任何内容
                        // 段落已经被清空，不需要额外操作
                        log.debug("图片为空，已清除占位符: {}", placeholder);
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
                        String cellParagraphText = paragraph.getText();

                        // 先检查单元格段落文本中是否包含占位符
                        if (cellParagraphText.contains(placeholder)) {
                            log.info("在表格单元格中找到占位符: {}, 单元格文本: {}", placeholder, cellParagraphText);

                            // 合并段落中的runs，解决Word分割占位符的问题
                            mergeParagraphRuns(paragraph, placeholder);

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
                                        log.info("成功在表格单元格中插入图片: {}", placeholder);
                                    }
                                } else {
                                    log.info("图片为空，已清除表格中的占位符: {}", placeholder);
                                }

                                // 如果有标题，在图片之后添加标题段落
                                if (imageTitle != null && !imageTitle.isEmpty()) {
                                    XWPFParagraph titleParagraph = cell.addParagraph();
                                    setSingleLineSpacing(titleParagraph);  // 设置单倍行距
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

        log.warn("未找到占位符在文档中: {}, 请检查Word模板中是否存在该占位符", placeholder);
    }

    /**
     * 合并段落中的所有文本运行（Runs），解决Word将占位符分割的问题
     *
     * @param paragraph   段落对象
     * @param placeholder 要查找的占位符
     * @return 如果找到占位符返回true，否则返回false
     */
    private static boolean mergeParagraphRuns(XWPFParagraph paragraph, String placeholder) {
        String fullText = paragraph.getText();
        if (!fullText.contains(placeholder)) {
            return false;
        }

        // 清除所有现有的runs
        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }

        // 创建一个新的run，包含合并后的文本
        XWPFRun newRun = paragraph.createRun();
        newRun.setText(fullText);

        return true;
    }


    /**
     * 设置表格标题行在跨页时重复显示
     * <p>
     * 当表格内容跨越多页时，会在每个新页面的顶部自动重复显示标题行，
     * 这对于长表格非常有用，可以让读者始终看到列标题。
     * </p>
     *
     * @param headerRow 需要设置为重复标题的表格行（通常是第一行）
     */
    public static void setTableHeaderRepeat(XWPFTableRow headerRow) {
        CTTrPr trPr = headerRow.getCtRow().getTrPr();
        if (trPr == null) {
            trPr = headerRow.getCtRow().addNewTrPr();
        }

        // 设置 tblHeader 属性，标记该行为表头
        // 直接添加tblHeader元素即表示启用
        trPr.addNewTblHeader();
    }

    /**
     * 设置段落为单倍行距（用于表格单元格）
     * <p>
     * 单倍行距（240 Twips = 1.0倍）可以使表格内容更紧凑，
     * 相比默认的1.15倍行距，可减少约13%的行高。
     * </p>
     *
     * @param paragraph 需要设置行距的段落
     */
    public static void setSingleLineSpacing(XWPFParagraph paragraph) {
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr == null) {
            ppr = paragraph.getCTP().addNewPPr();
        }

        CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        // 设置单倍行距：240 Twips = 1.0倍
        spacing.setLine(BigInteger.valueOf(240));
        spacing.setLineRule(STLineSpacingRule.AUTO);
    }

    /**
     * 设置Run的中英文字体（英文Times New Roman，中文宋体），并指定字号
     * <p>
     * 英文和数字使用Times New Roman，中文使用宋体。
     * </p>
     *
     * @param run      需要设置字体的Run对象
     * @param fontSize 字号（half-points，例如21表示10.5pt，20表示10pt）
     */
    public static void setMixedFontFamily(XWPFRun run, int fontSize) {
        CTRPr rpr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();

        // 设置字体（直接添加，不检查isSet）
        CTFonts fonts = rpr.addNewRFonts();
        fonts.setAscii("Times New Roman");        // ASCII字符（英文字母、数字、标点）
        fonts.setHAnsi("Times New Roman");        // 高位ANSI字符（英文）
        fonts.setEastAsia("宋体");                 // 东亚字符（中文）

        // 设置字号（直接添加，不检查isSet）
        CTHpsMeasure sz = rpr.addNewSz();
        sz.setVal(BigInteger.valueOf(fontSize));

        CTHpsMeasure szCs = rpr.addNewSzCs();
        szCs.setVal(BigInteger.valueOf(fontSize));
    }

    private static void clearParagraph(XWPFParagraph paragraph) {
        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }
    }

    /**
     * 根据占位符查找段落
     */
    public static XWPFParagraph findParagraphByPlaceholder(XWPFDocument document, String placeholder) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (text != null && text.contains(placeholder)) {
                return paragraph;
            }
        }
        return null;
    }

    @NotNull
    public static List<Map<String, Object>> getDiseaseImage(Long id) {
        List<Attachment> attachments = attachmentService.getAttachmentList(id).stream().filter(e -> e.getName().startsWith("disease")).toList();

        // 转换为需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Attachment attachment : attachments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attachment.getId());
            map.put("fileName", attachment.getName().split("_")[1]);
            FileMap fileMap = fileMapService.selectFileMapById(attachment.getThumbMinioId() == null ? attachment.getMinioId() : attachment.getThumbMinioId());
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
    public static boolean isImageFile(String fileName) {
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

    /**
     * 针对报告的需求， 根据病害裂缝特征 返回 带有特征的病害类型 ， 例如 纵向裂缝。
     */
    public static String reportDiseaseTypeNameIfCrack(Disease disease) {
        if (ObjectUtil.isEmpty(disease.getDiseaseType())) {
            log.error("病害类型对象缺失，数据异常或未封装病害类型");
            return disease.getType();
        }
        // 非裂缝类型
        if (StringUtils.isEmpty(disease.getCrackType()) || !disease.getDiseaseType().getName().contains(ReportConstants.DISEASE_TYPE_NAME_CRACK)) {
            return disease.getDiseaseType().getName();
        }
        return disease.getCrackType() + ReportConstants.DISEASE_TYPE_NAME_CRACK;
    }
}
