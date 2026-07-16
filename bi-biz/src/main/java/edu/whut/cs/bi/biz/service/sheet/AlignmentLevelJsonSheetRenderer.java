package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.formatSheetTitle;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.fillCellAfterLabel;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getRowText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.normalizeTextStyles;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 桥梁结构结构线形测量记录表（水准仪法，JGLP05009a-2）JSON → Word 渲染。
 */
@Component
public class AlignmentLevelJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    private static final String TITLE = "桥梁结构结构线形测量记录表";
    private static final String SHEET_NO = "JGLP05009a-2";

    public AlignmentLevelJsonSheetRenderer() {
        super("alignment_level",
                "桥梁结构结构线形测量记录表（水准仪法）.docx",
                "桥梁结构结构线形测量记录表（水准仪法）",
                TITLE,
                SHEET_NO,
                Arrays.asList("测点", "后视", "前视", "高程"),
                Arrays.asList("测点", "后视", "前视", "高程"),
                Arrays.asList("station", "point", "backSightReading", "foreSightReading", "elevation", "designElevation", "deviation", "note"));
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
        fillBenchmarkElevation(document, header);
        fillLevelRows(document, pageJson);
        normalizeTextStyles(document);
        formatSheetTitle(document, TITLE, SHEET_NO);
    }

    private void fillBenchmarkElevation(XWPFDocument document, JSONObject header) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "基准点高程");
        if (table == null) {
            return;
        }
        fillCellAfterLabel(table, "基准点高程", valueOf(header, "basePointElevation", "benchmarkElevation"));
    }

    private void fillLevelRows(XWPFDocument document, JSONObject pageJson) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "后视读数");
        if (table == null) {
            return;
        }
        int headerRowIndex = findLevelHeaderRow(table);
        if (headerRowIndex < 0) {
            return;
        }
        List<JSONObject> records = toRecords(pageJson);
        int recordIndex = 0;
        for (int i = headerRowIndex + 1; i < table.getRows().size() && recordIndex < records.size(); i++) {
            XWPFTableRow row = table.getRow(i);
            String rowText = getRowText(row).replaceAll("\\s+", "");
            if (rowText.contains("备注") || rowText.contains("检测：")) {
                break;
            }
            if (row.getTableCells().size() < 7) {
                continue;
            }
            fillLevelRow(row, records.get(recordIndex));
            recordIndex++;
        }
        fillLevelRemarkRow(table, pageJson != null ? pageJson.getString("remark") : null);
    }

    private int findLevelHeaderRow(XWPFTable table) {
        for (int i = 0; i < table.getRows().size(); i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("测站") && rowText.contains("测点编号")
                    && rowText.contains("后视读数") && rowText.contains("前视读数")) {
                if (i + 1 < table.getRows().size()) {
                    String nextRowText = getRowText(table.getRow(i + 1)).replaceAll("\\s+", "");
                    if (nextRowText.contains("测量值") || nextRowText.contains("修正值")
                            || nextRowText.contains("修正后高程")) {
                        return i + 1;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private void fillLevelRow(XWPFTableRow row, JSONObject record) {
        List<XWPFTableCell> cells = row.getTableCells();
        setCellText(cells.get(0), record.getString("station"));
        setCellText(cells.get(1), valueOf(record, "pointNumber", "point"));
        setCellText(cells.get(2), record.getString("backSightReading"));
        setCellText(cells.get(3), record.getString("foreSightReading"));
        setCellText(cells.get(4), valueOf(record, "measuredElevation", "elevation"));
        setCellText(cells.get(5), valueOf(record, "correction", "designElevation"));
        setCellText(cells.get(6), valueOf(record, "correctedElevation", "deviation"));
        if (cells.size() > 7) {
            setCellText(cells.get(7), record.getString("note"));
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

    private void fillLevelRemarkRow(XWPFTable table, String remark) {
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.isEmpty()) {
                continue;
            }
            String firstCellText = cells.get(0).getText().replaceAll("\\s+", "");
            String rowText = getRowText(row).replaceAll("\\s+", "");
            if (!firstCellText.startsWith("备注") || rowText.contains("测站") || rowText.contains("测点编号")) {
                continue;
            }
            if (cells.size() > 1) {
                setCellText(cells.get(1), WordSheetPoiUtils.cellDisplayValue(remark),
                        ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
            } else {
                if (remark == null || remark.trim().isEmpty()) {
                    WordSheetPoiUtils.fillRemarkPlaceholderCells(row);
                } else {
                    setCellText(cells.get(0), "备注：" + remark,
                            ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
                }
            }
            return;
        }
    }
}
