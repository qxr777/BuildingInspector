package edu.whut.cs.bi.api.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.api.service.ApiService;
import edu.whut.cs.bi.api.service.OssBridgeUploadService;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.DiseaseServiceImpl;
import edu.whut.cs.bi.biz.service.impl.TaskServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @Author:wanzheng
 * @Date:2025/7/11 08:54
 * @Description:
 **/
@Slf4j
@Service
public class ApiServiceImpl implements ApiService {
    @Resource
    private IBuildingService buildingService;

    @Resource
    private IBiObjectService biObjectService;

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private FileMapController fileMapController;

    @Resource
    private AttachmentService attachmentService;

    @Autowired
    private DiseaseServiceImpl diseaseServiceImpl;
    @Autowired
    private TaskServiceImpl taskServiceImpl;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private ITaskSheetService taskSheetService;
    @Autowired
    private OssBridgeUploadService ossBridgeUploadService;

    /**
     * 上传桥梁压缩包
     */
    @Override
    public AjaxResult uploadBridgeData(MultipartFile file) {
        if (file.isEmpty()) {
            return AjaxResult.error("上传文件为空");
        }

        return processBridgeData(file.getOriginalFilename(), file::getInputStream, true);
    }

    /**
     * 平板已将 ZIP 上传至 OSS 后，从 OSS 读取并使用与普通上传完全相同的解析流程。
     */
    @Override
    public AjaxResult uploadBridgeDataFromOss(String objectName) {
        String validatedObjectName = ossBridgeUploadService.validateObjectName(objectName);
        String originalFileName = ossBridgeUploadService.getOriginalFileName(validatedObjectName);

        AjaxResult result = processBridgeData(
                originalFileName,
                () -> ossBridgeUploadService.openObjectStream(validatedObjectName),
                false);
        if (result.isSuccess()) {
            // OSS 只作为临时高带宽中转空间。若当前请求由事务包裹，必须等事务真正提交
            // 再删除对象；否则事务回滚会造成“ZIP 已删但数据库未完整落库”。
            deleteOssObjectAfterTransactionCommit(validatedObjectName);
        }
        return result;
    }

