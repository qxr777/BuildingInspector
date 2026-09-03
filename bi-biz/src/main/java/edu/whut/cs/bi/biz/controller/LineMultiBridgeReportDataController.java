package edu.whut.cs.bi.biz.controller;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.LineBridgeGroup;
import edu.whut.cs.bi.biz.domain.LineReportData;
import edu.whut.cs.bi.biz.domain.Report;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.service.ILineMultiBridgeReportDataService;
import edu.whut.cs.bi.biz.service.IReportDataService;
import edu.whut.cs.bi.biz.service.IReportService;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
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
    private IReportDataService reportDataService;

    @Autowired
    private IReportService reportService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private FileMapServiceImpl fileMapService;

    @Autowired
    private MinioConfig minioConfig;

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

            for (int i = 0; i < dataKeys.length; i++) {
                String key = dataKeys[i];
                // 跳过空的key（前端为了防止Spring分割数组而添加的占位符）
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                Integer type = dataTypes != null && i < dataTypes.length ? dataTypes[i] : 0;
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
     * 获取指定桥梁的构件病害数据，供病害选择器使用。
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
                return AjaxResult.success("获取成功", Collections.emptyList());
            }
            Map<Long, Map<String, Object>> byTask = reportDataService.getDiseaseComponentData(report);
            List<Object> diseases = new ArrayList<>();
            for (Long id : targetTaskIds) {
                Map<String, Object> taskData = byTask.get(id);
                if (taskData != null && taskData.get("diseases") instanceof List) {
                    diseases.addAll((List<?>) taskData.get("diseases"));
                }
            }
            return AjaxResult.success("获取成功", diseases);
        } catch (Exception e) {
            logger.error("获取构件病害数据失败", e);
            return AjaxResult.error("获取构件病害数据失败：" + e.getMessage());
        }
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
