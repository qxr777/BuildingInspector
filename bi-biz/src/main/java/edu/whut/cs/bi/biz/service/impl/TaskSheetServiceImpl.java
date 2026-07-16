package edu.whut.cs.bi.biz.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.TaskSheet;
import edu.whut.cs.bi.biz.domain.vo.Jglp05017Vo;
import edu.whut.cs.bi.biz.domain.vo.TaskSheetStatusVo;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.TaskSheetMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.service.ITaskSheetService;
import edu.whut.cs.bi.biz.service.sheet.Jglp05017WordRenderer;
import edu.whut.cs.bi.biz.service.sheet.JsonSheetWordRendererRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;

import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务表格数据 Service 实现
 */
@Slf4j
@Service
public class TaskSheetServiceImpl implements ITaskSheetService {

    /**
     * 检测记录表展示顺序与表号（按表名关键词匹配 dict_label）
     * 第一项固定为桥梁结构桥梁技术状况检测记录表
     */
    private static final LinkedHashMap<String, String> INSPECTION_SHEET_CATALOG = new LinkedHashMap<>();

    static {
        INSPECTION_SHEET_CATALOG.put("桥梁结构桥梁技术状况检测记录表", "JGLP05017");
        INSPECTION_SHEET_CATALOG.put("碳化深度检测记录表", "JGLP02002");
        INSPECTION_SHEET_CATALOG.put("钢筋位置和保护层厚度检测记录表", "JGLP02003");
        INSPECTION_SHEET_CATALOG.put("钢筋锈蚀电位检测记录表", "JGLP02008");
        INSPECTION_SHEET_CATALOG.put("桥梁结构位移试验检测记录表（全站仪）", "JGLP05001b-1");
        INSPECTION_SHEET_CATALOG.put("桥梁结构位移试验检测记录表（水准仪）", "JGLP05001b-2");
        INSPECTION_SHEET_CATALOG.put("桥梁结构结构线形检测记录表（全站仪）", "JGLP05009a-1");
        INSPECTION_SHEET_CATALOG.put("桥梁结构结构线形测量记录表（水准仪法）", "JGLP05009a-2");
        INSPECTION_SHEET_CATALOG.put("索力检测记录表（振动法）", "JGLP05012a");
        INSPECTION_SHEET_CATALOG.put("桥梁结构检测与监测索力试验检测记录表（测力传感器法）", "JGLP05012b");
    }

    private static final List<String> SHEET_CATALOG_ORDER = new ArrayList<>(INSPECTION_SHEET_CATALOG.keySet());

    @Autowired
    private TaskSheetMapper taskSheetMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private JsonSheetWordRendererRegistry jsonSheetWordRendererRegistry;

