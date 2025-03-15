package edu.whut.cs.bm.biz.controller;

import java.util.List;

import edu.whut.cs.bm.biz.domain.*;
import edu.whut.cs.bm.biz.service.IIndexService;
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
import edu.whut.cs.bm.biz.service.IAlertRuleService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

import javax.validation.Valid;

/**
 * 预警规则Controller
 * 
 * @author qixin
 * @date 2021-08-13
 */
@Controller
@RequestMapping("/biz/rule")
public class AlertRuleController extends BaseController
{
    private String prefix = "biz/rule";

    @Autowired
    private IAlertRuleService alertRuleService;

    @Autowired
    private IIndexService indexService;

    @Autowired
    private IPlanService planService;

    @RequiresPermissions("biz:rule:view")
    @GetMapping()
    public String rule(ModelMap modelMap)
    {
        List<Index> list = indexService.selectIndexList(new Index());
        modelMap.put("list", list);
        return prefix + "/rule";
    }

    /**
     * 查询预警规则列表
     */
    @RequiresPermissions("biz:rule:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(AlertRule alertRule)
    {
        startPage();
        List<AlertRule> list = alertRuleService.selectAlertRuleList(alertRule);
        return getDataTable(list);
    }

    /**
     * 导出预警规则列表
     */
    @RequiresPermissions("biz:rule:export")
    @Log(title = "预警规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(AlertRule alertRule)
    {
        List<AlertRule> list = alertRuleService.selectAlertRuleList(alertRule);
        ExcelUtil<AlertRule> util = new ExcelUtil<AlertRule>(AlertRule.class);
        return util.exportExcel(list, "预警规则数据");
    }

    /**
     * 新增预警规则
     */
    @GetMapping("/add")
    public String add(ModelMap modelMap)
    {
        List<Index> list = indexService.selectIndexList(new Index());
        modelMap.put("list", list);
        return prefix + "/add";
    }

    /**
     * 新增保存预警规则
     */
    @RequiresPermissions("biz:rule:add")
    @Log(title = "预警规则", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid AlertRule alertRule)
    {
        return toAjax(alertRuleService.insertAlertRule(alertRule));
    }

    /**
     * 修改预警规则
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        List<Index> list = indexService.selectIndexList(new Index());
        mmap.put("list", list);
        AlertRule alertRule = alertRuleService.selectAlertRuleById(id);
        mmap.put("alertRule", alertRule);
        return prefix + "/edit";
    }

    /**
     * 修改保存预警规则
     */
    @RequiresPermissions("biz:rule:edit")
    @Log(title = "预警规则", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid AlertRule alertRule)
    {
        return toAjax(alertRuleService.updateAlertRule(alertRule));
    }

    /**
     * 删除预警规则
     */
    @RequiresPermissions("biz:rule:remove")
    @Log(title = "预警规则", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(alertRuleService.deleteAlertRuleByIds(ids));
    }

    /**
     * 进入关联抢修抢建方案页
     */
    @GetMapping("/assignPlan/{alertRuleId}")
    public String assignPlan(@PathVariable("alertRuleId") Long alertRuleId, ModelMap mmap)
    {
        AlertRule alertRule = alertRuleService.selectAlertRuleById(alertRuleId);
        // 获取所有抢修抢建方案
        List<Plan> plans = planService.selectByAlertRule4Assign(alertRuleId);
        mmap.put("alertRule", alertRule);
        mmap.put("plans", plans);
        return prefix + "/assignPlan";
    }

    /**
     * 预警规则关联抢修抢建方案
     */
    @RequiresPermissions("biz:rule:edit")
    @Log(title = "预警规则关联抢修抢建方案", businessType = BusinessType.GRANT)
    @PostMapping("/assignPlan/insertAlertRulePlans")
    @ResponseBody
    public AjaxResult insertAlertRulePlans(Long alertRuleId, Long[] planIds)
    {
        alertRuleService.insertAlertRulePlans(alertRuleId, planIds);
        return success();
    }
}
