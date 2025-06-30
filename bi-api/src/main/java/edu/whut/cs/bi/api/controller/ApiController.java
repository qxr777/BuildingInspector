package edu.whut.cs.bi.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.system.service.ISysUserService;
import edu.whut.cs.bi.api.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.api.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.api.vo.PropertyTreeVo;
import edu.whut.cs.bi.api.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.schema.Collections;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.ruoyi.common.utils.ShiroUtils.getSysUser;
import static com.ruoyi.common.utils.ShiroUtils.setSysUser;

@RestController
@RequestMapping("/api")
public class ApiController {

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
    private IComponentService componentService;

    @Resource
    private DiseaseMapper diseaseMapper;

    @Resource
    private DiseaseDetailMapper diseaseDetailMapper;

    @Resource
    private FileMapController fileMapController;

    @Resource
    private ISysUserService userService;

    @Resource
    private SysPasswordService passwordService;

    @Resource
    private AttachmentService attachmentService;


    /**
     * 无权限访问
     *
     * @return
     */
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success("list success");
    }

    /**
     * 菜单权限 system:user:list
     */
    @GetMapping("/user/list")
    @RequiresPermissions("system:user:list")
    public AjaxResult userlist() {
        return AjaxResult.success("user list success");
    }

    /**
     * 角色权限 admin
     */
    @GetMapping("/role/list")
    @RequiresRoles("admin")
    public AjaxResult rolelist() {
        return AjaxResult.success("role list success");
    }

    /**
     * 通过Building ID 获取对应桥梁 property
     */
    @GetMapping("/building/{bid}/property")
    @RequiresPermissions("biz:building:view")
    @ResponseBody
    public AjaxResult getProperty(@PathVariable("bid") Long buildingId) {
        if (buildingId == null) {
            return AjaxResult.error("参数错误");
        }
        Building building = buildingService.selectBuildingById(buildingId);
        List<FileMap> imageMaps = fileMapController.getImageMaps(buildingId, "newfront", "newside");
        Map<String, List<String>> collect = imageMaps.stream().collect(Collectors.groupingBy(
                image -> image.getOldName().split("_")[1],
                Collectors.mapping(FileMap::getNewName, Collectors.toList())
        ));

        Property property;
        try {
            property = propertyService.selectPropertyTree(building.getRootPropertyId());
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }

        // 封装返回结果
        PropertyTreeVo propertyTreeVo = new PropertyTreeVo();
        propertyTreeVo.setProperty(property);
        propertyTreeVo.setImages(collect);
        return AjaxResult.success("查询成功", propertyTreeVo);
    }

    /**
     * 获取建筑物对象树结构
     */
    @GetMapping("/building/{bid}/object")
    @RequiresPermissions("biz:object:list")
    @ResponseBody
    public AjaxResult getObjectTree(@PathVariable("bid") Long buildingId) {
        try {
            // 查询建筑物的root_object_id
            Building building = buildingService.selectBuildingById(buildingId);
            if (building == null || building.getRootObjectId() == null) {
                return AjaxResult.error("未找到指定的建筑物或其结构信息");
            }

            // 获取对象树的JSON结构
            String jsonTree = biObjectService.bridgeStructureJson(building.getRootObjectId());
            JSONObject jsonObject = JSONObject.parseObject(jsonTree);
            return AjaxResult.success("ObjectTree success", jsonObject);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 根据 BuidlingId 和 Year 查询桥梁历史病害
     */
    @GetMapping("/building/{bid}/disease")
    @RequiresPermissions("biz:disease:list")
    @ResponseBody
    public AjaxResult getDisease(@PathVariable("bid") Long buildingId, @RequestParam(required = false, name = "year") Integer year) {
        if (buildingId == null) {
            return AjaxResult.error("参数错误");
        }
        Disease disease = new Disease();
        disease.setBuildingId(buildingId);
        if (year != null) {
            disease.setYear(year);
        }

        List<Disease> diseases = diseaseService.selectDiseaseListForApi(disease);
        List<DiseasesOfYearVo> result = null;
        if (year == null) {
            Map<Integer, List<Disease>> map = diseases.stream()
                    .collect(Collectors.groupingBy(d -> d.getProject().getYear()));

            result = map.keySet().stream().map(y -> {
                DiseasesOfYearVo diseasesOfYearVo = new DiseasesOfYearVo();
                diseasesOfYearVo.setYear(y);
                diseasesOfYearVo.setDiseases(map.get(y));
                diseasesOfYearVo.setBuildingId(buildingId);
                return diseasesOfYearVo;
            }).toList();
        } else {
            DiseasesOfYearVo diseasesOfYearVo = new DiseasesOfYearVo();
            diseasesOfYearVo.setDiseases(diseases);
            diseasesOfYearVo.setYear(year);
            diseasesOfYearVo.setBuildingId(buildingId);
            result = List.of(diseasesOfYearVo);
        }


        return AjaxResult.success("查询成功", result);
    }

    /**
     * 根据项目 ProjectId 查询任务列表
     */
    @GetMapping("/project/{pid}/task")
    @RequiresPermissions("biz:task:list")
    @ResponseBody
    public AjaxResult getTask(@PathVariable("pid") Long projectId) {
        if (projectId == null) {
            return AjaxResult.error("参数错误");
        }
        Task task = new Task();
        task.setProjectId(projectId);
        List<Task> tasks = taskService.selectTaskVOList(task);

        TasksOfProjectVo tasksOfProjectVo = new TasksOfProjectVo();
        tasksOfProjectVo.setTasks(tasks);
        tasksOfProjectVo.setProjectId(projectId);

        return AjaxResult.success("查询成功", tasksOfProjectVo);
    }

    /**
     * 根据用户id查询用户项目Project列表
     */
    @GetMapping("/project")
    @RequiresPermissions("biz:project:list")
    @ResponseBody
    public AjaxResult getProject() {
        Long userId = ShiroUtils.getUserId();

        List<Project> projects = projectService.selectProjectListByUserIdAndRole(userId, ProjectUserRoleEnum.INSPECTOR.getValue());

        ProjectsOfUserVo projectsOfUserVo = new ProjectsOfUserVo();
        projectsOfUserVo.setProjects(projects);
        projectsOfUserVo.setUserId(userId);

        return AjaxResult.success("查询成功", projectsOfUserVo);
    }

    /**
     * 批量保存病害信息
     * 根据构件名称自动关联构件ID
     */
    @PostMapping("/disease/batchSave")
    @RequiresPermissions("biz:disease:add")
    @ResponseBody
    @Transactional
    public AjaxResult batchSaveDiseases(@RequestBody List<Disease> diseases) {
        try {
            if (diseases == null || diseases.isEmpty()) {
                return AjaxResult.error("参数错误：病害列表为空");
            }
            int successCount = 0;
            //记录已经插入了的构件
            HashMap<String, Long> map = new HashMap<>(16);
            List<Component> components = componentService.selectComponentList(new Component());
            //记录已经插入的病害
            List<Disease> diseaseList = diseaseMapper.selectDiseaseList(new Disease());
            for (Component component : components) {
                BiObject biObject = component.getBiObject();
                String root = "";
                if (biObject != null && StringUtils.isNotEmpty(biObject.getAncestors())) {
                    String[] split = biObject.getAncestors().split(",");
                    root = split[1];
                }
                map.put(component.getName() + root, component.getId());
            }
            HashMap<Long, Disease> localIdMap = new HashMap<>(16);
            for (Disease disease : diseaseList) {
                localIdMap.put(disease.getLocalId(), disease);
            }
            StringJoiner joiner = new StringJoiner(",");
            // 删除对应的病害文件 类型为1或者2都先删除
            for (Disease disease : diseases) {
                int type = disease.getCommitType();
                boolean isTypeValid = (type == 1 || type == 2);
                if (isTypeValid && localIdMap.containsKey(disease.getLocalId())) {
                    Disease oldDisease = localIdMap.get(disease.getLocalId());
                    joiner.add(String.valueOf(oldDisease.getId()));
                    StringJoiner attachmentJoiner = new StringJoiner(",");

                    List<Attachment> attachmentBySubjectId = attachmentService.getAttachmentBySubjectId(oldDisease.getId()).stream().filter(e -> e.getName().startsWith("disease")).toList();
                    for (Attachment attachment : attachmentBySubjectId) {
                        attachmentJoiner.add(String.valueOf(attachment.getId()));
                    }
                    attachmentService.deleteAttachmentByIds(attachmentJoiner.toString());
                }
            }
            //批量删除病害跟详细病害信息
            diseaseService.deleteDiseaseByDiseaseIds(joiner.toString());
            for (Disease disease : diseases) {
                if (disease.getCommitType() == 0) {
                    continue;
                }
                //类型为1的需要再插入
                if (disease.getCommitType() == 1) {
                    // 通过构件名称查找构件ID
                    Component component = disease.getComponent();
                    component.setCreateBy(ShiroUtils.getLoginName());
                    component.setUpdateBy(ShiroUtils.getLoginName());
                    String root = component.getBiObject().getAncestors().split(",")[1];
                    if (disease.getComponent() != null && disease.getComponent().getName() != null && disease.getComponentId() == null && !map.containsKey(component.getName() + root)) {
                        componentService.insertComponent(component);
                        map.put(component.getName() + root, component.getId());
                    }
                    if (disease.getComponent() != null && disease.getComponentId() != null) {
                        componentService.updateComponent(component);
                    }
                    // 病害类型id为空则默认为其他的病害类型
                    if (disease.getDiseaseTypeId() == null || disease.getDiseaseType().getId() == null || disease.getDiseaseType().getName().equals("其他")) {
                        disease.setDiseaseTypeId(238L);
                    }
                    disease.setComponentId(map.get(component.getName() + root));
                    disease.setCreateBy(ShiroUtils.getLoginName());
                    disease.setUpdateBy(ShiroUtils.getLoginName());
                    disease.setUpdateTime(new Date());
                    // 插入病害记录
                    successCount += diseaseMapper.insertDisease(disease);
                    // 添加病害详情
                    List<DiseaseDetail> diseaseDetails = disease.getDiseaseDetails();
                    diseaseDetails.forEach(diseaseDetail -> diseaseDetail.setDiseaseId(disease.getId()));
                    diseaseDetailMapper.insertDiseaseDetails(diseaseDetails);
                }
            }

            return AjaxResult.success("批量保存病害成功", successCount);
        } catch (Exception e) {
            return AjaxResult.error("批量保存病害失败：" + e.getMessage());
        }
    }

    /**
     * 用户退出
     */
    @PostMapping("/user/logOut")
    @ResponseBody
    public AjaxResult userLogOut() {
        ShiroUtils.logout();
        return AjaxResult.success("退出成功");
    }

    /**
     * 用户退出
     */
    @PostMapping("/user/resetPassword")
    @ResponseBody
    @Transactional
    public AjaxResult resetPwd(String oldPassword, String newPassword) {
        SysUser user = getSysUser();
        if (!passwordService.matches(user, oldPassword)) {
            return AjaxResult.error("修改密码失败，旧密码错误");
        }
        if (passwordService.matches(user, newPassword)) {
            return AjaxResult.error("新密码不能与旧密码相同");
        }
        user.setSalt(ShiroUtils.randomSalt());
        user.setPassword(passwordService.encryptPassword(user.getLoginName(), newPassword, user.getSalt()));
        user.setPwdUpdateDate(DateUtils.getNowDate());
        if (userService.resetUserPwd(user) > 0) {
            setSysUser(userService.selectUserById(user.getUserId()));
            return AjaxResult.success("修改密码成功");
        }
        return AjaxResult.error("修改密码异常，请联系管理员");
    }

    /**
     * 上传桥梁压缩包数据（包含结构和病害）
     * 压缩包结构：
     * - buildingId目录
     * - object.json (桥梁结构数据)
     * - disease目录
     * - 2025.json (病害数据)
     * - 图片文件
     */
    @Log(title = "上传桥梁数据", businessType = BusinessType.INSERT)
    @PostMapping("/upload/bridgeData")
    @RequiresPermissions("biz:disease:add")
    @ResponseBody
    @Transactional
    public AjaxResult uploadBridgeData(@RequestParam("file") MultipartFile file) {
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
                JSONObject jsonObject = JSONObject.parseObject(objectJson);
                BiObject rootObject = JSONObject.parseObject(objectJson, BiObject.class);

                // 确保rootObject的ID与数据库中的一致
                if (!rootObject.getId().equals(building.getRootObjectId())) {
                    return AjaxResult.error("building与桥梁数据不对应");
                }
                biObjectService.updateBiObjectTreeRecursively(rootObject);
            }

            // 处理病害数据 - 根据当前年份获取对应的JSON文件
            String diseaseDir = buildingId + "/disease/";
            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);
            String yearJsonFileName = currentYear + ".json";

            Optional<String> jsonFilePathOpt = extractedFiles.keySet().stream()
                    .filter(path -> path.startsWith(diseaseDir) && path.endsWith(yearJsonFileName))
                    .findFirst();

            // 如果没有找到当前年份的文件，则直接返回
            if (!jsonFilePathOpt.isPresent()) {
                return AjaxResult.success("桥梁数据上传成功");
            }

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
                batchSaveDiseases(diseases);
                // 处理病害图片
                for (Disease disease : diseases) {
                    // 只有类型为1的才需要新增图片文件
                    if (disease.getCommitType() == 1) {
                        List<String> images = disease.getImages();
                        List<String> ADImages = disease.getADImgs();
                        List<MultipartFile> multipartImagesFiles = new ArrayList<>();
                        List<MultipartFile> multipartADImagesFiles = new ArrayList<>();
                        if (images != null && !images.isEmpty()) {
                            for (String imagePath : images) {
                                if (imagePath != null && !imagePath.isEmpty()) {
                                    // 检查路径是否已经包含buildingId
                                    String fullPath = imagePath;
                                    // 尝试查找文件
                                    if (extractedFiles.containsKey(fullPath)) {
                                        // 处理图片附件
                                        File imageFile = extractedFiles.get(fullPath).toFile();
                                        byte[] fileContent = Files.readAllBytes(imageFile.toPath());
                                        // 创建MockMultipartFile
                                        MockMultipartFile mockFile = new MockMultipartFile(
                                                "file",
                                                imageFile.getName(),
                                                Files.probeContentType(imageFile.toPath()),
                                                fileContent);
                                        multipartImagesFiles.add(mockFile);
                                    }
                                }
                            }
                            // 调用handleDiseaseAttachment方法
                            if (!multipartImagesFiles.isEmpty()) {
                                diseaseService.handleDiseaseAttachment(
                                        multipartImagesFiles.toArray(new MultipartFile[0]),
                                        disease.getId(),
                                        1
                                );
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
                                        byte[] fileContent = Files.readAllBytes(imageFile.toPath());
                                        // 创建MockMultipartFile
                                        MockMultipartFile mockFile = new MockMultipartFile(
                                                "file",
                                                imageFile.getName(),
                                                Files.probeContentType(imageFile.toPath()),
                                                fileContent);
                                        multipartADImagesFiles.add(mockFile);
                                    }
                                }
                            }
                            // 调用handleDiseaseAttachment方法
                            if (!multipartADImagesFiles.isEmpty()) {
                                diseaseService.handleDiseaseAttachment(
                                        multipartADImagesFiles.toArray(new MultipartFile[0]),
                                        disease.getId(),
                                        7
                                );
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

    // id 是buildId
    @PostMapping("/upload/bridgeDataImage")
    @ResponseBody
    public AjaxResult uploadBridgeDataImage(@RequestParam("id") long id, @RequestParam("front") MultipartFile frontFile[], @RequestParam("side") MultipartFile sideFile[]) {
        for (int i = 0; i < frontFile.length && i < 2; i++) {
            fileMapController.uploadAttachment(id, frontFile[i], "newfront", i);
        }
        for (int i = 0; i < sideFile.length && i < 2; i++) {
            fileMapController.uploadAttachment(id, sideFile[i], "newside", i);
        }
        return AjaxResult.success("上传成功");
    }

    // id 是buildId
    @GetMapping("/DataImage")
    @ResponseBody
    public AjaxResult getDataImage(@RequestParam("id") long id) {
        List<FileMap> Images = fileMapController.getImageMaps(id, "newfront", "newside");
        Map<String, List<String>> map = new HashMap<>();
        List<String> frontImagesList = new ArrayList<>();
        List<String> sideImagesList = new ArrayList<>();
        for (FileMap frontImage : Images) {
            if (frontImage.getOldName().split("_")[1].equals("newfront")) {
                frontImagesList.add(frontImage.getNewName());
            } else {
                sideImagesList.add(frontImage.getNewName());
            }
        }
        map.put("frontImages", frontImagesList);
        map.put("sideImages", sideImagesList);
        return AjaxResult.success("查询成功", map);
    }

    @PostMapping("/upload/diseaseExcel")
    @ResponseBody
    @Transactional
    public AjaxResult uploadDiseaseExcel(@RequestParam("file") MultipartFile file, @RequestParam("projectId") Long projectId) {
        diseaseService.readDiseaseExcel(file, projectId);

        return AjaxResult.success("上传成功");
    }

    @PostMapping("/upload/bridgeExcel")
    @ResponseBody
    public AjaxResult uploadBridgeExcel(@RequestParam("file") MultipartFile file, @RequestParam("projectId") Long projectId) {
        buildingService.readBuildingExcel(file, projectId);

        return AjaxResult.success("上传成功");
    }

}
