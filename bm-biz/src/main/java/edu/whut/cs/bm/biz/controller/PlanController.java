package edu.whut.cs.bm.biz.controller;

import java.util.List;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bm.biz.service.IPlanService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bm.biz.domain.Plan;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

import javax.validation.Valid;

/**
 * 抢修抢建方案Controller
 * 
 * @author qixin
 * @date 2021-08-09
 */
@Controller
@RequestMapping("/biz/plan")
public class PlanController extends BaseController
{
    private String prefix = "biz/plan";

    @Autowired
    private IPlanService planService;

    @RequiresPermissions("biz:plan:view")
    @GetMapping()
    public String plan()
    {
        return prefix + "/plan";
    }

    /**
     * 查询抢修抢建方案列表
     */
    @RequiresPermissions("biz:plan:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Plan plan)
    {
        startPage();
        List<Plan> list = planService.selectPlanList(plan);
        return getDataTable(list);
    }

    /**
     * 导出抢修抢建方案列表
     */
    @RequiresPermissions("biz:plan:export")
    @Log(title = "抢修抢建方案", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Plan plan)
    {
        List<Plan> list = planService.selectPlanList(plan);
        ExcelUtil<Plan> util = new ExcelUtil<Plan>(Plan.class);
        return util.exportExcel(list, "抢修抢建方案数据");
    }

    /**
     * 新增抢修抢建方案
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存抢修抢建方案
     */
    @RequiresPermissions("biz:plan:add")
    @Log(title = "抢修抢建方案", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid Plan plan)
    {
        plan.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(planService.insertPlan(plan));
    }

    /**
     * 修改抢修抢建方案
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Plan plan = planService.selectPlanById(id);
        mmap.put("plan", plan);
        return prefix + "/edit";
    }

    /**
     * 修改保存抢修抢建方案
     */
    @RequiresPermissions("biz:plan:edit")
    @Log(title = "抢修抢建方案", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid Plan plan)
    {
        plan.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(planService.updatePlan(plan));
    }

    /**
     * 删除抢修抢建方案
     */
    @RequiresPermissions("biz:plan:remove")
    @Log(title = "抢修抢建方案", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(planService.deletePlanByIds(ids));
    }
}
