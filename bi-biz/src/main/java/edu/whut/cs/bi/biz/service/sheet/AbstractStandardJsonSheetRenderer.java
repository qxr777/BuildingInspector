package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.fillCellAfterLabel;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.fillRemarkRow;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.findHeaderRowCountBySubHeader;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.findRemarkRowIndex;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.formatSheetTitle;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getRowText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getTableText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.normalizeTextStyles;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.nvl;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.replaceParagraphText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 标准 JSON 检测记录表渲染器。
 *
 * <p>沿用碳化深度检测记录表的数据包络：pages/header/records/remark。
 * 各表只配置 type、模板、表名、表号和数据字段顺序。</p>
 */
public abstract class AbstractStandardJsonSheetRenderer implements JsonSheetWordRenderer {

    private static final List<String> DEFAULT_DATA_TABLE_KEYWORDS = Arrays.asList("测点", "测值", "读数", "实测值", "检测值");
    private static final List<String> DEFAULT_HEADER_ROW_KEYWORDS = Arrays.asList("测点", "测值", "读数", "实测值", "检测值");
    private static final List<String> ARRAY_VALUE_KEYS = Arrays.asList("values", "cellValues", "columns", "data");
    private static final List<String> NON_DATA_ROW_KEYWORDS = Arrays.asList(
            "工程名称", "工程部位", "样品信息", "试验检测日期", "检测日期", "试验条件",
            "检测依据", "判定依据", "主要仪器设备", "仪器设备", "测试工况",
            "测站坐标", "基准点高程", "检测：", "记录：", "复核：", "日期：", "备注");

    private final String sheetType;
    private final String templateClasspath;
    private final String defaultDownloadBaseName;
    private final String titleKeyword;
    private final String sheetNo;
    private final List<String> dataTableKeywords;
    private final List<String> headerRowKeywords;
    private final List<String> recordFields;

    protected AbstractStandardJsonSheetRenderer(String sheetType,
                                                String templateFileName,
                                                String defaultDownloadBaseName,
                                                String titleKeyword,
                                                String sheetNo,
                                                List<String> dataTableKeywords,
                                                List<String> headerRowKeywords,
                                                List<String> recordFields) {
        this.sheetType = sheetType;
        this.templateClasspath = "word.biz/" + templateFileName;
        this.defaultDownloadBaseName = defaultDownloadBaseName;
        this.titleKeyword = titleKeyword;
        this.sheetNo = sheetNo;
        this.dataTableKeywords = dataTableKeywords == null || dataTableKeywords.isEmpty()
                ? DEFAULT_DATA_TABLE_KEYWORDS : dataTableKeywords;
        this.headerRowKeywords = headerRowKeywords == null || headerRowKeywords.isEmpty()
                ? DEFAULT_HEADER_ROW_KEYWORDS : headerRowKeywords;
        this.recordFields = recordFields == null ? Collections.emptyList() : recordFields;
    }

    @Override
    public String sheetType() {
        return sheetType;
    }

    @Override
    public String templateClasspath() {
        return templateClasspath;
    }

    @Override
    public String defaultDownloadBaseName() {
        return defaultDownloadBaseName;
    }

    @Override
    public byte[] render(JSONObject sheetJson) {
        return render(sheetJson, WordSheetPoiUtils.PageNumberPlacement.BODY);
    }

    @Override
    public byte[] render(JSONObject sheetJson, WordSheetPoiUtils.PageNumberPlacement pageNumberPlacement) {
        return WordSheetPoiUtils.buildMultiPageDocument(this, toPages(sheetJson), pageNumberPlacement);
    }

    @Override
    public void fillPage(XWPFDocument document, JSONObject pageJson) {
        JSONObject header = pageJson != null ? pageJson.getJSONObject("header") : null;
        if (header == null) {
            header = new JSONObject();
        }

        formatSheetTitle(document, titleKeyword, sheetNo);
        fillHeaderParagraphs(document, header);
        fillInfoTable(document, header);
        fillRecordsTable(document, pageJson);
        normalizeTextStyles(document);
        formatSheetTitle(document, titleKeyword, sheetNo);
    }

    private List<JSONObject> toPages(JSONObject sheetJson) {
        JSONArray pages = sheetJson != null ? sheetJson.getJSONArray("pages") : null;
        List<JSONObject> pageList = new ArrayList<>();
        if (pages != null) {
            for (int i = 0; i < pages.size(); i++) {
                pageList.add(pages.getJSONObject(i));
            }
        }
        return pageList;
    }

    protected void fillHeaderParagraphs(XWPFDocument document, JSONObject header) {
        document.getParagraphs().stream()
                .filter(para -> para.getText() != null
                        && (para.getText().contains("检测单位名称")
                        || para.getText().contains("试验室名称")
                        || para.getText().contains("记录编号")))
                .findFirst()
                .ifPresent(para -> replaceParagraphText(para,
                        resolveUnitLabel(para.getText()) + nvl(header.getString("inspectionUnitName"))
                                + "                               记录编号：" + nvl(header.getString("recordNumber"))));
    }

