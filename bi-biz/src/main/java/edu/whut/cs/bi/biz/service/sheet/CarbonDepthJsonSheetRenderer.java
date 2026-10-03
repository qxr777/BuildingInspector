package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.*;

/**
 * 碳化深度检测记录表（JGLP02002）JSON → Word 渲染。
 */
@Component
public class CarbonDepthJsonSheetRenderer implements JsonSheetWordRenderer {

    public static final String SHEET_TYPE = "carbon_depth";
    private static final String TEMPLATE = "word.biz/碳化深度检测记录表.docx";

    @Override
    public String sheetType() {
        return SHEET_TYPE;
    }

    @Override
    public String templateClasspath() {
        return TEMPLATE;
    }

    @Override
    public String defaultDownloadBaseName() {
        return "碳化深度检测记录表";
    }

    @Override
    public byte[] render(JSONObject sheetJson) {
        return render(sheetJson, WordSheetPoiUtils.PageNumberPlacement.BODY);
    }

    @Override
    public byte[] render(JSONObject sheetJson, WordSheetPoiUtils.PageNumberPlacement pageNumberPlacement) {
        JSONArray pages = sheetJson != null ? sheetJson.getJSONArray("pages") : null;
        List<JSONObject> pageList = new ArrayList<>();
        if (pages != null) {
            for (int i = 0; i < pages.size(); i++) {
                pageList.add(pages.getJSONObject(i));
            }
        }
        return WordSheetPoiUtils.buildMultiPageDocument(this, pageList, pageNumberPlacement);
    }

    @Override
    public void fillPage(XWPFDocument document, JSONObject pageJson) {
        JSONObject header = pageJson != null ? pageJson.getJSONObject("header") : null;
        if (header == null) {
            header = new JSONObject();
        }
        formatSheetTitle(document, "碳化深度检测记录表", "JGLP02002");
        fillHeaderParagraphs(document, header);
        fillInfoTable(document, header);
        fillRecordsTable(document, pageJson);
        normalizeTextStyles(document);
        formatSheetTitle(document, "碳化深度检测记录表", "JGLP02002");
    }

    private void fillHeaderParagraphs(XWPFDocument document, JSONObject header) {
        for (XWPFParagraph para : document.getParagraphs()) {
            String text = para.getText();
            if (text == null || (!text.contains("检测单位名称")
                    && !text.contains("试验室名称")
                    && !text.contains("记录编号"))) {
                continue;
            }
            String unit = cellDisplayValue(header.getString("inspectionUnitName"));
            String recordNo = cellDisplayValue(header.getString("recordNumber"));
            replaceParagraphText(para,
                    resolveUnitLabel(text) + unit + "                               记录编号：" + recordNo);
            return;
        }
    }

    private String resolveUnitLabel(String paragraphText) {
        return paragraphText != null && paragraphText.contains("试验室名称")
                ? "试验室名称：" : "检测单位名称：";
    }

    private void fillInfoTable(XWPFDocument document, JSONObject header) {
        XWPFTable table = findTableContaining(document, "工程名称");
        if (table == null) {
            return;
        }
        fillCellAfterLabel(table, "工程名称", header.getString("projectName"));
        fillCellAfterLabel(table, "工程部位/用途", header.getString("partUse"));
        fillCellAfterLabel(table, "样品信息", header.getString("sampleInfo"));
        fillCellAfterLabel(table, "试验检测日期", header.getString("testDate"));
        fillCellAfterLabel(table, "试验条件", header.getString("testCondition"));
        fillCellAfterLabel(table, "测试工况", header.getString("workCondition"));
        fillCellAfterLabelIfBlank(table, "检测依据", header.getString("inspectionBasis"));
        fillCellAfterLabelIfBlank(table, "判定依据", header.getString("judgementBasis"));
        fillCellAfterLabel(table, "主要仪器设备名称及编号", header.getString("equipment"));
    }

