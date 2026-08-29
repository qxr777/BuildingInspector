package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.FileMap;
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

    /**
     * 多桥报告填报页面
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/fill/{id}")
    public String fill(@PathVariable("id") Long id, ModelMap mmap) {
        Report report = reportService.selectReportById(id);
        mmap.put("report", report);
        mmap.put("reportTasks", selectReportTasks(report));
        return FILL_PAGE;
    }

    /**
     * 查询填报数据。taskId 为空表示线路级数据。
     */
    @RequiresPermissions("biz:report_data:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(@RequestParam("reportId") Long reportId,
                             @RequestParam(value = "taskId", required = false) Long taskId) {
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
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "dataKeys", required = false) String[] dataKeys,
            @RequestParam(value = "dataValues", required = false) String[] dataValues,
            @RequestParam(value = "dataTypes", required = false) Integer[] dataTypes,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        if (ObjectUtils.isEmpty(dataKeys) || ObjectUtils.isEmpty(dataValues)) {
            return AjaxResult.success("没有要保存的数据");
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
                if (taskId == null && !lineKey) {
                    // 没有桥梁任务ID的请求只能写线路级字段，避免多座桥共用一份桥梁数据。
                    continue;
                }
                if (taskId != null && lineKey) {
                    continue;
                }

                LineReportData reportData = new LineReportData();
                reportData.setReportId(reportId);
                reportData.setTaskId(taskId);
                reportData.setBuildingId(buildingId);
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
                                          @RequestParam(value = "taskId", required = false) Long taskId) {
        try {
            Report report = reportService.selectReportById(reportId);
            if (report == null) {
                return AjaxResult.error("报告不存在");
            }
            Long targetTaskId = taskId;
            if (targetTaskId == null) {
                List<Task> tasks = selectReportTasks(report);
                if (tasks.isEmpty()) {
                    return AjaxResult.error("报告未关联任务");
                }
                targetTaskId = tasks.get(0).getId();
            }
            Map<String, Object> taskData = reportDataService.getDiseaseComponentData(report).get(targetTaskId);
            if (taskData == null) {
                return AjaxResult.success("获取成功", Collections.emptyList());
            }
            return AjaxResult.success("获取成功", taskData.get("diseases"));
        } catch (Exception e) {
            logger.error("获取构件病害数据失败", e);
            return AjaxResult.error("获取构件病害数据失败：" + e.getMessage());
        }
    }

    /**
     * 按报告关联的任务顺序返回任务列表，页面的桥梁顺序与报告章节顺序保持一致。
     */
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
