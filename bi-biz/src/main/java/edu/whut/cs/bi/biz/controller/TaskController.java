package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.ProjectMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import edu.whut.cs.bi.biz.service.ITaskService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 任务Controller
 *
 * @author chenwenqi
 * @date 2025-04-09
 */
@Controller
@RequestMapping("/biz/task")
public class TaskController extends BaseController {
    private String prefix = "biz/task";

    @Resource
    private ITaskService taskService;
    @Autowired
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private BuildingMapper buildingMapper;

    @Resource
    private BiObjectMapper biObjectMapper;

    // 初始化OkHttp客户端（用于下载图片）
    private final okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @RequiresPermissions("biz:task:view")
    @GetMapping("/{select}")
    public String task(@PathVariable("select") String select, @RequestParam(name = "projectId", required = false) Long projectId, ModelMap mmap) {
        mmap.put("select", select);
        if (projectId != null) {
            mmap.put("projectId", projectId);
        }

        return prefix + "/task";
    }

    /**
     * 查询任务列表
     */
    @RequiresPermissions("biz:task:list")
    @PostMapping("/list/{select}")
    @ResponseBody
    public TableDataInfo list(@PathVariable("select") String select, Task task) {
        List<Task> list = taskService.selectTaskList(task, select);
        return getDataTable(list);
    }

    /**
     * 修改任务
     */
    @RequiresPermissions("biz:project:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Task task = taskService.selectTaskById(id);

        mmap.put("task", task);
        return prefix + "/edit";
    }

    /**
     * 修改任务
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "任务", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult changeStatus(Task task) {
        task.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(taskService.updateTask(task));
    }

    /**
     * 新增任务
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "项目-任务", businessType = BusinessType.INSERT)
    @PostMapping("/addProjectBuilding")
    @ResponseBody
    public AjaxResult addProjectBuilding(Task task) {
        task.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(taskService.insertTask(task));
    }

    /**
     * 批量新增任务
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "项目-任务", businessType = BusinessType.INSERT)
    @PostMapping("/batchAddProjectBuilding")
    @ResponseBody
    public AjaxResult batchAddProjectBuilding(Long projectId, @RequestParam List<Long> buildingIds) {

        return toAjax(taskService.batchInsertTasks(projectId, buildingIds));
    }

    /**
     * 删除项目桥梁
     */
    @RequiresPermissions("biz:project:remove")
    @Log(title = "项目-任务", businessType = BusinessType.DELETE)
    @PostMapping("/cancelProjectBuilding")
    @ResponseBody
    public AjaxResult cancelProjectBuilding(Long projectId, Long buildingId) {
        return toAjax(taskService.removeTask(projectId, buildingId));
    }

    /**
     * 批量新增项目桥梁
     */
    @RequiresPermissions("biz:project:remove")
    @Log(title = "项目-任务", businessType = BusinessType.DELETE)
    @PostMapping("/batchCancelProjectBuilding")
    @ResponseBody
    public AjaxResult batchCancelProjectBuilding(Long projectId, @RequestParam List<Long> buildingIds) {

        return toAjax(taskService.batchRemoveTasks(projectId, buildingIds));
    }

    /**
     * 病害列表
     */
    @RequiresPermissions("biz:disease:view")
    @GetMapping("/inspect/{taskId}")
    public String userslist(@PathVariable("taskId") Long taskId, ModelMap mmap) {
        Task task = taskService.selectTaskById(taskId);

        mmap.put("task", task);
        return "biz/disease/disease";
    }

    /**
     * 查询任务列表
     */
    @RequiresPermissions("biz:task:list")
    @PostMapping("/listAll/{projectId}")
    @ResponseBody
    public List<Task> tasklist(@PathVariable("projectId") Long projectId) {
        List<Task> list = taskMapper.selectFullTaskListByProjectId(projectId);
        return list;
    }

    /**
     * 批量导出多个任务的病害数据
     */
    @RequiresPermissions("biz:disease:export")
    @Log(title = "批量导出任务病害", businessType = BusinessType.EXPORT)
    @GetMapping("/batchExport")
    public void batchExport(@RequestParam("taskIds") String taskIds, HttpServletResponse response) throws IOException {
        System.out.println("开始批量导出任务病害数据，任务ID: " + taskIds);
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }

        // 解析任务ID
        String[] taskIdArray = taskIds.split(",");
        List<Long> taskIdList = new ArrayList<>();
        for (String taskId : taskIdArray) {
            try {
                taskIdList.add(Long.parseLong(taskId));
            } catch (NumberFormatException e) {
                // 忽略无效ID
            }
        }

        if (taskIdList.isEmpty()) {
            return;
        }

