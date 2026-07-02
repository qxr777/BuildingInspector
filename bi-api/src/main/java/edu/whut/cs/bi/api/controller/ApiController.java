package edu.whut.cs.bi.api.controller;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysDictDataService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;
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
import edu.whut.cs.bi.biz.domain.vo.BatchBridgeCardImportResult;
import edu.whut.cs.bi.biz.domain.vo.BatchCbmsDiseaseImportResult;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.FileMapServiceImpl;
import edu.whut.cs.bi.biz.service.impl.ReadFileServiceImpl;
import edu.whut.cs.bi.biz.utils.ThumbPhotoUtils;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.compress.utils.Lists;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
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
    private IPropertyIndexService propertyIndexService;

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
    private ISysDeptService deptService;

    @Resource
    private ISysRoleService roleService;

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
    private IPackageService packageService;

    @Autowired
    private IAppPackageService appPackageService;

    @Autowired
    private UserPackageTask userPackageTask;

    @Autowired
    private IReportService reportService;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private IComponentService componentService;

    @Autowired
    private ISysDictDataService dictDataService;
    @Autowired
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
     * 获取建筑物对象树结构
     */
    @GetMapping("/building/{bid}/object-online")
    @RequiresPermissions("biz:object:list")
    @ResponseBody
    public AjaxResult getObjectTreeOnline(@PathVariable("bid") Long buildingId) {
        try {
            // 查询建筑物的root_object_id
            Building building = buildingService.selectBuildingById(buildingId);
            if (building == null || building.getRootObjectId() == null) {
                return AjaxResult.error("未找到指定的建筑物或其结构信息");
            }

            // 获取对象树的JSON结构
            String jsonTree = biObjectService.bridgeStructureJsonWithPictures(building.getRootObjectId());
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
    @PostMapping("/disease/deleteByProject")
    @RequiresPermissions("biz:disease:remove")
    @Log(title = "批量删除项目任务病害", businessType = BusinessType.DELETE)
    public AjaxResult deleteDiseasesByProject(@RequestParam("projectName") String projectName,
                                              @RequestParam("year") Integer year,
                                              @RequestParam(value = "code", required = false) String code,
                                              @RequestParam(value = "projectCode", required = false) String projectCode) {
        try {
            String targetCode = code != null && !code.trim().isEmpty() ? code : projectCode;
            int deleted = diseaseService.deleteDiseasesByProjectIdentity(projectName, year, targetCode);
            return AjaxResult.success("删除成功").put("deletedCount", deleted);
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

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
     * 批量导入用户账号。
     *
     * Excel 前四列固定为：片区、姓名、手机号、登录账号。密码固定为 123456。
     */
    @Log(title = "API批量导入用户", businessType = BusinessType.IMPORT)
    @PostMapping("/user/importAccounts")
    @RequiresPermissions("system:user:add")
    @ResponseBody
    @Transactional
    public AjaxResult batchImportUsers(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "skipExisting", defaultValue = "true") boolean skipExisting,
                                       @RequestParam(value = "roleNames", defaultValue = "") String roleNames) throws Exception {
        List<AccountImportRow> rows = readAccountImportRows(file);
        if (rows.isEmpty()) {
            return AjaxResult.error("导入用户数据不能为空");
        }

        Map<String, SysDept> deptByName = buildDeptByName();
        Long[] roleIds = resolveAccountImportRoleIds(roleNames);
        AccountImportResult result = new AccountImportResult();

        for (AccountImportRow row : rows) {
            AccountImportRowResult rowResult = new AccountImportRowResult(row);
            try {
                validateAccountImportRow(row);
                SysDept dept = deptByName.get(row.getArea());
                if (dept == null) {
                    throw new IllegalArgumentException("未找到归属部门：" + row.getArea());
                }

                SysUser loginUser = userService.selectUserByLoginName(row.getLoginName());
                if (loginUser != null) {
                    if (skipExisting) {
                        rowResult.skip("登录账号已存在");
                        result.add(rowResult);
                        continue;
                    }
                    throw new IllegalArgumentException("登录账号已存在");
                }

                if (!isEmpty(row.getPhone()) && userService.selectUserByPhoneNumber(row.getPhone()) != null) {
                    if (skipExisting) {
                        rowResult.skip("手机号已存在");
                        result.add(rowResult);
                        continue;
                    }
                    throw new IllegalArgumentException("手机号已存在");
                }

                deptService.checkDeptDataScope(dept.getDeptId());
                roleService.checkRoleDataScope(roleIds);

                SysUser user = new SysUser();
                user.setDeptId(dept.getDeptId());
                user.setLoginName(row.getLoginName());
                user.setUserName(row.getName());
                user.setPhonenumber(row.getPhone());
                user.setSex("2");
                user.setStatus("0");
                user.setRoleIds(roleIds);
                user.setPostIds(new Long[0]);
                user.setRemark("API批量导入：" + row.getArea());
                user.setSalt(ShiroUtils.randomSalt());
                user.setPassword(passwordService.encryptPassword(user.getLoginName(), "123456", user.getSalt()));
                user.setPwdUpdateDate(DateUtils.getNowDate());
                user.setCreateBy(currentLoginName());
                userService.insertUser(user);

                rowResult.create();
            } catch (Exception e) {
                rowResult.fail(e.getMessage());
            }
            result.add(rowResult);
        }

        AjaxResult ajax = AjaxResult.success("导入完成，成功 " + result.getSuccessCount()
                + " 个，跳过 " + result.getSkippedCount()
                + " 个，失败 " + result.getFailureCount() + " 个");
        ajax.put("successCount", result.getSuccessCount());
        ajax.put("skippedCount", result.getSkippedCount());
        ajax.put("failureCount", result.getFailureCount());
        ajax.put("rows", result.getRows());
        return ajax;
    }

    private List<AccountImportRow> readAccountImportRows(MultipartFile file) throws Exception {
        List<AccountImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null) {
                    continue;
                }
                AccountImportRow row = new AccountImportRow();
                row.setRowNum(i + 1);
                row.setArea(cellString(excelRow, 0, formatter));
                row.setName(cellString(excelRow, 1, formatter));
                row.setPhone(cellString(excelRow, 2, formatter));
                row.setLoginName(cellString(excelRow, 3, formatter));
                if (isEmpty(row.getArea()) && isEmpty(row.getName()) && isEmpty(row.getPhone()) && isEmpty(row.getLoginName())) {
                    continue;
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String cellString(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Math.rint(value) == value) {
                return java.math.BigDecimal.valueOf(value).toPlainString().replaceFirst("\\.0$", "").trim();
            }
        }
        return formatter.formatCellValue(cell).trim();
    }

    private void validateAccountImportRow(AccountImportRow row) {
        if (isEmpty(row.getArea())) {
            throw new IllegalArgumentException("片区不能为空");
        }
        if (isEmpty(row.getName())) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (isEmpty(row.getLoginName())) {
            throw new IllegalArgumentException("登录账号不能为空");
        }
        if (!isEmpty(row.getPhone()) && !row.getPhone().matches("\\d{11}")) {
            throw new IllegalArgumentException("手机号必须为11位数字");
        }
    }

    private Map<String, SysDept> buildDeptByName() {
        Map<String, SysDept> deptByName = new HashMap<>();
        for (SysDept dept : deptService.selectDeptList(new SysDept())) {
            if (dept.getDeptName() != null && !deptByName.containsKey(dept.getDeptName())) {
                deptByName.put(dept.getDeptName(), dept);
            }
        }
        return deptByName;
    }

    private Long[] resolveAccountImportRoleIds(String roleNames) {
        Map<String, Long> roleByName = roleService.selectRoleAll().stream()
                .filter(role -> !role.isAdmin())
                .collect(Collectors.toMap(SysRole::getRoleName, SysRole::getRoleId, (left, right) -> left));
        List<String> names = parseRoleNames(roleNames);
        List<Long> roleIds = new ArrayList<>();
        for (String roleName : names) {
            Long roleId = "普通职员".equals(roleName)
                    ? resolveRoleId(roleByName, "普通职员", "普通角色")
                    : resolveRoleId(roleByName, roleName);
            if (!roleIds.contains(roleId)) {
                roleIds.add(roleId);
            }
        }
        return roleIds.toArray(new Long[0]);
    }

    private List<String> parseRoleNames(String roleNames) {
        String effectiveRoleNames = isEmpty(roleNames) ? "部门业务管理员,部门职员" : roleNames;
        return Arrays.stream(effectiveRoleNames.split("[,，]"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    private Long resolveRoleId(Map<String, Long> roleByName, String... aliases) {
        for (String alias : aliases) {
            Long roleId = roleByName.get(alias);
            if (roleId != null) {
                return roleId;
            }
        }
        throw new IllegalArgumentException("未找到角色：" + String.join("/", aliases));
    }

    private String currentLoginName() {
        try {
            String loginName = ShiroUtils.getLoginName();
            return isEmpty(loginName) ? "api" : loginName;
        } catch (Exception e) {
            return "api";
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Data
    private static class AccountImportRow {
        private int rowNum;
        private String area;
        private String name;
        private String phone;
        private String loginName;
    }

    @Data
    private static class AccountImportResult {
        private int successCount;
        private int skippedCount;
        private int failureCount;
        private List<AccountImportRowResult> rows = new ArrayList<>();

        private void add(AccountImportRowResult row) {
            rows.add(row);
            if ("created".equals(row.getStatus())) {
                successCount++;
            } else if ("skipped".equals(row.getStatus())) {
                skippedCount++;
            } else {
                failureCount++;
            }
        }
    }

    @Data
    private static class AccountImportRowResult {
        private int rowNum;
        private String area;
        private String name;
        private String phone;
        private String loginName;
        private String status;
        private String message;

        private AccountImportRowResult(AccountImportRow row) {
            this.rowNum = row.getRowNum();
            this.area = row.getArea();
            this.name = row.getName();
            this.phone = row.getPhone();
            this.loginName = row.getLoginName();
        }

        private void create() {
            this.status = "created";
            this.message = "创建成功";
        }

        private void skip(String message) {
            this.status = "skipped";
            this.message = message;
        }

        private void fail(String message) {
            this.status = "failed";
            this.message = message;
        }
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


    /**
     * 获取最新公共数据包下载信息。
     * 返回码遵循项目统一AjaxResult规范：code=0表示请求处理成功，code=500表示暂无公共数据包或文件缺失。
     * 公共数据包由后台“公共数据包”页面手动生成，本接口只返回最新已生成包的version、packageSize和MinIO下载url。
     */
    @GetMapping("/user/commonPackage")
    @ResponseBody
    public AjaxResult getCommonPackage() {
        return packageService.getLatestCommonTemplatePackage();
    }

    /**
     * 获取移动端App当前发布版本信息。
     * 返回码遵循项目统一AjaxResult规范：code=0表示请求处理成功，code=500表示暂无发布包或文件映射缺失。
     * 成功时返回version、apkName、packageSize和MinIO下载url，用于移动端检查版本更新。
     */
    @GetMapping("/user/appUpdate")
    @ResponseBody
    public AjaxResult getAppUpdate() {
        return appPackageService.getPublishedAppUpdate();
    }

    @GetMapping("/user/dataPackageTest")
    @ResponseBody
    public AjaxResult getUserDataPackageTest() {
        return userPackageTask.generateUserDataPackage();
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

    @Autowired
    private BiTemplateObjectMapper biTemplateObjectMapper;

    @Resource
    private TODiseaseTypeMapper toDiseaseTypeMapper;

    @GetMapping("/updateTDiseaseType")
    @ResponseBody
    @Transactional
    public AjaxResult updateTDiseaseType(@RequestParam("tdId") Long tdId) {
        List<BiTemplateObject> liangshis = biTemplateObjectMapper.selectChildrenById(102L);

        List<BiTemplateObject> biTemplateObjects = biTemplateObjectMapper.selectChildrenById(tdId);
        BiTemplateObject biTemplateObject_2 = biTemplateObjects.stream().filter(biTemplateObject -> biTemplateObject.getName().equals("下部结构")).findFirst().orElse(null);

        List<BiTemplateObject> children = biTemplateObjectMapper.selectChildrenById(biTemplateObject_2.getId());
        List<BiTemplateObject> children_3 = children.stream().filter(child -> child.getParentId().equals(biTemplateObject_2.getId())).toList();

        List<BiTemplateObject> liangshis_3 = liangshis.stream().filter(liangshi -> liangshi.getParentId().equals(102L)).toList();


        Map<Long, List<Long>> templateToDiseaseTypeIds = new HashMap<>();

        liangshis_3.forEach(liangshi_3 -> {
            children_3.stream().filter(child_3 -> child_3.getName().equals(liangshi_3.getName())).findFirst().ifPresent(child_3 -> {
                List<BiTemplateObject> liangshiList = liangshis.stream()
                        .filter(langshi -> langshi.getParentId().equals(liangshi_3.getId()))
                        .toList();
                Map<Long, BiTemplateObject> collect = liangshiList.stream().collect(Collectors.toMap(liangshi -> liangshi.getId(), liangshi -> liangshi));
                List<Long> liangshiIds = liangshiList.stream().map(liangshi -> liangshi.getId()).toList();
                List<Map<String, Object>> mappings = toDiseaseTypeMapper.selectTemplateObjectDiseaseTypeMappings(liangshiIds);


                for (Map<String, Object> mapping : mappings) {
                    Long templateObjectId = ((Number) mapping.get("template_object_id")).longValue();
                    Long diseaseTypeId = ((Number) mapping.get("disease_type_id")).longValue();

                    BiTemplateObject biTemplateObject = collect.get(templateObjectId);
                    children.stream().filter(child -> child.getParentId().equals(child_3.getId()) && child.getName().equals(biTemplateObject.getName()))
                            .findFirst().ifPresent(child -> {
                                templateToDiseaseTypeIds.computeIfAbsent(child.getId(), k -> new ArrayList<>()).add(diseaseTypeId);
                            });
                }
            });
        });

        templateToDiseaseTypeIds.forEach((templateObjectId, diseaseTypeIds) -> {
            toDiseaseTypeMapper.batchInsertBridgeTemplateDiseaseType(templateObjectId, diseaseTypeIds);
        });

        return AjaxResult.success();
    }

    /**
     * 修正桥梁数据
     */
    @PostMapping("/reassignComponentsFromOthers/{rootObjectId}")
    public AjaxResult reassignComponentsFromOthers(
            @PathVariable("rootObjectId") Long rootObjectId) {
        return AjaxResult.success(biObjectService.reassignComponentsFromOthers(rootObjectId));
    }

    /**
     * 批量修正桥梁数据
     */
    @PostMapping("/batchreassignComponentsFromOthers")
    public AjaxResult batchreassignComponentsFromOthers() {
        List<BiObject> biObjects = biObjectService.selectDirectChildrenByParentId(0L);
        for (int i=0;i<biObjects.size();i++) {
            BiObject biObject = biObjects.get(i);
            log.info("开始处理第{}个，桥名{}", i,biObject.getName());
            biObjectService.reassignComponentsFromOthers(biObject.getId());
            log.info("处理结束第{}个，桥名{}", i,biObject.getName());
        }
        return AjaxResult.success();
    }

    @GetMapping("/updateErrorData")
    @ResponseBody
    @Transactional
    public AjaxResult updateErrorData(Date updateTime) {

        List<Disease> diseases = diseaseMapper.selectErrorDiseases(updateTime);

        Map<Long, List<BiObject>> biObjectsMap = diseases.stream().map(disease -> disease.getBuildingId())
                .distinct()
                .collect(Collectors.toMap(buildingId -> buildingId, buildingId -> {
                    Building building = buildingService.selectBuildingById(buildingId);
                    return biObjectService.selectBiObjectAndChildren(building.getRootObjectId());
                }));

        final int[] count = {0};
        diseases.stream().forEach(disease -> {
            List<BiObject> biObjects = biObjectsMap.get(disease.getBuildingId());
            BiObject target = biObjects.stream().filter(biObject -> biObject.getTemplateObjectId().equals(disease.getBiObjectId()))
                    .findFirst().orElse(null);
            if (target == null)
                throw new RuntimeException("没有对应的biObject: " + disease.getBiObjectId());
            disease.setBiObjectId(target.getId());
            count[0] += diseaseService.updateDisease(disease);
        });


        return AjaxResult.success(count[0]);
    }

    @Resource
    private IBiTemplateObjectService biTemplateObjectService;
    @GetMapping("/updateTemplate")
    @ResponseBody
    @Transactional
    public AjaxResult updateTemplate() {
        BiTemplateObject biTemplateObject = new BiTemplateObject();
        biTemplateObject.setParentId(0L);
        List<BiTemplateObject> biTemplateObjects = biTemplateObjectMapper.selectBiTemplateObjectList(biTemplateObject);

        biTemplateObjects.forEach(bt -> {
            List<BiTemplateObject> biTemplateObject2s = biTemplateObjectMapper.selectChildrenById(bt.getId());
            // 找到下部结构
            BiTemplateObject biTemplateObject_2 = biTemplateObject2s.stream()
                    .filter(biTemplateObject2 -> biTemplateObject2.getParentId().equals(bt.getId()) && biTemplateObject2.getName().equals("下部结构"))
                    .findFirst().orElse(null);

            // 第三层
            List<BiTemplateObject> biTemplateObject3s = biTemplateObject2s.stream().filter(bt2 -> bt2.getParentId().equals(biTemplateObject_2.getId())).toList();

            biTemplateObject3s.forEach(biTemplateObject3 -> {
                // 第四层
                List<BiTemplateObject> biTemplateObject4s = biTemplateObject2s.stream()
                        .filter(bt3 -> bt3.getParentId().equals(biTemplateObject3.getId()))
                        .toList();

                // 查第五层
                biTemplateObject4s.forEach(biTemplateObject4 -> {
                    // 判断其下有无左侧面和左侧面
                    List<BiTemplateObject> list = biTemplateObject2s.stream().filter(bt4 -> bt4.getParentId().equals(biTemplateObject4.getId())).toList();

                    BiTemplateObject left = list.stream().filter(bt5 -> bt5.getName().equals("左侧面")).findFirst().orElse(null);
                    BiTemplateObject right = list.stream().filter(bt5 -> bt5.getName().equals("右侧面")).findFirst().orElse(null);
                    if (left != null && right != null) {
                        // 判断是否有内侧面和外侧面
                        BiTemplateObject inner = list.stream().filter(bt5 -> bt5.getName().equals("内侧面")).findFirst().orElse(null);

                        if (inner != null) {
                            inner.setOrderNum(4);
                            biTemplateObjectService.updateBiTemplateObject(inner);
                        }
                    }
                });
            });

        });


        return AjaxResult.success();
    }

    @Resource
    private ReadFileServiceImpl readFileService;

    @PostMapping("/batchAddBuilding")
    @ResponseBody
    public AjaxResult batchAddBuilding(MultipartFile file, Long projectId) {
        int importCount = readFileService.ReadBuildingFile(file, projectId);
        AjaxResult ajax = AjaxResult.success("导入成功，共新增 " + importCount + " 座桥梁");
        ajax.put("importCount", importCount);
        return ajax;
    }

    @PostMapping("/batchResumeBuilding")
    @ResponseBody
    public AjaxResult batchResumeBuilding(MultipartFile file) {
        int resumeCount = readFileService.resumeBuildingFile(file);
        AjaxResult ajax = AjaxResult.success("修复成功，共修复 " + resumeCount + " 座桥梁");
        ajax.put("resumeCount", resumeCount);
        return ajax;
    }

    @PostMapping("/batchImportBridgeCards")
    @ResponseBody
    public AjaxResult batchImportBridgeCards(MultipartFile file, Long projectId) {
        BatchBridgeCardImportResult result = readFileService.batchImportBridgeCards(file, projectId);
        AjaxResult ajax = AjaxResult.success("导入完成，成功 " + result.getSuccessCount()
                + " 个，跳过 " + result.getSkippedCount()
                + " 个，失败 " + result.getFailureCount() + " 个");
        ajax.put("result", result);
        return ajax;
    }

    @PostMapping("/batchImportCBMSDiseases")
    @ResponseBody
    public AjaxResult batchImportCBMSDiseases(MultipartFile file, Long projectId, String projectName) {
        BatchCbmsDiseaseImportResult result = readFileService.batchImportCBMSDiseases(file, projectId, projectName);
        AjaxResult ajax = AjaxResult.success("导入完成，成功 " + result.getSuccessCount()
                + " 个，跳过 " + result.getSkippedCount()
                + " 个，失败 " + result.getFailureCount() + " 个");
        ajax.put("result", result);
        return ajax;
    }


    /**
     * 修复病害与构件的关联关系
     * 扫描2025-09-13至2025-09-14期间的病害数据，
     * 清理不一致的构件关联，并重新匹配或创建正确的构件
     */
    @PostMapping("/building/batchUpdateLine")
    @ResponseBody
    public AjaxResult batchUpdateBuildingLine(@RequestParam("originalLine") String originalLine,
                                              @RequestParam("targetLine") String targetLine) {
        int updateCount = buildingService.batchUpdateLine(originalLine, targetLine);
        AjaxResult ajax = AjaxResult.success("修改成功，共更新 " + updateCount + " 座桥梁");
        ajax.put("updateCount", updateCount);
        return ajax;
    }

    @PostMapping("/fixDiseaseComponents")
    @ResponseBody
    @Transactional
    public AjaxResult fixDiseaseComponents() {
        try {
            // 查询指定时间范围内的病害
            String startTime = "2025-09-13 00:00:00";
            String endTime = "2025-09-14 23:59:59";
            List<Disease> diseases = diseaseMapper.selectDiseasesByTimeRange(startTime, endTime);

            if (diseases == null || diseases.isEmpty()) {
                return AjaxResult.success("未找到需要处理的病害数据");
            }

            int processedCount = 0; // 处理的病害数量
            int fixedCount = 0; // 修复的构件关联数量
            int createdCount = 0; // 新建的构件数量
            int deletedCount = 0; // 删除的构件数量

            for (Disease disease : diseases) {
                // 步骤1: 解析病害描述，提取code和name
                String description = disease.getDescription();
                if (description == null || description.trim().isEmpty()) {
                    processedCount++;
                    continue;
                }

                String code = extractCode(description);
                String name = extractName(description);

                // 步骤2: 验证并清理旧的构件关联
                if (disease.getComponentId() != null) {
                    Component oldComponent = componentService.selectComponentById(disease.getComponentId());
                    if (oldComponent != null) {
                        // 检查构件的bi_object_id是否与病害的bi_object_id一致
                        if (!oldComponent.getBiObjectId().equals(disease.getBiObjectId())) {
                            // 不一致，逻辑删除该构件
                            code = oldComponent.getCode();
                            name = oldComponent.getName();
                            componentService.deleteComponentById(oldComponent.getId());
                            deletedCount++;
                        }
                    }
                }
                log.info("code:{},name:{}",code,name);
                // 步骤3: 查询病害的bi_object_id对应的构件列表
                List<Component> components = componentService.selectComponentsByBiObjectId(disease.getBiObjectId());
                log.info("查询构件列表：{}", components);
                Component matchedComponent = null;

                // 步骤4: 匹配逻辑
                if (components != null && !components.isEmpty()) {
                    // 遍历构件列表，比对name
                    for (Component component : components) {
                        if (name.equals(component.getName())) {
                            matchedComponent = component;
                            log.info("匹配已有构件：{}", matchedComponent);
                            break;
                        }
                    }
                }

                // 步骤5: 如果没有匹配到，创建新构件
                if (matchedComponent == null) {
                    Component newComponent = new Component();
                    newComponent.setBiObjectId(disease.getBiObjectId());
                    newComponent.setCode(code);
                    newComponent.setName(name);
                    newComponent.setStatus("0");
                    newComponent.setDelFlag("0");
                    newComponent.setCreateBy(ShiroUtils.getLoginName());
                    newComponent.setCreateTime(new Date());

                    componentService.insertComponent(newComponent);
                    matchedComponent = newComponent;
                    createdCount++;
                }

                // 步骤6: 更新病害的component_id
                if (matchedComponent != null && !matchedComponent.getId().equals(disease.getComponentId())) {
                    disease.setComponentId(matchedComponent.getId());
                    disease.setUpdateBy(ShiroUtils.getLoginName());
                    disease.setUpdateTime(new Date());
                    diseaseService.updateDisease(disease);
                    fixedCount++;
                }

                processedCount++;
            }


            String message = String.format("处理完成！共处理 %d 条病害数据，修复 %d 个构件关联，新建 %d 个构件，删除 %d 个不一致的构件",
                    processedCount, fixedCount, createdCount, deletedCount);
            log.info(message);
            return AjaxResult.success(message);

        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("处理失败：" + e.getMessage());
        }
    }

    /**
     * 从描述中提取构件编号（#号前的内容）
     */
    private String extractCode(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        int hashIndex = description.indexOf("#");
        if (hashIndex > 0) {
            return description.substring(0, hashIndex);
        }
        return "";
    }

    /**
     * 从描述中提取构件名称（第一个逗号前的内容）
     */
    private String extractName(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }
        int commaIndex = description.indexOf("，");
        if (commaIndex == -1) {
            commaIndex = description.indexOf(",");
        }
        if (commaIndex > 0) {
            return description.substring(0, commaIndex).trim();
        }
        // 没有逗号，返回整个描述
        return description.trim();
    }

    @PostMapping("/dict/data/list")
    public List<SysDictData> listDictData(SysDictData dictData) {
        return dictDataService.selectDictDataListForApi(dictData);
    }

    @PostMapping("/getParentBuilding")
    public AjaxResult getParentBuilding(Long id) {
        // 获取所有可选的父桥（组合桥）
        Building parentQuery = new Building();
        parentQuery.setIsLeaf("0");
        parentQuery.setStatus("0");
        List<Building> parentBuildings = buildingService.selectBuildingList(parentQuery);

        return AjaxResult.success(parentBuildings);
    }

    @PostMapping("/addBuilding")
    public AjaxResult addBuilding(Building building, Long projectId) {
        String area = building.getArea();
        String line = building.getLine();
        String buildingName = building.getName();
        Long templateId = building.getTemplateId();
        if (area == null || line == null || buildingName == null || templateId != null || projectId == null) {
            throw new RuntimeException("错误，参数不全");
        }

        // 判断是否已经存在
        Building query = new Building();
        query.setName(buildingName);
        query.setArea(area);
        query.setLine(line);
        List<Building> buildings = buildingService.selectBuildingList(query);
        if (buildings != null && !buildings.isEmpty()) {
            return AjaxResult.error("该区域线路下已存在同名桥");
        }

        // 新增新桥
        Building newBuilding = new Building();

        if (building.getParentId() != null) {
            // 先获取父桥
            Building parentBuilding = buildingService.selectBuildingById(building.getParentId());
            newBuilding.setParentId(parentBuilding.getId());
        }

        newBuilding.setLine(line);
        newBuilding.setName(buildingName);
        newBuilding.setArea(area);
        newBuilding.setStatus("0");
        newBuilding.setTemplateId(templateId);
        newBuilding.setIsLeaf("1");
        buildingService.insertBuilding(newBuilding);

        // 添加到项目
        taskService.batchInsertTasks(projectId, List.of(newBuilding.getId()));

        return AjaxResult.success();
    }

    @PostMapping("/batchImportPropertyData")
    public AjaxResult batchImportPropertyData(MultipartFile file, boolean needLine) {
        return AjaxResult.success(propertyIndexService.batchImportPropertyData(file, needLine));
    }

    @PostMapping("/addThumbPhoto")
    public AjaxResult addThumbPhoto(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null)
            return AjaxResult.error("任务不存在");

        Disease query = new Disease();
        query.setBuildingId(task.getBuildingId());
        query.setProjectId(task.getProjectId());
        List<Disease> diseases = diseaseMapper.selectDiseaseList(query);
        if (CollUtil.isEmpty(diseases))
            return AjaxResult.success();

        // 获取 Attachments
        List<Long> subjectIds = diseases.stream().map(Disease::getId).toList();
        List<Attachment> attachmentBySubjects = attachmentService.getAttachmentBySubjectIds(subjectIds);
        List<CompletableFuture<Void>> completableFutures = readFileService.addThumbPhoto(attachmentBySubjects);
        // 等待所有缩略图生成完成
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();

        return AjaxResult.success();
    }
}
