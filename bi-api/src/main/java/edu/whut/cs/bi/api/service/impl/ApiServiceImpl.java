package edu.whut.cs.bi.api.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.api.service.ApiService;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.DiseaseServiceImpl;
import edu.whut.cs.bi.biz.service.impl.TaskServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @Author:wanzheng
 * @Date:2025/7/11 08:54
 * @Description:
 **/
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

    /**
     * 上传桥梁压缩包
     */
    @Override
    public AjaxResult uploadBridgeData(MultipartFile file) {
        if (file.isEmpty()) {
            return AjaxResult.error("上传文件为空");
        }

        // 检查文件是否为ZIP格式
        if (!file.getOriginalFilename().endsWith(".zip")) {
            return AjaxResult.error("请上传ZIP格式的文件");
        }

        Path tempDir = null;
        try {
            // 创建临时目录存放解压文件
            tempDir = Files.createTempDirectory("bridge_upload_");
            Map<String, Path> extractedFiles = new HashMap<>();
            Long buildingId = null;

            // 解压文件

            try (ZipInputStream zipIn = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        // 获取文件路径
                        String filePath = entry.getName().replace('\\', '/');

                        // 提取buildingId
                        if (buildingId == null) {
                            String[] pathParts = filePath.split("/");
                            if (pathParts.length > 0) {
                                try {
                                    buildingId = Long.parseLong(pathParts[0]);
                                } catch (NumberFormatException e) {
                                    // 忽略非数字目录名
                                }
                            }
                        }

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
                return AjaxResult.error("压缩包结构无效：未找到有效的buildingId目录");
            }

            Building building = buildingService.selectBuildingById(buildingId);
            if (building == null) {
                return AjaxResult.error("未找到ID为 " + buildingId + " 的建筑物");
            }

            // 处理桥梁结构数据
            String objectJsonPath = buildingId + "/object.json";
            if (extractedFiles.containsKey(objectJsonPath)) {
                String objectJson = new String(Files.readAllBytes(extractedFiles.get(objectJsonPath)));
                BiObject rootObject = JSONObject.parseObject(objectJson, BiObject.class);

                // 确保rootObject的ID与数据库中的一致
                if (!rootObject.getId().equals(building.getRootObjectId())) {
                    return AjaxResult.error("building与桥梁数据不对应");
                }
                biObjectService.updateBiObjectTreeRecursively(rootObject, extractedFiles);
            }

            // 处理病害数据 - 根据当前年份获取对应的JSON文件
            String diseaseDir = buildingId + "/disease/";
            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);
            String yearJsonFileName = currentYear + ".json";

            Optional<String> jsonFilePathOpt = extractedFiles.keySet().stream()
                    .filter(path -> path.startsWith(diseaseDir) && path.endsWith(yearJsonFileName))
                    .findFirst();

            if (jsonFilePathOpt.isPresent()) {
                String jsonFilePath = jsonFilePathOpt.get();
                String diseaseJson = new String(Files.readAllBytes(extractedFiles.get(jsonFilePath)));
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
                    return AjaxResult.error("病害数据JSON格式错误: " + e.getMessage());
                }

                for (Disease disease : diseases) {
                    if (!disease.getBuildingId().equals(building.getId())) {
                        return AjaxResult.error("building与病害数据不对应");
                    }
                }
                // 批量保存病害数据
                Long projectId = null;
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
                        taskMapper.updateTask(task);
                    }
                }
            }

            return AjaxResult.success("桥梁数据上传成功");
        } catch (Exception e) {
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

    public void uploadBridgeDataImage(long id, MultipartFile frontFile[], MultipartFile sideFile[]) {
        for (int i = 0; i < frontFile.length; i++) {
            fileMapController.uploadAttachment(id, frontFile[i], "newfront", i);
        }
        for (int i = 0; i < sideFile.length; i++) {
            fileMapController.uploadAttachment(id, sideFile[i], "newside", i);
        }
    }
}