    private String resolveUnitLabel(String paragraphText) {
        return paragraphText != null && paragraphText.contains("试验室名称")
                ? "试验室名称：" : "检测单位名称：";
    }

    protected void fillInfoTable(XWPFDocument document, JSONObject header) {
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "工程名称");
        if (table == null) {
            return;
        }
        fillCellAfterLabel(table, "工程名称", header.getString("projectName"));
        fillCellAfterLabel(table, "工程部位/用途", header.getString("partUse"));
        fillCellAfterLabel(table, "工程部位", header.getString("partUse"));
        fillCellAfterLabel(table, "样品信息", header.getString("sampleInfo"));
        fillCellAfterLabel(table, "试验检测日期", header.getString("testDate"));
        fillCellAfterLabel(table, "检测日期", header.getString("testDate"));
        fillCellAfterLabel(table, "试验条件", header.getString("testCondition"));
        fillCellAfterLabel(table, "测试工况", header.getString("workCondition"));
        fillCellAfterLabelIfBlank(table, "检测依据", header.getString("inspectionBasis"));
        fillCellAfterLabelIfBlank(table, "判定依据", header.getString("judgementBasis"));
        fillCellAfterLabel(table, "主要仪器设备名称及编号", header.getString("equipment"));
        fillCellAfterLabel(table, "仪器设备", header.getString("equipment"));
    }

    private void fillCellAfterLabelIfBlank(XWPFTable table, String labelKeyword, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).getText().contains(labelKeyword)) {
                    if (labelCellHasExistingValue(cells.get(i), labelKeyword)) {
                        return;
                    }
                    int end = findNextInfoLabelIndex(cells, i + 1);
                    if (i + 1 < end && isBlankRange(cells, i + 1, end)) {
                        setCellText(cells.get(i + 1), value);
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
        List<String> labels = Arrays.asList(
                "工程名称", "工程部位/用途", "工程部位", "样品信息", "试验检测日期", "检测日期",
                "试验条件", "测试工况", "检测依据", "判定依据", "主要仪器设备名称及编号", "仪器设备");
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
        XWPFTable table = findRecordsTable(document);
        if (table == null) {
            return;
        }

        int headerRowCount = findHeaderRowCountBySubHeader(table, headerRowKeywords.toArray(new String[0]));
        int remarkRowIndex = findRemarkRowIndex(table);
        fillDataRowsWithoutChangingTemplateRows(table, headerRowCount, remarkRowIndex, toRecords(pageJson));
        fillRemarkRow(table, pageJson != null ? pageJson.getString("remark") : null);
    }

    private XWPFTable findRecordsTable(XWPFDocument document) {
        for (String keyword : dataTableKeywords) {
            XWPFTable table = WordSheetPoiUtils.findTableContaining(document, keyword);
            if (table != null && !getTableText(table).contains("工程名称")) {
                return table;
            }
        }
        List<XWPFTable> tables = document.getTables();
        return tables.isEmpty() ? null : tables.get(tables.size() - 1);
    }

    protected List<JSONObject> toRecords(JSONObject pageJson) {
        JSONArray records = pageJson != null ? pageJson.getJSONArray("records") : null;
        List<JSONObject> recordList = new ArrayList<>();
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                recordList.add(records.getJSONObject(i));
            }
        }
        return recordList;
    }

    private void fillDataRowsWithoutChangingTemplateRows(XWPFTable table, int headerRowCount, int remarkRowIndex,
                                                         List<JSONObject> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        int end = remarkRowIndex > headerRowCount ? remarkRowIndex : table.getRows().size();
        int recordIndex = 0;
        for (int rowIndex = headerRowCount; rowIndex < end && recordIndex < records.size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            if (!isDataRow(row)) {
                continue;
            }
            fillRecordRow(row, records.get(recordIndex));
            recordIndex++;
        }
    }

    private boolean isDataRow(XWPFTableRow row) {
        if (row == null || row.getTableCells().size() < minDataCellCount()) {
            return false;
        }
        String rowText = getRowText(row).replaceAll("\\s+", "");
        if (rowText.isEmpty()) {
            return true;
        }
        for (String keyword : NON_DATA_ROW_KEYWORDS) {
            if (rowText.contains(keyword.replaceAll("\\s+", ""))) {
                return false;
            }
        }
        return true;
    }

    protected int minDataCellCount() {
        return Math.max(recordFields.size(), 1);
    }

    private void fillRecordRow(XWPFTableRow row, JSONObject record) {
        List<XWPFTableCell> cells = row.getTableCells();
        for (int i = 0; i < cells.size(); i++) {
            String value = valueAt(record, i);
            if (value != null) {
                setCellText(cells.get(i), value);
            }
        }
    }

    protected String valueAt(JSONObject record, int index) {
        if (record == null) {
            return "";
        }
        if (index < recordFields.size()) {
            String value = record.getString(recordFields.get(index));
            if (value != null) {
                return value;
            }
        }
        for (String key : ARRAY_VALUE_KEYS) {
            JSONArray values = record.getJSONArray(key);
            if (values != null && index < values.size()) {
                Object value = values.get(index);
                return value == null ? "" : String.valueOf(value);
            }
        }
        return "";
    }
}
