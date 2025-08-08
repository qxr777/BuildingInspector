package edu.whut.cs.bi.biz.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.system.service.ISysUserService;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.domain.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.biz.domain.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.biz.domain.vo.PropertyTreeVo;
import edu.whut.cs.bi.biz.domain.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.service.*;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.PackageMapper;

import javax.annotation.Resource;

/**
 * 用户压缩包Service业务层处理
 *
 * @author wanzheng
 * @date 2025-07-18
 */
@Slf4j
@Service
public class PackageServiceImpl implements IPackageService {
    @Autowired
    private PackageMapper packageMapper;

    @Autowired
    private FileMapServiceImpl fileMapServiceImpl;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;

    @Resource
    private ITaskService taskService;

    @Resource
    private IProjectService projectService;

    @Resource
    private IPropertyService propertyService;

    @Resource
    private IBuildingService buildingService;

    @Resource
    private IBiObjectService biObjectService;

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private AttachmentService attachmentService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private IFileMapService fileMapService;


    /**
     * 查询用户压缩包
     *
     * @param id 用户压缩包主键
     * @return 用户压缩包
     */
    @Override
    public Package selectPackageById(Long id) {
        return packageMapper.selectPackageById(id);
    }

    /**
     * 查询用户压缩包列表
     *
     * @param package1 用户压缩包
     * @return 用户压缩包
     */
    @Override
    public List<Package> selectPackageList(Package package1) {
        return packageMapper.selectPackageList(package1);
    }

    /**
     * 新增用户压缩包
     *
     * @param package1 用户压缩包
     * @return 结果
     */
    @Override
    public int insertPackage(Package package1) {
        return packageMapper.insertPackage(package1);
    }

    /**
     * 修改用户压缩包
     *
     * @param package1 用户压缩包
     * @return 结果
     */
    @Override
    public int updatePackage(Package package1) {
        package1.setUpdateTime(DateUtils.getNowDate());
        return packageMapper.updatePackage(package1);
    }

    /**
     * 批量删除用户压缩包
     *
     * @param ids 需要删除的用户压缩包主键
     * @return 结果
     */
    @Override
    public int deletePackageByIds(String ids) {
        String[] stringArray = {"1", "2", "3"};
        Long[] longArray = new Long[stringArray.length];

        for (int i = 0; i < stringArray.length; i++) {
            longArray[i] = Long.parseLong(stringArray[i]);
        }

        return packageMapper.deletePackageByIds(longArray);
    }

    /**
     * 删除用户压缩包信息
     *
     * @param id 用户压缩包主键
     * @return 结果
     */
    @Override
    public int deletePackageById(Long id) {
        return packageMapper.deletePackageById(id);
    }

    /**
     * 异步批量生成用户数据包
     */
    @Async("taskExecutor")
    public void batchGeneratePackagesAsync(List<Long> userIds) {
        for (Long userId : userIds) {
            try {
                // 获取用户信息
                SysUser user = userService.selectUserById(userId);
                List<Package> packages = packageMapper.selectPackageListByUserId(userId);
                Package aPackage = null;
                if (!packages.isEmpty()) {
                    aPackage = packages.get(0);
                }
                if (user != null && aPackage != null) {
                    // 生成用户数据包
                    fileMapService.deleteFileMapById(aPackage.getMinioId());
                    AjaxResult ajaxResult = generateUserDataPackage(user);
                    if (ajaxResult.isSuccess() && !packages.isEmpty()) {
                        Date nowDate = DateUtils.getNowDate();
                        aPackage.setPackageTime(nowDate);
                        aPackage.setUpdateTime(nowDate);
                        aPackage.setMinioId(Long.valueOf(ajaxResult.get("data").toString()));
                        aPackage.setPackageSize(ajaxResult.get("size").toString());
                        packageMapper.updatePackage(aPackage);
                    } else {
                        log.error("为用户ID {} 生成数据包失败", userId);
                    }
                    log.info("成功为用户 {} 生成数据包", user.getLoginName());
                }
            } catch (Exception e) {
                log.error("为用户ID {} 生成数据包失败: {}", userId, e.getMessage(), e);
            }
        }
    }


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
        // 获取当前年份
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        Project project1 = new Project();
        project1.setYear(currentYear);
        // 获取用户项目列表
        List<Project> projects = projectService.selectProjectListByUserIdAndRole(project1, userId, ProjectUserRoleEnum.INSPECTOR.getValue());
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
        // 获取当前年份
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        Project project1 = new Project();
        project1.setYear(currentYear);
        // 获取用户关联的项目
        List<Project> projects = projectService.selectProjectListByUserIdAndRole(project1, userId, ProjectUserRoleEnum.INSPECTOR.getValue());

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
            // 2 获取建筑物照片数据并创建frontPhoto.json 获取建筑物属性
            try {
                // 获取建筑物的附件列表
                List<Attachment> attachments = attachmentService.getAttachmentBySubjectId(buildingId);

                // 过滤出与桥梁照片相关的附件
                List<Attachment> bridgePhotoAttachments = attachments.stream()
                        .filter(e -> {
                            String name = e.getName();
                            return name != null && name.matches("^\\d+_(newfront|newside)_.*$");
                        })
                        .collect(Collectors.toList());

                List<Attachment> propertyPhotoAttachments = attachments.stream()
                        .filter(e -> {
                            String name = e.getName();
                            return name != null && name.matches("^\\d+_(front|side)_.*$");
                        })
                        .collect(Collectors.toList());

                if (!bridgePhotoAttachments.isEmpty()) {

                    // 创建frontPhoto.json
                    Map<String, List<String>> newfrontAndSide = getFrontAndSide(bridgePhotoAttachments, zipOut, buildingId, rootDirName);
                    String frontPhotoJsonPath = rootDirName + "/building/" + buildingId + "/frontPhoto.json";
                    addJsonToZip(zipOut, frontPhotoJsonPath, JSONObject.toJSONString(newfrontAndSide));
                    log.info(userId + " 桥梁正立面照收集完成" + buildingId);
                }
                Map<String, List<String>> frontAndSide = getFrontAndSide(propertyPhotoAttachments, zipOut, buildingId, rootDirName);
                Property property = propertyService.selectPropertyTree(building.getRootPropertyId());

                PropertyTreeVo propertyTreeVo = new PropertyTreeVo();
                propertyTreeVo.setProperty(property);
                propertyTreeVo.setImages(frontAndSide);

                String propertyJsonPath = rootDirName + "/building/" + buildingId + "/property.json";
                addJsonToZip(zipOut, propertyJsonPath, JSONObject.toJSONString(propertyTreeVo));
                log.info(userId + " 桥梁属性卡片收集完成" + buildingId);
            } catch (Exception e) {
                // 记录错误但继续处理
                log.error("获取建筑物照片数据失败：buildingId={}, 错误={}", buildingId, e.getMessage(), e);
            }