        // -------------------------- 步骤1：生成Excel --------------------------
        ByteArrayOutputStream excelBaos = new ByteArrayOutputStream();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("病害数据");

        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"序号", "桥梁名称", "幅别", "部位", "部件", "缺损位置", "缺损类型", "数量", "数量合计", "单位",
                "缺损情况", "维修建议", "评定类别", "照片编号", "发展趋势", "备注"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // 存储所有需要下载的图片URL
        List<String> allPhotoUrls = new ArrayList<>();

        // 行索引（从1开始，0是表头）
        int rowIndex = 1;
        // 全局图片序号
        int photoSerialNum = 1;

        // 处理每个任务的病害数据
        for (Long taskId : taskIdList) {
            Task task = taskService.selectTaskById(taskId);
            if (task == null) {
                continue;
            }

            // 查询该任务的所有病害
            Disease queryDisease = new Disease();
            queryDisease.setTaskId(taskId);
            List<Disease> diseaseList = diseaseService.selectDiseaseListForTask(queryDisease);

            // 填充Excel数据
            for (Disease disease : diseaseList) {
                Row row = sheet.createRow(rowIndex++);
                int cellIndex = 0;

                // 序号
                row.createCell(cellIndex++).setCellValue(rowIndex - 1);

                // 桥梁名称
                String bridgeName = "";
                if (disease.getBuildingId() != null) {
                    Building building = buildingMapper.selectBuildingById(disease.getBuildingId());
                    if (building != null && building.getName() != null) {
                        bridgeName = building.getName();
                    }
                }
                row.createCell(cellIndex++).setCellValue(bridgeName);

                // 幅别 - 暂无数据源
                row.createCell(cellIndex++).setCellValue("");
                BiObject biObject = biObjectMapper.selectBiObjectById(disease.getBiObjectId());
                String[] acestorsIdArray = null;
                if (biObject != null) {
                    acestorsIdArray = biObject.getAncestors().split(",");
                }
                // 第二层 object
                BiObject partLocationObject = null;
                // 第三层 object
                BiObject nextPartLocationObject = null;
                if (acestorsIdArray != null && acestorsIdArray.length >= 4) {
                    // 第二层 object
                    partLocationObject = biObjectMapper.selectBiObjectById(Long.valueOf(acestorsIdArray[2]));
                    // 第三层 object
                    nextPartLocationObject = biObjectMapper.selectBiObjectById(Long.valueOf(acestorsIdArray[3]));
                }
                // 部位 - 对应 模板 的第二层（第一层是桥名） ，如上部结构
                String partLocation = "";
                if (partLocationObject != null && partLocationObject.getName() != null) {
                    partLocation = partLocationObject.getName();
                }
                row.createCell(cellIndex++).setCellValue(partLocation);
                // 部件
                String partLocationNext = "";
                if (nextPartLocationObject != null && nextPartLocationObject.getName() != null) {
                    partLocationNext = nextPartLocationObject.getName();
                }
                row.createCell(cellIndex++).setCellValue(partLocationNext);
                // 缺损位置。
                String componentName = "";
                if (disease.getComponent() != null && disease.getComponent().getName() != null) {
                    componentName = disease.getComponent().getName();
                }


                // 缺损位置 (当前 批量导出的excel 的模板使用的是componentName)
                row.createCell(cellIndex++).setCellValue(componentName != null ? componentName : "");

                // 缺损类型
                // 批量导出的excel 模板 不需要 # 编号.
                String type = disease.getType();
                if (null != type && !type.isEmpty() && type.contains("#")) {
                    type = type.substring(type.lastIndexOf("#") + 1);
                }
                row.createCell(cellIndex++).setCellValue(type != null ? type : "");

                // 数量
                row.createCell(cellIndex++).setCellValue(disease.getQuantity());

                // 数量合计 - 暂无明确计算逻辑
                row.createCell(cellIndex++).setCellValue("");

                // 单位
                row.createCell(cellIndex++).setCellValue(disease.getUnits() != null ? disease.getUnits() : "");

                // 缺损情况（病害描述）
                row.createCell(cellIndex++).setCellValue(disease.getDescription() != null ? disease.getDescription() : "");

                // 维修建议
                row.createCell(cellIndex++).setCellValue(disease.getRepairRecommendation() != null ? disease.getRepairRecommendation() : "");

                // 评定类别（1-5）- 对应标度
                row.createCell(cellIndex++).setCellValue(disease.getLevel());

                // 照片编号 - 处理照片
                List<String> diseaseImages = disease.getImages();
                if (diseaseImages != null && !diseaseImages.isEmpty()) {
                    StringBuilder photoNames = new StringBuilder();
                    for (String imgUrl : diseaseImages) {
                        if (imgUrl == null || imgUrl.trim().isEmpty()) continue;

                        // 生成3位序号文件名（001.jpg、002.jpg...）
                        String photoFileName = String.format("%03d.jpg", photoSerialNum);

                        // 记录URL（后续下载用）
                        allPhotoUrls.add(imgUrl);

                        // 拼接图片名到Excel单元格（多个图片用逗号分隔）
                        if (photoNames.length() > 0) photoNames.append(", ");
                        photoNames.append(photoFileName);

                        // 序号自增（下一张图用）
                        photoSerialNum++;
                    }
                    row.createCell(cellIndex++).setCellValue(photoNames.toString());
                } else {
                    row.createCell(cellIndex++).setCellValue("");
                }

                // 发展趋势
                row.createCell(cellIndex++).setCellValue(disease.getDevelopmentTrend() != null ? disease.getDevelopmentTrend() : "");

                // 备注
                row.createCell(cellIndex).setCellValue(disease.getRemark() != null ? disease.getRemark() : "");
            }
        }

