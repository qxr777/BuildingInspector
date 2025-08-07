package edu.whut.cs.bi.biz.controller;

import java.util.List;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.ReportTemplate;
import edu.whut.cs.bi.biz.service.IReportTemplateService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;

/**
 * 报告模板Controller
 * 
 * @author wanzheng
 */
@Controller
@RequestMapping("/biz/report_template")
public class ReportTemplateController extends BaseController {
    private String prefix = "biz/report_template";

    @Autowired
    private IReportTemplateService reportTemplateService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private MinioConfig minioConfig;

    @RequiresPermissions("biz:report_template:view")
    @GetMapping()
    public String reportTemplate() {
        return prefix + "/report_template";
    }

    /**
     * 查询报告模板列表
     */
    @RequiresPermissions("biz:report_template:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ReportTemplate reportTemplate) {
        startPage();
        List<ReportTemplate> list = reportTemplateService.selectReportTemplateList(reportTemplate);
        return getDataTable(list);
    }

    /**
     * 导出报告模板列表
     */
    @RequiresPermissions("biz:report_template:export")
    @Log(title = "报告模板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(ReportTemplate reportTemplate) {
        List<ReportTemplate> list = reportTemplateService.selectReportTemplateList(reportTemplate);
        ExcelUtil<ReportTemplate> util = new ExcelUtil<ReportTemplate>(ReportTemplate.class);
        return util.exportExcel(list, "报告模板数据");
    }

    /**
     * 新增报告模板
     */
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存报告模板
     */
    @RequiresPermissions("biz:report_template:add")
    @Log(title = "报告模板", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(ReportTemplate reportTemplate, MultipartFile file) {
        try {
            // 上传文件
            if (file != null && !file.isEmpty()) {
                FileMap fileMap = fileMapService.handleFileUpload((file));
                String s = fileMap.getNewName();
                String url = minioConfig.getUrl()+ "/"+minioConfig.getBucketName()+"/"+s.substring(0,2)+"/"+s;
                reportTemplate.setMinioId(Long.valueOf(fileMap.getId()));
                reportTemplate.setFileUrl(url);
            }
            return toAjax(reportTemplateService.insertReportTemplate(reportTemplate));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * 修改报告模板
     */
    @RequiresPermissions("biz:report_template:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        ReportTemplate reportTemplate = reportTemplateService.selectReportTemplateById(id);
        mmap.put("reportTemplate", reportTemplate);
        return prefix + "/edit";
    }

    /**
     * 修改保存报告模板
     */
    @RequiresPermissions("biz:report_template:edit")
    @Log(title = "报告模板", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(ReportTemplate reportTemplate, MultipartFile file) {
        try {
            // 上传文件
            if (file != null && !file.isEmpty()) {
                fileMapService.deleteFileMapById(reportTemplate.getMinioId());
                FileMap fileMap = fileMapService.handleFileUpload((file));
                String s = fileMap.getNewName();
                String url = minioConfig.getUrl()+ "/"+minioConfig.getBucketName()+"/"+s.substring(0,2)+"/"+s;
                reportTemplate.setMinioId(Long.valueOf(fileMap.getId()));
                reportTemplate.setFileUrl(url);
            }
            return toAjax(reportTemplateService.updateReportTemplate(reportTemplate));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * 删除报告模板
     */
    @RequiresPermissions("biz:report_template:remove")
    @Log(title = "报告模板", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(reportTemplateService.deleteReportTemplateByIds(ids));
    }
    
    /**
     * 跳转到模板变量页面
     */
    @RequiresPermissions("biz:template_variable:view")
    @GetMapping("/variables/{id}")
    public String variables(@PathVariable("id") Long id, ModelMap mmap) {
        ReportTemplate reportTemplate = reportTemplateService.selectReportTemplateById(id);
        mmap.put("reportTemplate", reportTemplate);
        return "biz/template_variable/template_variable";
    }
} 