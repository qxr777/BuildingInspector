package edu.whut.cs.bi.api.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import edu.whut.cs.bi.api.service.ApiService;
import edu.whut.cs.bi.api.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.api.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.api.vo.PropertyTreeVo;
import edu.whut.cs.bi.api.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.DiseaseServiceImpl;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @Author:wanzheng
 * @Date:2025/7/11 08:54
 * @Description:
 **/
@Service
public class ApiServiceImpl implements ApiService {
    private static final Logger log = LoggerFactory.getLogger(ApiServiceImpl.class);
    @Resource
    private IBuildingService buildingService;

    @Resource
    private IPropertyService propertyService;

    @Resource
    private IBiObjectService biObjectService;

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private ITaskService taskService;

    @Resource
    private IProjectService projectService;

    @Resource
    private FileMapController fileMapController;

    @Resource
    private AttachmentService attachmentService;

    @Autowired
    private FileMapServiceImpl fileMapServiceImpl;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private DiseaseServiceImpl diseaseServiceImpl;


    /**
     * 生成用户压缩包
     */
    @Override
    public AjaxResult generateUserDataPackage(SysUser user) {
        if (user == null) {
            return AjaxResult.success();
        }

        File tempFile = null;
        try {
            // 获取当前用户信息
            Long userId = user.getUserId();

            // 创建日期格式化对象，用于生成文件夹名称（时间戳中间不加横线）
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            String dateStr = dateFormat.format(new Date());

            // 根目录名称：UD-时间戳-用户名
            String rootDirName = "UD-" + dateStr + "-" + user.getLoginName();
            String zipFileName = rootDirName + ".zip";

            // 创建临时文件直接写入ZIP数据
            tempFile = File.createTempFile("datapackage_", ".zip");
            String zipSize;

            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 ZipOutputStream zipOut = new ZipOutputStream(fos)) {

                // 1. 创建项目数据
                createProjectData(zipOut, rootDirName, userId);
                log.info(userId + "项目数据创建完成");
                // 2. 创建建筑物数据
                createBuildingData(zipOut, rootDirName, userId);
                log.info(userId + "建筑物数据创建完成");

                zipOut.close();
                long length = tempFile.length(); // 单位是字节
                double sizeInMB = length / 1024.0 / 1024.0;

                DecimalFormat df = new DecimalFormat("#.###");
                zipSize = df.format(sizeInMB) + "MB";
                log.info("ZIP文件生成完成，大小: {}", zipSize);
            }

            // 直接上传临时文件到MinIO
            try {
                FileMap fileMap = fileMapServiceImpl.handleFileUploadFromFile(tempFile, zipFileName, user.getLoginName());

                // 返回成功信息和minioId
                return AjaxResult.success("数据包已生成", Long.valueOf(fileMap.getId())).put("size", zipSize);

            } catch (Exception e) {
                log.error("上传数据包到MinIO失败", e);
                return AjaxResult.error("生成用户数据包失败");
            }

        } catch (Exception e) {
            log.error("生成用户数据包失败", e);
            return AjaxResult.error("生成用户数据包失败");
        } finally {
            // 确保临时文件被删除
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 创建项目相关数据
     */
    public void createProjectData(ZipOutputStream zipOut, String rootDirName, Long userId) throws IOException {
        // 获取用户项目列表
        List<Project> projects = projectService.selectProjectListByUserIdAndRole(userId, ProjectUserRoleEnum.INSPECTOR.getValue());
        ProjectsOfUserVo projectsOfUserVo = new ProjectsOfUserVo();
        projectsOfUserVo.setProjects(projects);
        projectsOfUserVo.setUserId(userId);

        // 添加project.json文件
        String projectJsonPath = rootDirName + "/project/projects.json";
        addJsonToZip(zipOut, projectJsonPath, JSONObject.toJSONString(projectsOfUserVo));

        // 遍历项目，创建每个项目的任务数据
        for (Project project : projects) {
            // 获取项目的任务列表
            Task task = new Task();
            task.setProjectId(project.getId());
            List<Task> tasks = taskService.selectTaskVOList(task);

            TasksOfProjectVo tasksOfProjectVo = new TasksOfProjectVo();
            tasksOfProjectVo.setTasks(tasks);
            tasksOfProjectVo.setProjectId(project.getId());

            // 添加任务JSON文件
            String taskJsonPath = rootDirName + "/project/" + project.getId() + "/task.json";
            addJsonToZip(zipOut, taskJsonPath, JSONObject.toJSONString(tasksOfProjectVo));
        }
    }

    /**
     * 创建建筑物相关数据
     */
    public void createBuildingData(ZipOutputStream zipOut, String rootDirName, Long userId) throws IOException {
        // 获取用户关联的项目
        List<Project> projects = projectService.selectProjectListByUserIdAndRole(userId, ProjectUserRoleEnum.INSPECTOR.getValue());

        // 收集所有项目中的任务
        Set<Long> buildingIds = new HashSet<>();
        for (Project project : projects) {
            Task taskQuery = new Task();
            taskQuery.setProjectId(project.getId());
            List<Task> tasks = taskService.selectTaskVOList(taskQuery);

            // 从任务中提取建筑物ID
            for (Task task : tasks) {
                if (task.getBuildingId() != null) {
                    buildingIds.add(task.getBuildingId());
                }
            }
        }

        // 处理每个建筑物的数据
        for (Long buildingId : buildingIds) {
            Building building = buildingService.selectBuildingById(buildingId);
            // 1. 获取建筑物对象树
            try {
                if (building != null && building.getRootObjectId() != null) {
                    String jsonTree = biObjectService.bridgeStructureJson(building.getRootObjectId());
                    String objectJsonPath = rootDirName + "/building/" + buildingId + "/object.json";
                    addJsonToZip(zipOut, objectJsonPath, jsonTree);
                }
            } catch (Exception e) {
                // 记录错误但继续处理
                log.error("获取建筑物病害数据失败：buildingId={}, 错误={}", buildingId, e.getMessage(), e);
                continue;
            }

            // 2. 获取建筑物属性
            try {
                List<FileMap> imageMaps = fileMapController.getImageMaps(buildingId, "newfront", "newside");
                Map<String, List<String>> collect = imageMaps.stream().collect(Collectors.groupingBy(
                        image -> image.getOldName().split("_")[1],
                        Collectors.mapping(FileMap::getNewName, Collectors.toList())
                ));

                Property property = propertyService.selectPropertyTree(building.getRootPropertyId());

                PropertyTreeVo propertyTreeVo = new PropertyTreeVo();
                propertyTreeVo.setProperty(property);
                propertyTreeVo.setImages(collect);

                String propertyJsonPath = rootDirName + "/building/" + buildingId + "/property.json";
                addJsonToZip(zipOut, propertyJsonPath, JSONObject.toJSONString(propertyTreeVo));
            } catch (Exception e) {
                // 记录错误但继续处理
                log.error("获取建筑物属性失败：buildingId={}, 错误={}", buildingId, e.getMessage(), e);
                continue;
            }

            // 3. 获取建筑物病害数据
            try {
                // 获取当前年份
                Calendar calendar = Calendar.getInstance();
                int currentYear = calendar.get(Calendar.YEAR);
                // 只获取上一年的病害数据
                int targetYear = currentYear - 1;

                Disease disease = new Disease();
                disease.setBuildingId(buildingId);
                // 设置为上一年
                disease.setYear(targetYear);

                // 使用selectDiseaseListForZip方法，不加载图片URLs
                log.info(userId + "病害信息开始收集" + disease.getBuildingId());
                List<Disease> diseases = diseaseService.selectDiseaseListForZip(disease);
                log.info(userId + " 病害信息数据完成" + disease.getBuildingId());
                log.info(userId + "图片信息开始收集" + disease.getBuildingId());
                if (!diseases.isEmpty()) {
                    // 创建年份病害数据对象，此时Disease对象中的图片路径已更新为相对路径
                    DiseasesOfYearVo diseasesOfYearVo = new DiseasesOfYearVo();
                    diseasesOfYearVo.setYear(targetYear);
                    diseasesOfYearVo.setDiseases(diseases);
                    diseasesOfYearVo.setBuildingId(buildingId);
                    // 创建 ObjectMapper 实例
                    ObjectMapper objectMapper = new ObjectMapper();

                    // 序列化时使用 ObjectMapper
                    String jsonString = objectMapper.writeValueAsString(diseasesOfYearVo);
                    // 添加到zip文件
                    String diseaseJsonPath = rootDirName + "/building/" + buildingId + "/disease/" + targetYear + ".json";
                    addJsonToZip(zipOut, diseaseJsonPath, jsonString);
                }
                // 收集所有需要处理的路径和对应的文件名
                List<String> allFileNames = new ArrayList<>();
                List<Long> ids = new ArrayList<>();
                for (Disease d : diseases) {
                    // 处理普通图片路径列表
                    List<String> imagePaths = d.getImages();
                    if (imagePaths != null && !imagePaths.isEmpty()) {
                        for (String relativePath : imagePaths) {
                            // 获取原始文件名
                            String[] parts = relativePath.split("/");
                            String fileName = parts[parts.length - 1];
                            allFileNames.add(fileName);
                        }
                        ids.add(d.getId());
                    }

                    // 处理AD图片路径列表
                    List<String> adImagePaths = d.getADImgs();
                    if (adImagePaths != null && !adImagePaths.isEmpty()) {
                        for (String relativePath : adImagePaths) {
                            // 获取原始文件名
                            String[] parts = relativePath.split("/");
                            String fileName = parts[parts.length - 1];
                            allFileNames.add(fileName);
                        }
                        ids.add(d.getId());
                    }
                }
                if (!allFileNames.isEmpty()) {
                    addDiseaseImages(zipOut, rootDirName, buildingId, allFileNames, ids);
                }
                log.info(userId + "图片信息收集完成" + disease.getBuildingId());
            } catch (Exception e) {
                // 记录错误但继续处理
                log.error("获取建筑物病害数据失败：buildingId={}, 错误={}", buildingId, e.getMessage(), e);
            }
        }
    }

    /**
     * 添加病害相关的图片
     */
    private void addDiseaseImages(ZipOutputStream zipOut, String rootDirName, Long buildingId, List<String> allFileNames, List<Long> ids) {
        try {
            // 批量获取所有附件
            List<Attachment> attachments = attachmentService.getAttachmentBySubjectIds(ids);
            if (attachments == null || attachments.isEmpty()) {
                return;
            }

            // 过滤出需要的附件
            List<Attachment> neededAttachments = attachments.stream()
                    .filter(attachment -> {
                        if (!attachment.getName().startsWith("disease_")) {
                            return false;
                        }

                        // 检查文件名是否在我们需要的列表中
                        String attachmentFileName = attachment.getId() + "_" + (attachment.getName().split("_", 2).length > 1 ?
                                attachment.getName().split("_", 2)[1] : attachment.getName());

                        return allFileNames.contains(attachmentFileName);
                    })
                    .collect(Collectors.toList());

            if (neededAttachments.isEmpty()) {
                return;
            }

            // 收集所有 MinioId，进行批量查询
            List<Long> minioIds = neededAttachments.stream()
                    .map(Attachment::getMinioId)
                    .collect(Collectors.toList());

            // 批量查询 FileMap
            Map<Long, FileMap> fileMapMap = new HashMap<>();
            if (!minioIds.isEmpty()) {
                List<FileMap> fileMaps = fileMapServiceImpl.selectFileMapByIds(minioIds);
                fileMapMap = fileMaps.stream()
                        .collect(Collectors.toMap(fileMap -> Long.valueOf(fileMap.getId()), fileMap -> fileMap));
            }

            // 流式读取并直接写入ZIP
            for (Attachment attachment : neededAttachments) {
                try {
                    // 获取FileMap
                    FileMap fileMap = fileMapMap.get(attachment.getMinioId());
                    if (fileMap == null) {
                        continue;
                    }

                    String newName = fileMap.getNewName();

                    // 获取原始文件名
                    String originalFileName = attachment.getId() + "_" + (attachment.getName().split("_", 2).length > 1 ?
                            attachment.getName().split("_", 2)[1] : attachment.getName());

                    // 创建图片在zip包中的保存路径
                    String zipImagePath = rootDirName + "/building/" + buildingId + "/disease/images/" + originalFileName;

                    // 创建ZIP条目
                    ZipEntry entry = new ZipEntry(zipImagePath);
                    zipOut.putNextEntry(entry);

                    // 流式从MinIO读取并直接写入ZIP
                    try (InputStream imageStream = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(newName.substring(0, 2) + "/" + newName)
                            .build())) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = imageStream.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, bytesRead);
                        }
                    }

                    // 关闭当前ZIP条目
                    zipOut.closeEntry();

                } catch (Exception e) {
                    log.error("处理图片失败: " + attachment.getId() + ", 错误: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("处理病害图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将JSON内容添加到ZIP文件中
     */
    private void addJsonToZip(ZipOutputStream zipOut, String entryPath, String jsonContent) throws IOException {
        ZipEntry entry = new ZipEntry(entryPath);
        zipOut.putNextEntry(entry);
        zipOut.write(jsonContent.getBytes());
        zipOut.closeEntry();
    }

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
                if (!diseases.isEmpty()) {
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
