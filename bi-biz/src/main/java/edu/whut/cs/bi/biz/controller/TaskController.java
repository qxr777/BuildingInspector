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
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final String[] BATCH_DISEASE_HEADERS = {
            "序号", "桥梁名称", "幅别", "部位", "部件", "缺损位置", "缺损类型", "数量", "数量合计", "单位",
            "缺损情况", "维修建议", "评定类别", "照片编号", "发展趋势", "备注"
    };

    /** 纯Excel普通列的最大宽度（字符数）；缺损位置、缺损类型、缺损情况使用对应的固定宽度。 */
    private static final int[] BATCH_DISEASE_EXCEL_MAX_WIDTHS = {
            8, 18, 10, 16, 16, 22, 26, 8, 10, 8, 46, 30, 10, 12, 24
    };

    private static class BatchDiseaseExcelData {
        private final Map<Long, Task> taskMap = new HashMap<>();
        private final Map<Long, List<Disease>> diseaseMap = new LinkedHashMap<>();
        private final Map<Long, Building> buildingMap = new HashMap<>();
        private final Map<Long, BiObject> biObjectMap = new HashMap<>();
        private int maxPhotoCount = 1;
    }

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
        List<Long> taskIdList = parseTaskIds(taskIds);
        if (taskIdList.isEmpty()) {
            return;
        }

        // -------------------------- 步骤1：生成Excel --------------------------
        ByteArrayOutputStream excelBaos = new ByteArrayOutputStream();
        List<String> allPhotoUrls = new ArrayList<>();
        try (Workbook workbook = buildBatchDiseaseWorkbook(taskIdList, allPhotoUrls, false)) {
            workbook.write(excelBaos);
        }

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
     * 批量导出多个任务的病害 Excel，照片编号为可点击的图片 URL。
     * 照片列统一放在末尾；同一条病害有多张照片时，在同一行增加照片列，
     * 让每个照片编号都对应一个独立的 Excel 超链接。
     */
    @RequiresPermissions("biz:disease:export")
    @Log(title = "批量导出任务病害Excel", businessType = BusinessType.EXPORT)
    @GetMapping("/batchExportExcel")
    public void batchExportExcel(@RequestParam("taskIds") String taskIds, HttpServletResponse response) throws IOException {
        System.out.println("开始批量导出任务病害Excel，任务ID: " + taskIds);
        List<Long> taskIdList = parseTaskIds(taskIds);
        if (taskIdList.isEmpty()) {
            return;
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("任务病害数据.xlsx", StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-store, no-cache");

        Workbook workbook = buildBatchDiseaseWorkbook(taskIdList, null, true);
        try {
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        } finally {
            workbook.close();
        }
    }

    private List<Long> parseTaskIds(String taskIds) {
        List<Long> taskIdList = new ArrayList<>();
        if (taskIds == null || taskIds.trim().isEmpty()) {
            return taskIdList;
        }

        for (String taskId : taskIds.split(",")) {
            try {
                taskIdList.add(Long.parseLong(taskId.trim()));
            } catch (NumberFormatException e) {
                // 忽略无效ID，保持与原批量导出逻辑一致
            }
        }
        return taskIdList;
    }

    /**
     * 构造批量病害 Excel。
     *
     * @param taskIdList   任务 ID
     * @param allPhotoUrls ZIP 导出时收集图片 URL；纯 Excel 导出时可传 null
     * @param photoLinks   是否将照片编号设置为图片 URL 超链接
     */
    private Workbook buildBatchDiseaseWorkbook(List<Long> taskIdList, List<String> allPhotoUrls,
                                               boolean photoLinks) {
        BatchDiseaseExcelData excelData = photoLinks ? loadBatchDiseaseExcelData(taskIdList) : null;
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("病害数据");
        CellStyle leftAlignedStyle = createLeftAlignedStyle(workbook);
        CellStyle hyperlinkStyle = photoLinks ? createHyperlinkStyle(workbook) : null;
        String[] headers = photoLinks
                ? createBatchDiseaseExcelHeaders(excelData.maxPhotoCount)
                : BATCH_DISEASE_HEADERS;

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers[i]);
            headerCell.setCellStyle(leftAlignedStyle);
        }

        int rowIndex = 1;
        int diseaseSerialNum = 1;
        int photoSerialNum = 1;

        for (Long taskId : taskIdList) {
            Task task = photoLinks ? excelData.taskMap.get(taskId) : taskService.selectTaskById(taskId);
            if (task == null) {
                continue;
            }

            List<Disease> diseaseList;
            if (photoLinks) {
                diseaseList = excelData.diseaseMap.getOrDefault(taskId, Collections.emptyList());
            } else {
                Disease queryDisease = new Disease();
                queryDisease.setTaskId(taskId);
                diseaseList = diseaseService.selectDiseaseListForTask(queryDisease);
            }

            for (Disease disease : diseaseList) {
                Row row = sheet.createRow(rowIndex++);
                int cellIndex = 0;

                row.createCell(cellIndex++).setCellValue(diseaseSerialNum++);

                Building building = disease.getBuildingId() == null ? null
                        : photoLinks ? excelData.buildingMap.get(disease.getBuildingId())
                        : buildingMapper.selectBuildingById(disease.getBuildingId());
                String bridgeName = building != null && building.getName() != null ? building.getName() : "";
                row.createCell(cellIndex++).setCellValue(bridgeName);

                // 幅别暂无数据源
                row.createCell(cellIndex++).setCellValue("");

                BiObject biObject = photoLinks ? excelData.biObjectMap.get(disease.getBiObjectId())
                        : biObjectMapper.selectBiObjectById(disease.getBiObjectId());
                String[] ancestorsIdArray = null;
                if (biObject != null && biObject.getAncestors() != null && !biObject.getAncestors().isEmpty()) {
                    ancestorsIdArray = biObject.getAncestors().split(",");
                }

                BiObject buildingObject = null;
                if (building != null && building.getRootObjectId() != null) {
                    buildingObject = photoLinks ? excelData.biObjectMap.get(building.getRootObjectId())
                            : biObjectMapper.selectBiObjectById(building.getRootObjectId());
                }
                boolean isFixedBridge = buildingObject != null
                        && buildingObject.getAncestors() != null
                        && buildingObject.getAncestors().length() >= 2;

                BiObject partLocationObject = null;
                BiObject nextPartLocationObject = null;
                if (ancestorsIdArray != null) {
                    int partLocationObjectIndex = isFixedBridge ? 3 : 2;
                    int nextPartLocationObjectIndex = isFixedBridge ? 4 : 3;
                    if (partLocationObjectIndex < ancestorsIdArray.length) {
                        Long partLocationObjectId = Long.valueOf(ancestorsIdArray[partLocationObjectIndex]);
                        partLocationObject = photoLinks ? excelData.biObjectMap.get(partLocationObjectId)
                                : biObjectMapper.selectBiObjectById(partLocationObjectId);
                    }
                    if (nextPartLocationObjectIndex < ancestorsIdArray.length) {
                        Long nextPartLocationObjectId = Long.valueOf(ancestorsIdArray[nextPartLocationObjectIndex]);
                        nextPartLocationObject = photoLinks ? excelData.biObjectMap.get(nextPartLocationObjectId)
                                : biObjectMapper.selectBiObjectById(nextPartLocationObjectId);
                    }
                }

                row.createCell(cellIndex++).setCellValue(
                        partLocationObject != null && partLocationObject.getName() != null
                                ? partLocationObject.getName() : "");
                row.createCell(cellIndex++).setCellValue(
                        nextPartLocationObject != null && nextPartLocationObject.getName() != null
                                ? nextPartLocationObject.getName() : "");

                String componentName = disease.getComponent() != null && disease.getComponent().getName() != null
                        ? disease.getComponent().getName() : "";
                row.createCell(cellIndex++).setCellValue(componentName);

                String type = disease.getType();
                if (type != null && !type.isEmpty() && type.contains("#")) {
                    type = type.substring(type.lastIndexOf("#") + 1);
                }
                row.createCell(cellIndex++).setCellValue(type != null ? type : "");
                row.createCell(cellIndex++).setCellValue(disease.getQuantity());
                // 数量合计暂无明确计算逻辑
                row.createCell(cellIndex++).setCellValue("");
                row.createCell(cellIndex++).setCellValue(disease.getUnits() != null ? disease.getUnits() : "");
                row.createCell(cellIndex++).setCellValue(
                        disease.getDescription() != null ? disease.getDescription() : "");
                row.createCell(cellIndex++).setCellValue(
                        disease.getRepairRecommendation() != null ? disease.getRepairRecommendation() : "");
                row.createCell(cellIndex++).setCellValue(disease.getLevel());

                List<String> photoNames = new ArrayList<>();
                List<String> photoUrls = new ArrayList<>();
                List<String> diseaseImages = disease.getImages();
                if (diseaseImages != null) {
                    for (String imgUrl : diseaseImages) {
                        if (imgUrl == null || imgUrl.trim().isEmpty()) {
                            continue;
                        }
                        String photoFileName = String.format("%03d.jpg", photoSerialNum++);
                        photoNames.add(photoFileName);
                        photoUrls.add(imgUrl);
                        if (allPhotoUrls != null) {
                            allPhotoUrls.add(imgUrl);
                        }
                    }
                }

                if (photoLinks) {
                    row.createCell(cellIndex++).setCellValue(
                            disease.getDevelopmentTrend() != null ? disease.getDevelopmentTrend() : "");
                    row.createCell(cellIndex++).setCellValue(
                            disease.getRemark() != null ? disease.getRemark() : "");

                    for (int i = 0; i < excelData.maxPhotoCount; i++) {
                        Cell photoCell = row.createCell(cellIndex++);
                        if (i < photoNames.size()) {
                            setPhotoHyperlink(photoCell, photoNames.get(i), photoUrls.get(i), workbook, hyperlinkStyle);
                        }
                    }
                    applyLeftAlignment(row, leftAlignedStyle, headers.length);
                } else {
                    Cell photoCell = row.createCell(cellIndex++);
                    photoCell.setCellValue(String.join(", ", photoNames));
                    row.createCell(cellIndex++).setCellValue(
                            disease.getDevelopmentTrend() != null ? disease.getDevelopmentTrend() : "");
                    row.createCell(cellIndex).setCellValue(disease.getRemark() != null ? disease.getRemark() : "");
                    applyLeftAlignment(row, leftAlignedStyle, headers.length);
                }
            }
        }

        int maxColumnWidth = 255 * 256;
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = Math.min(sheet.getColumnWidth(i), maxColumnWidth);
            int compactWidth = Math.min(currentWidth + 2 * 256, maxColumnWidth);
            if (i == 5 || i == 6 || i == 10) {
                // 缺损位置、缺损类型、缺损情况使用指定的固定宽度。
                sheet.setColumnWidth(i, BATCH_DISEASE_EXCEL_MAX_WIDTHS[i] * 256);
            } else {
                int compactMaxWidth = getBatchDiseaseColumnMaxWidth(i, photoLinks) * 256;
                sheet.setColumnWidth(i, Math.min(compactWidth, compactMaxWidth));
            }
        }
        return workbook;
    }

    /**
     * 批量准备纯Excel导出数据。ZIP导出不调用此方法，继续沿用原查询链路。
     */
    private BatchDiseaseExcelData loadBatchDiseaseExcelData(List<Long> taskIdList) {
        BatchDiseaseExcelData data = new BatchDiseaseExcelData();

        for (Task task : taskService.selectTaskListByIds(taskIdList)) {
            data.taskMap.put(task.getId(), task);
        }

        List<Disease> diseases = diseaseService.selectDiseaseListForExcel(taskIdList);
        Set<Long> buildingIds = new LinkedHashSet<>();
        Set<Long> initialBiObjectIds = new LinkedHashSet<>();
        for (Disease disease : diseases) {
            data.diseaseMap.computeIfAbsent(disease.getTaskId(), key -> new ArrayList<>()).add(disease);
            if (disease.getImages() != null) {
                int photoCount = 0;
                for (String imageUrl : disease.getImages()) {
                    if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                        photoCount++;
                    }
                }
                data.maxPhotoCount = Math.max(data.maxPhotoCount, photoCount);
            }
            if (disease.getBuildingId() != null) {
                buildingIds.add(disease.getBuildingId());
            }
            if (disease.getBiObjectId() != null) {
                initialBiObjectIds.add(disease.getBiObjectId());
            }
        }

        if (!buildingIds.isEmpty()) {
            for (Building building : buildingMapper.selectBuildingsByIds(new ArrayList<>(buildingIds))) {
                data.buildingMap.put(building.getId(), building);
                if (building.getRootObjectId() != null) {
                    initialBiObjectIds.add(building.getRootObjectId());
                }
            }
        }

        putValidBiObjects(data.biObjectMap, initialBiObjectIds);

        Set<Long> ancestorIds = new LinkedHashSet<>();
        for (Disease disease : diseases) {
            BiObject biObject = data.biObjectMap.get(disease.getBiObjectId());
            if (biObject == null || biObject.getAncestors() == null || biObject.getAncestors().isEmpty()) {
                continue;
            }
            for (String ancestorId : biObject.getAncestors().split(",")) {
                try {
                    Long id = Long.valueOf(ancestorId.trim());
                    if (!data.biObjectMap.containsKey(id)) {
                        ancestorIds.add(id);
                    }
                } catch (NumberFormatException ignored) {
                    // 与原导出保持空值容错，非法祖先ID不参与批量查询。
                }
            }
        }
        putValidBiObjects(data.biObjectMap, ancestorIds);
        return data;
    }

    private void putValidBiObjects(Map<Long, BiObject> target, Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        for (BiObject biObject : biObjectMapper.selectBiObjectsByIds(new ArrayList<>(ids))) {
            if ("0".equals(biObject.getDelFlag())) {
                target.put(biObject.getId(), biObject);
            }
        }
    }

    private String[] createBatchDiseaseExcelHeaders(int photoCount) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            headers.add(BATCH_DISEASE_HEADERS[i]);
        }
        headers.add(BATCH_DISEASE_HEADERS[14]);
        headers.add(BATCH_DISEASE_HEADERS[15]);
        for (int i = 0; i < photoCount; i++) {
            headers.add(photoCount == 1 ? "照片" : "照片" + (i + 1));
        }
        return headers.toArray(new String[0]);
    }

    private int getBatchDiseaseColumnMaxWidth(int columnIndex, boolean photoLinks) {
        if (photoLinks) {
            return columnIndex < BATCH_DISEASE_EXCEL_MAX_WIDTHS.length
                    ? BATCH_DISEASE_EXCEL_MAX_WIDTHS[columnIndex] : 12;
        }

        // ZIP内Excel仍保持“照片编号、发展趋势、备注”的原列顺序。
        if (columnIndex == 13) {
            return 12;
        }
        if (columnIndex == 14) {
            return BATCH_DISEASE_EXCEL_MAX_WIDTHS[13];
        }
        if (columnIndex == 15) {
            return BATCH_DISEASE_EXCEL_MAX_WIDTHS[14];
        }
        return BATCH_DISEASE_EXCEL_MAX_WIDTHS[columnIndex];
    }

    private CellStyle createLeftAlignedStyle(Workbook workbook) {
        CellStyle leftAlignedStyle = workbook.createCellStyle();
        leftAlignedStyle.setAlignment(HorizontalAlignment.LEFT);
        leftAlignedStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return leftAlignedStyle;
    }

    private CellStyle createHyperlinkStyle(Workbook workbook) {
        Font hyperlinkFont = workbook.createFont();
        hyperlinkFont.setColor(IndexedColors.BLUE.getIndex());
        hyperlinkFont.setUnderline(Font.U_SINGLE);

        CellStyle hyperlinkStyle = workbook.createCellStyle();
        hyperlinkStyle.setFont(hyperlinkFont);
        hyperlinkStyle.setAlignment(HorizontalAlignment.LEFT);
        hyperlinkStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return hyperlinkStyle;
    }

    private void applyLeftAlignment(Row row, CellStyle leftAlignedStyle, int columnCount) {
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null && cell.getHyperlink() == null) {
                cell.setCellStyle(leftAlignedStyle);
            }
        }
    }

    private void setPhotoHyperlink(Cell cell, String displayName, String imageUrl,
                                   Workbook workbook, CellStyle hyperlinkStyle) {
        cell.setCellValue(displayName);
        Hyperlink hyperlink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
        hyperlink.setAddress(imageUrl);
        cell.setHyperlink(hyperlink);
        cell.setCellStyle(hyperlinkStyle);
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
