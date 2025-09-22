package edu.whut.cs.bi.biz.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import edu.whut.cs.bi.biz.domain.enums.AreaIdentifierEnums;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 病害Controller
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
@Controller
@Slf4j
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

    // 初始化OkHttp客户端（设置超时，避免下载卡死）
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // 连接超时10秒
            .readTimeout(15, TimeUnit.SECONDS)    // 读取超时15秒
            .build();

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
        List<Disease> diseaseList = diseaseService.selectDiseaseList(disease);
        return getDataTable(diseaseList);
    }

    /**
     * 导出病害列表
     */
    @RequiresPermissions("biz:disease:export")
    @Log(title = "病害", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(Disease disease, HttpServletResponse response) throws IOException {
        List<Disease> list = diseaseService.selectDiseaseListForTask(disease);
        // -------------------------- 步骤1：生成Excel（关联001/002图片名） --------------------------
        //  创建Excel工作簿

        // 用于记录图片序号（全局连续：001、002、003...）
        int photoSerialNum = 1;
        // 存储“图片序号→图片URL”的映射（后续下载用）
        List<String> allPhotoUrls = new ArrayList<>();
        ByteArrayOutputStream excelBaos = new ByteArrayOutputStream();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("病害数据");

        //  创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"构件编号", "缺损类型", "构件", "缺损位置", "病害描述", "标度", "长度(m)", "宽度(m)", "缝宽(mm)", "高度/深度(m)", "面积(㎡)", "照片名称", "发展趋势", "备注", "病害数量"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // 填充数据
        for (int i = 0; i < list.size(); i++) {
            Disease item = list.get(i);
            Row row = sheet.createRow(i + 1);
            List<DiseaseDetail> detailList = null;
            if (!item.getDiseaseDetails().isEmpty()) {
                detailList = item.getDiseaseDetails();
            }

            // 构件编号
            row.createCell(0).setCellValue(item.getComponent().getCode());
            // 病害类型
            row.createCell(1).setCellValue(item.getType());
            // 构件名称
            row.createCell(2).setCellValue(item.getComponent().getName());
            // 位置
            row.createCell(3).setCellValue(item.getPosition());
            // 病害描述
            row.createCell(4).setCellValue(item.getDescription());
            // 标度
            row.createCell(5).setCellValue(item.getLevel());
            //发展趋势。
            row.createCell(12).setCellValue(item.getDevelopmentTrend());
            //备注
            if (item.getRemark() != null) {
                row.createCell(13).setCellValue(item.getRemark());
            }
            // 病害数量
            row.createCell(14).setCellValue(item.getQuantity());
            int detail_length = detailList.size();
            if (detail_length == 1) {
                DiseaseDetail detail = detailList.get(0);
                //长度
                if (detail.getLength1() != null) {
                    row.createCell(6).setCellValue(detail.getLength1().toPlainString());
                }
                //宽度
                if (detail.getWidth() != null) {
                    row.createCell(7).setCellValue(detail.getWidth().toPlainString());
                }
                //缝宽
                if (detail.getCrackWidth() != null) {
                    row.createCell(8).setCellValue(detail.getCrackWidth().toPlainString());
                }
                //高度深度
                if (detail.getHeightDepth() != null) {
                    row.createCell(9).setCellValue(detail.getHeightDepth().toPlainString());
                }
                //面积
                // 判断 是否 是 均值。
                if (detail.getAreaWidth() != null && detail.getAreaLength() != null) {
                    if (detail.getAreaIdentifier() != null && detail.getAreaIdentifier().equals(AreaIdentifierEnums.AVERAGE.getCode())) {
                        row.createCell(10).setCellValue("S均:" + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
                    } else if (detail.getAreaIdentifier() != null && detail.getAreaIdentifier().equals(AreaIdentifierEnums.COUNT.getCode())) {
                        row.createCell(10).setCellValue("S总:" + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
                    } else {
                        row.createCell(10).setCellValue("S均:" + detail.getAreaLength().toPlainString() + "x" + detail.getAreaWidth().toPlainString());
                    }
                }
            } else if (detail_length > 1) {
                DiseaseDetail detail = detailList.get(0);
                //长度
                if (detail.getLength1() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getLength1).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
                    row.createCell(6).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                }
                //宽度
                if (detail.getWidth() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getWidth).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
                    row.createCell(7).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                }
                //缝宽
                if (detail.getCrackWidth() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getCrackWidth).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
                    row.createCell(8).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                }
                //高度深度
                if (detail.getHeightDepth() != null) {
                    List<BigDecimal> tempList = detailList.stream().map(DiseaseDetail::getHeightDepth).toList();
                    BigDecimal max = tempList.stream().filter(num -> num != null).max(BigDecimal::compareTo).get();
                    BigDecimal min = tempList.stream().filter(num -> num != null).min(BigDecimal::compareTo).get();
                    row.createCell(9).setCellValue(min.toPlainString() + "-" + max.toPlainString());
                }
                //面积
                if (detail.getAreaWidth() != null && detail.getAreaLength() != null) {
                    BigDecimal count = BigDecimal.valueOf(0L);
                    List<BigDecimal> areaWidthList = detailList.stream().map(DiseaseDetail::getAreaWidth).toList();
                    List<BigDecimal> areaLengthList = detailList.stream().map(DiseaseDetail::getAreaLength).toList();
                    for (int index = 0; index < detail_length; index++) {
                        count.add(areaWidthList.get(index).multiply(areaLengthList.get(index)));
                    }
                    row.createCell(10).setCellValue("S总:" + count.toPlainString());
                }

            }

            //处理照片名称
            List<String> diseaseImages = item.getImages(); // 获取当前病害的图片URL列表
            if (diseaseImages != null && !diseaseImages.isEmpty()) {
                // 拼接当前病害的所有图片名称（如“001.jpg, 002.jpg”）
                StringBuilder photoNames = new StringBuilder();
                for (String imgUrl : diseaseImages) {
                    // 跳过空URL
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
                // 将拼接的图片名写入Excel“照片名称”列（第10列，索引从0开始）
                row.createCell(11).setCellValue(photoNames.toString());
            }
        }

        // 调整列宽
        for (int i = 0; i < headers.length; i++) {
            // 先自动计算宽度
            sheet.autoSizeColumn(i);
            // 额外增加 10 个字符的宽度（避免内容紧贴边框）
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, currentWidth + 10 * 256); // 256是POI中一个字符的基准宽度
        }
        workbook.write(excelBaos); // Excel写入字节流
        // 关闭资源
        workbook.close();


        // -------------------------- 步骤2：构建Zip（含Excel+照片文件夹） --------------------------

        // 设置Zip响应头
        response.setContentType("application/zip");
        String buildingName = "未知建筑";
        Building building = buildingMapper.selectBuildingById(disease.getBuildingId());
        if (building != null && hasText(building.getName())) {
            buildingName = building.getName();
        }
        String zipFileName = URLEncoder.encode(buildingName + "病害数据.zip", StandardCharsets.UTF_8.name());
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

    // 原生逻辑：替代StringUtils.hasText(String str)
    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }


    /**
     * 工具方法：通过URL下载图片，返回图片输入流
     *
     * @param imgUrl 图片URL（如http://xxx.com/photos/123.jpg）
     * @return 图片输入流（null表示下载失败）
     */
    private InputStream downloadImageByUrl(String imgUrl) {
        try {
            // 构建HTTP请求
            Request request = new Request.Builder()
                    .url(imgUrl)
                    .build();
            // 发送请求获取响应
            Response response = okHttpClient.newCall(request).execute();
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

        String imgNoExp = disease.getImgNoExp();
        if (imgNoExp != null) {
            ObjectMapper mapper = new ObjectMapper();

            try {
                List<String> imgs = mapper.readValue(imgNoExp, new TypeReference<List<String>>() {
                });
                disease.setImgNoExp(imgs.stream().collect(Collectors.joining("、")));
            } catch (JsonProcessingException e) {
                log.error("图片格式有误转化失败");
            }

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

    @RequiresPermissions("biz:disease:add")
    @PostMapping("/upload/diseaseHistoryPhotos")
    @ResponseBody
    public AjaxResult diseaseHistoryPhotos(@RequestParam("photos") List<MultipartFile> photos, @RequestParam("taskId") Long taskId) {

        List<String> unmatchedPhotos = readFileService.uploadPictures(photos, taskId);

        if (!unmatchedPhotos.isEmpty()) {
            return AjaxResult.success(unmatchedPhotos);
        }

        return AjaxResult.success();
    }
}