        // 调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, currentWidth + 10 * 256); // 256是POI中一个字符的基准宽度
        }

        workbook.write(excelBaos);
        workbook.close();

        // -------------------------- 步骤2：构建Zip（含Excel+照片文件夹） --------------------------

        // 设置Zip响应头
        response.setContentType("application/zip");
        String zipFileName = URLEncoder.encode("任务病害数据.zip", StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
        response.setHeader("Cache-Control", "no-store, no-cache");

        // 初始化Zip输出流（直接写入响应，无中间文件）
        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream());
             InputStream excelIs = new ByteArrayInputStream(excelBaos.toByteArray())) {

            // 2.1 向Zip添加Excel文件（根目录）
            ZipEntry excelEntry = new ZipEntry("病害清单.xlsx");
            zipOut.putNextEntry(excelEntry);
            byte[] buffer = new byte[1024 * 8]; // 8KB缓冲区
            int len;
            while ((len = excelIs.read(buffer)) != -1) {
                zipOut.write(buffer, 0, len);
            }
            zipOut.closeEntry(); // 关闭Excel条目

            // 2.2 向Zip添加照片文件夹及图片（001.jpg、002.jpg...）
            // 遍历所有图片URL，下载并写入Zip
            for (int i = 0; i < allPhotoUrls.size(); i++) {
                String imgUrl = allPhotoUrls.get(i);
                // 生成图片文件名（与Excel一致：001.jpg、002.jpg...）
                String photoFileName = String.format("%03d.jpg", i + 1);
                // Zip中路径：病害图片/001.jpg（放入单独文件夹）
                String zipPhotoPath = "病害图片/" + photoFileName;
                ZipEntry photoEntry = new ZipEntry(zipPhotoPath);
                zipOut.putNextEntry(photoEntry);

                // 核心：通过URL下载图片到Zip流
                try (InputStream imgIs = downloadImageByUrl(imgUrl)) {
                    if (imgIs == null) {
                        // 图片下载失败，写入提示文本（避免Zip损坏）
                        String errorMsg = "图片下载失败：" + imgUrl;
                        zipOut.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                        System.err.println(errorMsg);
                        zipOut.closeEntry();
                        continue;
                    }
                    // 将图片流写入Zip
                    while ((len = imgIs.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    // 捕获下载异常，避免整个导出失败
                    String errorMsg = "图片处理异常：" + imgUrl + "，原因：" + e.getMessage();
                    zipOut.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                    System.err.println(errorMsg);
                }
                zipOut.closeEntry(); // 关闭当前图片条目
            }

            zipOut.flush(); // 强制刷新，确保所有数据写入响应
        }
    }

    /**
     * 工具方法：通过URL下载图片，返回图片输入流
     *
     * @param imgUrl 图片URL
     * @return 图片输入流（null表示下载失败）
     */
    private InputStream downloadImageByUrl(String imgUrl) {
        try {
            // 构建HTTP请求
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(imgUrl)
                    .build();
            // 发送请求获取响应
            okhttp3.Response response = okHttpClient.newCall(request).execute();
            // 检查响应是否成功（200状态码）
            if (!response.isSuccessful()) {
                System.err.println("图片URL响应失败：" + imgUrl + "，状态码：" + response.code());
                return null;
            }
            // 返回图片输入流（无需关闭，外层try-with-resources会处理）
            return response.body().byteStream();
        } catch (Exception e) {
            System.err.println("图片下载异常：" + imgUrl + "，原因：" + e.getMessage());
            return null;
        }
    }
}
