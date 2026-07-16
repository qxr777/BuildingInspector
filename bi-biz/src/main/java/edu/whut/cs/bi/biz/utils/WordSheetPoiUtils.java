package edu.whut.cs.bi.biz.utils;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.sheet.JsonSheetWordRenderer;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVerticalJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 检测记录表填充通用 POI 工具。
 */
public final class WordSheetPoiUtils {

    /** 无数据单元格统一占位符（表尾签字区除外） */
    public static final String EMPTY_CELL_PLACEHOLDER = "-";

    /** 页码写入位置：预览写正文，下载写页眉 */
    public enum PageNumberPlacement {
        BODY,
        HEADER
    }

    private WordSheetPoiUtils() {
    }

    public static String nvl(String value) {
        return value != null ? value : "";
    }

    /** 将空值转为占位符「-」，供数据单元格展示使用 */
    public static String cellDisplayValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EMPTY_CELL_PLACEHOLDER;
        }
        return value;
    }

    public static XWPFDocument loadTemplate(String classpath) {
        try {
            Resource resource = new ClassPathResource(classpath);
            return new XWPFDocument(resource.getInputStream());
        } catch (Exception e) {
            throw new ServiceException("Word 模板未找到：" + classpath);
        }
    }

    public static byte[] toBytes(XWPFDocument document) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.write(baos);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw wordGenerationException(e);
        }
    }

    public static byte[] buildMultiPageDocument(JsonSheetWordRenderer renderer,
                                                List<com.alibaba.fastjson.JSONObject> pages) {
        return buildMultiPageDocument(renderer, pages, PageNumberPlacement.BODY);
    }

    public static byte[] buildMultiPageDocument(JsonSheetWordRenderer renderer,
                                                List<com.alibaba.fastjson.JSONObject> pages,
                                                PageNumberPlacement pageNumberPlacement) {
        return buildMultiPageDocument(renderer.templateClasspath(), pages,
                (document, pageJson, pageIndex, totalPages) -> renderer.fillPage(document, pageJson),
                pageNumberPlacement);
    }

    /**
     * 通用多页 Word 组装：按页加载模板、填充内容并写入页码。
     *
     * @param pageFiller pageIndex 为 0-based 页序号
     */
    public static <T> byte[] buildMultiPageDocument(String templateClasspath,
                                                    List<T> pages,
                                                    WordPageFiller<T> pageFiller,
                                                    PageNumberPlacement pageNumberPlacement) {
        if (pages == null || pages.isEmpty()) {
            pages = new ArrayList<>();
            pages.add(null);
        }
        int totalPages = pages.size();
        XWPFDocument document = loadTemplate(templateClasspath);
        try {
            pageFiller.fillPage(document, pages.get(0), 0, totalPages);
            applyEmptyCellPlaceholders(document);
            int pageNo = 1;
            for (int i = 1; i < totalPages; i++) {
                pageNo++;
                XWPFDocument pageDocument = loadTemplate(templateClasspath);
                pageFiller.fillPage(pageDocument, pages.get(i), i, totalPages);
                applyEmptyCellPlaceholders(pageDocument);
                if (pageNumberPlacement == PageNumberPlacement.HEADER) {
                    addSectionEndWithPageHeader(document, pageNo - 1, totalPages);
                    appendDocumentBody(document, pageDocument);
                } else {
                    appendDocumentPageWithBodyNumber(document, pageDocument, pageNo, totalPages);
                }
                pageDocument.close();
            }
            if (pageNumberPlacement == PageNumberPlacement.HEADER) {
                applyLastSectionPageHeader(document, pageNo, totalPages);
            } else {
                clearHeaderPageNumbers(document);
                insertBodyPageNumberAtStart(document, 1, totalPages);
            }
            return toBytes(document);
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            throw wordGenerationException(e);
        }
    }

    private static ServiceException wordGenerationException(Exception e) {
        String detail = e.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = e.getClass().getSimpleName();
        }
        ServiceException se = new ServiceException("生成 Word 文件失败：" + detail);
        se.initCause(e);
        return se;
    }

    public static void fillCellAfterLabel(XWPFTable table, String labelKeyword, String value) {
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).getText().contains(labelKeyword)) {
                    if (i + 1 < cells.size()) {
                        setCellText(cells.get(i + 1), cellDisplayValue(value),
                                ParagraphAlignment.CENTER, XWPFTableCell.XWPFVertAlign.CENTER, cells.get(i));
                    }
                    return;
                }
            }
        }
    }

    public static void setCellText(XWPFTableCell cell, String text) {
        setCellText(cell, text, ParagraphAlignment.CENTER, XWPFTableCell.XWPFVertAlign.CENTER);
    }

    public static void setCellText(XWPFTableCell cell, String text,
                                   ParagraphAlignment hAlign, XWPFTableCell.XWPFVertAlign vAlign) {
        setCellText(cell, text, hAlign, vAlign, null);
    }

    public static void setCellText(XWPFTableCell cell, String text,
                                   ParagraphAlignment hAlign, XWPFTableCell.XWPFVertAlign vAlign,
                                   XWPFTableCell styleSourceCell) {
        CTRPr styleRPr = resolveRunStyle(cell, styleSourceCell);
        for (int i = cell.getParagraphs().size() - 1; i > 0; i--) {
            cell.removeParagraph(i);
        }
        XWPFParagraph para = cell.getParagraphs().isEmpty()
                ? cell.addParagraph() : cell.getParagraphArray(0);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
        applyRunStyle(run, styleRPr, 21);
        applyCellAlignment(cell, hAlign, vAlign);
        if (hAlign == ParagraphAlignment.CENTER) {
            clearParagraphIndentation(cell);
        }
    }

    /** 段落 + 单元格双层居中，确保 Word / docx-preview 均生效 */
    public static void centerTableCell(XWPFTableCell cell) {
        applyCellAlignment(cell, ParagraphAlignment.CENTER, XWPFTableCell.XWPFVertAlign.CENTER);
        clearParagraphIndentation(cell);
    }

    private static void clearParagraphIndentation(XWPFTableCell cell) {
        for (XWPFParagraph para : cell.getParagraphs()) {
            para.setIndentationLeft(0);
            para.setIndentationRight(0);
            para.setIndentationFirstLine(0);
            CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
            if (pPr.isSetInd()) {
                pPr.unsetInd();
            }
            if (pPr.isSetTabs()) {
                pPr.unsetTabs();
            }
            if (pPr.isSetJc()) {
                pPr.getJc().setVal(STJc.CENTER);
            } else {
                pPr.addNewJc().setVal(STJc.CENTER);
            }
        }
    }

    /**
     * 重写表头单元格为水平居中文字。
     * 清除模板中的竖排、右对齐、缩进等样式（JGLP05017「缺损位置」「缺损类型」列头常见）。
     */
    public static void rewriteCenteredHeaderCell(XWPFTableCell cell, String text) {
        CTTc tc = cell.getCTTc();
        CTTcPr tcPr = tc.isSetTcPr() ? tc.getTcPr() : tc.addNewTcPr();
        if (tcPr.isSetTextDirection()) {
            tcPr.unsetTextDirection();
        }
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
        setCellText(cell, text != null ? text : "", ParagraphAlignment.CENTER, XWPFTableCell.XWPFVertAlign.CENTER);
        for (XWPFParagraph para : cell.getParagraphs()) {
            para.setIndentationLeft(0);
            para.setIndentationRight(0);
            para.setIndentationFirstLine(0);
            CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
            if (pPr.isSetInd()) {
                pPr.unsetInd();
            }
            if (pPr.isSetTabs()) {
                pPr.unsetTabs();
            }
            if (pPr.isSetJc()) {
                pPr.getJc().setVal(STJc.CENTER);
            } else {
                pPr.addNewJc().setVal(STJc.CENTER);
            }
            ReportGenerateTools.setSingleLineSpacing(para);
        }
        applyCellAlignment(cell, ParagraphAlignment.CENTER, XWPFTableCell.XWPFVertAlign.CENTER);
    }

    public static void applyCellAlignment(XWPFTableCell cell, ParagraphAlignment hAlign,
                                          XWPFTableCell.XWPFVertAlign vAlign) {
        cell.setVerticalAlignment(vAlign);
        CTTc tc = cell.getCTTc();
        CTTcPr tcPr = tc.isSetTcPr() ? tc.getTcPr() : tc.addNewTcPr();
        STVerticalJc.Enum vertical = vAlign == XWPFTableCell.XWPFVertAlign.CENTER
                ? STVerticalJc.CENTER
                : (vAlign == XWPFTableCell.XWPFVertAlign.BOTTOM ? STVerticalJc.BOTTOM : STVerticalJc.TOP);
        if (tcPr.isSetVAlign()) {
            tcPr.getVAlign().setVal(vertical);
        } else {
            tcPr.addNewVAlign().setVal(vertical);
        }
        for (XWPFParagraph para : cell.getParagraphs()) {
            para.setAlignment(hAlign);
            CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
            STJc.Enum jc = toStJc(hAlign);
            if (pPr.isSetJc()) {
                pPr.getJc().setVal(jc);
            } else {
                pPr.addNewJc().setVal(jc);
            }
        }
    }

    private static STJc.Enum toStJc(ParagraphAlignment alignment) {
        if (alignment == ParagraphAlignment.CENTER) {
            return STJc.CENTER;
        }
        if (alignment == ParagraphAlignment.RIGHT) {
            return STJc.RIGHT;
        }
        if (alignment == ParagraphAlignment.BOTH) {
            return STJc.BOTH;
        }
        return STJc.LEFT;
    }

    public static void replaceParagraphText(XWPFParagraph para, String text) {
        CTRPr styleRPr = extractFirstRunStyle(para);
        para.setAlignment(ParagraphAlignment.LEFT);
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
        applyRunStyle(run, styleRPr, 20);
    }

    private static CTRPr resolveRunStyle(XWPFTableCell cell, XWPFTableCell styleSourceCell) {
        if (styleSourceCell != null) {
            CTRPr style = extractFirstRunStyle(styleSourceCell);
            if (style != null) {
                return style;
            }
        }
        CTRPr style = extractFirstRunStyle(cell);
        if (style != null) {
            return style;
        }
        return extractLabelStyleFromRow(cell);
    }

    private static CTRPr extractLabelStyleFromRow(XWPFTableCell cell) {
        XWPFTableRow row = cell.getTableRow();
        if (row == null) {
            return extractColumnHeaderStyle(cell);
        }
        List<XWPFTableCell> cells = row.getTableCells();
        int targetIndex = cells.indexOf(cell);
        for (int i = targetIndex - 1; i >= 0; i--) {
            String text = cells.get(i).getText().replaceAll("\\s+", "");
            if (!text.isEmpty() && !"/".equals(text)) {
                CTRPr style = extractFirstRunStyle(cells.get(i));
                if (style != null) {
                    return style;
                }
            }
        }
        return extractColumnHeaderStyle(cell);
    }

    private static CTRPr extractColumnHeaderStyle(XWPFTableCell cell) {
        XWPFTableRow row = cell.getTableRow();
        if (row == null) {
            return null;
        }
        XWPFTable table = row.getTable();
        List<XWPFTableCell> cells = row.getTableCells();
        int colIndex = cells.indexOf(cell);
        if (colIndex < 0) {
            return null;
        }
        int rowIndex = table.getRows().indexOf(row);
        for (int r = 0; r < rowIndex; r++) {
            List<XWPFTableCell> headerCells = table.getRow(r).getTableCells();
            if (colIndex >= headerCells.size()) {
                continue;
            }
            XWPFTableCell headerCell = headerCells.get(colIndex);
            String text = headerCell.getText().replaceAll("\\s+", "");
            if (text.isEmpty()) {
                continue;
            }
            CTRPr style = extractFirstRunStyle(headerCell);
            if (style != null) {
                return style;
            }
        }
        return null;
    }

    private static CTRPr extractFirstRunStyle(XWPFTableCell cell) {
        if (cell == null) {
            return null;
        }
        for (XWPFParagraph para : cell.getParagraphs()) {
            CTRPr style = extractFirstRunStyle(para);
            if (style != null) {
                return style;
            }
        }
        return null;
    }

    private static CTRPr extractFirstRunStyle(XWPFParagraph para) {
        if (para == null) {
            return null;
        }
        for (XWPFRun run : para.getRuns()) {
            if (run.getCTR().isSetRPr()) {
                return (CTRPr) run.getCTR().getRPr().copy();
            }
        }
        return null;
    }

    private static void applyRunStyle(XWPFRun run, CTRPr styleRPr, int fallbackFontSize) {
        if (styleRPr != null) {
            try {
                run.getCTR().setRPr((CTRPr) styleRPr.copy());
                return;
            } catch (Exception ignored) {
                // Fall back to the standard sheet font if a template style cannot be copied safely.
            }
        }
        ReportGenerateTools.setMixedFontFamily(run, fallbackFontSize);
    }

    public static int findRemarkRowIndex(XWPFTable table) {
        for (int i = 0; i < table.getRows().size(); i++) {
            if (getRowText(table.getRow(i)).replaceAll("\\s+", "").contains("备注")) {
                return i;
            }
        }
        return -1;
    }

    public static int findHeaderRowCountBySubHeader(XWPFTable table, String... keywords) {
        for (int i = 0; i < table.getRows().size(); i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            for (String keyword : keywords) {
                if (rowText.contains(keyword.replaceAll("\\s+", ""))) {
                    return i + 1;
                }
            }
        }
        return 1;
    }

    public static void fillDataRows(XWPFTable table, int headerRowCount, int remarkRowIndex,
                                    List<com.alibaba.fastjson.JSONObject> records,
                                    RowFiller rowFiller) {
        if (records == null) {
            records = new ArrayList<>();
        }
        int dataAreaEnd = remarkRowIndex > headerRowCount ? remarkRowIndex : table.getRows().size();
        int existingDataRows = dataAreaEnd - headerRowCount;
        int fillCount = Math.min(records.size(), existingDataRows);

        for (int i = 0; i < fillCount; i++) {
            rowFiller.fill(table.getRow(headerRowCount + i), records.get(i));
        }
        if (fillCount < existingDataRows) {
            for (int i = dataAreaEnd - 1; i >= headerRowCount + fillCount; i--) {
                table.removeRow(i);
            }
        }
    }

    public static void fillRemarkRow(XWPFTable table, String remark) {
        int remarkRowIndex = findRemarkRowIndex(table);
        if (remarkRowIndex < 0) {
            return;
        }
        XWPFTableRow remarkRow = table.getRow(remarkRowIndex);
        if (remarkRow.getTableCells().isEmpty()) {
            return;
        }
        if (remark == null || remark.trim().isEmpty()) {
            fillRemarkPlaceholderCells(remarkRow);
            return;
        }
        if (remarkRow.getTableCells().size() > 1) {
            setCellText(remarkRow.getCell(1), remark,
                    ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
            return;
        }
        setCellText(remarkRow.getCell(0), "备注：" + remark,
                ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
    }

    public static boolean isRemarkRow(XWPFTableRow row) {
        if (row == null) {
            return false;
        }
        return getRowText(row).replaceAll("\\s+", "").contains("备注");
    }

    /**
     * 备注行占位：标签格保留「备注：」，内容区无数据时写入「-」。
     */
    public static void fillRemarkPlaceholderCells(XWPFTableRow row) {
        List<XWPFTableCell> cells = row.getTableCells();
        if (cells.isEmpty()) {
            return;
        }
        if (cells.size() == 1) {
            fillSingleCellRemarkPlaceholder(cells.get(0));
            return;
        }
        for (int i = 0; i < cells.size(); i++) {
            XWPFTableCell cell = cells.get(i);
            String text = cell.getText().trim();
            if (i == 0 && isRemarkLabelOnly(text)) {
                continue;
            }
            if (i == 0 && text.contains("备注")) {
                String content = extractRemarkContent(text);
                if (content != null && content.isEmpty()) {
                    setRemarkContentCell(cell, EMPTY_CELL_PLACEHOLDER);
                }
                continue;
            }
            if (text.isEmpty()) {
                setRemarkContentCell(cell, EMPTY_CELL_PLACEHOLDER);
            }
        }
    }

    private static void fillSingleCellRemarkPlaceholder(XWPFTableCell cell) {
        String content = extractRemarkContent(cell.getText());
        if (content != null && content.isEmpty()) {
            setRemarkContentCell(cell, EMPTY_CELL_PLACEHOLDER);
        }
    }

    private static void setRemarkContentCell(XWPFTableCell cell, String content) {
        setCellText(cell, "备注：" + content,
                ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
    }

    private static boolean isRemarkLabelOnly(String text) {
        String normalized = text.replaceAll("\\s+", "");
        return "备注".equals(normalized) || "备注：".equals(normalized) || "备注:".equals(normalized);
    }

    /** 提取备注单元格中标签后的正文；非备注单元格返回 null */
    private static String extractRemarkContent(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        String normalized = trimmed.replaceAll("\\s+", "");
        if (!normalized.contains("备注")) {
            return null;
        }
        if ("备注".equals(normalized) || "备注：".equals(normalized) || "备注:".equals(normalized)) {
            return "";
        }
        int colonIndex = trimmed.indexOf('：');
        if (colonIndex < 0) {
            colonIndex = trimmed.indexOf(':');
        }
        if (colonIndex >= 0) {
            return trimmed.substring(colonIndex + 1).trim();
        }
        return trimmed.replaceFirst("^备注\\s*", "").trim();
    }

    /** 与 JGLP05017 一致：表名加粗 28 半磅，表号常规 21 半磅 */
    public static void formatSheetTitle(XWPFDocument document, String titleKeyword, String sheetNoKeyword) {
        for (XWPFParagraph para : document.getParagraphs()) {
            formatSheetTitleParagraph(para, titleKeyword, sheetNoKeyword);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        formatSheetTitleParagraph(para, titleKeyword, sheetNoKeyword);
                    }
                }
            }
        }
    }

    private static void formatSheetTitleParagraph(XWPFParagraph para, String titleKeyword, String sheetNoKeyword) {
        String text = para.getText().replaceAll("\\s+", "");
        String normalizedTitle = titleKeyword.replaceAll("\\s+", "");
        if (text.contains(normalizedTitle)) {
            para.setAlignment(ParagraphAlignment.CENTER);
            para.setIndentationLeft(0);
            para.setIndentationRight(0);
            para.setIndentationFirstLine(0);
            boolean inSheetNo = false;
            for (XWPFRun run : para.getRuns()) {
                String runText = run.text().replaceAll("\\s+", "");
                if (isSheetNoRun(runText, sheetNoKeyword) || inSheetNo) {
                    setNormalRunStyle(run);
                    inSheetNo = true;
                } else if (!runText.isEmpty()) {
                    setTitleRunStyle(run);
                }
            }
            return;
        }
        if (text.contains(sheetNoKeyword)) {
            para.setAlignment(ParagraphAlignment.RIGHT);
            for (XWPFRun run : para.getRuns()) {
                if (run.text() != null && !run.text().isEmpty()) {
                    setNormalRunStyle(run);
                }
            }
        }
    }

    private static boolean isSheetNoRun(String runText, String sheetNoKeyword) {
        if (runText == null || runText.isEmpty()) {
            return false;
        }
        String normalizedRun = runText.replaceAll("[^A-Za-z0-9-]", "");
        String normalizedSheetNo = sheetNoKeyword.replaceAll("[^A-Za-z0-9-]", "");
        return normalizedRun.contains("JGLP")
                || normalizedRun.equals(normalizedSheetNo)
                || (normalizedRun.length() > 1 && normalizedSheetNo.contains(normalizedRun));
    }

    public static XWPFTable findTableContaining(XWPFDocument document, String keyword) {
        for (XWPFTable table : document.getTables()) {
            if (getTableText(table).contains(keyword)) {
                return table;
            }
        }
        return null;
    }

    /**
     * 读取信息表中某标签对应的固定值：
     * 优先取标签单元格内除标签与冒号外的剩余文字；否则取标签右侧单元格文字。
     */
    public static String readValueAfterLabel(XWPFTable table, String labelKeyword) {
        if (table == null || labelKeyword == null) {
            return "";
        }
        String normalizedLabel = labelKeyword.replaceAll("\\s+", "");
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                String cellText = cells.get(i).getText();
                if (cellText == null || !cellText.contains(labelKeyword)) {
                    continue;
                }
                String remaining = cellText.replaceAll("\\s+", "")
                        .replace(normalizedLabel, "")
                        .replace("：", "")
                        .replace(":", "");
                if (!remaining.isEmpty()) {
                    return remaining;
                }
                if (i + 1 < cells.size()) {
                    String next = cells.get(i + 1).getText();
                    return next == null ? "" : next.trim();
                }
                return "";
            }
        }
        return "";
    }

    public static String getTableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            sb.append(getRowText(row));
        }
        return sb.toString();
    }

    public static String getRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            sb.append(cell.getText());
        }
        return sb.toString();
    }

    public static void normalizeTextStyles(XWPFDocument document) {
        for (XWPFParagraph para : document.getParagraphs()) {
            normalizeParagraphTextStyle(para);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        normalizeParagraphTextStyle(para);
                    }
                }
            }
        }
    }

    private static void normalizeParagraphTextStyle(XWPFParagraph para) {
        String text = para.getText().replaceAll("\\s+", "");
        if (text.contains("碳化深度检测记录表")
                || text.contains("桥梁结构桥梁技术状况检测记录表")
                || text.startsWith("备注")) {
            return;
        }
        if (isFooterSignatureText(text)) {
            return;
        }
        for (XWPFRun run : para.getRuns()) {
            clearRunDecoration(run);
            run.setBold(false);
            run.setColor("000000");
        }
    }

    private static boolean isFooterSignatureText(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        if (normalized.contains("试验检测日期") || normalized.contains("检测依据")) {
            return false;
        }
        return normalized.contains("检测：") || normalized.contains("检测:")
                || normalized.contains("记录：") || normalized.contains("记录:")
                || normalized.contains("复核：") || normalized.contains("复核:")
                || normalized.contains("日期：") || normalized.contains("日期:");
    }

    public static boolean isFooterSignatureRow(XWPFTableRow row) {
        if (row == null) {
            return false;
        }
        return isFooterSignatureText(getRowText(row).replaceAll("\\s+", ""));
    }

    /**
     * 将表格中尚未填写的空白单元格统一写入「-」。
     * 自表尾签字行（检测/记录/复核/日期）起及其下方行不做处理。
     */
    public static void applyEmptyCellPlaceholders(XWPFDocument document) {
        if (document == null) {
            return;
        }
        for (XWPFTable table : document.getTables()) {
            applyEmptyCellPlaceholders(table);
        }
    }

    public static void applyEmptyCellPlaceholders(XWPFTable table) {
        if (table == null) {
            return;
        }
        boolean footerReached = false;
        for (XWPFTableRow row : table.getRows()) {
            if (footerReached) {
                continue;
            }
            if (isFooterSignatureRow(row)) {
                footerReached = true;
                continue;
            }
            if (isRemarkRow(row)) {
                fillRemarkPlaceholderCells(row);
                continue;
            }
            fillPlaceholderInEmptyRowCells(row);
        }
    }

    public static void fillPlaceholderInEmptyRowCells(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            if (cell.getText().trim().isEmpty()) {
                setCellText(cell, EMPTY_CELL_PLACEHOLDER);
            }
        }
    }

    private static void setTitleRunStyle(XWPFRun run) {
        clearRunDecoration(run);
        run.setBold(true);
        run.setColor("000000");
        ReportGenerateTools.setMixedFontFamily(run, 28);
    }

    private static void setNormalRunStyle(XWPFRun run) {
        clearRunDecoration(run);
        run.setBold(false);
        run.setColor("000000");
        ReportGenerateTools.setMixedFontFamily(run, 21);
    }

    private static void clearRunDecoration(XWPFRun run) {
        run.setItalic(false);
        run.setUnderline(UnderlinePatterns.NONE);
        run.setStrikeThrough(false);
        run.setDoubleStrikethrough(false);
        run.setEmbossed(false);
        run.setImprinted(false);
        run.setShadow(false);
        run.setVanish(false);
    }

    private static void appendDocumentBody(XWPFDocument target, XWPFDocument pageDocument) {
        for (IBodyElement element : pageDocument.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                target.getDocument().getBody().addNewP().set(paragraph.getCTP().copy());
            } else if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                target.getDocument().getBody().addNewTbl().set(table.getCTTbl().copy());
            }
        }
    }

    private static void appendDocumentPageWithBodyNumber(XWPFDocument target, XWPFDocument pageDocument,
                                                         int pageNo, int totalPages) {
        XWPFParagraph breakPara = target.createParagraph();
        breakPara.setPageBreak(true);
        writePageNumberParagraph(target.createParagraph(), pageNo, totalPages);
        appendDocumentBody(target, pageDocument);
    }

    private static void addSectionEndWithPageHeader(XWPFDocument document, int pageNo, int totalPages) {
        XWPFParagraph sectionBreak = document.createParagraph();
        CTPPr pPr = sectionBreak.getCTP().addNewPPr();
        CTSectPr sectPr = pPr.addNewSectPr();
        sectPr.addNewType().setVal(STSectionMark.NEXT_PAGE);
        copyPageLayoutFromBody(document, sectPr);

        XWPFHeader header = createHeaderForSection(document, sectPr);
        writePageNumberToHeader(header, pageNo, totalPages);
    }

    private static void applyLastSectionPageHeader(XWPFDocument document, int pageNo, int totalPages) {
        CTBody body = document.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        XWPFHeader header = createHeaderForSection(document, sectPr);
        writePageNumberToHeader(header, pageNo, totalPages);
    }

    private static XWPFHeader createHeaderForSection(XWPFDocument document, CTSectPr sectPr) {
        clearDefaultHeaderReference(sectPr);
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(document, sectPr);
        return policy.createHeader(STHdrFtr.DEFAULT);
    }

    private static void clearDefaultHeaderReference(CTSectPr sectPr) {
        for (int i = sectPr.sizeOfHeaderReferenceArray() - 1; i >= 0; i--) {
            if (sectPr.getHeaderReferenceArray(i).getType() == STHdrFtr.DEFAULT) {
                sectPr.removeHeaderReference(i);
            }
        }
    }

    private static void copyPageLayoutFromBody(XWPFDocument document, CTSectPr targetSectPr) {
        CTBody body = document.getDocument().getBody();
        if (!body.isSetSectPr()) {
            return;
        }
        CTSectPr source = body.getSectPr();
        if (source.isSetPgSz()) {
            targetSectPr.setPgSz((CTPageSz) source.getPgSz().copy());
        }
        if (source.isSetPgMar()) {
            targetSectPr.setPgMar((CTPageMar) source.getPgMar().copy());
        }
    }

    private static void writePageNumberToHeader(XWPFHeader header, int pageNo, int totalPages) {
        for (int i = header.getParagraphs().size() - 1; i >= 0; i--) {
            header.removeParagraph(header.getParagraphs().get(i));
        }
        header.createParagraph();
        header.createParagraph();
        writePageNumberParagraph(header.createParagraph(), pageNo, totalPages);
    }

    private static void insertBodyPageNumberAtStart(XWPFDocument document, int pageNo, int totalPages) {
        CTP ctp = document.getDocument().getBody().insertNewP(0);
        XWPFParagraph para = new XWPFParagraph(ctp, document);
        writePageNumberParagraph(para, pageNo, totalPages);
    }

    private static void clearHeaderPageNumbers(XWPFDocument document) {
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph para : new ArrayList<>(header.getParagraphs())) {
                if (isPageNumberParagraph(para)) {
                    header.removeParagraph(para);
                }
            }
        }
    }

    private static boolean isPageNumberParagraph(XWPFParagraph para) {
        String text = para.getText().replaceAll("\\s+", "");
        return text.contains("第") && text.contains("页") && text.contains("共");
    }

    private static void writePageNumberParagraph(XWPFParagraph para, int pageNo, int totalPages) {
        para.setAlignment(ParagraphAlignment.RIGHT);
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
        XWPFRun run = para.createRun();
        run.setText("第 " + pageNo + " 页 共 " + totalPages + " 页");
        ReportGenerateTools.setMixedFontFamily(run, 21);
    }

    @FunctionalInterface
    public interface WordPageFiller<T> {
        void fillPage(XWPFDocument document, T pageData, int pageIndex, int totalPages);
    }

    @FunctionalInterface
    public interface RowFiller {
        void fill(XWPFTableRow row, com.alibaba.fastjson.JSONObject record);
    }
}
