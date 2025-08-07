package edu.whut.cs.bi.biz.controller;

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
import edu.whut.cs.bi.biz.domain.TemplateVariable;
import edu.whut.cs.bi.biz.domain.ReportTemplate;
import edu.whut.cs.bi.biz.service.ITemplateVariableService;
import edu.whut.cs.bi.biz.service.IReportTemplateService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 模板变量Controller
 * 
 * @author wanzheng
 */
@Controller
@RequestMapping("/biz/template_variable")
public class TemplateVariableController extends BaseController {
    private String prefix = "biz/template_variable";

    @Autowired
    private ITemplateVariableService templateVariableService;
    
    @Autowired
    private IReportTemplateService reportTemplateService;

    @RequiresPermissions("biz:template_variable:view")
    @GetMapping()
    public String templateVariable() {
        return prefix + "/template_variable";
    }

    /**
     * 查询模板变量列表
     */
    @RequiresPermissions("biz:template_variable:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(TemplateVariable templateVariable) {
        startPage();
        List<TemplateVariable> list = templateVariableService.selectTemplateVariableList(templateVariable);
        return getDataTable(list);
    }

    /**
     * 导出模板变量列表
     */
    @RequiresPermissions("biz:template_variable:export")
    @Log(title = "模板变量", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(TemplateVariable templateVariable) {
        List<TemplateVariable> list = templateVariableService.selectTemplateVariableList(templateVariable);
        ExcelUtil<TemplateVariable> util = new ExcelUtil<TemplateVariable>(TemplateVariable.class);
        return util.exportExcel(list, "模板变量数据");
    }

    /**
     * 新增模板变量
     */
    @GetMapping("/add/{reportTemplateId}")
    public String add(@PathVariable("reportTemplateId") Long reportTemplateId, ModelMap mmap) {
        ReportTemplate reportTemplate = reportTemplateService.selectReportTemplateById(reportTemplateId);
        mmap.put("reportTemplate", reportTemplate);
        return prefix + "/add";
    }

    /**
     * 新增保存模板变量
     */
    @RequiresPermissions("biz:template_variable:add")
    @Log(title = "模板变量", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(TemplateVariable templateVariable) {
        return toAjax(templateVariableService.insertTemplateVariable(templateVariable));
    }

    /**
     * 修改模板变量
     */
    @RequiresPermissions("biz:template_variable:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        TemplateVariable templateVariable = templateVariableService.selectTemplateVariableById(id);
        mmap.put("templateVariable", templateVariable);
        return prefix + "/edit";
    }

    /**
     * 修改保存模板变量
     */
    @RequiresPermissions("biz:template_variable:edit")
    @Log(title = "模板变量", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(TemplateVariable templateVariable) {
        return toAjax(templateVariableService.updateTemplateVariable(templateVariable));
    }

    /**
     * 删除模板变量
     */
    @RequiresPermissions("biz:template_variable:remove")
    @Log(title = "模板变量", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(templateVariableService.deleteTemplateVariableByIds(ids));
    }
} 