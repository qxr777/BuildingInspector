package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Arrays;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.findHeaderRowCountBySubHeader;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.findRemarkRowIndex;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.formatSheetTitle;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getTableText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.normalizeTextStyles;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 钢筋锈蚀电位检测记录表（JGLP02008）JSON → Word 渲染。
 */
@Component
public class RebarCorrosionJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    private static final String TITLE = "钢筋锈蚀电位检测记录表";
    private static final String SHEET_NO = "JGLP02008";

    public RebarCorrosionJsonSheetRenderer() {
        super("rebar_corrosion",
                "钢筋锈蚀电位检测记录表.docx",
                TITLE,
                TITLE,
                SHEET_NO,
                Arrays.asList("检测部位", "测点电位值", "构件名称", "测区编号"),
                Arrays.asList("构件名称", "测区编号"),
                Arrays.asList("componentName", "point",
                        "value1", "value2", "value3", "value4", "value5",
                        "value6", "value7", "value8", "value9", "value10",
                        "temperature"));
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
        fillCorrosionRows(document, pageJson);
        normalizeTextStyles(document);
        formatSheetTitle(document, TITLE, SHEET_NO);
    }

    private void fillCorrosionRows(XWPFDocument document, JSONObject pageJson) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "测点电位值");
        if (table == null) {
            return;
        }
        List<JSONObject> records = toRecords(pageJson);
        int headerRowCount = findHeaderRowCountBySubHeader(table, "构件名称", "测区编号");
        int remarkRowIndex = findRemarkRowIndex(table);
        int end = remarkRowIndex > headerRowCount ? remarkRowIndex : table.getRows().size();
        for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
            int rowIndex = headerRowCount + recordIndex * 2;
            if (rowIndex >= end) {
                break;
            }
            fillCorrosionRow(table.getRow(rowIndex), records.get(recordIndex), 0);
            if (rowIndex + 1 < end) {
                fillCorrosionRow(table.getRow(rowIndex + 1), records.get(recordIndex), 10);
            }
        }
        fillRemarkRows(document, pageJson != null ? pageJson.getString("remark") : null);
    }

    private void fillRemarkRows(XWPFDocument document, String remark) {
        if (remark == null || remark.isEmpty()) {
            return;
        }
        for (XWPFTable table : document.getTables()) {
            String tableText = getTableText(table);
            if (tableText.contains("备注") || tableText.contains("测点布置示意图")) {
                fillCorrosionRemarkRow(table, remark);
            }
        }
    }

    private void fillCorrosionRemarkRow(XWPFTable table, String remark) {
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (XWPFTableCell cell : cells) {
                String text = cell.getText().replaceAll("\\s+", "");
                if (text.contains("备注")) {
                    setCellText(cell, "备注：" + remark,
                            ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
                    return;
                }
                if (text.contains("测点布置示意图")) {
                    setCellText(cell, "测点布置示意图：" + remark,
                            ParagraphAlignment.LEFT, XWPFTableCell.XWPFVertAlign.TOP);
                    return;
                }
            }
        }
    }

    private void fillCorrosionRow(XWPFTableRow row, JSONObject record, int valueOffset) {
        List<XWPFTableCell> cells = row.getTableCells();
        if (valueOffset == 0) {
            setCell(cells, 0, record.getString("componentName"));
            setCell(cells, 1, valueOf(record, "areaNumber", "point"));
        }
        for (int i = 1; i <= 10; i++) {
            int valueIndex = valueOffset + i;
            setCell(cells, i + 1, valueOf(record, "potentialValue" + valueIndex, "value" + valueIndex));
        }
        if (valueOffset == 0) {
            setCell(cells, 12, record.getString("temperature"));
        }
    }

    private void setCell(List<XWPFTableCell> cells, int index, String value) {
        if (value != null && !value.isEmpty() && index >= 0 && index < cells.size()) {
            setCellText(cells.get(index), value);
        }
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
}
