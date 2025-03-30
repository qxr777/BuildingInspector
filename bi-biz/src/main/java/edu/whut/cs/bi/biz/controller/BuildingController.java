package edu.whut.cs.bi.biz.controller;

import java.util.List;

import com.ruoyi.common.utils.ShiroUtils;
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

    @Autowired
    private IBuildingService buildingService;

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
    public String add() {
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
        Building building = buildingService.selectBuildingById(id);
        mmap.put("building", building);
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
        building.setUpdateBy(ShiroUtils.getLoginName());
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
}
