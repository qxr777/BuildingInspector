package edu.whut.cs.bi.biz.controller;

import java.util.ArrayList;
import java.util.List;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.dto.CodeSegment;
import edu.whut.cs.bi.biz.service.IComponentService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.Component;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 构件管理Controller
 */
@Controller
@RequestMapping("/biz/component")
public class ComponentController extends BaseController {
    private String prefix = "biz/component";

    @Autowired
    private IComponentService componentService;

    @RequiresPermissions("biz:component:view")
    @GetMapping()
    public String component() {
        return prefix + "/component";
    }

    /**
     * 打开构件列表页面
     */
    @RequiresPermissions("biz:component:view")
    @GetMapping("/list")
    public String list(@RequestParam("rootObjectId") String rootObjectId, ModelMap mmap) {
        mmap.put("rootObjectId", rootObjectId);
        return prefix + "/component";
    }

    /**
     * 查询构件列表
     */
    @RequiresPermissions("biz:component:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Component component) {
        startPage();
        List<Component> list;
        if (component.getBiObjectId() != null && !component.getBiObjectId().isEmpty()) {
            list = componentService.selectComponentsByBiObjectIdAndChildren(component.getBiObjectId());
        } else {
            list = new ArrayList<>();
        }
        return getDataTable(list);
    }

    /**
     * 查询构件列表
     */
    @RequiresPermissions("biz:component:list")
    @PostMapping("/selectList")
    @ResponseBody
    public List<Component> selectList(Component component) {
        return componentService.selectComponentsByBiObjectIdAndChildren(component.getBiObjectId());
    }

    /**
     * 导出构件列表
     */
    @RequiresPermissions("biz:component:export")
    @Log(title = "构件", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Component component) {
        List<Component> list = componentService.selectComponentList(component);
        ExcelUtil<Component> util = new ExcelUtil<Component>(Component.class);
        return util.exportExcel(list, "构件数据");
    }

    /**
     * 新增构件
     */
    @GetMapping("/add")
    public String add(Long biObjectId, ModelMap mma) {
        if (biObjectId != null) {
            mma.put("biObjectId", biObjectId);
        }
        return prefix + "/add";
    }

    /**
     * 新增保存构件
     */
    @RequiresPermissions("biz:component:add")
    @Log(title = "构件", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Component component) {
        component.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(componentService.insertComponent(component));
    }

    /**
     * 修改构件
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Component component = componentService.selectComponentById(id);
        mmap.put("component", component);
        return prefix + "/edit";
    }

    /**
     * 修改保存构件
     */
    @RequiresPermissions("biz:component:edit")
    @Log(title = "构件", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Component component) {
        component.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(componentService.updateComponent(component));
    }

    /**
     * 删除构件
     */
    @RequiresPermissions("biz:component:remove")
    @Log(title = "构件", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(componentService.deleteComponentByIds(ids));
    }

    /**
     * 打开编号生成配置页面
     */
    @GetMapping("/generateCode/{biObjectId}")
    public String generateCode(@PathVariable("biObjectId") String biObjectId, ModelMap mmap) {
        mmap.put("biObjectId", biObjectId);
        return prefix + "/generateCode";
    }

    /**
     * 生成构件编号
     */
    @RequiresPermissions("biz:component:add")
    @Log(title = "构件", businessType = BusinessType.INSERT)
    @PostMapping("/generateComponents")
    @ResponseBody
    public AjaxResult generateComponents(@RequestParam("biObjectId") String biObjectId, @RequestBody List<CodeSegment> segments) {
        try {
            int count = componentService.generateComponents(biObjectId, segments);
            return success("成功生成" + count + "个构件");
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}