package edu.whut.cs.bi.biz.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 病害Controller
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
@Controller
@RequestMapping("/biz/disease")
public class DiseaseController extends BaseController {
    private String prefix = "biz/disease";

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private ITaskService taskService;

    @Resource
    private IBiObjectService biObjectService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private MinioConfig minioConfig;

    @Resource
    private IBiTemplateObjectService biTemplateObjectService;

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Resource
    private ReadFileService readFileService;
    @Autowired
    private BuildingMapper buildingMapper;

    @RequiresPermissions("biz:disease:view")
    @GetMapping()
    public String disease() {
        return prefix + "/disease";
    }

    /**
     * 查询病害列表
     */
    @RequiresPermissions("biz:disease:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Disease disease) {
        List<Disease> properties = diseaseService.selectDiseaseList(disease);
        return getDataTable(properties);
    }

    /**
     * 导出病害列表
     */
    @RequiresPermissions("biz:disease:export")
    @Log(title = "病害", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(Disease disease, HttpServletResponse response) throws IOException {
        List<Disease> list = diseaseService.selectDiseaseList(disease);
        // 2. 创建Excel工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("病害数据");

        // 3. 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"构件编号", "缺损类型", "缺损位置", "病害描述", "标度", "长度(m)", "宽度(m)", "缝宽(mm)", "高度/深度(m)", "面积(㎡)", "照片名称", "备注", "病害数量"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // 4. 填充数据
        for (int i = 0; i < list.size(); i++) {
            Disease item = list.get(i);
            Row row = sheet.createRow(i + 1);
            DiseaseDetail detail = null;
            if (!item.getDiseaseDetails().isEmpty()) {
                detail = item.getDiseaseDetails().get(0);
            }

            // 构件编号
            row.createCell(0).setCellValue(item.getComponent().getCode());
            // 病害类型
            row.createCell(1).setCellValue(item.getType());
            // 位置
            row.createCell(2).setCellValue(item.getPosition());
            // 病害描述
            row.createCell(3).setCellValue(item.getDescription());
            // 标度
            row.createCell(4).setCellValue(item.getLevel());
            //备注
            if (item.getRemark() != null) {
                row.createCell(11).setCellValue(item.getRemark());
            }
            // 病害数量
            row.createCell(12).setCellValue(item.getQuantity());
            if (detail == null) {
                continue;
            }
            //长度
            if (detail.getLength1() != null) {
                row.createCell(5).setCellValue(detail.getLength1().toPlainString());
            }
            //宽度
            if (detail.getWidth() != null) {
                row.createCell(6).setCellValue(detail.getWidth().toPlainString());
            }
            //缝宽
            if (detail.getCrackWidth() != null) {
                row.createCell(7).setCellValue(detail.getCrackWidth().toPlainString());
            }
            //高度深度
            if (detail.getHeightDepth() != null) {
                row.createCell(8).setCellValue(detail.getHeightDepth().toPlainString());
            }
            //面积
            if (detail.getAreaWidth() != null && detail.getAreaLength() != null) {
                row.createCell(9).setCellValue(detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
            }
            //照片名称 未找到
        }

        // 5. 调整列宽
        for (int i = 0; i < headers.length; i++) {
            // 先自动计算宽度
            sheet.autoSizeColumn(i);
            // 额外增加2个字符的宽度（避免内容紧贴边框）
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, currentWidth + 10 * 256); // 256是POI中一个字符的基准宽度
        }

        // 6. 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = buildingMapper.selectBuildingById(disease.getBuildingId()).getName() + "病害清单.xlsx";
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-store, no-cache");

        // 7. 写入响应输出流
        workbook.write(response.getOutputStream());
        // 8. 关闭资源
        workbook.close();
    }


    /**
     * 新增病害
     */
    @GetMapping(value = {"/add/{taskId}/{biObjectId}"})
    public String add(@PathVariable("taskId") Long taskId, @PathVariable("biObjectId") Long biObjectId, ModelMap mmap) {
        if (taskId != null) {
            mmap.put("task", taskService.selectTaskById(taskId));
        }
        if (biObjectId != null) {
            mmap.put("biObject", biObjectService.selectBiObjectById(biObjectId));
        }

        return prefix + "/add";
    }

    /**
     * 新增保存病害
     */
    @RequiresPermissions("biz:disease:add")
    @Log(title = "病害", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    @Transactional
    public AjaxResult addSave(@Valid Disease disease, @RequestParam(value = "files", required = false) MultipartFile[] files) {
        disease.setCreateBy(ShiroUtils.getLoginName());
        if (files != null && files.length > 0) {
            disease.setAttachmentCount(files.length);
        }

        diseaseService.insertDisease(disease);
        if (files != null && files.length > 0) {
            diseaseService.handleDiseaseAttachment(files, disease.getId(), 1);
        }

        return toAjax(Math.toIntExact(disease.getId()));
    }


    /**
     * 修改病害
     */
    @RequiresPermissions("biz:disease:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Disease disease = diseaseService.selectDiseaseById(id);

        BiObject biObject = disease.getBiObject();
        mmap.put("biObject", biObject);
        if (biObject.getName().equals("其他")) {
            String customPosition = disease.getPosition();
            disease.setPosition(String.valueOf(biObject.getChildren().get(0).getId()));
            mmap.put("customPosition", customPosition);
        }

        // 位置
        mmap.put("disease", disease);

        return prefix + "/edit";
    }

    /**
     * 修改保存病害
     */
    @RequiresPermissions("biz:disease:edit")
    @Log(title = "病害", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(
            @Valid Disease disease,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "existingAttachmentIds", required = false) String existingAttachmentIds,
            @RequestParam(value = "deletedAttachmentIds", required = false) String[] deletedAttachmentIds
    ) {
        disease.setUpdateBy(ShiroUtils.getLoginName());
        List<Attachment> attachment = attachmentService.getAttachmentBySubjectId(disease.getId());
        Set<String> existingIdSet = null;
        if (StrUtil.isNotEmpty(existingAttachmentIds)) {
            existingIdSet = StrUtil.split(existingAttachmentIds, ',')
                    .stream()
                    .map(String::trim)
                    .collect(Collectors.toSet());
        } else {
            existingIdSet = new HashSet<>();
        }

        if (CollUtil.isNotEmpty(attachment)) {
            Set<String> finalExistingIdSet = existingIdSet;
            String attachmentIds = attachment.stream()
                    .filter(e -> e.getName().startsWith("disease"))
                    .map(e -> e.getId().toString())
                    .filter(id -> !finalExistingIdSet.contains(id))
                    .collect(Collectors.joining(","));
            attachmentService.deleteAttachmentByIds(attachmentIds);
        }
        diseaseService.handleDiseaseAttachment(files, disease.getId(), 1);
        if (files != null && files.length > 0) {
            disease.setAttachmentCount(files.length + existingIdSet.size());
        }

        return toAjax(diseaseService.updateDisease(disease));
    }

    /**
     * 删除
     */
    @RequiresPermissions("biz:disease:remove")
    @Log(title = "病害", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(diseaseService.deleteDiseaseByIds(ids));
    }

    /**
     * 修改病害
     */
    @RequiresPermissions("biz:disease:list")
    @GetMapping("/showDiseaseDetail/{id}")
    public String showDiseaseDetail(@PathVariable("id") Long id, ModelMap mmap) {
        Disease disease = diseaseService.selectDiseaseById(id);
        mmap.put("disease", disease);
        mmap.put("biObject", disease.getBiObject());

        return prefix + "/detail";
    }

    /**
     * 构件扣分
     */
    @RequiresPermissions("biz:disease:add")
    @GetMapping("/computeDeductPoints")
    @ResponseBody
    public AjaxResult computeDeductPoints(int maxScale, int scale) {

        if (StringUtils.isNull(maxScale) || StringUtils.isNull(scale)) {
            return AjaxResult.error("参数错误");
        }

        return AjaxResult.success(diseaseService.computeDeductPoints(maxScale, scale));
    }


    @GetMapping("/attachments/{id}")
    @ResponseBody  // 添加此注解以返回JSON数据
    public AjaxResult getAttachments(@PathVariable("id") Long id) {
        try {
            // 获取病害对应的附件列表
            List<Map<String, Object>> result = getDiseaseImage(id);

            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("获取附件列表失败：" + e.getMessage());
        }
    }


    /**
     * 获取与指定病害ID关联的附件信息，特别是以 "disease" 开头的图片类附件。
     * 该接口会返回包含附件ID、文件名、访问URL及是否为图片类型的附件列表，
     * 供前端展示病害相关的图片和其他附件信息。
     *
     * @param id 病害的唯一标识
     *           返回的List立面有个map，map.get(“url”)就是病害图片的url，要看一下map.get(”isImage“)是否为true
     */
    @NotNull
    public List<Map<String, Object>> getDiseaseImage(Long id) {
        List<Attachment> attachments = attachmentService.getAttachmentList(id).stream().filter(e -> e.getName().startsWith("disease")).toList();

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Attachment attachment : attachments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attachment.getId());
            map.put("fileName", attachment.getName().split("_")[1]);
            FileMap fileMap = fileMapService.selectFileMapById(attachment.getMinioId());
            if (fileMap == null) continue;
            String s = fileMap.getNewName();
            map.put("url", minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" + s.substring(0, 2) + "/" + s);
            // 根据文件后缀判断是否为图片
            map.put("isImage", isImageFile(attachment.getName()));
            map.put("type", attachment.getType());
            result.add(map);
        }
        return result;
    }

    // 判断文件是否为图片的辅助方法
    private boolean isImageFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String extension = fileName.toLowerCase();
        return extension.endsWith(".jpg") ||
                extension.endsWith(".jpeg") ||
                extension.endsWith(".png") ||
                extension.endsWith(".gif") ||
                extension.endsWith(".bmp");
    }

