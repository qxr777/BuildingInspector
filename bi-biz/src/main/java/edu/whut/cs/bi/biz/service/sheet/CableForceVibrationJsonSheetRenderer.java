package edu.whut.cs.bi.biz.service.sheet;

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
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 索力检测记录表（振动法，JGLP05012a）JSON → Word 渲染。
 */
@Component
public class CableForceVibrationJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    private static final String TITLE = "索力检测记录表";
    private static final String SHEET_NO = "JGLP05012a";

    public CableForceVibrationJsonSheetRenderer() {
        super("cable_force_vibration",
                "索力检测记录表（振动法）.docx",
                "索力检测记录表（振动法）",
                TITLE,
                SHEET_NO,
                Arrays.asList("索号", "频率", "索力", "振动"),
                Arrays.asList("索号", "频率"),
                Arrays.asList("cableNo", "point", "frequency1", "frequency2", "frequency3", "averageFrequency", "cableForce", "temperature", "note"));
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
        fillVibrationRows(document, pageJson);
        normalizeTextStyles(document);
        formatSheetTitle(document, TITLE, SHEET_NO);
    }

    private void fillVibrationRows(XWPFDocument document, JSONObject pageJson) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "阶数");
        if (table == null) {
            return;
        }
        List<Integer> blockStarts = findBlockStarts(table);
        List<JSONObject> records = toRecords(pageJson);
        int count = Math.min(blockStarts.size(), records.size());
        for (int i = 0; i < count; i++) {
            fillBlock(table, blockStarts.get(i), records.get(i), i + 1);
        }
        fillRemarkRow(table, pageJson != null ? pageJson.getString("remark") : null);
    }

    private List<Integer> findBlockStarts(XWPFTable table) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < table.getRows().size() - 1; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            String nextRowText = getRowText(table.getRow(i + 1)).replaceAll("\\s+", "");
            if (rowText.contains("阶数") && nextRowText.contains("频率")) {
                starts.add(i);
            }
        }
        return starts;
    }

    private void fillBlock(XWPFTable table, int startRow, JSONObject record, int defaultIndex) {
        XWPFTableRow orderRow = table.getRow(startRow);
        XWPFTableRow frequencyRow = table.getRow(startRow + 1);

        List<XWPFTableCell> orderCells = orderRow.getTableCells();
        if (orderCells.size() > 0) {
            setCellText(orderCells.get(0), valueOrDefault(record.getString("rowIndex"), String.valueOf(defaultIndex)));
        }
        if (orderCells.size() > 1) {
            setCellText(orderCells.get(1), record.getString("cableNo"));
        }
        int averageColumn = orderCells.size() - 1;
        if (averageColumn > 2) {
            setCellText(orderCells.get(averageColumn), valueOf(record, "frequencySummary", "averageFrequency"));
        }
        int orderLabelIndex = findCellContaining(orderRow, "阶数");
        int orderDataStart = orderLabelIndex >= 0 ? orderLabelIndex + 1 : 3;
        int orderCapacity = Math.max(0, averageColumn - orderDataStart);
        int frequencyCount = Math.min(6, orderCapacity);
        for (int i = 1; i <= frequencyCount; i++) {
            setIfExists(orderCells, orderDataStart + i - 1,
                    valueOrDefault(valueOf(record, "frequencyOrder" + i, "order" + i), String.valueOf(i)));
        }

        List<XWPFTableCell> frequencyCells = frequencyRow.getTableCells();
        int labelIndex = findCellContaining(frequencyRow, "频率");
        int dataStart = labelIndex >= 0 ? labelIndex + 1 : 3;
        int frequencyValueCount = Math.min(6, Math.max(0, frequencyCells.size() - dataStart));
        for (int i = 1; i <= frequencyValueCount; i++) {
            setIfExists(frequencyCells, dataStart + i - 1,
                    valueOf(record, "frequencyValue" + i, "frequency" + i));
        }

        centerRow(orderRow);
        centerRow(frequencyRow);
    }

    private void setIfExists(List<XWPFTableCell> cells, int index, String value) {
        if (index >= 0 && index < cells.size()) {
            setCellText(cells.get(index), value);
        }
    }

    private int findCellContaining(XWPFTableRow row, String keyword) {
        List<XWPFTableCell> cells = row.getTableCells();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).getText().contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private String valueOf(JSONObject record, String... keys) {
        if (record == null) {
            return null;
        }
        for (String key : keys) {
            String value = record.getString(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void centerRow(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                paragraph.setIndentationLeft(0);
                paragraph.setIndentationRight(0);
                paragraph.setIndentationFirstLine(0);
            }
        }
    }
}
