package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.vo.Jglp05017Vo;
import edu.whut.cs.bi.biz.utils.ReportGenerateTools;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.EMPTY_CELL_PLACEHOLDER;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.cellDisplayValue;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.fillCellAfterLabel;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.formatSheetTitle;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getRowText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.getTableText;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.nvl;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.normalizeTextStyles;
import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * JGLP05017 桥梁结构桥梁技术状况检测记录表 Word 渲染器。
 *
 * <p>负责分页估算、模板填充与多页 DOCX 组装；数据查询由 {@link edu.whut.cs.bi.biz.service.ITaskSheetService} 完成。</p>
 */
@Slf4j
@Component
public class Jglp05017WordRenderer {

    public static final String SHEET_TITLE = "桥梁结构桥梁技术状况检测记录表";
    public static final String SHEET_NO = "JGLP05017";
    public static final String TEMPLATE_CLASSPATH = "word.biz/" + SHEET_TITLE + ".docx";

    /** 模板数据行探测失败时的兜底容量 */
    private static final int FALLBACK_PAGE_DISEASE_COUNT = 11;

    /** 各列按模板宽度估算的单行字符容量（全角字符计 1 个，半角计 0.5 个） */
    private static final int POSITION_CHARS_PER_LINE = 12;
    private static final int TYPE_CHARS_PER_LINE = 12;
    private static final int QUANTITY_CHARS_PER_LINE = 8;
    /**
     * 病害描述列单行估算字符数。
     * 偏大：行高估低 → 一页塞过多 → Word 页内自动分页；
     * 偏小：行高估高 → 提早换页、页内留白。
     */
    private static final int DESCRIPTION_CHARS_PER_LINE = 19;

    /** 分页高度预算单位：1 个模板空行约等价多少文本行 */
    private static final int LINES_PER_TEMPLATE_ROW = 2;

    /** 额外预留的模板行等价高度，防止估算偏差导致页内超出、被 Word 自动分页 */
    private static final int PAGE_SAFETY_TEMPLATE_ROWS = 1;

    /**
     * 将视图数据渲染为 DOCX 字节流。
     *
     * @param placement 预览写正文页码，下载写页眉页码
     */
    public byte[] render(Jglp05017Vo vo, WordSheetPoiUtils.PageNumberPlacement placement) {
        if (vo == null) {
            throw new ServiceException("JGLP05017 数据为空，无法生成 Word");
        }
        List<Disease> diseases = vo.getDiseases() != null ? vo.getDiseases() : new ArrayList<>();
        List<List<Disease>> pageDiseaseList = paginateDiseases(diseases);
        try {
            return WordSheetPoiUtils.buildMultiPageDocument(
                    TEMPLATE_CLASSPATH,
                    pageDiseaseList,
                    (document, pageDiseases, pageIndex, totalPages) -> {
                        boolean padWithBlankRows = pageIndex == totalPages - 1;
                        fillPageContent(document, vo, pageDiseases, pageIndex, padWithBlankRows);
                    },
                    placement);
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            throw new ServiceException("生成 JGLP05017 Word 文件失败：" + e.getMessage());
        }
    }

    /**
     * 供网页编辑页复用 Word 渲染分页规则，保证编辑页页码与预览/下载页码一致。
     */
    public List<List<Disease>> paginateForEdit(List<Disease> diseases) {
        return paginateDiseases(diseases);
    }

