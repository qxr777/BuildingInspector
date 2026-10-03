package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.fillRemarkRow;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.formatSheetTitle;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getRowText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.normalizeTextStyles;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.nvl;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.centerTableCell;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 钢筋位置和保护层厚度检测记录表（JGLP02003）JSON → Word 渲染。
 */
@Component
public class RebarCoverJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    private static final String TITLE = "钢筋位置和保护层厚度检测记录表";
    private static final String SHEET_NO = "JGLP02003";
    private static final int BLOCK_ROW_COUNT = 7;
    private static final int MAX_REBARS_PER_RECORD = 10;

    public RebarCoverJsonSheetRenderer() {
        super("rebar_cover",
                "钢筋位置和保护层厚度检测记录表.docx",
                TITLE,
                TITLE,
                SHEET_NO,
                Arrays.asList("构件名称", "测区", "测点", "保护层"),
                Arrays.asList("测区", "测点", "保护层"),
                Arrays.asList("rowIndex", "componentName", "point", "value1", "value2", "value3", "average", "designValue", "deviation", "direction"));
    }

    @Override
    public void fillPage(XWPFDocument document, JSONObject pageJson) {
        JSONObject header = pageJson != null ? pageJson.getJSONObject("header") : null;
        if (header == null) {
            header = new JSONObject();
        }
        formatSheetTitle(document, TITLE, SHEET_NO);
        fillHeaderParagraphs(document, header);
        fillInfoTable(document, header);
        fillRecordBlocks(document, pageJson);
        normalizeTextStyles(document);
        centerRecordsTable(document);
        formatSheetTitle(document, TITLE, SHEET_NO);
    }

    private void fillRecordBlocks(XWPFDocument document, JSONObject pageJson) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "钢筋编号");
        if (table == null) {
            return;
        }
        List<JSONObject> records = toRecords(pageJson);
        List<Integer> blockStarts = findBlockStarts(table);
        int count = Math.min(records.size(), blockStarts.size());
        for (int i = 0; i < count; i++) {
            fillBlock(table, blockStarts.get(i), records.get(i), i + 1);
        }
        fillRemarkRow(table, pageJson != null ? pageJson.getString("remark") : null);
    }

    private List<Integer> findBlockStarts(XWPFTable table) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < table.getRows().size(); i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("钢筋编号")) {
                starts.add(i);
            }
        }
        return starts;
    }

    private void fillBlock(XWPFTable table, int startRow, JSONObject record, int defaultIndex) {
        XWPFTableRow firstRow = table.getRow(startRow);
        fillFixedColumns(firstRow, record, defaultIndex);

        List<Integer> rebarNoCells = findBlankDataCellIndexes(firstRow, 3);
        List<Integer> value1Cells = findBlankDataCellIndexes(table.getRow(startRow + 1), 4);
        List<Integer> value2Cells = findBlankDataCellIndexes(table.getRow(startRow + 2), 4);
        List<Integer> averageCells = findBlankDataCellIndexes(table.getRow(startRow + 3), 4);
        List<Integer> spacing1Cells = findBlankDataCellIndexes(table.getRow(startRow + 4), 4);
        List<Integer> spacing2Cells = findBlankDataCellIndexes(table.getRow(startRow + 5), 4);
        List<Integer> spacingAverageCells = findBlankDataCellIndexes(table.getRow(startRow + 6), 4);

        List<JSONObject> rebars = parseRebars(record);
        for (int col = 0; col < rebars.size() && col < MAX_REBARS_PER_RECORD; col++) {
            JSONObject rebar = rebars.get(col);
            setCellTextAt(firstRow, rebarNoCells, col, valueOf(rebar, "rebarNumber", "rebarNo"));
            setCellTextAt(table.getRow(startRow + 1), value1Cells, col, valueOf(rebar, "coverThickness1", "value1"));
            setCellTextAt(table.getRow(startRow + 2), value2Cells, col, valueOf(rebar, "coverThickness2", "value2"));
            setCellTextAt(table.getRow(startRow + 3), averageCells, col,
                    formatAverage(valueOf(rebar, "coverThicknessAverage", "average")));
            setCellTextAt(table.getRow(startRow + 4), spacing1Cells, col, valueOf(rebar, "rebarSpacing1", "spacing1"));
            setCellTextAt(table.getRow(startRow + 5), spacing2Cells, col, valueOf(rebar, "rebarSpacing2", "spacing2"));
            setCellTextAt(table.getRow(startRow + 6), spacingAverageCells, col,
                    formatAverage(valueOf(rebar, "rebarSpacingAverage", "spacingAverage")));
        }
        centerBlock(table, startRow);
    }

    private List<JSONObject> parseRebars(JSONObject record) {
        List<JSONObject> rebars = new ArrayList<>();
        if (record == null) {
            return rebars;
        }
        JSONArray rebarArray = record.getJSONArray("rebarCoverItems");
        if (rebarArray == null || rebarArray.isEmpty()) {
            rebarArray = record.getJSONArray("rebars");
        }
        if (rebarArray != null && !rebarArray.isEmpty()) {
            for (int i = 0; i < rebarArray.size() && i < MAX_REBARS_PER_RECORD; i++) {
                rebars.add(rebarArray.getJSONObject(i));
            }
            return rebars;
        }
        if (hasLegacyRebarFields(record)) {
            JSONObject rebar = new JSONObject();
            rebar.put("rebarNo", record.getString("rebarNo"));
            rebar.put("value1", record.getString("value1"));
            rebar.put("value2", record.getString("value2"));
            rebar.put("average", record.getString("average"));
            rebar.put("spacing1", record.getString("spacing1"));
            rebar.put("spacing2", record.getString("spacing2"));
            rebar.put("spacingAverage", record.getString("spacingAverage"));
            rebars.add(rebar);
        }
        return rebars;
    }

    private boolean hasLegacyRebarFields(JSONObject record) {
        return !nvl(record.getString("rebarNo")).isEmpty()
                || !nvl(record.getString("value1")).isEmpty()
                || !nvl(record.getString("value2")).isEmpty()
                || !nvl(record.getString("average")).isEmpty()
                || !nvl(record.getString("spacing1")).isEmpty()
                || !nvl(record.getString("spacing2")).isEmpty()
                || !nvl(record.getString("spacingAverage")).isEmpty();
    }

    private void fillFixedColumns(XWPFTableRow row, JSONObject record, int defaultIndex) {
        List<XWPFTableCell> cells = row.getTableCells();
        if (cells.size() > 0) {
            setCellText(cells.get(0), nvl(record.getString("rowIndex")).isEmpty()
                    ? String.valueOf(defaultIndex) : record.getString("rowIndex"));
        }
        if (cells.size() > 1) {
            setCellText(cells.get(1), record.getString("componentName"));
        }
        if (cells.size() > 2) {
            setCellText(cells.get(2), valueOf(record, "testPosition", "point", "serialNumber"));
        }
        String direction = valueOf(record, "rebarDirection", "direction");
        if (cells.size() > 3 && direction != null) {
            setCellText(cells.get(cells.size() - 1), direction);
        }
    }

    private String valueOf(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void setCellTextAt(XWPFTableRow row, List<Integer> cellIndexes, int columnIndex, String value) {
        if (row == null || value == null || value.isEmpty()) {
            return;
        }
        if (columnIndex < 0 || columnIndex >= cellIndexes.size()) {
            return;
        }
        setCellText(row.getCell(cellIndexes.get(columnIndex)), value);
    }

    private List<Integer> findBlankDataCellIndexes(XWPFTableRow row, int startCell) {
        List<Integer> indexes = new ArrayList<>();
        List<XWPFTableCell> cells = row.getTableCells();
        for (int i = Math.max(0, startCell); i < cells.size(); i++) {
            String text = cells.get(i).getText().trim();
            if (text.isEmpty() || "/".equals(text)) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private String formatAverage(String value) {
        String text = nvl(value).trim();
        if (text.isEmpty() || text.contains(".")) {
            return text;
        }
        return text.matches("-?\\d+") ? text + ".00" : text;
    }

    private void centerBlock(XWPFTable table, int startRow) {
        for (int rowIndex = startRow; rowIndex < startRow + BLOCK_ROW_COUNT && rowIndex < table.getRows().size(); rowIndex++) {
            for (XWPFTableCell cell : table.getRow(rowIndex).getTableCells()) {
                centerCell(cell);
            }
        }
    }

    private void centerRecordsTable(XWPFDocument document) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "钢筋编号");
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            String rowText = getRowText(row).replaceAll("\\s+", "");
            if (rowText.contains("备注") || rowText.contains("检测：")) {
                break;
            }
            for (XWPFTableCell cell : row.getTableCells()) {
                centerCell(cell);
            }
        }
    }

    private void centerCell(XWPFTableCell cell) {
        centerTableCell(cell);
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            paragraph.setIndentationLeft(0);
            paragraph.setIndentationRight(0);
            paragraph.setIndentationFirstLine(0);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(0);
        }
    }
}
