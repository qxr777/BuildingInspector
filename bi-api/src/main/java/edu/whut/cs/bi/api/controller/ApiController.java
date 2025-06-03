package edu.whut.cs.bi.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.api.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.api.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.api.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.AjaxResult;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

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
        return AjaxResult.success("查询成功", propertyService.selectPropertyTree(building.getRootPropertyId()));
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

        List<Disease> diseases = diseaseService.selectDiseaseList(disease);

        DiseasesOfYearVo diseasesOfYearVo = new DiseasesOfYearVo();
        diseasesOfYearVo.setDiseases(diseases);
        if (year != null) {
            diseasesOfYearVo.setYear(year);
        }

        diseasesOfYearVo.setBuildingId(buildingId);

        return AjaxResult.success("查询成功", diseasesOfYearVo);
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
        if (userId == null) {
            return AjaxResult.error("参数错误");
        }

        List<Project> projects = projectService.selectProjectListByUserIdAndRole(userId, ProjectUserRoleEnum.INSPECTOR.getValue());

        ProjectsOfUserVo projectsOfUserVo = new ProjectsOfUserVo();
        projectsOfUserVo.setProjects(projects);
        projectsOfUserVo.setUserId(userId);

        return AjaxResult.success("查询成功", projectsOfUserVo);
    }

    /**
     * 更新桥梁结构树
     * 接收完整的树形结构数据，递归处理每个节点及其子节点
     * 对于包含comments的节点，清空原有构件并添加新构件
     */
    @PostMapping("/building/updateObjectTree")
    @ResponseBody
    @Transactional
    public AjaxResult updateObjectTree(@RequestBody BiObject rootObject) {
        try {
            if (rootObject == null || rootObject.getId() == null) {
                return AjaxResult.error("参数错误：无效的根节点");
            }

            // 更新整个树结构
            int updateCount = biObjectService.updateBiObjectTreeRecursively(rootObject);

            return AjaxResult.success("桥梁结构更新成功", updateCount);
        } catch (Exception e) {
            return AjaxResult.error("更新桥梁结构失败：" + e.getMessage());
        }
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
            for (Disease disease : diseases) {
                // 设置创建时间
                disease.setCreateTime(DateUtils.getNowDate());

                // 通过构件名称查找构件ID
                if (disease.getComponent() != null && disease.getComponent().getName() != null && disease.getComponentId() == null) {
                    Component queryComponent = new Component();
                    queryComponent.setName(disease.getComponent().getName());
                    queryComponent.setBiObjectId(disease.getBiObjectId());

                    List<Component> components = componentService.selectComponentList(queryComponent);
                    if (!components.isEmpty()) {
                        disease.setComponentId(components.get(0).getId());
                    }
                }

                // 插入病害记录
                successCount += diseaseMapper.insertDisease(disease);
            }

            return AjaxResult.success("批量保存病害成功", successCount);
        } catch (Exception e) {
            return AjaxResult.error("批量保存病害失败：" + e.getMessage());
        }
    }

}