    private List<List<Disease>> paginateDiseases(List<Disease> diseases) {
        List<List<Disease>> pages = new ArrayList<>();
        int pageCapacity = detectDataRowCapacity();
        int pageHeightBudget = calculatePageHeightBudget(pageCapacity);
        if (diseases == null || diseases.isEmpty()) {
            pages.add(new ArrayList<>());
            return pages;
        }

        List<Disease> currentPage = new ArrayList<>();
        int currentHeight = 0;
        for (Disease disease : diseases) {
            int rowHeight = estimateRowHeight(disease);
            if (!currentPage.isEmpty()
                    && (currentPage.size() >= pageCapacity
                    || currentHeight + rowHeight > pageHeightBudget)) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                currentHeight = 0;
            }
            currentPage.add(disease);
            currentHeight += rowHeight;
        }
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }
        return pages;
    }

    private int calculatePageHeightBudget(int pageCapacity) {
        int usableTemplateRows = Math.max(1, pageCapacity - PAGE_SAFETY_TEMPLATE_ROWS);
        return usableTemplateRows * LINES_PER_TEMPLATE_ROW;
    }

    private int detectDataRowCapacity() {
        try (XWPFDocument template = WordSheetPoiUtils.loadTemplate(TEMPLATE_CLASSPATH)) {
            XWPFTable diseaseTable = findDiseaseTable(template);
            if (diseaseTable == null) {
                return FALLBACK_PAGE_DISEASE_COUNT;
            }
            int headerRowCount = detectHeaderRowCount(diseaseTable);
            int remarkRowIndex = findRemarkRowIndex(diseaseTable);
            int dataAreaEnd = (remarkRowIndex > headerRowCount) ? remarkRowIndex : diseaseTable.getRows().size();
            int capacity = dataAreaEnd - headerRowCount;
            return capacity > 0 ? capacity : FALLBACK_PAGE_DISEASE_COUNT;
        } catch (Exception e) {
            log.warn("探测 JGLP05017 模板数据行数失败，使用默认容量 {}", FALLBACK_PAGE_DISEASE_COUNT, e);
            return FALLBACK_PAGE_DISEASE_COUNT;
        }
    }

    private int estimateRowHeight(Disease disease) {
        return Math.max(1, estimateRowLines(disease));
    }

    private int estimateRowLines(Disease disease) {
        if (disease == null) {
            return 1;
        }
        String positionText = buildPositionText(disease);
        String qty = (disease.getQuantity() > 0 ? String.valueOf(disease.getQuantity()) : "")
                + (StringUtils.isNotEmpty(disease.getUnits()) ? disease.getUnits()
                : (disease.getQuantity() > 0 ? "处" : ""));
        String typeName = buildDiseaseTypeText(disease);
        String description = nvl(disease.getDescription());
        int lines = Math.max(estimateDisplayLines(positionText, POSITION_CHARS_PER_LINE),
                estimateDisplayLines(typeName, TYPE_CHARS_PER_LINE));
        lines = Math.max(lines, estimateDisplayLines(qty, QUANTITY_CHARS_PER_LINE));
        int descriptionLines = StringUtils.isEmpty(description)
                ? 2
                : estimateDisplayLines(description, DESCRIPTION_CHARS_PER_LINE);
        lines = Math.max(lines, descriptionLines);
        return lines;
    }

    private int estimateDisplayLines(String text, int charsPerLine) {
        if (StringUtils.isEmpty(text)) {
            return 1;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] physicalLines = normalized.split("\n", -1);
        int lines = 0;
        for (String line : physicalLines) {
            int weightedLength = estimateWeightedTextLength(line);
            if (weightedLength <= 0) {
                lines += 1;
            } else {
                lines += Math.max(1, (weightedLength + charsPerLine - 1) / charsPerLine);
            }
        }
        return lines;
    }

    private int estimateWeightedTextLength(String text) {
        if (StringUtils.isEmpty(text)) {
            return 0;
        }
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            length += c <= 0x007F ? 1 : 2;
        }
        return (int) Math.ceil(length / 2.0);
    }

    private void fillPageContent(XWPFDocument document, Jglp05017Vo vo, List<Disease> diseases,
                                 int pageIndex,
                                 boolean padWithBlankRows) {
        formatSheetTitle(document, SHEET_TITLE, SHEET_NO);
        fillInfoCells(document, vo, pageIndex);
        fillDiseaseTable(document, diseases, padWithBlankRows);
        fillRemark(document, vo, pageIndex);
        normalizeTextStyles(document);
        formatSheetTitle(document, SHEET_TITLE, SHEET_NO);
        centerDiseaseTableHeaders(document);
    }

    private void fillInfoCells(XWPFDocument document, Jglp05017Vo vo, int pageIndex) {
        JSONObject header = pageHeader(vo, pageIndex);
        String dateStr = headerValueOrDefault(header, "testDate",
                new SimpleDateFormat("yyyy-MM-dd").format(vo.getCheckDate()));
        for (XWPFTable table : document.getTables()) {
            String text = getTableText(table);
            if (text.contains("工程名称") || text.contains("检测单位")) {
                fillCellAfterLabel(table, "工程名称", headerValueOrDefault(header, "projectName", vo.getProjectName()));
                fillCellAfterLabel(table, "工程部位", headerValueOrDefault(header, "partUse", vo.getBuildingName()));
                fillCellAfterLabel(table, "记录编号", headerValueOrDefault(header, "recordNumber", String.valueOf(vo.getTaskId())));
                fillCellAfterLabel(table, "试验检测日期", dateStr);
                fillCellIfSaved(table, "检测单位名称", header, "inspectionUnitName");
                fillCellIfSaved(table, "试验室名称", header, "inspectionUnitName");
                fillCellIfSaved(table, "样品信息", header, "sampleInfo");
                fillCellIfSaved(table, "试验条件", header, "testCondition");
                fillCellFromHeaderOrTemplate(table, "检测依据", header, "inspectionBasis");
                fillCellFromHeaderOrTemplate(table, "判定依据", header, "judgementBasis");
                fillCellIfSaved(table, "主要仪器设备及编号", header, "equipment");
                break;
            }
        }
    }

    private JSONObject pageHeader(Jglp05017Vo vo, int pageIndex) {
        if (vo.getPageHeaders() != null && pageIndex >= 0 && pageIndex < vo.getPageHeaders().size()) {
            JSONObject pageHeader = vo.getPageHeaders().get(pageIndex);
            if (pageHeader != null && !pageHeader.isEmpty()) {
                return pageHeader;
            }
        }
        return vo.getHeader();
    }

    private void fillRemark(XWPFDocument document, Jglp05017Vo vo, int pageIndex) {
        if (vo.getPageRemarks() == null || pageIndex >= vo.getPageRemarks().size()) {
            return;
        }
        XWPFTable diseaseTable = findDiseaseTable(document);
        if (diseaseTable != null) {
            WordSheetPoiUtils.fillRemarkRow(diseaseTable, vo.getPageRemarks().get(pageIndex));
        }
    }

    private void fillCellIfSaved(XWPFTable table, String labelKeyword, JSONObject header, String key) {
        if (header != null && header.containsKey(key)) {
            fillCellAfterLabel(table, labelKeyword, nvl(header.getString(key)));
        }
    }

    private void fillCellFromHeaderOrTemplate(XWPFTable table, String labelKeyword, JSONObject header, String key) {
        String value = header != null && header.containsKey(key)
                ? nvl(header.getString(key))
                : WordSheetPoiUtils.readValueAfterLabel(table, labelKeyword);
        fillCellAfterLabel(table, labelKeyword, value);
    }

    private String headerValueOrDefault(JSONObject header, String key, String defaultValue) {
        if (header != null && header.containsKey(key)) {
            return nvl(header.getString(key));
        }
        return nvl(defaultValue);
    }

    private XWPFTable findDiseaseTable(XWPFDocument document) {
        return WordSheetPoiUtils.findTableContaining(document, "缺损位置");
    }

    private void fillDiseaseTable(XWPFDocument document, List<Disease> diseases, boolean padWithBlankRows) {
        XWPFTable diseaseTable = findDiseaseTable(document);
        if (diseaseTable == null) {
            log.warn("JGLP05017 模板中未找到包含「缺损位置」的病害表格，跳过填充");
            return;
        }
        if (diseases == null) {
            diseases = new ArrayList<>();
        }

        int headerRowCount = detectHeaderRowCount(diseaseTable);
        int remarkRowIndex = findRemarkRowIndex(diseaseTable);
        int dataAreaEnd = (remarkRowIndex > headerRowCount) ? remarkRowIndex : diseaseTable.getRows().size();
        int existingDataRows = dataAreaEnd - headerRowCount;
        int fillCount = Math.min(diseases.size(), existingDataRows);

        for (int i = 0; i < fillCount; i++) {
            fillDiseaseRow(diseaseTable.getRow(headerRowCount + i), diseases.get(i));
        }

        if (padWithBlankRows) {
            for (int i = headerRowCount + fillCount; i < dataAreaEnd; i++) {
                clearDiseaseRow(diseaseTable.getRow(i));
            }
        } else if (fillCount < existingDataRows) {
            for (int i = dataAreaEnd - 1; i >= headerRowCount + fillCount; i--) {
                diseaseTable.removeRow(i);
            }
        }

        removeRowsAfterRemark(diseaseTable);
        removeEmptyTablesAfter(document, diseaseTable);
    }

    private void clearDiseaseRow(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            setCellText(cell, EMPTY_CELL_PLACEHOLDER);
        }
    }

    private int findRemarkRowIndex(XWPFTable table) {
        for (int i = 0; i < table.getRows().size(); i++) {
            if (getRowText(table.getRow(i)).replaceAll("\\s+", "").contains("备注")) {
                return i;
            }
        }
        return -1;
    }

    private int detectHeaderRowCount(XWPFTable table) {
        int rowCount = table.getRows().size();
        for (int i = 0; i < rowCount; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("缺损数量") || rowText.contains("病害描述")
                    || rowText.contains("性质、范围") || rowText.contains("性质范围")) {
                return i + 1;
            }
        }
        for (int i = 0; i < rowCount; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("缺损情况")) {
                return Math.min(i + 2, rowCount);
            }
        }
        return 1;
    }

    private int findSubHeaderRowIndex(XWPFTable table) {
        int rowCount = table.getRows().size();
        for (int i = 0; i < rowCount; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("缺损数量") || rowText.contains("病害描述")
                    || rowText.contains("性质、范围") || rowText.contains("性质范围")) {
                return i;
            }
        }
        for (int i = 0; i < rowCount; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("缺损情况")) {
                return Math.min(i + 1, rowCount - 1);
            }
        }
        return -1;
    }

    private void centerDiseaseTableHeaders(XWPFDocument document) {
        XWPFTable table = findDiseaseTable(document);
        if (table == null) {
            return;
        }
        int headerRowCount = detectHeaderRowCount(table);
        for (int r = 0; r < headerRowCount; r++) {
            for (XWPFTableCell cell : table.getRow(r).getTableCells()) {
                String text = cell.getText().replaceAll("\\s+", "");
                if (text.contains("缺损位置")) {
                    WordSheetPoiUtils.rewriteCenteredHeaderCell(cell, "缺损位置");
                } else if (text.contains("缺损类型")) {
                    WordSheetPoiUtils.rewriteCenteredHeaderCell(cell, "缺损类型");
                }
            }
        }
        int subHeaderRow = findSubHeaderRowIndex(table);
        if (subHeaderRow >= 0) {
            List<XWPFTableCell> cells = table.getRow(subHeaderRow).getTableCells();
            for (int col = 0; col <= 1 && col < cells.size(); col++) {
                XWPFTableCell cell = cells.get(col);
                String text = cell.getText().replaceAll("\\s+", "");
                if (text.contains("缺损位置")) {
                    WordSheetPoiUtils.rewriteCenteredHeaderCell(cell, "缺损位置");
                } else if (text.contains("缺损类型")) {
                    WordSheetPoiUtils.rewriteCenteredHeaderCell(cell, "缺损类型");
                }
            }
        }
    }

    private void fillDiseaseRow(XWPFTableRow row, Disease d) {
        List<XWPFTableCell> cells = row.getTableCells();
        int n = cells.size();
        if (n < 4) {
            return;
        }

        String positionText = buildPositionText(d);
        String qty = (d.getQuantity() > 0 ? String.valueOf(d.getQuantity()) : "")
                + (StringUtils.isNotEmpty(d.getUnits()) ? d.getUnits()
                : (d.getQuantity() > 0 ? "处" : ""));
        String typeName = buildDiseaseTypeText(d);

        if (n >= 6) {
            setCellText(cells.get(0), cellDisplayValue(positionText));
            setCellText(cells.get(1), cellDisplayValue(typeName));
            setCellText(cells.get(2), cellDisplayValue(qty));
            setLongTextCell(cells.get(3), cellDisplayValue(nvl(d.getDescription())));
            setCellText(cells.get(4), d.getLevel() > 0 ? String.valueOf(d.getLevel()) : EMPTY_CELL_PLACEHOLDER);
            setCellText(cells.get(5), buildPhotoColumnText(d));
        } else if (n == 5) {
            setCellText(cells.get(0), cellDisplayValue(positionText));
            setCellText(cells.get(1), cellDisplayValue(typeName));
            String qtyDesc = qty.isEmpty() ? nvl(d.getDescription())
                    : qty + "  " + nvl(d.getDescription());
            setLongTextCell(cells.get(2), cellDisplayValue(qtyDesc));
            setCellText(cells.get(3), d.getLevel() > 0 ? String.valueOf(d.getLevel()) : EMPTY_CELL_PLACEHOLDER);
            setCellText(cells.get(4), buildPhotoColumnText(d));
        } else {
            setCellText(cells.get(0), cellDisplayValue(positionText));
            setCellText(cells.get(1), cellDisplayValue(typeName));
            if (n > 2) {
                setCellText(cells.get(2), cellDisplayValue(qty));
            }
            if (n > 3) {
                setLongTextCell(cells.get(3), cellDisplayValue(nvl(d.getDescription())));
            }
        }
        keepDiseaseRowTogether(row);
        compactDiseaseRow(row);
    }

    /**
     * 缺损位置显示：构件编号#构件名称。
     */
    private String buildPositionText(Disease disease) {
        if (disease == null) {
            return "";
        }
        edu.whut.cs.bi.biz.domain.Component component = disease.getComponent();
        if (component != null) {
            String componentName = StringUtils.trim(component.getName());
            if (StringUtils.isNotEmpty(componentName) && componentName.contains("#")) {
                return normalizePositionText(componentName);
            }
            String code = StringUtils.trim(component.getCode());
            if (StringUtils.isNotEmpty(code) && code.contains("#")) {
                return normalizePositionText(code);
            }
            String name = StringUtils.isNotEmpty(componentName)
                    ? componentName
                    : disease.getBiObjectName();
            if (StringUtils.isNotEmpty(code) && StringUtils.isNotEmpty(name)) {
                return code + "#" + name;
            }
            if (StringUtils.isNotEmpty(code)) {
                return code;
            }
            if (StringUtils.isNotEmpty(name)) {
                return name;
            }
        }
        if (StringUtils.isNotEmpty(disease.getBiObjectName())) {
            return normalizePositionText(disease.getBiObjectName());
        }
        return normalizePositionText(disease.getPosition());
    }

    private String buildDiseaseTypeText(Disease disease) {
        if (disease == null) {
            return "";
        }
        return nvl(disease.getType());
    }

    private String normalizePositionText(String text) {
        String trimmed = StringUtils.trim(text);
        if (StringUtils.isEmpty(trimmed) || !trimmed.contains("#")) {
            return nvl(trimmed);
        }
        String[] parts = trimmed.split("#", 2);
        String left = StringUtils.trim(parts[0]);
        String right = parts.length > 1 ? StringUtils.trim(parts[1]) : "";
        if (StringUtils.isNotEmpty(left) && StringUtils.isNotEmpty(right)
                && !isComponentCodeLike(left)
                && isComponentCodeLike(right)) {
            return right + "#" + left;
        }
        return trimmed;
    }

    private boolean isComponentCodeLike(String value) {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isNotEmpty(trimmed)
                && !trimmed.matches(".*[\\u4e00-\\u9fa5].*")
                && trimmed.matches("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*");
    }

    /** 照片/图片（编号/时间）列：有 imgNoExp 时展示，否则用「-」占位 */
    private String buildPhotoColumnText(Disease disease) {
        return cellDisplayValue(disease != null ? disease.getImgNoExp() : null);
    }

    private void setLongTextCell(XWPFTableCell cell, String text) {
        ParagraphAlignment alignment = EMPTY_CELL_PLACEHOLDER.equals(text)
                ? ParagraphAlignment.CENTER
                : ParagraphAlignment.LEFT;
        setCellText(cell, text, alignment, XWPFTableCell.XWPFVertAlign.CENTER);
    }

    private void keepDiseaseRowTogether(XWPFTableRow row) {
        CTTrPr trPr = row.getCtRow().getTrPr();
        if (trPr == null) {
            trPr = row.getCtRow().addNewTrPr();
        }
        trPr.addNewCantSplit();
    }

    private void compactDiseaseRow(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : cell.getParagraphs()) {
                paragraph.setSpacingBefore(0);
                paragraph.setSpacingAfter(0);
                ReportGenerateTools.setSingleLineSpacing(paragraph);
            }
        }
    }

    private void removeRowsAfterRemark(XWPFTable table) {
        int remarkRowIndex = -1;
        for (int i = 0; i < table.getRows().size(); i++) {
            if (getRowText(table.getRow(i)).replaceAll("\\s+", "").contains("备注")) {
                remarkRowIndex = i;
                break;
            }
        }
        if (remarkRowIndex < 0) {
            return;
        }
        for (int i = table.getRows().size() - 1; i > remarkRowIndex; i--) {
            String rowText = getRowText(table.getRow(i)).trim();
            if (rowText.isEmpty()) {
                table.removeRow(i);
            }
        }
    }

    private void removeEmptyTablesAfter(XWPFDocument document, XWPFTable anchorTable) {
        int anchorIndex = document.getPosOfTable(anchorTable);
        for (int i = document.getTables().size() - 1; i >= 0; i--) {
            XWPFTable table = document.getTables().get(i);
            int tableIndex = document.getPosOfTable(table);
            if (tableIndex > anchorIndex && getTableText(table).trim().isEmpty()) {
                document.removeBodyElement(tableIndex);
            }
        }
    }
}
