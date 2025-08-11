package edu.whut.cs.bi.api.controller;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.system.service.ISysUserService;
import edu.whut.cs.bi.api.service.ApiService;
import edu.whut.cs.bi.api.task.UserPackageTask;
import edu.whut.cs.bi.api.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.api.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.api.vo.PropertyTreeVo;
import edu.whut.cs.bi.api.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.ruoyi.common.utils.ShiroUtils.getSysUser;
import static com.ruoyi.common.utils.ShiroUtils.setSysUser;

@Slf4j
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
    private FileMapController fileMapController;

    @Resource
    private ISysUserService userService;

    @Resource
    private SysPasswordService passwordService;

    @Autowired
    private FileMapServiceImpl fileMapServiceImpl;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private ApiService apiService;

    @Autowired
    private PackageMapper packageMapper;

    @Autowired
    private UserPackageTask userPackageTask;
    @Autowired
    private ComponentMapper componentMapper;


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

        List<Project> projects = projectService.selectProjectListByUserIdAndRole(new Project(), userId, ProjectUserRoleEnum.INSPECTOR.getValue());

        ProjectsOfUserVo projectsOfUserVo = new ProjectsOfUserVo();
        projectsOfUserVo.setProjects(projects);
        projectsOfUserVo.setUserId(userId);

        return AjaxResult.success("查询成功", projectsOfUserVo);
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
        try {
            return apiService.uploadBridgeData(file);
        } catch (Exception e) {
            return AjaxResult.error("处理上传文件失败：" + e.getMessage());
        }
    }

    // id 是buildId
    @PostMapping("/upload/bridgeDataImage")
    @ResponseBody
    public AjaxResult uploadBridgeDataImage(@RequestParam("id") long id, @RequestParam("front") MultipartFile frontFile[], @RequestParam("side") MultipartFile sideFile[]) {
        for (int i = 0; i < frontFile.length; i++) {
            fileMapController.uploadAttachment(id, frontFile[i], "newfront", i);
        }
        for (int i = 0; i < sideFile.length; i++) {
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

    @PostMapping("/upload/diseaseZip")
    @ResponseBody
    public AjaxResult uploadDiseaseZip(@RequestParam("file") MultipartFile file) {
        diseaseService.readDiseaseZip(file);

        return AjaxResult.success("上传成功");
    }

    /**
     * 根据用户ID生成用户完整数据的压缩包
     * 压缩包结构:
     * - UD日期-用户名/
     * - building/
     * - 建筑物ID/
     * - object.json (getObjectTree方法结果)
     * - property.json (getProperty方法结果)
     * - disease/
     * - 年份.json (getDisease方法结果)
     * - images/ (存放从Minio获取的图片)
     * - project/
     * - project.json (getProject方法结果)
     * - 项目ID/
     * - task.json (getTask方法结果)
     */
    @GetMapping("/user/dataPackage")
    @ResponseBody
    public AjaxResult getUserDataPackage() {
        Long userId = ShiroUtils.getUserId();
        Package query = new Package();
        query.setUserId(userId);
        List<Package> packages = packageMapper.selectPackageList(query);
        if (packages.isEmpty()) {
            return AjaxResult.error("该用户暂时没有用户数据包");
        }
        FileMap fileMap = fileMapServiceImpl.selectFileMapById(packages.get(0).getMinioId());
        if (fileMap == null || fileMap.getNewName() == null || fileMap.getOldName() == null) {
            return AjaxResult.error("数据不完整");
        }
        String version = fileMap.getOldName();
        String prefix = fileMap.getNewName().substring(0, 2);
        String downloadUrl = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" +
                prefix + "/" + fileMap.getNewName();
        return AjaxResult.success().put("url", downloadUrl).put("version", version).put("packageSize", packages.get(0).getPackageSize());
    }

    @GetMapping("/user/dataPackageTest")
    @ResponseBody
    public AjaxResult getUserDataPackageTest() {
        return userPackageTask.generateUserDataPackage();
    }

    @Resource
    private ReadFileService readFileService;

    @Resource
    private TaskMapper taskMapper;

    @GetMapping("/batchDisease")
    @ResponseBody
    @Transactional
    public AjaxResult batchDisease(@RequestParam("folderPath") String folderPath, @RequestParam("area") String area) {
        // 指定文件夹路径
        File folder = new File(folderPath);

        // 检查文件夹是否存在
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("指定的文件夹路径无效！");
            return AjaxResult.error("指定的文件夹路径无效！");
        }

        Task task = new Task();
        task.setProjectId(40L);
        Building building = new Building();
        building.setArea(area);
        task.setBuilding(building);
        List<Task> tasks = taskMapper.selectTaskList(task, null);

        // 遍历文件夹中的所有文件
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isExcelFile(file)) {
                    String name = file.getName();
                    String buildingName = name.substring(0, name.indexOf("病"));
                    tasks.stream().filter(t -> t.getBuilding().getName().equals(buildingName)).findFirst().ifPresent(t -> {
                        readFileService.readDiseaseExcel(convert(file), t.getId());
                    });
                }
            }
        }

        return AjaxResult.success("处理成功");
    }

    // 判断是否为Excel文件
    private static boolean isExcelFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".xls") || fileName.endsWith(".xlsx");
    }

    /**
     * 将 File 转换为 MultipartFile
     *
     * @param file Excel 文件
     * @return MultipartFile 对象
     */
    public static MultipartFile convert(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getAbsolutePath());
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            // 获取文件内容
            byte[] fileContent = inputStream.readAllBytes();

            // 获取文件扩展名并推断 MIME 类型
            String originalFilename = file.getName();
            String contentType = determineContentType(originalFilename);

            // 创建 MockMultipartFile 实例
            return new MockMultipartFile(
                    "file",               // 表单字段名
                    originalFilename,    // 原始文件名
                    contentType,         // 内容类型
                    fileContent          // 文件内容
            );
        } catch (IOException e) {
            throw new RuntimeException("文件转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件扩展名推断 MIME 类型
     *
     * @param filename 文件名
     * @return MIME 类型
     */
    private static String determineContentType(String filename) {
        if (filename.toLowerCase().endsWith(".xls")) {
            return "application/vnd.ms-excel"; // Excel 97-2003
        } else if (filename.toLowerCase().endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; // Excel 2007+
        } else {
            return "application/octet-stream"; // 默认类型
        }
    }
    
    // 根据桥梁名称，桥梁行程编号，桥梁路线确定唯一桥梁
    @GetMapping("/building/unique")
    @ResponseBody
    public AjaxResult getUniqueBuilding(@RequestParam("bridgeName") String bridgeName, @RequestParam("lineCode") String lineCode, @RequestParam("zipCode") String zipCode) {
        Building building = buildingService.getUniqueBuilding(bridgeName, lineCode, zipCode);
        if (building == null) {
            return AjaxResult.error("没有找到该桥梁");
        }
        return AjaxResult.success("查询成功", building);
    }
    
    
    
    /**
     * 通过word文件添加
     */
    @PostMapping( "/readWord" )
    @ResponseBody
//    @RequiresPermissions("biz:property:add")
    @Log(title = "读取属性word文件", businessType = BusinessType.INSERT)
    public Building readWordFile(@RequestPart("file") MultipartFile file, Long buildingId)
    {
        if (buildingId == null) {
            throw new ServiceException("buildingId不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        Property property = new Property();
        property.setCreateBy(ShiroUtils.getLoginName());
        property.setUpdateBy(ShiroUtils.getLoginName());
        
        Boolean read = propertyService.readWordFile(file, property, buildingId);
        if (read == null || !read) {
            throw new ServiceException("读取Word并写入属性失败");
        }
        Building building = null;
        building = buildingService.selectBuildingById(buildingId);
        
        return building;
    }
    
}