            // 3. 获取建筑物病害数据
            try {
                // 查找近三年中最近一年有病害数据的年份
                List<Disease> diseases = null;
                int targetYear = 0;

                // 尝试从最近一年到往前三年，找到第一个有病害数据的年份
                for (int i = 1; i <= 3; i++) {
                    targetYear = currentYear - i;
                    Disease disease = new Disease();
                    disease.setBuildingId(buildingId);
                    disease.setYear(targetYear);

                    // 使用selectDiseaseListForZip方法，不加载图片URLs
                    List<Disease> yearDiseases = diseaseService.selectDiseaseListForZip(disease);
                    if (yearDiseases != null && !yearDiseases.isEmpty()) {
                        diseases = yearDiseases;
                        log.info(userId + "找到" + targetYear + "年的病害数据，共" + diseases.size() + "条");
                        break;
                    }
                }

                if (diseases != null && !diseases.isEmpty()) {
                    log.info(userId + "病害信息开始收集" + buildingId + "，年份：" + targetYear);
                    log.info(userId + " 病害信息数据完成" + buildingId);
                    log.info(userId + "图片信息开始收集" + buildingId);

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
                } else {
                    log.info(userId + "近三年内未找到病害数据，buildingId=" + buildingId);
                }
                // 收集所有需要处理的路径和对应的文件名
                List<String> allFileNames = new ArrayList<>();
                List<Long> ids = new ArrayList<>();
                if (diseases != null && !diseases.isEmpty()) {
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
                }
                if (!allFileNames.isEmpty()) {
                    addDiseaseImages(zipOut, rootDirName, buildingId, allFileNames, ids);
                }
                log.info(userId + "图片信息收集完成" + buildingId);
            } catch (Exception e) {
                // 记录错误但继续处理
                log.error("获取建筑物病害数据失败：buildingId={}, 错误={}", buildingId, e.getMessage(), e);
            }
        }
    }


    public Map<String, List<String>> getFrontAndSide(List<Attachment> attachments, ZipOutputStream zipOut, Long buildingId, String rootDirName) throws Exception {
        // 分组照片（frontLeft, frontRight, sideLeft, sideRight）
        Map<String, List<String>> photoGroups = new HashMap<>();
        photoGroups.put("frontLeft", new ArrayList<>());
        photoGroups.put("frontRight", new ArrayList<>());
        photoGroups.put("sideLeft", new ArrayList<>());
        photoGroups.put("sideRight", new ArrayList<>());
        if (!attachments.isEmpty()) {
            // 获取MinIO ID列表
            List<Long> minioIds = attachments.stream()
                    .map(Attachment::getMinioId)
                    .collect(Collectors.toList());

            // 批量查询FileMap
            Map<Long, FileMap> fileMapMap = new HashMap<>();
            if (!minioIds.isEmpty()) {
                List<FileMap> fileMaps = fileMapServiceImpl.selectFileMapByIds(minioIds);
                fileMapMap = fileMaps.stream()
                        .collect(Collectors.toMap(fileMap -> Long.valueOf(fileMap.getId()), fileMap -> fileMap));
            }

            // 处理每个附件
            for (Attachment attachment : attachments) {
                FileMap fileMap = fileMapMap.get(attachment.getMinioId());
                if (fileMap == null) continue;

                String[] nameParts = attachment.getName().split("_");
                if (nameParts.length < 2) continue;

                String position = nameParts[0]; // 0 或 1
                String type = nameParts[1];     // newfront 或 newside

                // 确定分组
                String groupKey;
                if ("newfront".equals(type)) {
                    groupKey = "0".equals(position) ? "frontLeft" : "frontRight";
                } else if ("newside".equals(type)) {
                    groupKey = "0".equals(position) ? "sideLeft" : "sideRight";
                } else {
                    continue; // 跳过不匹配的类型
                }

                // 使用fileMap的oldName作为图片名称
                String imageName = fileMap.getOldName();
                String imagePath = buildingId + "/images/" + imageName;
                photoGroups.get(groupKey).add(imagePath);

                // 从MinIO下载并添加到zip
                String zipImagePath = rootDirName + "/building/" + imagePath;
                ZipEntry entry = new ZipEntry(zipImagePath);
                zipOut.putNextEntry(entry);

                // 从MinIO读取
                try (InputStream imageStream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(fileMap.getNewName().substring(0, 2) + "/" + fileMap.getNewName())
                        .build())) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = imageStream.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, bytesRead);
                    }
                }
                zipOut.closeEntry();
            }
            return photoGroups;
        }
        return photoGroups;
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
}