    @DeleteMapping("/attachment/delete/{fileId}")
    @ResponseBody  // 添加此注解以返回JSON数据
    @Transactional
    public AjaxResult deleteAttachment(@PathVariable("fileId") Long id) {
        Attachment attachment = attachmentMapper.selectById(id);

        Disease disease = diseaseService.selectDiseaseById(attachment.getSubjectId());
        disease.setAttachmentCount(disease.getAttachmentCount() - 1);
        disease.setUpdateBy(ShiroUtils.getLoginName());
        disease.setUpdateTime(DateUtils.getNowDate());
        diseaseMapper.updateDisease(disease);

        attachmentService.deleteAttachmentById(id);

        return AjaxResult.success("success");
    }

    @PostMapping("/causeAnalysis")
    @ResponseBody
    public AjaxResult getCauseAnalysis(@RequestBody CauseQuery causeQuery) {
        // 获取根对象
        BiObject rootObject = biObjectService.selectBiObjectById(causeQuery.getObjectId());
        String[] split = rootObject.getAncestors().split(",");
        if (split.length > 1) {
            rootObject = biObjectService.selectBiObjectById(Long.parseLong(split[1]));
        }
        if (rootObject != null && rootObject.getTemplateObjectId() != null) {
            // 获取模板对象
            BiTemplateObject templateObject = biTemplateObjectService.selectBiTemplateObjectById(rootObject.getTemplateObjectId());
            if (templateObject != null && templateObject.getName() != null) {
                causeQuery.setTemplate(templateObject.getName());
            }
        }
        return AjaxResult.success(diseaseService.getCauseAnalysis(causeQuery));
    }

