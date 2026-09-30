package edu.whut.cs.bi.biz.controller;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.LineBridgeGroup;
import edu.whut.cs.bi.biz.domain.LineReportData;
import edu.whut.cs.bi.biz.domain.Report;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.service.ILineMultiBridgeReportDataService;
import edu.whut.cs.bi.biz.service.IComponentService;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.IReportService;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import edu.whut.cs.bi.biz.utils.LineReportWordMatcher;
import edu.whut.cs.bi.biz.utils.WordImportSupport;
import edu.whut.cs.bi.biz.service.impl.LineReportWordImportService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 定期检查多桥报告填报Controller。
 *
 * <p>与单桥报告填报完全分开：第一章按线路填一份，第三章起按桥梁分别填写，
 * 通过页面上的桥梁选择器切换。</p>
 *
 * @author wanzheng
 */
@Controller
@RequestMapping("/biz/line_multi_bridge_data")
public class LineMultiBridgeReportDataController extends BaseController {
    private static final String FILL_PAGE = "biz/report_data/fill_line_multi_bridge";

    @Autowired
    private ILineMultiBridgeReportDataService lineReportDataService;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private IComponentService componentService;

    @Autowired
    private IReportService reportService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private FileMapServiceImpl fileMapService;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private LineReportWordImportService wordImportService;

    /**
     * 多桥报告填报页面
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/fill/{id}")
    public String fill(@PathVariable("id") Long id, ModelMap mmap) {
        Report report = reportService.selectReportById(id);
        mmap.put("report", report);
        List<Task> reportTasks = selectReportTasks(report);
        mmap.put("reportTasks", reportTasks);
        mmap.put("reportTaskViewsJson", JSON.toJSONString(toReportTaskViews(reportTasks)));
        mmap.put("bridgeGroupsJson", JSON.toJSONString(lineReportDataService.resolveEffectiveGroups(id, reportTasks)));
        return FILL_PAGE;
    }

    /** Read-only preview: never writes form values or uploads source files to permanent storage. */
    @RequiresPermissions("biz:report:edit")
    @PostMapping("/matchWord")
    @ResponseBody
    public AjaxResult matchWord(@RequestParam("reportId") Long reportId,
                                @RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > LineReportWordMatcher.MAX_UPLOAD_BYTES) {
            return AjaxResult.error("请选择不超过 300MB 的 Word 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".docx")) {
            return AjaxResult.error("目前仅支持 .docx 文件，请先在 Word 中另存为该格式");
        }
        Report report = reportService.selectReportById(reportId);
        if (report == null) return AjaxResult.error("报告不存在");
        try (java.io.InputStream input = file.getInputStream()) {
            List<LineBridgeGroup> groups = lineReportDataService.resolveEffectiveGroups(reportId, selectReportTasks(report));
            return AjaxResult.success("识别完成，尚未修改填报数据", LineReportWordMatcher.match(
                    input, groups, lineReportDataService.selectByReportId(reportId)));
        } catch (Exception e) {
            logger.warn("Word 填报项匹配失败，reportId={}", reportId, e);
            return AjaxResult.error("无法解析该 Word，请确认文件未加密、未损坏，且是有效的 .docx 文件");
        }
    }

