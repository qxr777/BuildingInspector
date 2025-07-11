package edu.whut.cs.bi.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.impl.SysRoleServiceImpl;
import edu.whut.cs.bi.api.service.ApiService;
import edu.whut.cs.bi.api.task.UserPackageTask;
import edu.whut.cs.bi.api.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.api.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.api.vo.PropertyTreeVo;
import edu.whut.cs.bi.api.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.PackageMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

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

    @PostMapping("/upload/diseaseZip")
    @ResponseBody
    @Transactional
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
        FileMap fileMap = fileMapServiceImpl.selectFileMapById(packages.get(0).getId());
        if(fileMap==null || fileMap.getNewName()==null || fileMap.getOldName()==null) {
            return  AjaxResult.error("数据不完整");
        }
        String prefix = fileMap.getNewName().substring(0, 2);
        String downloadUrl = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" +
                prefix + "/" + fileMap.getNewName();
        return AjaxResult.success().put("url", downloadUrl).put("version",fileMap.getOldName());
    }

    @GetMapping("/user/dataPackageTest")
    @ResponseBody
    public AjaxResult getUserDataPackageTest() {
        return userPackageTask.generateUserDataPackage();
    }
}