    @Autowired
    private Jglp05017WordRenderer jglp05017WordRenderer;

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
            vo.setSheetNo(resolveSheetNo(dict));
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
        result.sort(Comparator
                .comparingInt((TaskSheetStatusVo vo) -> sheetDisplayIndex(vo.getType(), vo.getSheetName()))
                .thenComparing(TaskSheetStatusVo::getSheetName, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    private String resolveSheetNo(SysDictData dict) {
        if (StringUtils.isNotEmpty(dict.getRemark())) {
            return dict.getRemark().trim();
        }
        String matched = matchCatalogSheetNo(dict.getDictLabel());
        return matched != null ? matched : "";
    }

    private String matchCatalogSheetNo(String sheetLabel) {
        if (StringUtils.isEmpty(sheetLabel)) {
            return null;
        }
        String normalizedLabel = normalizeSheetLabel(sheetLabel);
        for (Map.Entry<String, String> entry : INSPECTION_SHEET_CATALOG.entrySet()) {
            if (normalizedLabel.contains(normalizeSheetLabel(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int sheetDisplayIndex(String type, String sheetLabel) {
        if (SHEET_TYPE_TECHNICAL_CONDITION.equals(type)) {
            return 0;
        }
        if (StringUtils.isNotEmpty(sheetLabel)
                && sheetLabel.contains("桥梁结构桥梁技术状况检测记录表")) {
            return 0;
        }
        String normalizedLabel = normalizeSheetLabel(sheetLabel);
        for (int i = 0; i < SHEET_CATALOG_ORDER.size(); i++) {
            if (normalizedLabel.contains(normalizeSheetLabel(SHEET_CATALOG_ORDER.get(i)))) {
                return i;
            }
        }
        return 1000;
    }

    private String normalizeSheetLabel(String label) {
        return label.replaceAll("\\s+", "")
                .replace('（', '(')
                .replace('）', ')');
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
    public byte[] downloadSheetBytes(Long taskId, String type) {
        TaskSheet taskSheet = taskSheetMapper.selectByTaskIdAndType(taskId, type);
        if (taskSheet == null || taskSheet.getSheetMinioId() == null) {
            throw new ServiceException("该表格尚未提交，无法下载");
        }
        byte[] bytes = fileMapService.handleFileDownload(taskSheet.getSheetMinioId());
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("表格文件不存在或已删除");
        }
        return bytes;
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
        attachComponentsForJglp05017(diseases);
        diseases.sort(this::compareDiseaseDisplayOrder);

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
        applyTechnicalConditionMetadataToVo(taskId, vo);
        return vo;
    }

    private void applyTechnicalConditionMetadataToVo(Long taskId, Jglp05017Vo vo) {
        JSONObject saved = readSavedTechnicalConditionJson(taskId);
        if (saved == null) {
            return;
        }
        JSONObject savedHeader = saved.getJSONObject("header");
        if (savedHeader != null && !savedHeader.isEmpty()) {
            vo.setHeader(savedHeader);
        }

        JSONArray savedPages = saved.getJSONArray("pages");
        if (savedPages == null || savedPages.isEmpty()) {
            return;
        }
        List<JSONObject> pageHeaders = new ArrayList<>();
        List<String> remarks = new ArrayList<>();
        for (int i = 0; i < savedPages.size(); i++) {
            JSONObject page = savedPages.getJSONObject(i);
            JSONObject pageHeader = page != null ? page.getJSONObject("header") : null;
            if (pageHeader != null && !pageHeader.isEmpty()) {
                pageHeaders.add(pageHeader);
            } else if (savedHeader != null && !savedHeader.isEmpty()) {
                pageHeaders.add(savedHeader);
            } else {
                pageHeaders.add(new JSONObject());
            }
            remarks.add(page != null ? nvl(page.getString("remark")) : "");
        }
        vo.setPageHeaders(pageHeaders);
        if ((savedHeader == null || savedHeader.isEmpty()) && !pageHeaders.isEmpty()) {
            vo.setHeader(pageHeaders.get(0));
        }
        vo.setPageRemarks(remarks);
    }

    private int compareDiseaseDisplayOrder(Disease a, Disease b) {
        Long aId = a.getId();
        Long bId = b.getId();
        if (aId == null && bId == null) {
            return 0;
        }
        if (aId == null) {
            return 1;
        }
        if (bId == null) {
            return -1;
        }
        return aId.compareTo(bId);
    }

    /** 为 JGLP05017 缺陷位置补充构件信息（构件编号 + 构件名称） */
    private void attachComponentsForJglp05017(List<Disease> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            return;
        }
        List<Long> componentIds = diseases.stream()
                .map(Disease::getComponentId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (componentIds.isEmpty()) {
            return;
        }
        List<Component> components = componentMapper.selectComponentsByIds(componentIds);
        if (components == null || components.isEmpty()) {
            return;
        }
        Map<Long, Component> componentMap = components.stream()
                .filter(c -> c != null && c.getId() != null)
                .collect(Collectors.toMap(Component::getId, Function.identity(), (a, b) -> a));
        for (Disease disease : diseases) {
            if (disease.getComponentId() != null) {
                disease.setComponent(componentMap.get(disease.getComponentId()));
            }
        }
    }

    @Override
    public byte[] generateJglp05017WordBytes(Long taskId) {
        return jglp05017WordRenderer.render(getJglp05017Data(taskId), WordSheetPoiUtils.PageNumberPlacement.BODY);
    }

    @Override
    public byte[] generateJglp05017WordDownloadBytes(Long taskId) {
        return jglp05017WordRenderer.render(getJglp05017Data(taskId), WordSheetPoiUtils.PageNumberPlacement.HEADER);
    }

    @Override
    public boolean hasDiseaseDataByTaskId(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            return false;
        }
        Disease query = new Disease();
        query.setProjectId(task.getProjectId());
        query.setBuildingId(task.getBuildingId());
        List<Disease> diseases = diseaseMapper.selectDiseaseList(query);
        return diseases != null && !diseases.isEmpty();
    }

    @Override
    public boolean supportsJsonSheetWord(String type) {
        return jsonSheetWordRendererRegistry.supports(type);
    }

    @Override
    public List<String> listJsonSheetWordTypes() {
        return jsonSheetWordRendererRegistry.supportedTypes();
    }

    @Override
    public byte[] generateJsonSheetWordBytes(Long taskId, String type) {
        return renderJsonSheetWord(taskId, type, WordSheetPoiUtils.PageNumberPlacement.BODY);
    }

    @Override
    public byte[] generateJsonSheetWordDownloadBytes(Long taskId, String type) {
        return renderJsonSheetWord(taskId, type, WordSheetPoiUtils.PageNumberPlacement.HEADER);
    }

    private byte[] renderJsonSheetWord(Long taskId, String type, WordSheetPoiUtils.PageNumberPlacement placement) {
        TaskSheet taskSheet = taskSheetMapper.selectByTaskIdAndType(taskId, type);
        if (taskSheet == null || taskSheet.getSheetMinioId() == null) {
            throw new ServiceException("该表格尚未提交，无法查看");
        }
        byte[] jsonBytes;
        try {
            jsonBytes = fileMapService.handleFileDownload(taskSheet.getSheetMinioId());
        } catch (Exception e) {
            throw new ServiceException("表格 JSON 文件下载失败，type=" + type
                    + "，sheetMinioId=" + taskSheet.getSheetMinioId()
                    + "，请重新上传该任务的表格数据");
        }
        if (jsonBytes == null || jsonBytes.length == 0) {
            throw new ServiceException("表格 JSON 文件不存在或已删除");
        }
        try {
            com.alibaba.fastjson.JSONObject sheetJson =
                    JSON.parseObject(new String(jsonBytes, StandardCharsets.UTF_8));
            return jsonSheetWordRendererRegistry.getRequired(type).render(sheetJson, placement);
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.trim().isEmpty()) {
                detail = e.getClass().getSimpleName();
            }
            ServiceException se = new ServiceException("生成表格 Word 失败：" + detail);
            se.initCause(e);
            throw se;
        }
    }

    @Override
    public String resolveJsonSheetDownloadBaseName(String type) {
        return jsonSheetWordRendererRegistry.getRequired(type).defaultDownloadBaseName();
    }

    @Override
    public boolean supportsWebJsonEdit(String type) {
        return supportsJsonSheetWord(type)
                && !SHEET_TYPE_TECHNICAL_CONDITION.equals(type);
    }

    @Override
    public boolean supportsWebEdit(String type) {
        return supportsWebJsonEdit(type) || SHEET_TYPE_TECHNICAL_CONDITION.equals(type);
    }

    @Override
    public String getTechnicalConditionJsonForEdit(Long taskId) {
        return JSON.toJSONString(buildTechnicalConditionSnapshotJson(taskId, null), SerializerFeature.PrettyFormat);
    }

    private JSONObject buildTechnicalConditionSnapshotJson(Long taskId, JSONObject sourceJson) {
        Jglp05017Vo vo = getJglp05017Data(taskId);
        List<Disease> diseases = vo.getDiseases() != null ? vo.getDiseases() : new ArrayList<>();
        JSONArray pages = new JSONArray();
        JSONObject defaultHeader = buildTechnicalConditionDefaultHeader(taskId);
        if (vo.getCheckDate() != null) {
            defaultHeader.put("testDate", new SimpleDateFormat("yyyy-MM-dd").format(vo.getCheckDate()));
        }
        if (!diseases.isEmpty()) {
            for (List<Disease> pageDiseases : jglp05017WordRenderer.paginateForEdit(diseases)) {
                pages.add(buildTechnicalConditionPage(pageDiseases, defaultHeader));
            }
        }
        applyTechnicalConditionHeaderAndRemark(taskId, sourceJson, defaultHeader, pages);
        JSONObject root = new JSONObject();
        root.put("sheetId", SHEET_TYPE_TECHNICAL_CONDITION);
        root.put("type", SHEET_TYPE_TECHNICAL_CONDITION);
        root.put("header", pages.isEmpty() ? defaultHeader : pages.getJSONObject(0).getJSONObject("header"));
        root.put("pages", pages);
        return root;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveTechnicalConditionFromWeb(Long taskId, byte[] jsonBytes) {
        Task task = requireTask(taskId);
        if (jsonBytes == null) {
            throw new ServiceException("请求体不能为空");
        }
        JSONObject sheetJson = JSON.parseObject(new String(jsonBytes, StandardCharsets.UTF_8));
        JSONObject snapshotJson = buildTechnicalConditionSnapshotJson(taskId, sheetJson);
        JSONArray pages = snapshotJson.getJSONArray("pages");
        if (pages == null || pages.isEmpty()) {
            deleteSheetByTaskIdAndType(taskId, SHEET_TYPE_TECHNICAL_CONDITION);
            return false;
        }
        saveOrUpdateSheet(taskId, task.getBuildingId(), SHEET_TYPE_TECHNICAL_CONDITION,
                JSON.toJSONString(snapshotJson, SerializerFeature.PrettyFormat).getBytes(StandardCharsets.UTF_8),
                SHEET_TYPE_TECHNICAL_CONDITION + ".json");
        return true;
    }

    @Override
    public JSONObject buildTechnicalConditionDefaultHeader(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("未找到检测任务，taskId=" + taskId);
        }
        JSONObject header = buildDefaultHeader(task);
        header.put("recordNumber", String.valueOf(taskId));
        header.put("testDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        mergeTechnicalConditionTemplateHeader(header, getSheetTemplateFixedFields(SHEET_TYPE_TECHNICAL_CONDITION));
        return header;
    }

    private void mergeTechnicalConditionTemplateHeader(JSONObject header, JSONObject templateFields) {
        if (templateFields == null || templateFields.isEmpty()) {
            return;
        }
        // Only merge fixed values that really exist in the template.
        putIfAbsentHeader(header, "inspectionUnitName", templateFields.getString("inspectionUnitName"));
    }

    private void putIfAbsentHeader(JSONObject header, String key, String value) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        if (StringUtils.isEmpty(header.getString(key))) {
            header.put(key, value.trim());
        }
    }

    private JSONObject buildTechnicalConditionPage(List<Disease> diseases, JSONObject defaultHeader) {
        JSONObject page = new JSONObject();
        JSONObject header = new JSONObject();
        header.putAll(defaultHeader);
        page.put("header", header);
        JSONArray records = new JSONArray();
        for (Disease disease : diseases) {
            records.add(diseaseToTechnicalConditionRecord(disease));
        }
        page.put("records", records);
        page.put("remark", "");
        return page;
    }

    private void applyTechnicalConditionHeaderAndRemark(Long taskId, JSONObject sourceJson,
                                                        JSONObject defaultHeader, JSONArray pages) {
        if (pages == null || pages.isEmpty()) {
            return;
        }
        JSONArray savedPages = sourceJson != null ? sourceJson.getJSONArray("pages") : null;
        JSONObject saved = null;
        if (savedPages == null) {
            saved = readSavedTechnicalConditionJson(taskId);
            savedPages = saved != null ? saved.getJSONArray("pages") : null;
        }
        JSONObject rootHeader = sourceJson != null ? sourceJson.getJSONObject("header") : null;
        if (rootHeader == null || rootHeader.isEmpty()) {
            rootHeader = saved != null ? saved.getJSONObject("header") : null;
        }

        for (int i = 0; i < pages.size(); i++) {
            JSONObject page = pages.getJSONObject(i);
            if (page == null) {
                continue;
            }
            JSONObject header = new JSONObject();
            header.putAll(defaultHeader);
            JSONObject savedPage = savedPages != null && i < savedPages.size() ? savedPages.getJSONObject(i) : null;
            JSONObject pageHeader = savedPage != null ? savedPage.getJSONObject("header") : null;
            if (pageHeader != null && !pageHeader.isEmpty()) {
                header.putAll(pageHeader);
            } else if (rootHeader != null && !rootHeader.isEmpty()) {
                header.putAll(rootHeader);
            }
            page.put("header", header);
            if (savedPage != null && savedPage.containsKey("remark")) {
                page.put("remark", nvl(savedPage.getString("remark")));
            }
        }
    }

    private JSONObject readSavedTechnicalConditionJson(Long taskId) {
        String existing = getSheetJsonContent(taskId, SHEET_TYPE_TECHNICAL_CONDITION);
        if (StringUtils.isEmpty(existing)) {
            return null;
        }
        try {
            return JSON.parseObject(existing);
        } catch (Exception e) {
            log.warn("读取技术状况表已保存 JSON 失败，taskId={}", taskId, e);
            return null;
        }
    }

    private JSONObject diseaseToTechnicalConditionRecord(Disease disease) {
        JSONObject record = new JSONObject();
        record.put("id", disease.getId());
        record.put("position", buildTechnicalConditionPositionText(disease));
        record.put("type", resolveTechnicalConditionTypeText(disease));
        if (disease.getQuantity() > 0) {
            String unit = StringUtils.isNotEmpty(disease.getUnits()) ? disease.getUnits() : "处";
            record.put("quantity", disease.getQuantity() + unit);
        } else {
            record.put("quantity", "");
        }
        record.put("description", nvl(disease.getDescription()));
        if (disease.getLevel() > 0) {
            record.put("level", String.valueOf(disease.getLevel()));
        } else {
            record.put("level", "");
        }
        record.put("imgNoExp", formatImgNoExpForDisplay(disease.getImgNoExp()));
        return record;
    }

    private String buildTechnicalConditionPositionText(Disease disease) {
        if (disease == null) {
            return "";
        }
        Component component = disease.getComponent();
        if (component != null) {
            String componentName = StringUtils.trim(component.getName());
            if (StringUtils.isNotEmpty(componentName) && componentName.contains("#")) {
                return normalizeTechnicalConditionPositionText(componentName);
            }
            String code = StringUtils.trim(component.getCode());
            if (StringUtils.isNotEmpty(code) && code.contains("#")) {
                return normalizeTechnicalConditionPositionText(code);
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
            return normalizeTechnicalConditionPositionText(disease.getBiObjectName());
        }
        return normalizeTechnicalConditionPositionText(disease.getPosition());
    }

    private String resolveTechnicalConditionTypeText(Disease disease) {
        if (disease == null) {
            return "";
        }
        return nvl(disease.getType());
    }

    private String normalizeTechnicalConditionPositionText(String text) {
        String trimmed = StringUtils.trim(text);
        if (StringUtils.isEmpty(trimmed) || !trimmed.contains("#")) {
            return nvl(trimmed);
        }
        String[] parts = trimmed.split("#", 2);
        String left = StringUtils.trim(parts[0]);
        String right = parts.length > 1 ? StringUtils.trim(parts[1]) : "";
        if (StringUtils.isNotEmpty(left) && StringUtils.isNotEmpty(right)
                && !isTechnicalConditionCodeLike(left)
                && isTechnicalConditionCodeLike(right)) {
            return right + "#" + left;
        }
        return trimmed;
    }

    private boolean isTechnicalConditionCodeLike(String value) {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isNotEmpty(trimmed)
                && !trimmed.matches(".*[\\u4e00-\\u9fa5].*")
                && trimmed.matches("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*");
    }

    private String formatImgNoExpForDisplay(String imgNoExp) {
        if (StringUtils.isEmpty(imgNoExp)) {
            return "";
        }
        try {
            JSONArray arr = JSON.parseArray(imgNoExp);
            if (arr == null || arr.isEmpty()) {
                return imgNoExp;
            }
            return String.join("、", arr.toJavaList(String.class));
        } catch (Exception ignored) {
            return imgNoExp;
        }
    }

    private Task requireTask(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("未找到检测任务，taskId=" + taskId);
        }
        if (task.getBuildingId() == null) {
            throw new ServiceException("任务未关联桥梁，无法保存表格");
        }
        return task;
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSheetByTaskIdAndType(Long taskId, String type) {
        TaskSheet existing = taskSheetMapper.selectByTaskIdAndType(taskId, type);
        if (existing == null) {
            return;
        }
        if (existing.getSheetMinioId() != null) {
            try {
                fileMapService.deleteFileMapById(existing.getSheetMinioId());
            } catch (Exception ignored) {
                log.warn("删除 MinIO 表格文件失败，id={}", existing.getSheetMinioId());
            }
        }
        taskSheetMapper.deleteById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSheetFromWeb(Long taskId, Long buildingId, String type, byte[] jsonBytes) {
        JSONObject sheetJson = JSON.parseObject(new String(jsonBytes, StandardCharsets.UTF_8));
        JSONArray pages = sheetJson.getJSONArray("pages");
        if (pages == null || pages.isEmpty()) {
            deleteSheetByTaskIdAndType(taskId, type);
            return;
        }
        saveOrUpdateSheet(taskId, buildingId, type, jsonBytes, type + ".json");
    }

    @Override
    public String getSheetJsonForEdit(Long taskId, String type) {
        String existing = getSheetJsonContent(taskId, type);
        if (existing != null) {
            return existing;
        }
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("未找到检测任务，taskId=" + taskId);
        }
        JSONObject root = new JSONObject();
        root.put("sheetId", type);
        root.put("type", type);
        root.put("pages", new JSONArray());
        return JSON.toJSONString(root, SerializerFeature.PrettyFormat);
    }

    /** 模板固定字段缓存：type -> {inspectionBasis, judgementBasis} */
    private final Map<String, JSONObject> templateFixedFieldsCache = new ConcurrentHashMap<>();

    @Override
    public JSONObject getSheetTemplateFixedFields(String type) {
        if (SHEET_TYPE_TECHNICAL_CONDITION.equals(type)) {
            return templateFixedFieldsCache.computeIfAbsent(type,
                    t -> readTemplateFixedFieldsFromClasspath(Jglp05017WordRenderer.TEMPLATE_CLASSPATH));
        }
        if (!supportsWebJsonEdit(type)) {
            return new JSONObject();
        }
        return templateFixedFieldsCache.computeIfAbsent(type, this::readTemplateFixedFields);
    }

    private JSONObject readTemplateFixedFields(String type) {
        return readTemplateFixedFieldsFromClasspath(
                jsonSheetWordRendererRegistry.getRequired(type).templateClasspath());
    }

    private JSONObject readTemplateFixedFieldsFromClasspath(String classpath) {
        JSONObject result = new JSONObject();
        XWPFDocument doc = null;
        try {
            doc = WordSheetPoiUtils.loadTemplate(classpath);
            readInspectionUnitNameFromTemplate(doc, result);
            XWPFTable table = WordSheetPoiUtils.findTableContaining(doc, "工程名称");
            if (table != null) {
                putTemplateValue(result, "inspectionBasis",
                        WordSheetPoiUtils.readValueAfterLabel(table, "检测依据"));
                putTemplateValue(result, "judgementBasis",
                        WordSheetPoiUtils.readValueAfterLabel(table, "判定依据"));
            }
        } catch (Exception e) {
            log.warn("读取模板固定字段失败，classpath={}", classpath, e);
        } finally {
            if (doc != null) {
                try {
                    doc.close();
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private void readInspectionUnitNameFromTemplate(XWPFDocument doc, JSONObject result) {
        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText();
            if (text == null) {
                continue;
            }
            if (!text.contains("检测单位名称") && !text.contains("试验室名称")) {
                continue;
            }
            String label = text.contains("试验室名称") ? "试验室名称" : "检测单位名称";
            int labelIdx = text.indexOf(label);
            if (labelIdx < 0) {
                continue;
            }
            String after = text.substring(labelIdx + label.length()).replaceFirst("^[：:\\s]+", "");
            int recordIdx = after.indexOf("记录编号");
            String unitName = recordIdx >= 0 ? after.substring(0, recordIdx).trim() : after.trim();
            putTemplateValue(result, "inspectionUnitName", unitName);
            return;
        }
        for (XWPFTable table : doc.getTables()) {
            String tableText = WordSheetPoiUtils.getTableText(table);
            if (!tableText.contains("检测单位名称") && !tableText.contains("试验室名称")) {
                continue;
            }
            putTemplateValue(result, "inspectionUnitName",
                    WordSheetPoiUtils.readValueAfterLabel(table, "检测单位名称"));
            if (!result.containsKey("inspectionUnitName")) {
                putTemplateValue(result, "inspectionUnitName",
                        WordSheetPoiUtils.readValueAfterLabel(table, "试验室名称"));
            }
            if (result.containsKey("inspectionUnitName")) {
                return;
            }
        }
    }

    private void putTemplateValue(JSONObject result, String key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            result.put(key, value.trim());
        }
    }

    @Override
    public JSONObject buildDefaultSheetHeader(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("未找到检测任务，taskId=" + taskId);
        }
        return buildDefaultHeader(task);
    }

    private JSONObject buildDefaultHeader(Task task) {
        JSONObject header = new JSONObject();
        if (task.getProject() != null && StringUtils.isNotEmpty(task.getProject().getName())) {
            header.put("projectName", task.getProject().getName());
        }
        if (task.getBuilding() != null && StringUtils.isNotEmpty(task.getBuilding().getName())) {
            header.put("partUse", task.getBuilding().getName());
        }
        return header;
    }
}
