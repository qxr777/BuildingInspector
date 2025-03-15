package edu.whut.cs.bm.biz.controller;

import java.util.List;
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
import edu.whut.cs.bm.biz.domain.ObjectIndexAlertRule;
import edu.whut.cs.bm.biz.service.IObjectIndexAlertRuleService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 关联预警规则Controller
 * 
 * @author qixin
 * @date 2021-08-14
 */
@Controller
@RequestMapping("/biz/objectIndexAlertRule")
public class ObjectIndexAlertRuleController extends BaseController
{
    private String prefix = "biz/objectIndexAlertRule";

    @Autowired
    private IObjectIndexAlertRuleService objectIndexAlertRuleService;

    @RequiresPermissions("biz:objectIndexAlertRule:view")
    @GetMapping()
    public String objectIndexAlertRule()
    {
        return prefix + "/objectIndexAlertRule";
    }

    /**
     * 查询关联预警规则列表
     */
    @RequiresPermissions("biz:objectIndexAlertRule:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ObjectIndexAlertRule objectIndexAlertRule)
    {
        startPage();
        List<ObjectIndexAlertRule> list = objectIndexAlertRuleService.selectObjectIndexAlertRuleList(objectIndexAlertRule);
        return getDataTable(list);
    }

    /**
     * 导出关联预警规则列表
     */
    @RequiresPermissions("biz:objectIndexAlertRule:export")
    @Log(title = "关联预警规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(ObjectIndexAlertRule objectIndexAlertRule)
    {
        List<ObjectIndexAlertRule> list = objectIndexAlertRuleService.selectObjectIndexAlertRuleList(objectIndexAlertRule);
        ExcelUtil<ObjectIndexAlertRule> util = new ExcelUtil<ObjectIndexAlertRule>(ObjectIndexAlertRule.class);
        return util.exportExcel(list, "关联预警规则数据");
    }

    /**
     * 新增关联预警规则
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存关联预警规则
     */
    @RequiresPermissions("biz:objectIndexAlertRule:add")
    @Log(title = "关联预警规则", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(ObjectIndexAlertRule objectIndexAlertRule)
    {
        return toAjax(objectIndexAlertRuleService.insertObjectIndexAlertRule(objectIndexAlertRule));
    }

    /**
     * 修改关联预警规则
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        ObjectIndexAlertRule objectIndexAlertRule = objectIndexAlertRuleService.selectObjectIndexAlertRuleById(id);
        mmap.put("objectIndexAlertRule", objectIndexAlertRule);
        return prefix + "/edit";
    }

    /**
     * 修改保存关联预警规则
     */
    @RequiresPermissions("biz:objectIndexAlertRule:edit")
    @Log(title = "关联预警规则", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(ObjectIndexAlertRule objectIndexAlertRule)
    {
        return toAjax(objectIndexAlertRuleService.updateObjectIndexAlertRule(objectIndexAlertRule));
    }

    /**
     * 删除关联预警规则
     */
    @RequiresPermissions("biz:objectIndexAlertRule:remove")
    @Log(title = "关联预警规则", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(objectIndexAlertRuleService.deleteObjectIndexAlertRuleByIds(ids));
    }
}
