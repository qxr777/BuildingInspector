package edu.whut.cs.bi.biz.utils;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Word域工具类，用于生成表格和图片的自动编号域
 *
 * @author wanzheng
 */
public class WordFieldUtils {

    // 全局计数器
    private static AtomicInteger tableSequence = new AtomicInteger(1);
    private static AtomicInteger figureSequence = new AtomicInteger(1);
    private static AtomicInteger bookmarkCounter = new AtomicInteger(1);

    /**
     * 重置计数器（用于新文档）
     */
    public static void resetCounters() {
        tableSequence.set(1);
        figureSequence.set(1);
        bookmarkCounter.set(1);
    }

    /**
     * 创建表格标题域
     * @param document Word文档
     * @param titleText 标题文本（如"检测结果表"）
     * @param cursor 插入位置的游标，如果为null则追加到文档末尾
     * @param chapterNumber 章节号，如果需要按章节编号则提供，否则传null
     * @return 生成的书签名，用于后续引用
     */
    public static String createTableCaption(XWPFDocument document, String titleText, XmlCursor cursor, Integer chapterNumber) {
        XWPFParagraph paragraph;
        if (cursor != null) {
            paragraph = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            paragraph = document.createParagraph();
        }

        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 设置段落样式
        try {
            paragraph.setStyle("12");
        } catch (Exception e) {
            // 如果样式不存在，手动设置格式
            CTPPr ppr = paragraph.getCTP().getPPr();
            if (ppr == null) {
                ppr = paragraph.getCTP().addNewPPr();
            }

            CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
            spacing.setAfter(BigInteger.valueOf(120));
            spacing.setBefore(BigInteger.valueOf(120));
            spacing.setLine(BigInteger.valueOf(240));
            spacing.setLineRule(STLineSpacingRule.AUTO);

            CTJc jc = ppr.isSetJc() ? ppr.getJc() : ppr.addNewJc();
            jc.setVal(STJc.CENTER);
        }

        // 生成唯一的书签名
        String bookmarkName = "Table_" + bookmarkCounter.getAndIncrement();

        // 添加静态前缀"表"字
        XWPFRun prefixRun = paragraph.createRun();
        prefixRun.setText("表");
        prefixRun.setFontFamily("黑体");
        prefixRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(22));
        prefixRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(22));

        // 创建表格序号域（使用平铺多域显示"3.1"格式）
        if (chapterNumber != null) {
            // 按章节编号
            createChapterSequenceField(paragraph, "Table", bookmarkName, chapterNumber);
        } else {
            // 全文档编号
            createSequenceField(paragraph, "Table", bookmarkName);
        }

        // 添加标题文本
        XWPFRun titleRun = paragraph.createRun();
        titleRun.setText(" " + titleText);
        titleRun.setFontFamily("黑体");
        titleRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(22));
        titleRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(22));

        return bookmarkName;
    }

    /**
     * 创建表格标题域（兼容性方法，无章节号）
     * @param document Word文档
     * @param titleText 标题文本（如"检测结果表"）
     * @param cursor 插入位置的游标，如果为null则追加到文档末尾
     * @return 生成的书签名，用于后续引用
     */
    public static String createTableCaption(XWPFDocument document, String titleText, XmlCursor cursor) {
        return createTableCaption(document, titleText, cursor, null);
    }

    /**
     * 创建表格标题，支持传入计数器
     * @param document Word文档
     * @param titleText 标题文本
     * @param cursor 插入位置的游标，如果为null则追加到文档末尾
     * @param chapterNumber 章节号
     * @param tableCounter 表格计数器
     * @return 返回表格的书签名，用于后续引用
     */
    public static String createTableCaptionWithCounter(XWPFDocument document, String titleText, XmlCursor cursor, Integer chapterNumber, AtomicInteger tableCounter) {
        XWPFParagraph paragraph;
        if (cursor != null) {
            paragraph = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            paragraph = document.createParagraph();
        }

        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 设置段落样式
        try {
            paragraph.setStyle("12");
        } catch (Exception e) {
            // 如果样式不存在，手动设置格式
            CTPPr ppr = paragraph.getCTP().getPPr();
            if (ppr == null) {
                ppr = paragraph.getCTP().addNewPPr();
            }

            CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
            spacing.setAfter(BigInteger.valueOf(120));
            spacing.setBefore(BigInteger.valueOf(120));
            spacing.setLine(BigInteger.valueOf(240));
            spacing.setLineRule(STLineSpacingRule.AUTO);

            CTJc jc = ppr.isSetJc() ? ppr.getJc() : ppr.addNewJc();
            jc.setVal(STJc.CENTER);
        }

        // 使用传入的计数器生成书签名
        String bookmarkName = "Table_Chapter" + chapterNumber + "_" + tableCounter.getAndIncrement();

        // 添加静态前缀"表"字
        XWPFRun prefixRun = paragraph.createRun();
        prefixRun.setText("表");
        prefixRun.setFontFamily("黑体");
        prefixRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(22));
        prefixRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(22));

        // 创建书签开始 - 只包围编号部分
        CTBookmark bookmarkStart = paragraph.getCTP().addNewBookmarkStart();
        bookmarkStart.setName(bookmarkName);
        bookmarkStart.setId(BigInteger.valueOf(bookmarkCounter.get()));

        // 创建表格序号域（使用平铺多域显示"3.1"格式）
        if (chapterNumber != null) {
            // 按章节编号 - 不创建内部书签
            createChapterSequenceFieldWithoutBookmark(paragraph, "表", chapterNumber);
        } else {
            // 全文档编号 - 不创建内部书签
            createSequenceFieldWithoutBookmark(paragraph, "表");
        }

        // 创建书签结束 - 只包围编号部分
        CTMarkupRange bookmarkEnd = paragraph.getCTP().addNewBookmarkEnd();
        bookmarkEnd.setId(BigInteger.valueOf(bookmarkCounter.get()));
        bookmarkCounter.incrementAndGet();

        // 添加标题文本
        XWPFRun titleRun = paragraph.createRun();
        titleRun.setText(" " + titleText);
        titleRun.setFontFamily("黑体");
        titleRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(22));
        titleRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(22));

        return bookmarkName;
    }

    /**
     * 创建图片标题域
     * @param document Word文档
     * @param titleText 标题文本（如"桥型布置图"）
     * @param cursor 插入位置的游标，如果为null则追加到文档末尾
     * @param chapterNumber 章节号，如果需要按章节编号则提供，否则传null
     * @return 生成的书签名，用于后续引用
     */
    public static String createFigureCaption(XWPFDocument document, String titleText, XmlCursor cursor, Integer chapterNumber) {
        return createFigureCaptionWithBookmark(document, titleText, cursor, chapterNumber, null);
    }

    /**
     * 创建图片标题域，指定书签名
     * @param document Word文档
     * @param titleText 标题文本（如"桥型布置图"）
     * @param cursor 插入位置的游标，如果为null则追加到文档末尾
     * @param chapterNumber 章节号，如果需要按章节编号则提供，否则传null
     * @param bookmarkName 指定的书签名，如果为null则自动生成
     * @return 生成的书签名，用于后续引用
     */
    public static String createFigureCaptionWithBookmark(XWPFDocument document, String titleText, XmlCursor cursor, Integer chapterNumber, String bookmarkName) {
        XWPFParagraph paragraph;
        if (cursor != null) {
            paragraph = document.insertNewParagraph(cursor);
            cursor.toNextToken();
        } else {
            paragraph = document.createParagraph();
        }

        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setStyle("12");
        paragraph.setSpacingBefore(100);
        paragraph.setSpacingAfter(200);

        XWPFRun run = paragraph.createRun();

        // 添加"图"字
        run.setText("图 ");
        run.setFontFamily("黑体");
        run.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21));
        run.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));

        // 生成或使用指定的书签名
        if (bookmarkName == null) {
            bookmarkName = "Figure_" + bookmarkCounter.getAndIncrement();
        }

        // 创建图片序号域
        if (chapterNumber != null) {
            // 按章节编号
            createChapterSequenceField(paragraph, "Figure", bookmarkName, chapterNumber);
        } else {
            // 全文档编号
            createSequenceField(paragraph, "Figure", bookmarkName);
        }

        // 添加标题文本
        if (titleText != null && !titleText.isEmpty()) {
            XWPFRun titleRun = paragraph.createRun();
            titleRun.setText(" " + titleText);
            titleRun.setFontFamily("黑体");
            titleRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21));
            titleRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));
        }

        return bookmarkName;
    }

    /**
     * 在现有段落中创建图片标题域（不创建新段落）
     * @param paragraph 现有的段落
     * @param titleText 标题文本
     * @param chapterNumber 章节号
     * @param bookmarkName 指定的书签名，如果为null则自动生成
     * @return 生成的书签名
     */
    public static String createFigureCaptionInParagraph(XWPFParagraph paragraph, String titleText, Integer chapterNumber, String bookmarkName) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setStyle("12");
        paragraph.setSpacingBefore(100);
        paragraph.setSpacingAfter(200);

        // 生成或使用指定的书签名
        if (bookmarkName == null) {
            bookmarkName = "Figure_" + bookmarkCounter.getAndIncrement();
        }

        // 添加静态前缀"图"字
        XWPFRun prefixRun = paragraph.createRun();
        prefixRun.setText("图");
        prefixRun.setFontFamily("黑体");
        prefixRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21));
        prefixRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));

        // 创建书签开始 - 只包围编号部分
        CTBookmark bookmarkStart = paragraph.getCTP().addNewBookmarkStart();
        bookmarkStart.setName(bookmarkName);
        bookmarkStart.setId(BigInteger.valueOf(bookmarkCounter.get()));

        // 创建图片序号域（使用平铺多域显示"3-1"格式）
        if (chapterNumber != null) {
            // 按章节编号 - 不创建内部书签
            createChapterSequenceFieldWithoutBookmark(paragraph, "图", chapterNumber);
        } else {
            // 全文档编号 - 不创建内部书签
            createSequenceFieldWithoutBookmark(paragraph, "图");
        }

        // 创建书签结束 - 只包围编号部分
        CTMarkupRange bookmarkEnd = paragraph.getCTP().addNewBookmarkEnd();
        bookmarkEnd.setId(BigInteger.valueOf(bookmarkCounter.get()));
        bookmarkCounter.incrementAndGet();

        // 添加标题文本
        if (titleText != null && !titleText.isEmpty()) {
            XWPFRun titleRun = paragraph.createRun();
            titleRun.setText(" " + titleText);
            titleRun.setFontFamily("黑体");
            titleRun.getCTR().addNewRPr().addNewSz().setVal(BigInteger.valueOf(21));
            titleRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));
        }

        return bookmarkName;
    }

    /**
     * 创建表格引用域
     * @param paragraph 要插入引用的段落
     * @param bookmarkName 表格的书签名
     * @param prefixText 前缀文本（如"具体检测结果见下表"）
     * @param suffixText 后缀文本（如":"）
     */
    public static void createTableReference(XWPFParagraph paragraph, String bookmarkName, String prefixText, String suffixText) {
        XWPFRun prefixRun = paragraph.createRun();
        prefixRun.setText(prefixText);

        // 创建引用域
        createReferenceField(paragraph, bookmarkName);

        if (suffixText != null && !suffixText.isEmpty()) {
            XWPFRun suffixRun = paragraph.createRun();
            suffixRun.setText(suffixText);
        }
    }

    /**
     * 创建章节格式的表格引用域（使用REF域引用整个表格标题）
     * @param paragraph 要插入引用的段落
     * @param bookmarkName 表格的书签名，格式如"Table_Chapter3_1"
     * @param prefixText 前缀文本（如"具体检测结果见下表"）
     * @param suffixText 后缀文本（如":"）
     */
    public static void createChapterTableReference(XWPFParagraph paragraph, String bookmarkName, String prefixText, String suffixText) {
        XWPFRun prefixRun = paragraph.createRun();
        prefixRun.setText(prefixText);
        // 设置前缀字体格式
        CTRPr prefixRPr = prefixRun.getCTR().addNewRPr();
        prefixRPr.addNewRFonts().setAscii("宋体");
        prefixRPr.addNewRFonts().setEastAsia("宋体");
        prefixRPr.addNewSz().setVal(BigInteger.valueOf(24)); // 12号字体
        prefixRPr.addNewSzCs().setVal(BigInteger.valueOf(24));

        // 使用REF域引用表格标题的完整内容
        // 关键：REF域应该能够引用到书签包围的完整内容
        createReferenceField(paragraph, bookmarkName);

        if (suffixText != null && !suffixText.isEmpty()) {
            XWPFRun suffixRun = paragraph.createRun();
            suffixRun.setText(suffixText);
            // 设置后缀字体格式
            CTRPr suffixRPr = suffixRun.getCTR().addNewRPr();
            suffixRPr.addNewRFonts().setAscii("宋体");
            suffixRPr.addNewRFonts().setEastAsia("宋体");
            suffixRPr.addNewSz().setVal(BigInteger.valueOf(24)); // 12号字体
            suffixRPr.addNewSzCs().setVal(BigInteger.valueOf(24));
        }
    }

    /**
     * 创建图片引用域
     * @param paragraph 要插入引用的段落
     * @param bookmarkName 图片的书签名
     * @param prefixText 前缀文本（如"如"）
     * @param suffixText 后缀文本（如"所示"）
     */
    public static void createFigureReference(XWPFParagraph paragraph, String bookmarkName, String prefixText, String suffixText) {
        if (prefixText != null && !prefixText.isEmpty()) {
            XWPFRun prefixRun = paragraph.createRun();
            prefixRun.setText(prefixText);
        }

        // 创建引用域
        createReferenceField(paragraph, bookmarkName);

        if (suffixText != null && !suffixText.isEmpty()) {
            XWPFRun suffixRun = paragraph.createRun();
            suffixRun.setText(suffixText);
        }
    }

    /**
     * 创建章节格式的图片引用域（使用REF域引用整个图片标题）
     * @param paragraph 段落
     * @param bookmarkName 书签名称，格式如"Figure_Chapter3_1"
     * @param prefixText 前缀文本（如"图"）
     * @param suffixText 后缀文本
     */
    public static void createChapterFigureReference(XWPFParagraph paragraph, String bookmarkName, String prefixText, String suffixText) {
        if (prefixText != null && !prefixText.isEmpty()) {
            XWPFRun prefixRun = paragraph.createRun();
            prefixRun.setText(prefixText);
            // 设置前缀字体格式
            CTRPr prefixRPr = prefixRun.getCTR().addNewRPr();
            prefixRPr.addNewRFonts().setAscii("宋体");
            prefixRPr.addNewRFonts().setEastAsia("宋体");
            prefixRPr.addNewSz().setVal(BigInteger.valueOf(21));
            prefixRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
        }

        // 使用REF域引用图片标题的完整内容
        // 关键：REF域应该能够引用到书签包围的完整内容
        createReferenceField(paragraph, bookmarkName);

        if (suffixText != null && !suffixText.isEmpty()) {
            XWPFRun suffixRun = paragraph.createRun();
            suffixRun.setText(suffixText);
            // 设置后缀字体格式
            CTRPr suffixRPr = suffixRun.getCTR().addNewRPr();
            suffixRPr.addNewRFonts().setAscii("宋体");
            suffixRPr.addNewRFonts().setEastAsia("宋体");
            suffixRPr.addNewSz().setVal(BigInteger.valueOf(21));
            suffixRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
        }
    }

    /**
     * 创建序列域（SEQ字段）
     * @param paragraph 段落
     * @param seqName 序列名称（如"Table"或"Figure"）
     * @param bookmarkName 书签名称
     */
    private static void createSequenceField(XWPFParagraph paragraph, String seqName, String bookmarkName) {
        CTR ctr = paragraph.getCTP().addNewR();

        // 创建书签开始
        CTBookmark bookmarkStart = paragraph.getCTP().addNewBookmarkStart();
        bookmarkStart.setName(bookmarkName);
        bookmarkStart.setId(BigInteger.valueOf(bookmarkCounter.get()));

        // 创建域开始
        CTFldChar fieldBegin = ctr.addNewFldChar();
        fieldBegin.setFldCharType(STFldCharType.BEGIN);

        // 创建域代码
        CTR instrCtr = paragraph.getCTP().addNewR();
        CTText instrText = instrCtr.addNewInstrText();
        instrText.setStringValue("SEQ " + seqName + " \\* ARABIC");

        // 创建域分隔符
        CTR sepCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldSep = sepCtr.addNewFldChar();
        fieldSep.setFldCharType(STFldCharType.SEPARATE);

        // 创建域结果（显示文本）
        CTR resultCtr = paragraph.getCTP().addNewR();
        // 设置域结果的字体格式，与前缀保持一致
        CTRPr resultRPr = resultCtr.addNewRPr();
        if ("Table".equals(seqName)) {
            // 表格字体：黑体，22pt
            resultRPr.addNewRFonts().setAscii("黑体");
            resultRPr.addNewRFonts().setEastAsia("黑体");
            resultRPr.addNewSz().setVal(BigInteger.valueOf(22));
            resultRPr.addNewSzCs().setVal(BigInteger.valueOf(22));
            // 设置初始显示值
            CTText resultText = resultCtr.addNewT();
            resultText.setStringValue(String.valueOf(tableSequence.getAndIncrement()));
        } else if ("Figure".equals(seqName)) {
            // 图片字体：黑体，21pt
            resultRPr.addNewRFonts().setAscii("黑体");
            resultRPr.addNewRFonts().setEastAsia("黑体");
            resultRPr.addNewSz().setVal(BigInteger.valueOf(21));
            resultRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
            // 设置初始显示值
            CTText resultText = resultCtr.addNewT();
            resultText.setStringValue(String.valueOf(figureSequence.getAndIncrement()));
        }

        // 创建域结束
        CTR endCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldEnd = endCtr.addNewFldChar();
        fieldEnd.setFldCharType(STFldCharType.END);

        // 创建书签结束
        CTMarkupRange bookmarkEnd = paragraph.getCTP().addNewBookmarkEnd();
        bookmarkEnd.setId(BigInteger.valueOf(bookmarkCounter.get()));
    }

    /**
     * 创建章节序列域（按章节重新编号）
     * @param paragraph 段落
     * @param seqName 序列名称
     * @param bookmarkName 书签名称
     * @param chapterNumber 章节号
     */
    private static void createChapterSequenceField(XWPFParagraph paragraph, String seqName, String bookmarkName, Integer chapterNumber) {
        // 使用平铺多域方案：STYLEREF + 连接符 + SEQ
        // 1. 创建STYLEREF域
        createStyleRefField(paragraph);

        // 2. 添加连接符
        XWPFRun connectorRun = paragraph.createRun();
        if ("表".equals(seqName)) {
            connectorRun.setText(".");
        } else if ("图".equals(seqName)) {
            connectorRun.setText("-");
        }
        // 设置连接符字体格式 - 使用Times New Roman
        CTRPr connectorRPr = connectorRun.getCTR().addNewRPr();
        connectorRPr.addNewRFonts().setAscii("Times New Roman");
        connectorRPr.addNewRFonts().setEastAsia("Times New Roman");
        if ("表".equals(seqName)) {
            connectorRPr.addNewSz().setVal(BigInteger.valueOf(21));
            connectorRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
        } else {
            connectorRPr.addNewSz().setVal(BigInteger.valueOf(21));
            connectorRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
        }

        // 3. 创建SEQ域
        createSimpleSEQField(paragraph, seqName, bookmarkName);
    }

    /**
     * 创建完整格式的图片引用（如"图3-1"）
     * @param paragraph 段落
     * @param bookmarkName 书签名称，格式如"Figure_Chapter3_1"
     * @param prefixText 前缀文本
     * @param suffixText 后缀文本
     * @param chapterNumber 章节号
     * @param sequenceNumber 序号
     */
    public static void createFullFormatFigureReference(XWPFParagraph paragraph, String bookmarkName, String prefixText, String suffixText, Integer chapterNumber, Integer sequenceNumber) {
        if (prefixText != null && !prefixText.isEmpty()) {
            XWPFRun prefixRun = paragraph.createRun();
            prefixRun.setText(prefixText);
            // 设置字体格式
            prefixRun.getCTR().addNewRPr().addNewRFonts().setEastAsia("宋体");
            prefixRun.getCTR().getRPr().addNewSz().setVal(BigInteger.valueOf(21));
            prefixRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));
        }

        // 生成完整格式显示文本
        String displayText;
        if (chapterNumber != null && sequenceNumber != null) {
            displayText = chapterNumber + "-" + sequenceNumber;
        } else {
            // 回退到解析书签名
            displayText = parseBookmarkToDisplayFormat(bookmarkName);
        }
        XWPFRun displayRun = paragraph.createRun();
        displayRun.setText(displayText);
        // 设置字体格式
        displayRun.getCTR().addNewRPr().addNewRFonts().setEastAsia("宋体");
        displayRun.getCTR().getRPr().addNewSz().setVal(BigInteger.valueOf(21));
        displayRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));

        if (suffixText != null && !suffixText.isEmpty()) {
            XWPFRun suffixRun = paragraph.createRun();
            suffixRun.setText(suffixText);
            // 设置字体格式
            suffixRun.getCTR().addNewRPr().addNewRFonts().setEastAsia("宋体");
            suffixRun.getCTR().getRPr().addNewSz().setVal(BigInteger.valueOf(21));
            suffixRun.getCTR().getRPr().addNewSzCs().setVal(BigInteger.valueOf(21));
        }
    }

    /**
     * 创建完整格式的图片引用（如"图3-1"）- 重载方法，向后兼容
     * @param paragraph 段落
     * @param bookmarkName 书签名称，格式如"Figure_Chapter3_1"
     * @param prefixText 前缀文本
     * @param suffixText 后缀文本
     */
    public static void createFullFormatFigureReference(XWPFParagraph paragraph, String bookmarkName, String prefixText, String suffixText) {
        createFullFormatFigureReference(paragraph, bookmarkName, prefixText, suffixText, null, null);
    }

    /**
     * 从书签名解析出显示格式
     * @param bookmarkName 书签名，如"Figure_Chapter3_1"或"Table_1"
     * @return 显示格式，如"3-1"或"3.1"
     */
    private static String parseBookmarkToDisplayFormat(String bookmarkName) {
        if (bookmarkName.startsWith("Figure_Chapter")) {
            // 解析图片书签：Figure_Chapter3_1 -> 3-1
            String[] parts = bookmarkName.split("_");
            if (parts.length >= 3) {
                String chapterPart = parts[1].replace("Chapter", ""); // "3"
                String sequencePart = parts[2]; // "1"
                // 需要将全局序号转换为章节内序号
                // 假设每章从1开始编号，这里简化处理
                int globalSeq = Integer.parseInt(sequencePart);
                int chapterSeq = ((globalSeq - 1) % 100) + 1; // 简化的章节内序号计算
                return chapterPart + "-" + chapterSeq;
            }
        } else if (bookmarkName.startsWith("Table_")) {
            // 解析表格书签：Table_1 -> 3.1
            String[] parts = bookmarkName.split("_");
            if (parts.length >= 2) {
                String sequencePart = parts[1];
                // 假设表格在第3章
                return "3." + sequencePart;
            }
        }
        // 默认返回序号
        return "1";
    }

    /**
     * 创建引用域（REF字段）
     * @param paragraph 段落
     * @param bookmarkName 要引用的书签名称
     */
    public static void createReferenceField(XWPFParagraph paragraph, String bookmarkName) {
        // 创建域开始
        CTR ctr = paragraph.getCTP().addNewR();
        CTFldChar fieldBegin = ctr.addNewFldChar();
        fieldBegin.setFldCharType(STFldCharType.BEGIN);

        // 创建域代码
        CTR instrCtr = paragraph.getCTP().addNewR();
        CTText instrText = instrCtr.addNewInstrText();
        instrText.setStringValue("REF " + bookmarkName + " \\h");

        // 创建域分隔符
        CTR sepCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldSep = sepCtr.addNewFldChar();
        fieldSep.setFldCharType(STFldCharType.SEPARATE);

        // 创建域结果（占位显示）
        CTR resultCtr = paragraph.getCTP().addNewR();
        // 设置引用域结果的字体格式，与表格内容保持一致
        CTRPr resultRPr = resultCtr.addNewRPr();
        resultRPr.addNewRFonts().setAscii("宋体");
        resultRPr.addNewRFonts().setEastAsia("宋体");
        resultRPr.addNewSz().setVal(BigInteger.valueOf(21));
        resultRPr.addNewSzCs().setVal(BigInteger.valueOf(21));

        CTText resultText = resultCtr.addNewT();
        resultText.setStringValue("3.1"); // 占位文本，REF域会引用QUOTE域的结果

        // 创建域结束
        CTR endCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldEnd = endCtr.addNewFldChar();
        fieldEnd.setFldCharType(STFldCharType.END);
    }

    /**
     * 创建STYLEREF域
     * @param paragraph 段落
     */
    private static void createStyleRefField(XWPFParagraph paragraph) {
        // 创建域开始
        CTR ctr = paragraph.getCTP().addNewR();
        CTFldChar fieldBegin = ctr.addNewFldChar();
        fieldBegin.setFldCharType(STFldCharType.BEGIN);

        // 创建域代码
        CTR instrCtr = paragraph.getCTP().addNewR();
        CTText instrText = instrCtr.addNewInstrText();
        instrText.setStringValue("STYLEREF 1 \\n");

        // 创建域分隔符
        CTR sepCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldSep = sepCtr.addNewFldChar();
        fieldSep.setFldCharType(STFldCharType.SEPARATE);

        // 创建域结果（显示文本）
        CTR resultCtr = paragraph.getCTP().addNewR();
        // 设置字体格式 - 章节号使用Times New Roman
        CTRPr resultRPr = resultCtr.addNewRPr();
        resultRPr.addNewRFonts().setAscii("Times New Roman");
        resultRPr.addNewRFonts().setEastAsia("Times New Roman");
        resultRPr.addNewSz().setVal(BigInteger.valueOf(21));
        resultRPr.addNewSzCs().setVal(BigInteger.valueOf(21));

        CTText resultText = resultCtr.addNewT();
        resultText.setStringValue("3");

        // 创建域结束
        CTR endCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldEnd = endCtr.addNewFldChar();
        fieldEnd.setFldCharType(STFldCharType.END);
    }

    /**
     * 创建简单的SEQ域
     * @param paragraph 段落
     * @param seqName 序列名称
     * @param bookmarkName 书签名称
     */
    private static void createSimpleSEQField(XWPFParagraph paragraph, String seqName, String bookmarkName) {
        // 创建书签开始
        CTBookmark bookmarkStart = paragraph.getCTP().addNewBookmarkStart();
        bookmarkStart.setName(bookmarkName);
        bookmarkStart.setId(BigInteger.valueOf(bookmarkCounter.get()));

        // 创建域开始
        CTR ctr = paragraph.getCTP().addNewR();
        CTFldChar fieldBegin = ctr.addNewFldChar();
        fieldBegin.setFldCharType(STFldCharType.BEGIN);

        // 创建域代码
        CTR instrCtr = paragraph.getCTP().addNewR();
        CTText instrText = instrCtr.addNewInstrText();
        instrText.setStringValue("SEQ " + seqName + " \\* ARABIC \\s 1");

        // 创建域分隔符
        CTR sepCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldSep = sepCtr.addNewFldChar();
        fieldSep.setFldCharType(STFldCharType.SEPARATE);

        // 创建域结果（显示文本）
        CTR resultCtr = paragraph.getCTP().addNewR();
        // 设置字体格式 - 序号使用Times New Roman
        CTRPr resultRPr = resultCtr.addNewRPr();
        resultRPr.addNewRFonts().setAscii("Times New Roman");
        resultRPr.addNewRFonts().setEastAsia("Times New Roman");
        resultRPr.addNewSz().setVal(BigInteger.valueOf(21));
        resultRPr.addNewSzCs().setVal(BigInteger.valueOf(21));

        CTText resultText = resultCtr.addNewT();
        resultText.setStringValue("1");

        // 创建域结束
        CTR endCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldEnd = endCtr.addNewFldChar();
        fieldEnd.setFldCharType(STFldCharType.END);

        // 创建书签结束
        CTMarkupRange bookmarkEnd = paragraph.getCTP().addNewBookmarkEnd();
        bookmarkEnd.setId(BigInteger.valueOf(bookmarkCounter.get()));
    }

    /**
     * 更新文档中的所有域
     * @param document Word文档
     */
    public static void updateAllFields(XWPFDocument document) {
        // POI目前不支持直接更新域，需要在Word中打开文档时手动更新
        // 或者可以设置文档属性，让Word在打开时自动更新域
        try {
            document.getProperties().getCoreProperties().setCategory("AutoUpdateFields");
        } catch (Exception e) {
            // 忽略异常
        }
    }

    /**
     * 创建章节序列域（不包含书签）
     * @param paragraph 段落
     * @param seqName 序列名称
     * @param chapterNumber 章节号
     */
    private static void createChapterSequenceFieldWithoutBookmark(XWPFParagraph paragraph, String seqName, Integer chapterNumber) {
        // 使用平铺多域方案：STYLEREF + 连接符 + SEQ
        // 1. 创建STYLEREF域
        createStyleRefField(paragraph);

        // 2. 添加连接符
        XWPFRun connectorRun = paragraph.createRun();
        if ("表".equals(seqName)) {
            connectorRun.setText(".");
        } else if ("图".equals(seqName)) {
            connectorRun.setText("-");
        }
        // 设置连接符字体格式 - 使用Times New Roman
        CTRPr connectorRPr = connectorRun.getCTR().addNewRPr();
        connectorRPr.addNewRFonts().setAscii("Times New Roman");
        connectorRPr.addNewRFonts().setEastAsia("Times New Roman");
        if ("表".equals(seqName)) {
            connectorRPr.addNewSz().setVal(BigInteger.valueOf(21));
            connectorRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
        } else {
            connectorRPr.addNewSz().setVal(BigInteger.valueOf(21));
            connectorRPr.addNewSzCs().setVal(BigInteger.valueOf(21));
        }

        // 3. 创建SEQ域（不包含书签）
        createSimpleSEQFieldWithoutBookmark(paragraph, seqName);
    }

    /**
     * 创建简单的SEQ域（不包含书签）
     * @param paragraph 段落
     * @param seqName 序列名称
     */
    private static void createSimpleSEQFieldWithoutBookmark(XWPFParagraph paragraph, String seqName) {
        // 创建域开始
        CTR ctr = paragraph.getCTP().addNewR();
        CTFldChar fieldBegin = ctr.addNewFldChar();
        fieldBegin.setFldCharType(STFldCharType.BEGIN);

        // 创建域代码
        CTR instrCtr = paragraph.getCTP().addNewR();
        CTText instrText = instrCtr.addNewInstrText();
        instrText.setStringValue("SEQ " + seqName + " \\* ARABIC \\s 1");

        // 创建域分隔符
        CTR sepCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldSep = sepCtr.addNewFldChar();
        fieldSep.setFldCharType(STFldCharType.SEPARATE);

        // 创建域结果（显示文本）
        CTR resultCtr = paragraph.getCTP().addNewR();
        // 设置字体格式 - 序号使用Times New Roman
        CTRPr resultRPr = resultCtr.addNewRPr();
        resultRPr.addNewRFonts().setAscii("Times New Roman");
        resultRPr.addNewRFonts().setEastAsia("Times New Roman");
        resultRPr.addNewSz().setVal(BigInteger.valueOf(21));
        resultRPr.addNewSzCs().setVal(BigInteger.valueOf(21));

        CTText resultText = resultCtr.addNewT();
        resultText.setStringValue("1");

        // 创建域结束
        CTR endCtr = paragraph.getCTP().addNewR();
        CTFldChar fieldEnd = endCtr.addNewFldChar();
        fieldEnd.setFldCharType(STFldCharType.END);
    }

    /**
     * 创建序列域（不包含书签）
     * @param paragraph 段落
     * @param seqName 序列名称
     */
    private static void createSequenceFieldWithoutBookmark(XWPFParagraph paragraph, String seqName) {
        createSimpleSEQFieldWithoutBookmark(paragraph, seqName);
    }
}