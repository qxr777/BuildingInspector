package edu.whut.cs.bi.biz.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Report;
import edu.whut.cs.bi.biz.service.IReportService;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.ReportData;
import edu.whut.cs.bi.biz.service.IReportDataService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 报告数据Controller
 *
 * @author wanzheng
 */
@Controller
@RequestMapping("/biz/report_data")
public class ReportDataController extends BaseController {
    private String prefix = "biz/report_data";

    @Autowired
    private IReportDataService reportDataService;

    @Autowired
    private FileMapServiceImpl fileMapService;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private IReportService reportService;

    /**
     * 查询报告数据列表
     */
    @RequiresPermissions("biz:report_data:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ReportData reportData) {
        List<ReportData> list = reportDataService.selectReportDataList(reportData);
        return getDataTable(list);
    }

    /**
     * 导出报告数据列表
     */
    @RequiresPermissions("biz:report_data:export")
    @Log(title = "报告数据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(ReportData reportData) {
        List<ReportData> list = reportDataService.selectReportDataList(reportData);
        ExcelUtil<ReportData> util = new ExcelUtil<ReportData>(ReportData.class);
        return util.exportExcel(list, "报告数据");
    }

    /**
     * 新增报告数据
     */
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存报告数据
     */
    @RequiresPermissions("biz:report_data:add")
    @Log(title = "报告数据", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(ReportData reportData) {
        return toAjax(reportDataService.insertReportData(reportData));
    }

    /**
     * 修改报告数据
     */
    @RequiresPermissions("biz:report_data:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        ReportData reportData = reportDataService.selectReportDataById(id);
        mmap.put("reportData", reportData);
        return prefix + "/edit";
    }

    /**
     * 修改保存报告数据
     */
    @RequiresPermissions("biz:report_data:edit")
    @Log(title = "报告数据", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(ReportData reportData) {
        return toAjax(reportDataService.updateReportData(reportData));
    }

    /**
     * 删除报告数据
     */
    @RequiresPermissions("biz:report_data:remove")
    @Log(title = "报告数据", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(reportDataService.deleteReportDataByIds(ids));
    }

    /**
     * 保存报告数据
     */
    @RequiresPermissions("biz:report_data:edit")
    @Log(title = "报告数据", businessType = BusinessType.UPDATE)
    @PostMapping("/save")
    @ResponseBody
    public AjaxResult save(
            @RequestParam("reportId") Long reportId,
            @RequestParam(value = "dataKeys", required = false) String[] dataKeys,
            @RequestParam(value = "dataValues", required = false) String[] dataValues,
            @RequestParam(value = "dataTypes", required = false) Integer[] dataTypes,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        if (dataKeys == null || dataKeys.length == 0) {
            return AjaxResult.error("没有要保存的数据");
        }

        try {
            List<ReportData> dataList = new ArrayList<>();

            // 创建一个Map来存储每个key对应的文件列表
            Map<String, List<MultipartFile>> fileMap = new HashMap<>();

            // 如果有文件上传，先整理文件
            if (files != null && files.length > 0) {
                for (int i = 0; i < dataKeys.length; i++) {
                    if (dataTypes[i] == 1) { 
                        String key = dataKeys[i];

                        // 从文件名中提取key
                        for (MultipartFile file : files) {
                            String fileName = file.getOriginalFilename();
                            if (fileName != null && !fileName.isEmpty()) {
                                // 假设文件名格式为：${key}_index.ext
                                int underscoreIndex = fileName.indexOf('_');
                                if (underscoreIndex > 0) {
                                    String fileKey = fileName.substring(0, underscoreIndex);
                                    if (key.equals(fileKey)) {
                                        // 将文件添加到对应key的列表中
                                        fileMap.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 处理数据
            for (int i = 0; i < dataKeys.length; i++) {
                String key = dataKeys[i];
                Integer type = dataTypes[i];

                ReportData reportData = new ReportData();
                reportData.setReportId(reportId);
                reportData.setKey(key);
                reportData.setType(type);

                if (type == 0) {
                    // 文本类型
                    reportData.setValue(dataValues[i]);
                    dataList.add(reportData);
                } else if (type == 1) {
                    // 图片类型
                    List<MultipartFile> fileList = fileMap.get(key);
                    if (fileList != null && !fileList.isEmpty()) {
                        // 上传多个文件并获取MinIO ID列表
                        List<String> minioIds = new ArrayList<>();
                        for (MultipartFile file : fileList) {
                            if (!file.isEmpty()) {
                                FileMap fileMap1 = fileMapService.handleFileUpload(file);
                                minioIds.add(fileMap1.getId().toString());
                            }
                        }

                        // 将新上传的MinIO ID添加到已有的ID列表中
                        if (!minioIds.isEmpty()) {
                            // 获取已有的值（如果有）
                            String existingValue = "";
                            if (dataValues != null && i < dataValues.length && dataValues[i] != null && !dataValues[i].isEmpty()) {
                                existingValue = dataValues[i];
                            }

                            // 合并现有值和新上传的值
                            if (!existingValue.isEmpty()) {
                                reportData.setValue(existingValue + "," + String.join(",", minioIds));
                            } else {
                                reportData.setValue(String.join(",", minioIds));
                            }
                            dataList.add(reportData);
                        } else if (dataValues != null && i < dataValues.length && dataValues[i] != null) {
                            // 如果没有新上传的文件，但有原来的值，保留原值
                            reportData.setValue(dataValues[i]);
                            dataList.add(reportData);
                        }
                    } else if (dataValues != null && i < dataValues.length && dataValues[i] != null) {
                        // 如果没有新上传的文件，但有原来的值，保留原值
                        reportData.setValue(dataValues[i]);
                        dataList.add(reportData);
                    }
                }
            }

            return toAjax(reportDataService.saveReportDataBatch(reportId, dataList));
        } catch (Exception e) {
            logger.error("保存报告数据失败", e);
            return AjaxResult.error("保存报告数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取文件下载URL
     */
    @PostMapping("/getFileUrl")
    @ResponseBody
    public AjaxResult getFileUrl(@RequestParam("fileId") String fileId) {
        try {
            FileMap fileMap = fileMapService.selectFileMapById(Long.valueOf(fileId));
            if (fileMap == null || fileMap.getNewName() == null || fileMap.getOldName() == null) {
                return AjaxResult.error("数据不完整");
            }
            String prefix = fileMap.getNewName().substring(0, 2);
            String downloadUrl = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" +
                    prefix + "/" + fileMap.getNewName();
            return AjaxResult.success("获取成功", downloadUrl);
        } catch (Exception e) {
            logger.error("获取文件URL失败", e);
            return AjaxResult.error("获取文件URL失败：" + e.getMessage());
        }
    }

    /**
     * 获取构件病害数据（用于病害选择器）
     */
    @PostMapping("/getDiseaseComponentData")
    @ResponseBody
    public AjaxResult getDiseaseComponentData(@RequestParam("reportId") Long reportId) {
        try {
            Report report = reportService.selectReportById(reportId);
            if(report == null) {
                return AjaxResult.error("未找到报告");
            }
            List<Map<String, Object>> diseaseComponentData = reportDataService.getDiseaseComponentData(report);
            return AjaxResult.success("获取成功", diseaseComponentData);
        } catch (Exception e) {
            logger.error("获取构件病害数据失败", e);
            return AjaxResult.error("获取构件病害数据失败：" + e.getMessage());
        }
    }

    /**
     * 跳转到单桥梁桥报告数据填充页面
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/fill_single_beam/{id}")
    public String fillSingleBeam(@PathVariable("id") Long id, ModelMap mmap) {
        Report report = reportService.selectReportById(id);
        mmap.put("report", report);
        return "biz/report_data/fill_single_beam";
    }

    /**
     * 跳转到单桥拱桥报告数据填充页面
     */
    @RequiresPermissions("biz:report:edit")
    @GetMapping("/fill_single_arch/{id}")
    public String fillSingleArch(@PathVariable("id") Long id, ModelMap mmap) {
        Report report = reportService.selectReportById(id);
        mmap.put("report", report);
        return "biz/report_data/fill_single_arch";
    }

} 