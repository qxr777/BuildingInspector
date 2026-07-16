package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.fillRemarkRow;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.formatSheetTitle;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getRowText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.normalizeTextStyles;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.centerTableCell;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.rewriteCenteredHeaderCell;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 桥梁结构位移试验检测记录表（全站仪，JGLP05001b-1）JSON → Word 渲染。
 */
@Component
public class DisplacementTotalStationJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    private static final String TITLE = "桥梁结构位移试验检测记录表";
    private static final String SHEET_NO = "JGLP05001b-1";

    public DisplacementTotalStationJsonSheetRenderer() {
        super("displacement_total_station",
                "桥梁结构位移试验检测记录表 （全站仪） .docx",
                "桥梁结构位移试验检测记录表（全站仪）",
                TITLE,
                SHEET_NO,
                Arrays.asList("测点", "坐标", "位移", "全站仪"),
                Arrays.asList("测点", "坐标", "位移"),
                Arrays.asList("station", "point", "x", "y", "z", "initialValue", "currentValue", "displacement", "note"));
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
        fillCoordinateTable(document, pageJson, header);
        normalizeTextStyles(document);
        formatSheetTitle(document, TITLE, SHEET_NO);
    }

    private void fillCoordinateTable(XWPFDocument document, JSONObject pageJson, JSONObject header) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "测站坐标");
        if (table == null) {
            return;
        }
        List<JSONObject> records = toRecords(pageJson);
        fillVerticalCoordinate(table, "测站坐标", header, "station", findCoordinateRecord(records, "测站坐标"));
        fillVerticalCoordinate(table, "后视点坐标", header, "backSight", findCoordinateRecord(records, "后视点坐标"));
        fillMeasurementCoordinates(table, records);
        fillRemarkRow(table, pageJson != null ? pageJson.getString("remark") : null);
    }

    private void fillVerticalCoordinate(XWPFTable table, String label, JSONObject header, String prefix, JSONObject record) {
        int start = findRowContaining(table, label);
        if (start < 0) {
            return;
        }
        rewriteCoordinateLabelCell(table.getRow(start), label);
        centerCoordinateRows(table, start);
        setValueAfterAxis(table.getRow(start), "X", coordinateValue(record, header, prefix, "X"));
        setValueAfterAxis(table.getRow(start + 1), "Y", coordinateValue(record, header, prefix, "Y"));
        setValueAfterAxis(table.getRow(start + 2), "Z", coordinateValue(record, header, prefix, "Z"));
    }

    private void fillMeasurementCoordinates(XWPFTable table, List<JSONObject> records) {
        int start = findRowContaining(table, "测点坐标");
        if (start < 0) {
            return;
        }
        int recordIndex = 0;
        for (int i = start + 1; i < table.getRows().size() && recordIndex < records.size(); i++) {
            XWPFTableRow row = table.getRow(i);
            String rowText = getRowText(row).replaceAll("\\s+", "");
            if (rowText.contains("X") && rowText.contains("Y") && rowText.contains("Z")) {
                continue;
            }
            if (rowText.contains("备注") || rowText.contains("检测：")) {
                break;
            }
            JSONObject record = nextMeasurementRecord(records, recordIndex);
            if (record == null) {
                break;
            }
            fillPointRow(row, record);
            recordIndex++;
        }
    }

    private void fillPointRow(XWPFTableRow row, JSONObject record) {
        List<XWPFTableCell> cells = row.getTableCells();
        if (cells.size() > 0) setCellText(cells.get(0), valueOf(record, "coordinateName", "point"));
        if (cells.size() > 1) setCellText(cells.get(1), valueOf(record, "coordinateX", "x"));
        if (cells.size() > 2) setCellText(cells.get(2), valueOf(record, "coordinateY", "y"));
        if (cells.size() > 3) setCellText(cells.get(3), valueOf(record, "coordinateZ", "z"));
        if (cells.size() > 4) setCellText(cells.get(4), record.getString("displacement"));
    }

    private JSONObject findCoordinateRecord(List<JSONObject> records, String coordinateName) {
        for (JSONObject record : records) {
            if (coordinateName.equals(record.getString("coordinateName"))) {
                return record;
            }
        }
        return null;
    }

    private JSONObject nextMeasurementRecord(List<JSONObject> records, int measurementIndex) {
        int seen = 0;
        for (JSONObject record : records) {
            if (isFixedCoordinateRecord(record)) {
                continue;
            }
            if (seen == measurementIndex) {
                return record;
            }
            seen++;
        }
        return null;
    }

    private boolean isFixedCoordinateRecord(JSONObject record) {
        String name = record != null ? record.getString("coordinateName") : null;
        return "测站坐标".equals(name) || "后视点坐标".equals(name) || "校核点坐标".equals(name);
    }

    private int findRowContaining(XWPFTable table, String keyword) {
        for (int i = 0; i < table.getRows().size(); i++) {
            if (getRowText(table.getRow(i)).replaceAll("\\s+", "").contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    private void setValueAfterAxis(XWPFTableRow row, String axis, String value) {
        if (row == null || value == null || value.isEmpty()) {
            return;
        }
        List<XWPFTableCell> cells = row.getTableCells();
        for (int i = 0; i < cells.size(); i++) {
            if (axis.equals(cells.get(i).getText().trim())) {
                setFirstBlankCellText(cells, i + 1, value);
                return;
            }
        }
        setFirstBlankCellText(cells, 1, value);
    }

    private void rewriteCoordinateLabelCell(XWPFTableRow row, String label) {
        if (row == null || label == null) {
            return;
        }
        String normalizedLabel = label.replaceAll("\\s+", "");
        for (XWPFTableCell cell : row.getTableCells()) {
            String cellText = cell.getText() == null ? "" : cell.getText().replaceAll("\\s+", "");
            if (cellText.contains(normalizedLabel)) {
                rewriteCenteredHeaderCell(cell, label);
                return;
            }
        }
    }

    private void centerCoordinateRows(XWPFTable table, int start) {
        for (int rowIndex = start; rowIndex < start + 3 && rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (XWPFTableCell cell : row.getTableCells()) {
                centerTableCell(cell);
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    paragraph.setIndentationLeft(0);
                    paragraph.setIndentationRight(0);
                    paragraph.setIndentationFirstLine(0);
                    paragraph.setSpacingBefore(0);
                    paragraph.setSpacingAfter(0);
                }
            }
        }
    }

    private void setFirstBlankCellText(List<XWPFTableCell> cells, int start, String value) {
        for (int i = Math.max(0, start); i < cells.size(); i++) {
            String text = cells.get(i).getText().trim();
            if (text.isEmpty()) {
                setCellText(cells.get(i), value);
                return;
            }
        }
    }

    private String valueOf(JSONObject object, String... keys) {
        if (object != null) {
            for (String key : keys) {
                String value = object.getString(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String coordinateValue(JSONObject record, JSONObject header, String prefix, String axis) {
        String value = valueOf(record, "coordinate" + axis, axis.toLowerCase());
        return value != null ? value : header.getString(prefix + axis);
    }
}