    @RequiresPermissions("biz:report_data:edit")
    @Log(title = "多桥报告Word导入", businessType = BusinessType.UPDATE)
    @PostMapping("/importWord")
    @ResponseBody
    public AjaxResult importWord(@RequestParam("reportId") Long reportId, @RequestParam("file") MultipartFile file,
                                 @RequestParam("matches") String matches) {
        if (file.isEmpty() || file.getSize() > LineReportWordMatcher.MAX_UPLOAD_BYTES
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".docx"))
            return AjaxResult.error("请选择不超过300MB的 .docx 文件");
        Report report = reportService.selectReportById(reportId);
        if (report == null) return AjaxResult.error("报告不存在");
        try (java.io.InputStream input = file.getInputStream()) {
            List<LineBridgeGroup> groups = lineReportDataService.resolveEffectiveGroups(reportId, selectReportTasks(report));
            LineReportWordMatcher.Result parsed = LineReportWordMatcher.match(input, groups, lineReportDataService.selectByReportId(reportId));
            List<LineReportWordImportService.Selection> selections = new com.fasterxml.jackson.databind.ObjectMapper().readValue(matches,
                    new com.fasterxml.jackson.core.type.TypeReference<List<LineReportWordImportService.Selection>>() { });
            int count = wordImportService.apply(reportId, file, parsed, selections);
            return AjaxResult.success("已导入 " + count + " 项，重新生成报告时将使用 Word 原格式内容");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            logger.error("保存Word导入失败，reportId={}", reportId, e);
            return AjaxResult.error("导入保存失败，请检查文件后重试，原填报数据未替换");
        }
    }

    /**
     * 查询填报数据。taskId 为空表示线路级数据。
     */
    @RequiresPermissions("biz:report_data:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(@RequestParam("reportId") Long reportId,
                             @RequestParam(value = "taskId", required = false) Long taskId,
                             @RequestParam(value = "groupId", required = false) String groupId) {
        if (groupId != null && !groupId.trim().isEmpty()) {
            return getDataTable(lineReportDataService.selectByGroup(reportId, groupId.trim()));
        }
        return getDataTable(lineReportDataService.selectByTask(reportId, taskId));
    }

    /**
     * 保存填报数据。taskId 为空存线路级，否则存对应桥梁。
     */
    @RequiresPermissions("biz:report_data:edit")
    @Log(title = "多桥报告数据", businessType = BusinessType.UPDATE)
    @PostMapping("/save")
    @ResponseBody
    public AjaxResult save(
            @RequestParam("reportId") Long reportId,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "groupId", required = false) String groupId,
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "dataKeys", required = false) String[] dataKeys,
            @RequestParam(value = "dataValues", required = false) String[] dataValues,
            @RequestParam(value = "dataTypes", required = false) Integer[] dataTypes,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "bridgeGroupsJson", required = false) String bridgeGroupsJson) {

        if (ObjectUtils.isEmpty(dataKeys) && (bridgeGroupsJson == null || bridgeGroupsJson.trim().isEmpty())) {
            return AjaxResult.success("没有要保存的数据");
        }
        if (ObjectUtils.isEmpty(dataKeys)) {
            dataKeys = new String[0];
            dataValues = new String[0];
        }

        try {
            Map<String, List<MultipartFile>> filesByKey = groupFilesByKey(dataKeys, dataTypes, files);
            List<LineReportData> dataList = new ArrayList<>();
            List<LineReportData> previous = lineReportDataService.selectByReportId(reportId);

            for (int i = 0; i < dataKeys.length; i++) {
                String key = dataKeys[i];
                // 跳过空的key（前端为了防止Spring分割数组而添加的占位符）
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                Integer type = dataTypes != null && i < dataTypes.length ? dataTypes[i] : 0;
                // An ordinary form autosave must never replace a formatted Word source reference.
                if (previous.stream().anyMatch(row -> WordImportSupport.isImported(row)
                        && Objects.equals(row.getKey(), key) && Objects.equals(row.getTaskId(), taskId)
                        && Objects.equals(row.getGroupId(), groupId == null || groupId.isBlank() ? null : groupId.trim()))) continue;
                if (type != null && type == WordImportSupport.TYPE) continue;
                String submittedValue = dataValues != null && i < dataValues.length ? dataValues[i] : null;

                boolean lineKey = key.startsWith("line-") || key.startsWith("line.");
                boolean groupScope = groupId != null && !groupId.trim().isEmpty();
                if (taskId == null && !groupScope && !lineKey) {
                    continue;
                }
                if ((taskId != null || groupScope) && lineKey) {
                    continue;
                }

                LineReportData reportData = new LineReportData();
                reportData.setReportId(reportId);
                if (groupScope) {
                    reportData.setGroupId(groupId.trim());
                } else {
                    reportData.setTaskId(taskId);
                    reportData.setBuildingId(buildingId);
                }
                reportData.setKey(key);
                reportData.setType(type);

                if (type != null && type == 1) {
                    String value = mergeUploadedFiles(filesByKey.get(key), submittedValue);
                    if (value == null) {
                        continue;
                    }
                    reportData.setValue(value);
                } else {
                    reportData.setValue(submittedValue);
                }
                dataList.add(reportData);
            }

            if (bridgeGroupsJson != null && !bridgeGroupsJson.trim().isEmpty()) {
                dataList.removeIf(item -> LineBridgeGroup.DATA_KEY.equals(item.getKey())
                        && item.getTaskId() == null
                        && (item.getGroupId() == null || item.getGroupId().trim().isEmpty()));
                LineReportData groups = new LineReportData();
                groups.setReportId(reportId);
                groups.setKey(LineBridgeGroup.DATA_KEY);
                groups.setType(0);
                groups.setValue(bridgeGroupsJson.trim());
                dataList.add(groups);
            }

            return toAjax(lineReportDataService.saveBatch(reportId, dataList));
        } catch (Exception e) {
            logger.error("保存多桥报告数据失败", e);
            return AjaxResult.error("保存多桥报告数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前大桥下各子桥本次任务的全部病害，保留每条记录用于逐条选择。
     */
    @PostMapping("/diseaseComponentData")
    @RequiresPermissions("biz:report_data:list")
    @ResponseBody
    public AjaxResult diseaseComponentData(@RequestParam("reportId") Long reportId,
                                          @RequestParam(value = "taskId", required = false) Long taskId,
                                          @RequestParam(value = "groupId", required = false) String groupId) {
        try {
            Report report = reportService.selectReportById(reportId);
            if (report == null) {
                return AjaxResult.error("报告不存在");
            }
            List<Task> reportTasks = selectReportTasks(report);
            List<Long> targetTaskIds = new ArrayList<>();
            if (groupId != null && !groupId.trim().isEmpty()) {
                lineReportDataService.resolveEffectiveGroups(reportId, reportTasks).stream()
                        .filter(group -> groupId.trim().equals(group.getId()))
                        .findFirst()
                        .ifPresent(group -> {
                            if (group.getTaskIds() != null) {
                                targetTaskIds.addAll(group.getTaskIds());
                            }
                        });
            } else if (taskId != null) {
                targetTaskIds.add(taskId);
            } else if (!reportTasks.isEmpty()) {
                targetTaskIds.add(reportTasks.get(0).getId());
            }
            if (targetTaskIds.isEmpty()) {
                return AjaxResult.success("获取成功", Collections.emptyMap());
            }
            Map<Long, Task> taskById = reportTasks.stream()
                    .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left));
            Map<String, Map<String, Object>> ordered = new LinkedHashMap<>();
            for (Long id : targetTaskIds) {
                Task task = taskById.get(id);
                if (task == null) continue;
                ordered.put(String.valueOf(id), buildTaskDiseaseRecords(task));
            }
            return AjaxResult.success("获取成功", ordered);
        } catch (Exception e) {
            logger.error("获取构件病害数据失败", e);
            return AjaxResult.error("获取构件病害数据失败：" + e.getMessage());
        }
    }

    private Map<String, Object> buildTaskDiseaseRecords(Task task) {
        Disease query = new Disease();
        query.setTaskId(task.getId());
        query.setBuildingId(task.getBuildingId());
        List<Disease> diseases = diseaseMapper.selectDiseaseList(query);
        if (diseases == null) diseases = Collections.emptyList();
        List<Long> componentIds = diseases.stream().map(Disease::getComponentId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Component> components = componentIds.isEmpty() ? Collections.emptyMap()
                : componentService.selectComponentsByIds(componentIds).stream()
                .collect(Collectors.toMap(Component::getId, item -> item, (left, right) -> left));
        Map<Long, BiObject> objects = new HashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        for (Disease disease : diseases) {
            if (disease.getId() == null) continue;
            Component component = components.get(disease.getComponentId());
            BiObject object = disease.getBiObjectId() == null ? null
                    : objects.computeIfAbsent(disease.getBiObjectId(), biObjectMapper::selectBiObjectById);
            String componentName = object == null ? disease.getBiObjectName() : object.getName();
            if (componentName == null || componentName.isBlank()) {
                componentName = component == null ? "未分类构件" : component.getName();
            }
            Map<String, Object> record = new LinkedHashMap<>();
            // ID 使用字符串，避免浏览器转换为数字时丢失大整数精度。
            record.put("diseaseId", String.valueOf(disease.getId()));
            record.put("componentId", disease.getBiObjectId() == null ? null : String.valueOf(disease.getBiObjectId()));
            record.put("componentName", componentName);
            record.put("componentCode", component == null ? "" : component.getCode());
            record.put("diseaseTypeId", disease.getDiseaseTypeId() == null ? null : String.valueOf(disease.getDiseaseTypeId()));
            String type = disease.getType();
            if (type == null || type.isBlank()) {
                type = disease.getDiseaseType() == null ? "未填写类型" : disease.getDiseaseType().getName();
            } else {
                type = type.substring(type.lastIndexOf('#') + 1);
            }
            record.put("diseaseTypeName", type);
            record.put("position", disease.getPosition());
            record.put("description", disease.getDescription());
            record.put("level", disease.getLevel());
            record.put("developmentTrend", disease.getDevelopmentTrend());
            records.add(record);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", String.valueOf(task.getId()));
        result.put("buildingName", displayTaskName(task));
        result.put("diseases", records);
        return result;
    }

    /**
     * 获取已上传图片的访问地址。
     */
    @PostMapping("/getFileUrl")
    @ResponseBody
    public AjaxResult getFileUrl(@RequestParam("fileId") String fileId) {
        try {
            FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(fileId));
            if (fileMap == null || fileMap.getNewName() == null) {
                return AjaxResult.error("数据不完整");
            }
            String prefix = fileMap.getNewName().substring(0, 2);
            String downloadUrl = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/"
                    + prefix + "/" + fileMap.getNewName();
            return AjaxResult.success("获取成功", downloadUrl);
        } catch (Exception e) {
            logger.error("获取文件URL失败", e);
            return AjaxResult.error("获取文件URL失败：" + e.getMessage());
        }
    }
    private List<Task> selectReportTasks(Report report) {
        if (report == null || report.getTaskIds() == null || report.getTaskIds().trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> taskIds = new ArrayList<>();
        for (String taskId : report.getTaskIds().split(",")) {
            try {
                taskIds.add(Long.parseLong(taskId.trim()));
            } catch (NumberFormatException ignored) {
                // 生成报告时会再次校验任务ID，这里只跳过无效的页面选项
            }
        }
        if (taskIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Task> taskById = taskService.selectTaskListByIds(taskIds).stream()
                .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left));
        return taskIds.stream()
                .map(taskById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 填报页大桥分组用的任务摘要，避免把整个 Task 序列进页面脚本。
     */
    private List<Map<String, Object>> toReportTaskViews(List<Task> tasks) {
        List<Map<String, Object>> views = new ArrayList<>();
        if (tasks == null) {
            return views;
        }
        for (Task task : tasks) {
            Map<String, Object> view = new HashMap<>();
            view.put("id", task.getId());
            view.put("buildingId", task.getBuildingId());
            view.put("name", displayTaskName(task));
            views.add(view);
        }
        return views;
    }

    private String displayTaskName(Task task) {
        if (task.getBuilding() != null && task.getBuilding().getName() != null
                && !task.getBuilding().getName().trim().isEmpty()) {
            String name = task.getBuilding().getName().trim();
            if (task.getBuilding().getParentName() != null
                    && !task.getBuilding().getParentName().trim().isEmpty()) {
                return task.getBuilding().getParentName().trim() + " - " + name;
            }
            return name;
        }
        return "任务 " + task.getId();
    }

    /**
     * 按 ${key}_index.ext 的文件名规则把上传文件归到对应的填报项下。
     */
    private Map<String, List<MultipartFile>> groupFilesByKey(String[] dataKeys, Integer[] dataTypes, MultipartFile[] files) {
        Map<String, List<MultipartFile>> filesByKey = new HashMap<>();
        if (files == null || files.length == 0) {
            return filesByKey;
        }
        for (int i = 0; i < dataKeys.length; i++) {
            if (dataTypes == null || i >= dataTypes.length || dataTypes[i] == null || dataTypes[i] != 1) {
                continue;
            }
            String key = dataKeys[i];
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            for (MultipartFile file : files) {
                String fileName = file.getOriginalFilename();
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }
                int underscoreIndex = fileName.indexOf('_');
                if (underscoreIndex > 0 && key.equals(fileName.substring(0, underscoreIndex))) {
                    filesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
                }
            }
        }
        return filesByKey;
    }

    /**
     * 上传新增图片并与页面上保留的图片ID合并。
     *
     * @return 合并后的值，没有任何图片时返回 null 表示该项无需保存
     */
    private String mergeUploadedFiles(List<MultipartFile> fileList, String submittedValue) throws Exception {
        List<String> minioIds = new ArrayList<>();
        if (fileList != null) {
            for (MultipartFile file : fileList) {
                if (!file.isEmpty()) {
                    FileMap uploaded = fileMapService.handleFileUpload(file);
                    minioIds.add(uploaded.getId().toString());
                }
            }
        }
        boolean hasExisting = submittedValue != null && !submittedValue.isEmpty();
        if (minioIds.isEmpty()) {
            return submittedValue == null ? null : submittedValue;
        }
        return hasExisting ? submittedValue + "," + String.join(",", minioIds) : String.join(",", minioIds);
    }
}