    @RequiresPermissions("biz:disease:add")
    @GetMapping("/importCBMS")
    public String importCBMS(@RequestParam("taskId") Long taskId, ModelMap mmap) {
        mmap.put("taskId", taskId);
        return prefix + "/importCBMS";
    }

    @RequiresPermissions("biz:disease:add")
    @PostMapping("/upload/CBMSExcel")
    @ResponseBody
    public AjaxResult uploadCBMSExcel(@RequestParam("file") MultipartFile file, @RequestParam("taskId") Long taskId) {
        readFileService.readCBMSDiseaseExcel(file, taskId);

        return AjaxResult.success("上传成功");
    }

    @RequiresPermissions("biz:disease:add")
    @GetMapping("/importHistory")
    public String importHistory(@RequestParam("taskId") Long taskId, ModelMap mmap) {
        mmap.put("taskId", taskId);
        return prefix + "/importHistory";
    }

    @RequiresPermissions("biz:disease:add")
    @PostMapping("/upload/diseaseHistoryExcel")
    @ResponseBody
    public AjaxResult diseaseHistoryExcel(@RequestParam("file") MultipartFile file, @RequestParam("taskId") Long taskId) {
        readFileService.readDiseaseExcel(file, taskId);

        return AjaxResult.success("上传成功");
    }

}
