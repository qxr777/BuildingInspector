package edu.whut.cs.bi.biz.utils;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 让正文页脚的总页数引用正文最后一页，避免模板中的固定页数或固定扣减。 */
public final class WordReportPageNumberUtils {
    private static final String LAST_BODY_PAGE = "BI_ReportLastBodyPage";

    private WordReportPageNumberUtils() {
    }

    public static void useLastBodyPageForFooterTotal(XWPFDocument document) {
        if (document == null) {
            return;
        }
        XWPFParagraph lastParagraph = lastBodyParagraph(document);
        if (lastParagraph == null) {
            throw new IllegalStateException("报告正文没有可用于总页数的末尾段落");
        }

        // 书签属于正文最后一个现有段落，不额外插入会改变分页的空段落。
        BigInteger bookmarkId = BigInteger.valueOf(ThreadLocalRandom.current().nextInt(1_000_000_000, 2_000_000_000));
        CTBookmark start = lastParagraph.getCTP().addNewBookmarkStart();
        start.setName(LAST_BODY_PAGE);
        start.setId(bookmarkId);
        CTMarkupRange end = lastParagraph.getCTP().addNewBookmarkEnd();
        end.setId(bookmarkId);

        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || !text.contains("共") || !text.contains("页")) {
                    continue;
                }
                replacePageTotal(paragraph);
            }
        }
        document.getSettings().setUpdateFields();
    }

    private static XWPFParagraph lastBodyParagraph(XWPFDocument document) {
        List<IBodyElement> elements = document.getBodyElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            IBodyElement element = elements.get(i);
            if (element instanceof XWPFParagraph paragraph) {
                return paragraph;
            }
            if (element instanceof XWPFTable table) {
                List<XWPFTableRow> rows = table.getRows();
                for (int row = rows.size() - 1; row >= 0; row--) {
                    List<XWPFTableCell> cells = rows.get(row).getTableCells();
                    for (int cell = cells.size() - 1; cell >= 0; cell--) {
                        List<XWPFParagraph> paragraphs = cells.get(cell).getParagraphs();
                        if (!paragraphs.isEmpty()) {
                            return paragraphs.get(paragraphs.size() - 1);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void replacePageTotal(XWPFParagraph paragraph) {
        CTRPr formatting = null;
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR().isSetRPr()) {
                formatting = (CTRPr) run.getCTR().getRPr().copy();
                break;
            }
        }
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }

        addText(paragraph, formatting, "第 ");
        addField(paragraph, formatting, " PAGE ", "1");
        addText(paragraph, formatting, " 页 共 ");
        addField(paragraph, formatting, " PAGEREF " + LAST_BODY_PAGE + " ", "1");
        addText(paragraph, formatting, " 页");
    }

    private static void addText(XWPFParagraph paragraph, CTRPr formatting, String value) {
        XWPFRun run = paragraph.createRun();
        applyFormatting(run, formatting);
        run.setText(value);
    }

    private static void addField(XWPFParagraph paragraph, CTRPr formatting, String instruction, String fallback) {
        XWPFRun begin = paragraph.createRun();
        applyFormatting(begin, formatting);
        CTFldChar beginChar = begin.getCTR().addNewFldChar();
        beginChar.setFldCharType(STFldCharType.BEGIN);

        XWPFRun code = paragraph.createRun();
        applyFormatting(code, formatting);
        CTText instructionText = code.getCTR().addNewInstrText();
        instructionText.setStringValue(instruction);

        XWPFRun separate = paragraph.createRun();
        applyFormatting(separate, formatting);
        CTFldChar separator = separate.getCTR().addNewFldChar();
        separator.setFldCharType(STFldCharType.SEPARATE);

        addText(paragraph, formatting, fallback);

        XWPFRun end = paragraph.createRun();
        applyFormatting(end, formatting);
        CTFldChar endChar = end.getCTR().addNewFldChar();
        endChar.setFldCharType(STFldCharType.END);
    }

    private static void applyFormatting(XWPFRun run, CTRPr formatting) {
        if (formatting != null) {
            run.getCTR().setRPr((CTRPr) formatting.copy());
        }
    }
}
