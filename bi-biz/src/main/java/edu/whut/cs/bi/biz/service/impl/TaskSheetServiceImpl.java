package edu.whut.cs.bi.biz.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.TaskSheet;
import edu.whut.cs.bi.biz.domain.vo.Jglp05017Vo;
import edu.whut.cs.bi.biz.domain.vo.TaskSheetStatusVo;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.TaskSheetMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.service.ITaskSheetService;
import edu.whut.cs.bi.biz.utils.ReportGenerateTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务表格数据 Service 实现
 */
@Slf4j
@Service
public class TaskSheetServiceImpl implements ITaskSheetService {

    private static final int JGLP05017_PAGE_DISEASE_COUNT = 15;

    @Autowired
    private TaskSheetMapper taskSheetMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private DiseaseMapper diseaseMapper;

    // ─────────────────── 通用表格数据方法 ───────────────────────────────

    @Override
    public List<TaskSheetStatusVo> listSheetStatusByTaskId(Long taskId) {
        // 从字典获取所有表格类型，按 dictSort 升序
        List<SysDictData> dictList = DictUtils.getDictCache(SHEET_DICT_TYPE);
        if (dictList == null) {
            dictList = new ArrayList<>();
        }
        dictList.sort(Comparator.comparing(SysDictData::getDictSort, Long::compareTo));

        // 已提交记录按 type 建索引
        List<TaskSheet> submitted = taskSheetMapper.selectListByTaskId(taskId);
        Map<String, TaskSheet> submittedMap = submitted.stream()
                .collect(Collectors.toMap(TaskSheet::getType, Function.identity(), (a, b) -> a));

        List<TaskSheetStatusVo> result = new ArrayList<>();
        for (SysDictData dict : dictList) {
            TaskSheetStatusVo vo = new TaskSheetStatusVo();
            vo.setType(dict.getDictValue());
            vo.setSheetName(dict.getDictLabel());
            TaskSheet ts = submittedMap.get(dict.getDictValue());
            if (ts != null) {
                vo.setSubmitted(true);
                vo.setTaskSheetId(ts.getId());
                vo.setSheetMinioId(ts.getSheetMinioId());
            } else {
                vo.setSubmitted(false);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public String getSheetJsonContent(Long taskId, String type) {
        TaskSheet taskSheet = taskSheetMapper.selectByTaskIdAndType(taskId, type);
        if (taskSheet == null || taskSheet.getSheetMinioId() == null) {
            return null;
        }
        byte[] bytes = fileMapService.handleFileDownload(taskSheet.getSheetMinioId());
        if (bytes == null) {
            return null;
        }
        try {
            String raw = new String(bytes, StandardCharsets.UTF_8);
            Object obj = JSON.parse(raw);
            return JSON.toJSONString(obj, SerializerFeature.PrettyFormat);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateSheet(Long taskId, Long buildingId, String type,
                                  byte[] jsonBytes, String fileName) {
        String mimeType = "application/json";
        MultipartFile mockFile = new MockMultipartFile("file", fileName, mimeType, jsonBytes);
        FileMap newFileMap = fileMapService.handleFileUpload(mockFile);
        if (newFileMap == null) {
            throw new ServiceException("表格文件上传 MinIO 失败，type=" + type);
        }
        Long newMinioId = Long.valueOf(newFileMap.getId());

        TaskSheet existing = taskSheetMapper.selectByTaskIdAndType(taskId, type);
        if (existing != null) {
            // 删除旧文件（失败不阻断主流程）
            if (existing.getSheetMinioId() != null) {
                try {
                    fileMapService.deleteFileMapById(existing.getSheetMinioId());
                } catch (Exception ignored) {
                    log.warn("删除旧 MinIO 文件失败，id={}", existing.getSheetMinioId());
                }
            }
            existing.setSheetMinioId(newMinioId);
            taskSheetMapper.updateSheetMinioId(existing);
        } else {
            TaskSheet ts = new TaskSheet();
            ts.setTaskId(taskId);
            ts.setBuildingId(buildingId);
            ts.setType(type);
            ts.setSheetMinioId(newMinioId);
            taskSheetMapper.insert(ts);
        }
    }

    // ─────────────────── JGLP05017 相关方法 ────────────────────────────

    @Override
    public Jglp05017Vo getJglp05017Data(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("未找到检测任务，taskId=" + taskId);
        }
        Disease query = new Disease();
        query.setBuildingId(task.getBuildingId());
        query.setProjectId(task.getProjectId());
        List<Disease> rawList = diseaseMapper.selectDiseaseList(query);

        // JOIN 可能产生同一 id 的重复行，按 id 去重并保持顺序
        java.util.LinkedHashMap<Long, Disease> uniqueMap = new java.util.LinkedHashMap<>();
        for (Disease d : rawList) {
            if (d.getId() != null) uniqueMap.putIfAbsent(d.getId(), d);
        }
        List<Disease> diseases = new ArrayList<>(uniqueMap.values());
        log.info("JGLP05017 taskId={} 病害查询: 原始={} 去重后={}", taskId, rawList.size(), diseases.size());

        // 试验检测日期：取该任务下病害数据上传/更新的最新时间
        Date latestUploadDate = diseases.stream()
                .map(d -> d.getUpdateTime() != null ? d.getUpdateTime() : d.getCreateTime())
                .filter(d -> d != null)
                .max(Date::compareTo)
                .orElse(new Date());

        Jglp05017Vo vo = new Jglp05017Vo();
        vo.setTaskId(taskId);
        vo.setProjectName(task.getProject() != null ? task.getProject().getName() : "");
        vo.setBuildingName(task.getBuilding() != null ? task.getBuilding().getName() : "");
        vo.setBuildingId(task.getBuildingId());
        vo.setCheckDate(latestUploadDate);
        vo.setDiseases(diseases);
        return vo;
    }

    @Override
    public boolean hasDiseaseDataByTaskId(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        return task != null && Integer.valueOf(1).equals(task.getType());
    }

    @Override
    public byte[] generateJglp05017WordBytes(Long taskId) {
        return buildDocxBytes(getJglp05017Data(taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public byte[] generateAndSaveJglp05017Word(Long taskId) {
        Jglp05017Vo vo = getJglp05017Data(taskId);
        byte[] bytes = buildDocxBytes(vo);

        String docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        String fileName = "JGLP05017_" + taskId + ".docx";
        MultipartFile mockFile = new MockMultipartFile("file", fileName, docxMime, bytes);
        FileMap newFileMap;
        try {
            newFileMap = fileMapService.handleFileUpload(mockFile);
        } catch (Exception ex) {
            throw new ServiceException("JGLP05017 Word 文件上传 MinIO 失败：" + ex.getMessage());
        }
        Long newMinioId = Long.valueOf(newFileMap.getId());

        TaskSheet existing = taskSheetMapper.selectByTaskIdAndType(taskId, "technical_condition");
        if (existing != null) {
            try { fileMapService.deleteFileMapById(existing.getSheetMinioId()); } catch (Exception ignored) {}
            existing.setSheetMinioId(newMinioId);
            taskSheetMapper.updateSheetMinioId(existing);
        } else {
            TaskSheet ts = new TaskSheet();
            ts.setTaskId(taskId);
            ts.setBuildingId(vo.getBuildingId());
            ts.setType("technical_condition");
            ts.setSheetMinioId(newMinioId);
            taskSheetMapper.insert(ts);
        }
        return bytes;
    }

    /** 加载模板 → 填充数据 → 返回 DOCX 字节流（不涉及任何存储） */
    private byte[] buildDocxBytes(Jglp05017Vo vo) {
        List<Disease> diseases = vo.getDiseases() != null ? vo.getDiseases() : new ArrayList<>();
        int totalPages = Math.max(1, (int) Math.ceil(diseases.size() / (double) JGLP05017_PAGE_DISEASE_COUNT));
        XWPFDocument document = loadJglp05017Template();
        try {
            centerDocumentTitle(document);
            fillInfoCells(document, vo);
            fillPageNumber(document, 1, totalPages);
            fillDiseaseTable(document, pageDiseases(diseases, 0));
            normalizeTextStyles(document);
            centerDocumentTitle(document);

            for (int start = JGLP05017_PAGE_DISEASE_COUNT; start < diseases.size(); start += JGLP05017_PAGE_DISEASE_COUNT) {
                int pageNo = start / JGLP05017_PAGE_DISEASE_COUNT + 1;
                XWPFDocument pageDocument = loadJglp05017Template();
                centerDocumentTitle(pageDocument);
                fillInfoCells(pageDocument, vo);
                fillPageNumber(pageDocument, pageNo, totalPages);
                fillDiseaseTable(pageDocument, pageDiseases(diseases, start));
                normalizeTextStyles(pageDocument);
                centerDocumentTitle(pageDocument);
                appendDocumentPage(document, pageDocument);
                pageDocument.close();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            document.close();
            return baos.toByteArray();
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            throw new ServiceException("生成 JGLP05017 Word 文件失败：" + e.getMessage());
        }
    }

    private List<Disease> pageDiseases(List<Disease> diseases, int start) {
        if (start >= diseases.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(start + JGLP05017_PAGE_DISEASE_COUNT, diseases.size());
        return diseases.subList(start, end);
    }

    private void appendDocumentPage(XWPFDocument target, XWPFDocument pageDocument) {
        XWPFParagraph pageBreak = target.createParagraph();
        pageBreak.setPageBreak(true);

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

    private XWPFDocument loadJglp05017Template() {
        try {
            Resource resource = new ClassPathResource("word.biz/桥梁结构桥梁技术状况检测记录表.docx");
            return new XWPFDocument(resource.getInputStream());
        } catch (Exception e) {
            throw new ServiceException(
                    "JGLP05017 模板文件未找到，请将模板复制到 bi-biz/src/main/resources/word.biz/桥梁结构桥梁技术状况检测记录表.docx");
        }
    }

    // ─────────────────── Apache POI 帮助方法 ───────────────────────────

    private void fillInfoCells(XWPFDocument document, Jglp05017Vo vo) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(vo.getCheckDate());
        for (XWPFTable table : document.getTables()) {
            String text = getTableText(table);
            if (text.contains("工程名称") || text.contains("检测单位")) {
                fillCellAfterLabel(table, "工程名称", vo.getProjectName());
                fillCellAfterLabel(table, "工程部位", vo.getBuildingName());
                fillCellAfterLabel(table, "记录编号", String.valueOf(vo.getTaskId()));
                fillCellAfterLabel(table, "试验检测日期", dateStr);
                break;
            }
        }
    }

    private void fillCellAfterLabel(XWPFTable table, String labelKeyword, String value) {
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).getText().contains(labelKeyword)) {
                    if (i + 1 < cells.size() && cells.get(i + 1).getText().trim().isEmpty()) {
                        setCellText(cells.get(i + 1), value);
                    }
                    return;
                }
            }
        }
    }

    private void fillPageNumber(XWPFDocument document, int pageNo, int totalPages) {
        String pageText = "第 " + pageNo + " 页 共 " + totalPages + " 页";
        for (XWPFParagraph para : document.getParagraphs()) {
            replacePageNumberText(para, pageText);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        replacePageNumberText(para, pageText);
                    }
                }
            }
        }
    }

    private void replacePageNumberText(XWPFParagraph para, String pageText) {
        String text = para.getText().replaceAll("\\s+", "");
        if (!text.contains("第") || !text.contains("页") || !text.contains("共")) {
            return;
        }
        para.setAlignment(ParagraphAlignment.RIGHT);
        clearRuns(para);
        XWPFRun run = para.createRun();
        run.setText(pageText);
        setNormalRunStyle(run);
    }

    private void fillDiseaseTable(XWPFDocument document, List<Disease> diseases) {
        XWPFTable diseaseTable = null;
        for (XWPFTable table : document.getTables()) {
            if (getTableText(table).contains("缺损位置")) {
                diseaseTable = table;
                break;
            }
        }
        if (diseaseTable == null) {
            log.warn("JGLP05017 模板中未找到包含「缺损位置」的病害表格，跳过填充");
            return;
        }
        if (diseases == null) {
            diseases = new ArrayList<>();
        }

        int headerRowCount = detectHeaderRowCount(diseaseTable);
        // 备注行不属于数据区，计算数据行时排除它
        int remarkRowIndex = findRemarkRowIndex(diseaseTable);
        int dataAreaEnd = (remarkRowIndex > headerRowCount) ? remarkRowIndex : diseaseTable.getRows().size();
        int existingDataRows = dataAreaEnd - headerRowCount;
        int fillCount = Math.min(diseases.size(), existingDataRows);
        log.info("JGLP05017 fillDiseaseTable: 总行={} headerRows={} remarkRow={} 数据行={} 填入={}",
                diseaseTable.getRows().size(), headerRowCount, remarkRowIndex, existingDataRows, fillCount);

        // 每一页只填模板预留的数据行；超过容量的数据由 buildDocxBytes 拆到下一整页模板
        for (int i = 0; i < fillCount; i++) {
            fillDiseaseRow(diseaseTable.getRow(headerRowCount + i), diseases.get(i));
        }

        // 删除模板多余的空白数据行
        if (fillCount < existingDataRows) {
            for (int i = dataAreaEnd - 1; i >= headerRowCount + fillCount; i--) {
                diseaseTable.removeRow(i);
            }
        }

        removeRowsAfterRemark(diseaseTable);
        removeEmptyTablesAfter(document, diseaseTable);
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
        // 扫描全部行（不限前5行），找包含「缺损数量」或「病害描述」的子表头行
        for (int i = 0; i < rowCount; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("缺损数量") || rowText.contains("病害描述")
                    || rowText.contains("性质、范围") || rowText.contains("性质范围")) {
                return i + 1;
            }
        }
        // 回退：找「缺损情况」行，其紧邻下一行为最后一级表头
        for (int i = 0; i < rowCount; i++) {
            String rowText = getRowText(table.getRow(i)).replaceAll("\\s+", "");
            if (rowText.contains("缺损情况")) {
                return Math.min(i + 2, rowCount);
            }
        }
        return 1;
    }

    private void fillDiseaseRow(XWPFTableRow row, Disease d) {
        List<XWPFTableCell> cells = row.getTableCells();
        int n = cells.size();
        if (n < 4) return;
        log.debug("fillDiseaseRow: cellCount={}", n);

        // 缺损位置：直接用 bi_disease.position 字段
        String positionText = nvl(d.getPosition());

        String qty = (d.getQuantity() > 0 ? String.valueOf(d.getQuantity()) : "")
                + (d.getUnits() != null ? d.getUnits() : "");
        String typeName = d.getDiseaseType() != null ? nvl(d.getDiseaseType().getName()) : "";

        if (n >= 6) {
            // 标准6列：缺损位置 | 缺损类型 | 缺损数量 | 病害描述 | 评定类别 | 照片
            setCellText(cells.get(0), positionText);
            setCellText(cells.get(1), typeName);
            setCellText(cells.get(2), qty);
            setCellText(cells.get(3), nvl(d.getDescription()));
            setCellText(cells.get(4), d.getLevel() > 0 ? String.valueOf(d.getLevel()) : "");
            setCellText(cells.get(5), "\\");
        } else if (n == 5) {
            // 5列：缺损情况合并为单列
            setCellText(cells.get(0), positionText);
            setCellText(cells.get(1), typeName);
            String qtyDesc = qty.isEmpty() ? nvl(d.getDescription())
                    : qty + "  " + nvl(d.getDescription());
            setCellText(cells.get(2), qtyDesc);
            setCellText(cells.get(3), d.getLevel() > 0 ? String.valueOf(d.getLevel()) : "");
            setCellText(cells.get(4), "\\");
        } else {
            // 4列及其他情况，按序填入
            setCellText(cells.get(0), positionText);
            setCellText(cells.get(1), typeName);
            if (n > 2) setCellText(cells.get(2), qty);
            if (n > 3) setCellText(cells.get(3), nvl(d.getDescription()));
        }
    }

    private void setCellText(XWPFTableCell cell, String text) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        for (int i = cell.getParagraphs().size() - 1; i > 0; i--) {
            cell.removeParagraph(i);
        }
        XWPFParagraph para = cell.getParagraphs().isEmpty()
                ? cell.addParagraph() : cell.getParagraphArray(0);
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
        ReportGenerateTools.setMixedFontFamily(run, 21);
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

    private String getTableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            sb.append(getRowText(row));
        }
        return sb.toString();
    }

    private String getRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            sb.append(cell.getText());
        }
        return sb.toString();
    }

    /** 将文档中的表名和表号分别设置为模板要求的样式 */
    private void centerDocumentTitle(XWPFDocument document) {
        for (XWPFParagraph para : document.getParagraphs()) {
            formatTitleAndSheetNoParagraph(para, null);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : new ArrayList<>(cell.getParagraphs())) {
                        formatTitleAndSheetNoParagraph(para, cell);
                    }
                }
            }
        }
    }

    private void formatTitleAndSheetNoParagraph(XWPFParagraph para, XWPFTableCell cell) {
        String text = para.getText().replaceAll("\\s+", "");
        if (text.contains("桥梁结构桥梁技术状况检测记录表")) {
            para.setAlignment(ParagraphAlignment.CENTER);
            para.setIndentationLeft(0);
            para.setIndentationRight(0);
            para.setIndentationFirstLine(0);
            if (para.getRuns().isEmpty()) {
                XWPFRun run = para.createRun();
                run.setText("桥梁结构桥梁技术状况检测记录表");
                setTitleRunStyle(run);
                return;
            }
            // 不清空 runs，不追加新段落——直接对已有 run 按内容分别样式化，保持同行
            for (XWPFRun run : para.getRuns()) {
                String runText = run.text().replaceAll("\\s+", "");
                if (runText.contains("JGLP05017")) {
                    setNormalRunStyle(run);
                } else if (!runText.isEmpty()) {
                    setTitleRunStyle(run);
                }
            }
            return;
        }
        if (text.contains("JGLP05017")) {
            para.setAlignment(ParagraphAlignment.RIGHT);
            for (XWPFRun run : para.getRuns()) {
                setNormalRunStyle(run);
            }
        }
    }

    private void setTitleRunStyle(XWPFRun run) {
        clearRunDecoration(run);
        run.setBold(true);
        run.setColor("000000");
        ReportGenerateTools.setMixedFontFamily(run, 28);
    }

    private void setNormalRunStyle(XWPFRun run) {
        clearRunDecoration(run);
        run.setBold(false);
        run.setColor("000000");
        ReportGenerateTools.setMixedFontFamily(run, 21);
    }

    private void clearRuns(XWPFParagraph para) {
        for (int i = para.getRuns().size() - 1; i >= 0; i--) {
            para.removeRun(i);
        }
    }

    private void clearRunDecoration(XWPFRun run) {
        run.setItalic(false);
        run.setUnderline(UnderlinePatterns.NONE);
        run.setStrikeThrough(false);
        run.setDoubleStrikethrough(false);
        run.setEmbossed(false);
        run.setImprinted(false);
        run.setShadow(false);
        run.setVanish(false);
    }

    private void normalizeTextStyles(XWPFDocument document) {
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

    private void normalizeParagraphTextStyle(XWPFParagraph para) {
        String text = para.getText().replaceAll("\\s+", "");
        if (text.contains("桥梁结构桥梁技术状况检测记录表")) {
            return;
        }
        // 只清除装饰（颜色/阴影等），不修改字号，避免 footer 等行字体放大后换行
        for (XWPFRun run : para.getRuns()) {
            clearRunDecoration(run);
            run.setBold(false);
            run.setColor("000000");
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
