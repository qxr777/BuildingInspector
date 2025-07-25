package edu.whut.cs.bi.biz.controller;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.vo.ProjectBuildingVO;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.service.IBuildingService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 建筑Controller
 *
 * @author wanzheng
 * @date 2025-03-27
 */
@Controller
@RequestMapping("/biz/building")
public class BuildingController extends BaseController {
    private String prefix = "biz/building";

    @Resource
    private IBuildingService buildingService;

    @Autowired
    private IBiTemplateObjectService biTemplateObjectService;

    @Autowired
    private IBiObjectService biObjectService;

    @RequiresPermissions("biz:building:view")
    @GetMapping()
    public String building() {
        return prefix + "/building";
    }

    /**
     * 查询建筑列表
     */
    @RequiresPermissions("biz:building:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Building building) {
        startPage();
        List<Building> list = buildingService.selectBuildingList(building);
        return getDataTable(list);
    }

    /**
     * 查询建筑列表
     */
    @RequiresPermissions("biz:building:list")
    @PostMapping("/listVO")
    @ResponseBody
    public TableDataInfo listVO(ProjectBuildingVO building, Long projectId) {
        startPage();
        List<ProjectBuildingVO> list = buildingService.selectBuildingVOList(building, projectId);
        return getDataTable(list);
    }

    /**
     * 导出建筑列表
     */
    @RequiresPermissions("biz:building:export")
    @Log(title = "建筑", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Building building) {
        List<Building> list = buildingService.selectBuildingList(building);
        ExcelUtil<Building> util = new ExcelUtil<Building>(Building.class);
        return util.exportExcel(list, "建筑数据");
    }

    /**
     * 新增建筑
     */
    @RequiresPermissions("biz:building:add")
    @GetMapping("/add")
    public String add(ModelMap mmap) {
        // 获取所有可用的模板（状态为正常的根节点模板）
        BiTemplateObject templateQuery = new BiTemplateObject();
        templateQuery.setParentId(0L);
        templateQuery.setStatus("0");
        List<BiTemplateObject> templates = biTemplateObjectService.selectBiTemplateObjectList(templateQuery);
        mmap.put("templates", templates);

        // 获取所有可选的父桥（组合桥）
        Building parentQuery = new Building();
        parentQuery.setIsLeaf("0");
        parentQuery.setStatus("0");
        List<Building> parentBuildings = buildingService.selectBuildingList(parentQuery);
        mmap.put("parents", parentBuildings);

        return prefix + "/add";
    }

    /**
     * 新增保存建筑
     */
    @RequiresPermissions("biz:building:add")
    @Log(title = "建筑", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Building building) {
        building.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(buildingService.insertBuilding(building));
    }

    /**
     * 修改建筑
     */
    @RequiresPermissions("biz:building:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        // 使用带有父桥信息的查询
        Building building = buildingService.selectBuildingWithParentInfo(id);
        mmap.put("building", building);

        // 获取所有可选的父桥（组合桥）
        Building parentQuery = new Building();
        parentQuery.setIsLeaf("0");
        parentQuery.setStatus("0");
        parentQuery.setDelFlag("0");
        List<Building> allParentBuildings = buildingService.selectBuildingList(parentQuery);

        // 获取当前桥梁的所有子桥（如果是组合桥）
        List<Long> excludeIds = new ArrayList<>();
        excludeIds.add(id); // 添加自身ID，避免自选

        if ("0".equals(building.getIsLeaf())) {
            // 获取所有子桥的ID
            List<Long> childIds = buildingService.selectChildBuildingIds(id);
            if (childIds != null && !childIds.isEmpty()) {
                excludeIds.addAll(childIds);
            }
        }

        // 排除当前桥梁及其所有子桥，避免循环依赖
        List<Building> parentBuildings = allParentBuildings.stream()
                .filter(parent -> !excludeIds.contains(parent.getId()))
                .collect(Collectors.toList());

        mmap.put("parents", parentBuildings);

        return prefix + "/edit";
    }

    /**
     * 修改保存建筑
     */
    @RequiresPermissions("biz:building:edit")
    @Log(title = "建筑", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Building building) {
        building.setCreateBy(ShiroUtils.getLoginName());
        building.setUpdateTime(new Date());
        return toAjax(buildingService.updateBuilding(building));
    }

    /**
     * 删除建筑
     */
    @RequiresPermissions("biz:building:remove")
    @Log(title = "建筑", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(buildingService.deleteBuildingByIds(ids));
    }

    @GetMapping("/readJson")
    public String readJson() {
        return prefix + "/readJson";
    }

    /**
     * 通过json文件添加
     */
    @PostMapping("/readJson")
    @ResponseBody
    @RequiresPermissions("biz:building:add")
    @Log(title = "建筑", businessType = BusinessType.IMPORT)
    public AjaxResult readJsonFile(@RequestPart("file") MultipartFile file) throws IOException {
        return toAjax(buildingService.importJson(file));
    }

    /**
     * 项目选择桥梁
     */
    @RequiresPermissions("biz:project:edit")
    @GetMapping("/list/{projectId}")
    public String assignUsers(@PathVariable("projectId") String projectId, ModelMap mmap) {
        mmap.put("projectId", projectId);
        return "biz/project/selectBuilding";
    }

    /**
     * 获取当前部件的图片
     */
    @RequiresPermissions("biz:building:view")
    @GetMapping("/currentPhoto")
    public String list(@RequestParam("rootObjectId") String rootObjectId, ModelMap mmap) {
        mmap.put("biObjectId", rootObjectId);
        return prefix + "/photo/currentPhoto";
    }

}
