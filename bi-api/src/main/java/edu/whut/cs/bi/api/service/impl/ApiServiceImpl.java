package edu.whut.cs.bi.api.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.api.service.ApiService;
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
import java.util.stream.Collectors;
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

    /**
     * 上传桥梁压缩包
     */
    @Override
    public AjaxResult uploadBridgeData(MultipartFile file, Long taskId) {
        if (file.isEmpty()) {
            return AjaxResult.error("上传文件为空");
        }

        // 检查文件是否为ZIP格式
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.endsWith(".zip")) {
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

//            // TEMP: 20260913 确保保存所有压缩包目录存在
//            Path normalZipDir = Paths.get("logs", "sys-normal-zips");
//            Files.createDirectories(normalZipDir);
//
//            // 构造文件名：时间戳 + 原始文件名
//            String timeSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//            String safeFileName = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.zip");
//            Path normalZipPath = normalZipDir.resolve(timeSuffix + "_" + safeFileName);
//
//            // 保存原始压缩包
//            Files.copy(file.getInputStream(), normalZipPath, StandardCopyOption.REPLACE_EXISTING);
//            // TEMP: end of 20260913 确保压缩包目录存在

            // 创建临时目录存放解压文件
            tempDir = Files.createTempDirectory("bridge_upload_");
            Map<String, Path> extractedFiles = new HashMap<>();

            try (ZipInputStream zipIn = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        // 获取文件路径并统一路径分隔符为 /
                        String filePath = entry.getName().replace('\\', '/');

                        // 创建文件并保存
                        Path outputPath = tempDir.resolve(filePath);
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

            Task submitTask = null;
            if (taskId != null) {
                submitTask = taskMapper.selectTaskById(taskId);
                if (submitTask == null) {
                    throw new ServiceException("未找到ID为 " + taskId + " 的检测任务");
                }
                if (!buildingId.equals(submitTask.getBuildingId())) {
                    throw new ServiceException("检测任务 " + taskId + " 与桥梁 " + buildingId + " 不对应");
                }
                projectId = submitTask.getProjectId();
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
                // 同一座桥同一年的病害文件可能混有多个检测任务的病害，指定了任务时只处理该任务的
                if (taskId != null) {
                    int totalCount = diseases.size();
                    diseases = diseases.stream()
                            .filter(disease -> taskId.equals(disease.getTaskId()))
                            .toList();
                    if (diseases.size() < totalCount) {
                        log.info("桥梁 {} 上传只处理任务 {} 的病害 {} 条，跳过其他任务的病害 {} 条",
                                buildingId, taskId, diseases.size(), totalCount - diseases.size());
                    }
                }
                // 批量保存病害数据
                if (!diseases.isEmpty()) {
                    if (submitTask == null) {
                        projectId = diseases.get(0).getProjectId();
                    }
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
            }

            // 正立面照独立于病害 JSON：压缩包有 frontPhoto.json 就处理，避免无病害文件时整段被跳过
            importElevationPhotos(extractedFiles, buildingId);

            if (submitTask != null) {
                submitTask.setType(1);
                submitTask.setUpdateBy(ShiroUtils.getLoginName());
                taskMapper.updateTask(submitTask);
            } else if (projectId != null) {
                Task queryTask = new Task();
                queryTask.setBuildingId(buildingId);
                queryTask.setProjectId(projectId);
                List<Task> tasks = taskMapper.selectTaskList(queryTask, null);
                if (!tasks.isEmpty()) {
                    Task task = tasks.get(0);
                    task.setType(1);
                    task.setUpdateBy(ShiroUtils.getLoginName());
                    taskMapper.updateTask(task);
                }
            }
            //处理检测任务的表格数据
            importInspectionSheets(extractedFiles, buildingId, projectId, submitTask);

            AjaxResult result = AjaxResult.success("桥梁数据上传成功");
            if (taskId != null) {
                result.put("taskId", taskId);
            }
            return result;
        } catch (Exception e) {
            try {
                // 确保目录存在
                Path errorZipDir = Paths.get("logs", "sys-error-zips");
                Files.createDirectories(errorZipDir);

                // 构造文件名：时间戳 + 原始文件名
                String timeSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String safeFileName = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.zip");
                Path errorZipPath = errorZipDir.resolve(timeSuffix + "_" + safeFileName);

                // 保存原始压缩包
                Files.copy(file.getInputStream(), errorZipPath, StandardCopyOption.REPLACE_EXISTING);

                // 记录日志
                log.error("处理上传文件失败，压缩包已保存到: {}", errorZipPath, e);
            } catch (IOException ioEx) {
                log.error("保存异常压缩包失败", ioEx);
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

    private void importInspectionSheets(Map<String, Path> extractedFiles, Long buildingId, Long projectId, Task submitTask) throws IOException {
        String sheetsPrefix = buildingId + "/sheets/";
        List<Map.Entry<String, Path>> sheetEntries = extractedFiles.entrySet().stream()
                .filter(entry -> isInspectionSheetJson(entry.getKey(), sheetsPrefix))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        if (sheetEntries.isEmpty()) {
            return;
        }

        Task task = submitTask;
        if (task == null) {
            if (projectId == null) {
                throw new ServiceException("检测记录表导入失败：无法通过病害数据获取projectId，不能定位检测任务");
            }
            task = resolveUniqueTaskForSheets(projectId, buildingId);
        }
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

    /**
     * 按 frontPhoto.json 槽位替换正立面照。
     * 仅当压缩包中真实存在对应图片时才删除旧附件并上传；声明了路径但缺文件时跳过该槽，避免只删不补。
     * 槽位固定映射：frontLeft/sideLeft → index 0，frontRight/sideRight → index 1。
     */
    private void importElevationPhotos(Map<String, Path> extractedFiles, Long buildingId) throws IOException {
        String frontPhotoJsonPath = buildingId + "/frontPhoto.json";
        if (!extractedFiles.containsKey(frontPhotoJsonPath)) {
            return;
        }

        String frontPhotoJson = new String(Files.readAllBytes(extractedFiles.get(frontPhotoJsonPath)));
        JSONObject frontPhotoObject = JSONObject.parseObject(frontPhotoJson);
        if (frontPhotoObject == null) {
            log.warn("frontPhoto.json 解析为空，跳过正立面照处理: buildingId={}", buildingId);
            return;
        }

        List<Attachment> existAttachments = attachmentService.getAttachmentBySubjectId(buildingId)
                .stream()
                .filter(e -> e.getName() != null && e.getName().matches("^\\d+_(newfront|newside)_.*$"))
                .toList();

        replaceElevationPhotoSlot(extractedFiles, buildingId, existAttachments, frontPhotoObject, "frontLeft", "newfront", 0);
        replaceElevationPhotoSlot(extractedFiles, buildingId, existAttachments, frontPhotoObject, "frontRight", "newfront", 1);
        replaceElevationPhotoSlot(extractedFiles, buildingId, existAttachments, frontPhotoObject, "sideLeft", "newside", 0);
        replaceElevationPhotoSlot(extractedFiles, buildingId, existAttachments, frontPhotoObject, "sideRight", "newside", 1);
    }

    private void replaceElevationPhotoSlot(
            Map<String, Path> extractedFiles,
            Long buildingId,
            List<Attachment> existAttachments,
            JSONObject frontPhotoObject,
            String field,
            String type,
            int index
    ) throws IOException {
        String imagePath = firstFrontPhotoPath(frontPhotoObject, field);
        if (imagePath == null) {
            return;
        }

        Path imagePathOnDisk = resolveExtractedImagePath(extractedFiles, imagePath);
        if (imagePathOnDisk == null) {
            log.warn("正立面照声明了路径但压缩包中缺少图片，跳过替换: buildingId={}, field={}, path={}",
                    buildingId, field, imagePath);
            return;
        }

        String namePrefix = index + "_" + type;
        String attachmentIds = existAttachments.stream()
                .filter(e -> e.getName() != null && e.getName().startsWith(namePrefix))
                .map(e -> String.valueOf(e.getId()))
                .collect(Collectors.joining(","));
        if (!attachmentIds.isEmpty()) {
            attachmentService.deleteAttachmentByIds(attachmentIds);
        }

        File imageFile = imagePathOnDisk.toFile();
        String contentType = Files.probeContentType(imagePathOnDisk);
        MockMultipartFile mockFile = new MockMultipartFile(
                type,
                imageFile.getName(),
                contentType != null ? contentType : "image/jpeg",
                Files.readAllBytes(imagePathOnDisk));
        fileMapController.uploadAttachment(buildingId, mockFile, type, index);
    }

    private String firstFrontPhotoPath(JSONObject frontPhotoObject, String field) {
        JSONArray paths = frontPhotoObject.getJSONArray(field);
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.getString(i);
            if (path != null && !path.trim().isEmpty()) {
                return path.trim().replace('\\', '/');
            }
        }
        return null;
    }

    private Path resolveExtractedImagePath(Map<String, Path> extractedFiles, String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return null;
        }
        String normalized = imagePath.replace('\\', '/').replaceAll("^/+", "");
        if (extractedFiles.containsKey(normalized)) {
            return extractedFiles.get(normalized);
        }
        String withBuildingPrefix = "building/" + normalized;
        if (extractedFiles.containsKey(withBuildingPrefix)) {
            return extractedFiles.get(withBuildingPrefix);
        }
        return null;
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