    private void fillCellAfterLabelIfBlank(XWPFTable table, String labelKeyword, String value) {
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).getText().contains(labelKeyword)) {
                    if (labelCellHasExistingValue(cells.get(i), labelKeyword)) {
                        return;
                    }
                    int end = findNextInfoLabelIndex(cells, i + 1);
                    if (i + 1 < end && isBlankRange(cells, i + 1, end)) {
                        setCellText(cells.get(i + 1), cellDisplayValue(value));
                    }
                    return;
                }
            }
        }
    }

    private boolean labelCellHasExistingValue(XWPFTableCell cell, String labelKeyword) {
        String normalizedText = cell.getText().replaceAll("\\s+", "");
        String normalizedLabel = labelKeyword.replaceAll("\\s+", "");
        String remaining = normalizedText
                .replace(normalizedLabel, "")
                .replace("：", "")
                .replace(":", "");
        return !remaining.isEmpty();
    }

    private int findNextInfoLabelIndex(List<XWPFTableCell> cells, int start) {
        List<String> labels = java.util.Arrays.asList(
                "工程名称", "工程部位/用途", "工程部位", "样品信息", "试验检测日期",
                "试验条件", "测试工况", "检测依据", "判定依据", "主要仪器设备名称及编号");
        for (int i = start; i < cells.size(); i++) {
            String text = cells.get(i).getText();
            for (String label : labels) {
                if (text.contains(label)) {
                    return i;
                }
            }
        }
        return cells.size();
    }

    private boolean isBlankRange(List<XWPFTableCell> cells, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!cells.get(i).getText().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void fillRecordsTable(XWPFDocument document, JSONObject pageJson) {
        XWPFTable table = findTableContaining(document, "构件名称");
        if (table == null) {
            return;
        }
        int headerRowCount = findHeaderRowCountBySubHeader(table, "测点", "测值");
        int remarkRowIndex = findRemarkRowIndex(table);

        JSONArray records = pageJson != null ? pageJson.getJSONArray("records") : null;
        List<JSONObject> recordList = new ArrayList<>();
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                recordList.add(records.getJSONObject(i));
            }
        }

        fillCarbonRowsWithoutRemovingTemplateRows(table, headerRowCount, remarkRowIndex, recordList);

        fillRemarkRow(table, pageJson != null ? pageJson.getString("remark") : null);
    }

    private void fillCarbonRowsWithoutRemovingTemplateRows(XWPFTable table, int headerRowCount, int remarkRowIndex,
                                                           List<JSONObject> records) {
        int end = remarkRowIndex > headerRowCount ? remarkRowIndex : table.getRows().size();
        int recordIndex = 0;
        for (int rowIndex = headerRowCount; rowIndex < end && recordIndex < records.size(); rowIndex++) {
            while (recordIndex < records.size() && !hasCarbonData(records.get(recordIndex))) {
                recordIndex++;
            }
            if (recordIndex >= records.size()) {
                break;
            }
            JSONObject record = records.get(recordIndex);
            XWPFTableRow row = table.getRow(rowIndex);
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.size() >= 6) {
                setCellText(cells.get(0), cellDisplayValue(record.getString("componentName")));
                setCellText(cells.get(1), cellDisplayValue(record.getString("point")));
                setCellText(cells.get(2), cellDisplayValue(record.getString("value1")));
                setCellText(cells.get(3), cellDisplayValue(record.getString("value2")));
                setCellText(cells.get(4), cellDisplayValue(record.getString("value3")));
                setCellText(cells.get(5), cellDisplayValue(record.getString("average")));
            }
            recordIndex++;
        }
    }

    private boolean hasCarbonData(JSONObject record) {
        return record != null
                && (!nvl(record.getString("componentName")).isEmpty()
                || !nvl(record.getString("point")).isEmpty()
                || !nvl(record.getString("value1")).isEmpty()
                || !nvl(record.getString("value2")).isEmpty()
                || !nvl(record.getString("value3")).isEmpty()
                || !nvl(record.getString("average")).isEmpty());
    }
}
