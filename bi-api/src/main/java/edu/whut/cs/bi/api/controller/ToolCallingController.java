package edu.whut.cs.bi.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.api.vo.DiseasesOfYearVo;
import edu.whut.cs.bi.api.vo.ProjectsOfUserVo;
import edu.whut.cs.bi.api.vo.PropertyTreeVo;
import edu.whut.cs.bi.api.vo.TasksOfProjectVo;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.service.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Junbo
 * @date 2025/6/11
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/toolCallingApi")
public class ToolCallingController {
    @Resource
    private FileMapController fileMapController;
    @Resource
    private IBuildingService buildingService;
    @Resource
    private IPropertyService propertyService;
    @Resource
    private IBiObjectService biObjectService;
    @Resource
    private IProjectService projectService;
    @Resource
    private ITaskService taskService;
    @Resource
    private IDiseaseService diseaseService;

    @GetMapping("/building/{bName}/property")
    @ResponseBody
    @Anonymous
    public AjaxResult getProperty(@PathVariable("bName") String bName) {
        if (bName == null) {
            return AjaxResult.error("参数错误");
        }
        Building building_name = new Building();
        building_name.setName(bName);
        Long buildingId = buildingService.selectBuildingList(building_name).get(0).getId();
        Building building = buildingService.selectBuildingById(buildingId);
        List<FileMap> imageMaps = fileMapController.getImageMaps(buildingId,"newfront","newside");
        Map<String, List<String>> collect = imageMaps.stream().collect(Collectors.groupingBy(
                image -> image.getOldName().split("_")[1],
                Collectors.mapping(FileMap::getNewName, Collectors.toList())
        ));
        Property property = propertyService.selectPropertyTree(building.getRootPropertyId());

        // 封装返回结果
        PropertyTreeVo propertyTreeVo = new PropertyTreeVo();
        propertyTreeVo.setProperty(property);
        propertyTreeVo.setImages(collect);
        return AjaxResult.success("查询成功", propertyTreeVo);
    }


    @GetMapping("/building/{bName}/object")
    @ResponseBody
    public AjaxResult getObjectTree(@PathVariable("bName") String bName) {
        try {
            // 查询建筑物的root_object_id
            Building building_query = new Building();
            building_query.setName(bName);
            Building building = buildingService.selectBuildingList(building_query).get(0);
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

    @GetMapping("/project/{id}")
    @ResponseBody
    public AjaxResult getProject(@PathVariable("id") Long userId) {

        List<Project> projects = projectService.selectProjectListByUserIdAndRole(userId, ProjectUserRoleEnum.INSPECTOR.getValue());
        ProjectsOfUserVo projectsOfUserVo = new ProjectsOfUserVo();
        projectsOfUserVo.setProjects(projects);
        projectsOfUserVo.setUserId(userId);
        return AjaxResult.success("查询成功", projectsOfUserVo);
    }

    @GetMapping("/project/{projectId}/task")
    @ResponseBody
    public AjaxResult getTask(@PathVariable("projectId") Long projectId) {
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
    @GetMapping("/building/{bid}/disease")
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

    @GetMapping("/building/{bName}")
    @ResponseBody
    public AjaxResult getBuildingListByName(@PathVariable(required = true) String bName){
        Building building = new Building();
        building.setName(bName);
        List<Building> buildings = buildingService.selectBuildingList(building);
        return AjaxResult.success("查询成功", buildings);
    }





}