    private void deleteOssObjectAfterTransactionCommit(String objectName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ossBridgeUploadService.deleteObject(objectName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    ossBridgeUploadService.deleteObject(objectName);
                } catch (Exception e) {
                    // 数据已提交，不能再以删除失败为由回滚；保留对象供后续人工或定时清理。
                    log.error("数据库已提交，但删除OSS临时ZIP失败，objectName={}", objectName, e);
                }
            }
        });
    }

    /**
     * 上传桥梁压缩包的公共解析入口。无论文件来自 HTTP Multipart 还是 OSS，均使用该实现。
     */
    private AjaxResult processBridgeData(String originalFileName,
                                         InputStreamSupplier inputStreamSupplier,
                                         boolean saveFailedZipLocally) {

        // 检查文件是否为ZIP格式
        if (originalFileName == null || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ServiceException("请上传ZIP格式的文件");
        }

        Path tempDir = null;
        Long buildingId = null;
        Long projectId = null;
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);

        try {
            String fileNameWithoutSuffix = originalFileName.substring(0, originalFileName.lastIndexOf("."));
            String[] fileNameParts = fileNameWithoutSuffix.split("_");
            try {
                if (fileNameParts.length > 0) {
                    buildingId = Long.parseLong(fileNameParts[0]);
                }
                // 4. 提取year（如果有下划线分隔，第二个部分就是year）
                if (fileNameParts.length >= 2) {
                    currentYear = Integer.parseInt(fileNameParts[1]);
                }
            } catch (NumberFormatException e) {
                throw new ServiceException("压缩包文件名格式错误，应为：buildingId.zip 或 buildingId_year.zip");
            }

            // 创建临时目录存放解压文件
            tempDir = Files.createTempDirectory("bridge_upload_");
            Map<String, Path> extractedFiles = new HashMap<>();

            try (InputStream inputStream = inputStreamSupplier.open();
                 ZipInputStream zipIn = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        // 获取文件路径并统一路径分隔符为 /
                        String filePath = entry.getName().replace('\\', '/');

                        // 创建文件并保存。normalize 后必须仍在临时目录中，防止 ZIP Slip 路径穿越。
                        Path outputPath = tempDir.resolve(filePath).normalize();
                        if (!outputPath.startsWith(tempDir)) {
                            throw new ServiceException("压缩包包含非法文件路径");
                        }
                        Files.createDirectories(outputPath.getParent());
                        Files.copy(zipIn, outputPath, StandardCopyOption.REPLACE_EXISTING);
                        extractedFiles.put(filePath, outputPath);
                    }
                }
            }

            // 验证buildingId是否有效
            if (buildingId == null) {
                throw  new ServiceException("压缩包结构无效：未找到有效的buildingId目录");
            }

            Building building = buildingService.selectBuildingById(buildingId);
            if (building == null) {
                throw  new ServiceException("未找到ID为 " + buildingId + " 的建筑物");
            }

            // 处理桥梁结构数据
            String objectJsonPath = buildingId + "/object.json";
            if (extractedFiles.containsKey(objectJsonPath)) {
                String objectJson = new String(Files.readAllBytes(extractedFiles.get(objectJsonPath)));
                BiObject rootObject = JSONObject.parseObject(objectJson, BiObject.class);

                // 确保rootObject的ID与数据库中的一致
                if (!rootObject.getId().equals(building.getRootObjectId())) {
                    throw  new ServiceException("building与桥梁数据不对应");
                }
                biObjectService.updateBiObjectTreeRecursively(rootObject, extractedFiles);
            }

            // 处理病害数据 - 根据当前年份获取对应的JSON文件
            String diseaseDir = buildingId + "/disease/";
            String yearJsonFileName = currentYear + ".json";

            Optional<String> jsonFilePathOpt = extractedFiles.keySet().stream()
                    .filter(path -> path.startsWith(diseaseDir) && path.endsWith(yearJsonFileName))
                    .findFirst();

            if (jsonFilePathOpt.isPresent()) {
                String jsonFilePath = jsonFilePathOpt.get();
                String diseaseJson = new String(Files.readAllBytes(extractedFiles.get(jsonFilePath)));
                diseaseJson = sanitizeDiseaseJson(diseaseJson);
                // 检查JSON格式，处理可能的包装对象
                JSONObject jsonObject;
                List<Disease> diseases;
                try {
                    jsonObject = JSONObject.parseObject(diseaseJson);
                    // 检查是否有diseases数组字段
                    if (jsonObject.containsKey("diseases")) {
                        diseases = jsonObject.getJSONArray("diseases").toJavaList(Disease.class);
                    } else {
                        // 直接尝试解析为数组
                        diseases = JSONObject.parseArray(diseaseJson, Disease.class);
                    }
                } catch (Exception e) {
                    log.error("病害JSON解析失败，原始JSON: {}", diseaseJson, e);
                    throw new ServiceException("病害数据JSON格式错误: " + e.getMessage());
                }

                for (Disease disease : diseases) {
                    if (!disease.getBuildingId().equals(building.getId())) {
                        throw  new ServiceException("building与病害数据不对应");
                    }
                }
                // 批量保存病害数据
                if (!diseases.isEmpty()) {
                    projectId =diseases.get(0).getProjectId();
                    diseaseService.batchSaveDiseases(diseases);
                }
                // 处理病害图片
                for (Disease disease : diseases) {
                    // 只有类型为1的才需要新增图片文件
                    if (disease.getCommitType() == 1) {
                        List<String> images = disease.getImages();
                        List<String> ADImages = disease.getADImgs();
                        List<File> imagesFiles = new ArrayList<>();
                        List<File> adImagesFiles = new ArrayList<>();
                        if (images != null && !images.isEmpty()) {
                            for (String imagePath : images) {
                                if (imagePath != null && !imagePath.isEmpty()) {
                                    // 检查路径是否已经包含buildingId
                                    String fullPath = imagePath;
                                    // 尝试查找文件
                                    if (extractedFiles.containsKey(fullPath)) {
                                        // 处理图片附件
                                        File imageFile = extractedFiles.get(fullPath).toFile();
                                        imagesFiles.add(imageFile);
                                    }
                                }
                            }
                            // 调用handleDiseaseAttachment方法
                            if (!imagesFiles.isEmpty()) {
                                diseaseServiceImpl.handleDiseaseAttachmentWithFile(imagesFiles, 1, disease.getId());
                            }
                        }
                        if (ADImages != null && !ADImages.isEmpty()) {
                            for (String imagePath : ADImages) {
                                if (imagePath != null && !imagePath.isEmpty()) {
                                    // 检查路径是否已经包含buildingId
                                    String fullPath = imagePath;
                                    // 尝试查找文件
                                    if (extractedFiles.containsKey(fullPath)) {
                                        // 处理图片附件
                                        File imageFile = extractedFiles.get(fullPath).toFile();
                                        adImagesFiles.add(imageFile);
                                    }
                                }
                            }
                            // 调用handleDiseaseAttachment方法
                            if (!adImagesFiles.isEmpty()) {
                                diseaseServiceImpl.handleDiseaseAttachmentWithFile(adImagesFiles, 7, disease.getId());
                            }
                        }

                    }
                }
                // 处理桥梁图片数据

                String frontPhotoJsonPath = buildingId + "/frontPhoto.json";
                if (extractedFiles.containsKey(frontPhotoJsonPath)) {
                    String frontPhotoJson = new String(Files.readAllBytes(extractedFiles.get(frontPhotoJsonPath)));
                    JSONObject jsonObject2 = JSONObject.parseObject(frontPhotoJson);

                    // 获取现有的桥梁附件
                    List<Attachment> existAttachments = attachmentService.getAttachmentBySubjectId(buildingId)
                            .stream()
                            .filter(e -> e.getName().matches("^\\d+_(newfront|newside)_.*$"))
                            .toList();

                    // 检查是否需要删除现有图片
                    StringJoiner attachmentJoiner = new StringJoiner(",");

                    // 处理前视图左侧图片
                    List<String> frontLeftPaths = jsonObject2.getJSONArray("frontLeft").toJavaList(String.class);
                    if (!frontLeftPaths.isEmpty()) {
                        // 如果有新的frontLeft图片，删除现有的0_newfront
                        existAttachments.stream()
                                .filter(e -> e.getName().startsWith("0_newfront"))
                                .forEach(e -> attachmentJoiner.add(String.valueOf(e.getId())));
                    }

                    // 处理前视图右侧图片
                    List<String> frontRightPaths = jsonObject2.getJSONArray("frontRight").toJavaList(String.class);
                    if (!frontRightPaths.isEmpty()) {
                        // 如果有新的frontRight图片，删除现有的1_newfront
                        existAttachments.stream()
                                .filter(e -> e.getName().startsWith("1_newfront"))
                                .forEach(e -> attachmentJoiner.add(String.valueOf(e.getId())));
                    }

                    // 处理侧视图左侧图片
                    List<String> sideLeftPaths = jsonObject2.getJSONArray("sideLeft").toJavaList(String.class);
                    if (!sideLeftPaths.isEmpty()) {
                        // 如果有新的sideLeft图片，删除现有的0_newside
                        existAttachments.stream()
                                .filter(e -> e.getName().startsWith("0_newside"))
                                .forEach(e -> attachmentJoiner.add(String.valueOf(e.getId())));
                    }

                    // 处理侧视图右侧图片
                    List<String> sideRightPaths = jsonObject2.getJSONArray("sideRight").toJavaList(String.class);
                    if (!sideRightPaths.isEmpty()) {
                        // 如果有新的sideRight图片，删除现有的1_newside
                        existAttachments.stream()
                                .filter(e -> e.getName().startsWith("1_newside"))
                                .forEach(e -> attachmentJoiner.add(String.valueOf(e.getId())));
                    }

                    // 如果有附件需要删除，执行删除操作
                    String attachmentIds = attachmentJoiner.toString();
                    if (!attachmentIds.isEmpty()) {
                        attachmentService.deleteAttachmentByIds(attachmentIds);
                    }

                    // 处理前视图图片
                    List<MultipartFile> frontFiles = new ArrayList<>();
                    List<String> frontPaths = new ArrayList<>();
                    frontPaths.addAll(frontLeftPaths);
                    frontPaths.addAll(frontRightPaths);

                    for (String imagePath : frontPaths) {
                        if (extractedFiles.containsKey(imagePath)) {
                            File imageFile = extractedFiles.get(imagePath).toFile();
                            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
                            MockMultipartFile mockFile = new MockMultipartFile(
                                    "front",
                                    imageFile.getName(),
                                    Files.probeContentType(imageFile.toPath()),
                                    fileContent);
                            frontFiles.add(mockFile);
                        }
                    }

                    // 处理侧视图图片
                    List<MultipartFile> sideFiles = new ArrayList<>();
                    List<String> sidePaths = new ArrayList<>();
                    sidePaths.addAll(sideLeftPaths);
                    sidePaths.addAll(sideRightPaths);

                    for (String imagePath : sidePaths) {
                        if (extractedFiles.containsKey(imagePath)) {
                            File imageFile = extractedFiles.get(imagePath).toFile();
                            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
                            MockMultipartFile mockFile = new MockMultipartFile(
                                    "side",
                                    imageFile.getName(),
                                    Files.probeContentType(imageFile.toPath()),
                                    fileContent);
                            sideFiles.add(mockFile);
                        }
                    }

                    // 调用上传桥梁图片方法
                    if (!frontFiles.isEmpty() || !sideFiles.isEmpty()) {
                        MultipartFile[] frontArray = frontFiles.isEmpty() ? new MultipartFile[0] : frontFiles.toArray(new MultipartFile[0]);
                        MultipartFile[] sideArray = sideFiles.isEmpty() ? new MultipartFile[0] : sideFiles.toArray(new MultipartFile[0]);
                        uploadBridgeDataImage(buildingId, frontArray, sideArray);
                    }
                }
                if (projectId != null) {
                    Task queryTask = new Task();
                    queryTask.setBuildingId(buildingId);
                    queryTask.setProjectId(projectId);
                    List<Task> tasks = taskMapper.selectTaskList(queryTask, null);
                    if(!tasks.isEmpty()) {
                        Task task = tasks.get(0);
                        task.setType(1);
                        task.setUpdateBy(ShiroUtils.getLoginName());
                        taskMapper.updateTask(task);
                    }
                }
            }
            //处理检测任务的表格数据
            importInspectionSheets(extractedFiles, buildingId, projectId);

            return AjaxResult.success("桥梁数据上传成功");
        } catch (Exception e) {
            if (saveFailedZipLocally) {
                saveFailedZipLocally(originalFileName, inputStreamSupplier, e);
            } else {
                // OSS 文件保留在 Bucket 中，供调用方修正后以相同 objectName 重试。
                log.error("处理OSS上传文件失败，文件名: {}", originalFileName, e);
            }
            return AjaxResult.error("处理上传文件失败：" + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    // 忽略清理错误
                }
            }
        }
    }

    private void saveFailedZipLocally(String originalFileName,
                                      InputStreamSupplier inputStreamSupplier,
                                      Exception originalException) {
        try {
            Path errorZipDir = Paths.get("logs", "sys-error-zips");
            Files.createDirectories(errorZipDir);

            String timeSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path errorZipPath = errorZipDir.resolve(timeSuffix + "_" + getFileName(originalFileName));
            try (InputStream inputStream = inputStreamSupplier.open()) {
                Files.copy(inputStream, errorZipPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.error("处理上传文件失败，压缩包已保存到: {}", errorZipPath, originalException);
        } catch (Exception saveException) {
            log.error("保存异常压缩包失败", saveException);
        }
    }

    private String getFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "upload.zip";
        }
        String normalized = fileName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream open() throws IOException;
    }

    private void importInspectionSheets(Map<String, Path> extractedFiles, Long buildingId, Long projectId) throws IOException {
        String sheetsPrefix = buildingId + "/sheets/";
        List<Map.Entry<String, Path>> sheetEntries = extractedFiles.entrySet().stream()
                .filter(entry -> isInspectionSheetJson(entry.getKey(), sheetsPrefix))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        if (sheetEntries.isEmpty()) {
            return;
        }

        if (projectId == null) {
            throw new ServiceException("检测记录表导入失败：无法通过病害数据获取projectId，不能定位检测任务");
        }

        Task task = resolveUniqueTaskForSheets(projectId, buildingId);
        for (Map.Entry<String, Path> entry : sheetEntries) {
            String sheetType = extractSheetType(entry.getKey(), sheetsPrefix);
            validateSheetType(sheetType);

            byte[] jsonBytes = Files.readAllBytes(entry.getValue());
            taskSheetService.saveOrUpdateSheet(task.getId(), buildingId, sheetType, jsonBytes, sheetType + ".json");
        }
    }

    private boolean isInspectionSheetJson(String path, String sheetsPrefix) {
        if (path == null || !path.startsWith(sheetsPrefix) || !path.endsWith(".json")) {
            return false;
        }
        String relativePath = path.substring(sheetsPrefix.length());
        return !relativePath.isEmpty() && !relativePath.contains("/");
    }

    private String extractSheetType(String path, String sheetsPrefix) {
        String fileName = path.substring(sheetsPrefix.length());
        return fileName.substring(0, fileName.length() - ".json".length());
    }

    private Task resolveUniqueTaskForSheets(Long projectId, Long buildingId) {
        Task queryTask = new Task();
        queryTask.setProjectId(projectId);
        queryTask.setBuildingId(buildingId);
        List<Task> tasks = taskMapper.selectTaskList(queryTask, null);
        if (tasks == null || tasks.isEmpty()) {
            throw new ServiceException("检测记录表导入失败：未找到projectId=" + projectId + "、buildingId=" + buildingId + "对应的检测任务");
        }
        if (tasks.size() > 1) {
            throw new ServiceException("检测记录表导入失败：projectId=" + projectId + "、buildingId=" + buildingId + "匹配到多个检测任务");
        }
        return tasks.get(0);
    }

    private void validateSheetType(String sheetType) {
        if (!taskSheetService.supportsJsonSheetWord(sheetType)) {
            throw new ServiceException("检测记录表导入失败：不支持的表格类型 " + sheetType);
        }
    }

    public void uploadBridgeDataImage(long id, MultipartFile frontFile[], MultipartFile sideFile[]) {
        for (int i = 0; i < frontFile.length; i++) {
            fileMapController.uploadAttachment(id, frontFile[i], "newfront", i);
        }
        for (int i = 0; i < sideFile.length; i++) {
            fileMapController.uploadAttachment(id, sideFile[i], "newside", i);
        }
    }

    private static final Set<String> NUMERIC_FIELDS = new HashSet<>(Arrays.asList(
            "reference1LocationStart", "reference1LocationEnd",
            "reference2LocationStart", "reference2LocationEnd",
            "length1", "length2", "length3", "width", "heightDepth", "crackWidth",
            "areaLength", "areaWidth", "deformation",
            "lengthRangeStart", "lengthRangeEnd",
            "widthRangeStart", "widthRangeEnd",
            "heightDepthRangeStart", "heightDepthRangeEnd",
            "crackWidthRangeStart", "crackWidthRangeEnd",
            "areaRangeStart", "areaRangeEnd",
            "deformationRangeStart", "deformationRangeEnd",
            "areaIdentifier", "angle", "numeratorRatio", "denominatorRatio"
    ));

    private String sanitizeDiseaseJson(String json) {
        json = json.replaceAll("\"areaIdentifier\"\\s*:\\s*\"普通\"", "\"areaIdentifier\":0");
        json = json.replaceAll("\"areaIdentifier\"\\s*:\\s*\"平均\"", "\"areaIdentifier\":1");
        json = json.replaceAll("\"areaIdentifier\"\\s*:\\s*\"总计\"", "\"areaIdentifier\":2");
        Object parsed = JSON.parse(json);
        sanitizeNumericFields(parsed);
        return JSON.toJSONString(parsed);
    }

    private void sanitizeNumericFields(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jsonObj = (JSONObject) obj;
            for (String key : new HashSet<>(jsonObj.keySet())) {
                Object value = jsonObj.get(key);
                if (value instanceof String) {
                    String s = ((String) value).trim();
                    if (s.isEmpty()) {
                        jsonObj.put(key, null);
                    } else if (NUMERIC_FIELDS.contains(key)) {
                        try {
                            new BigDecimal(s);
                        } catch (NumberFormatException e) {
                            jsonObj.put(key, null);
                        }
                    }
                } else if (value instanceof JSONObject || value instanceof JSONArray) {
                    sanitizeNumericFields(value);
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            for (int i = 0; i < arr.size(); i++) {
                sanitizeNumericFields(arr.get(i));
            }
        }
    }
}
