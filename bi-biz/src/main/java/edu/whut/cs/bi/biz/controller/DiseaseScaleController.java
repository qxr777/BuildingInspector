package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;

import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.service.IDiseaseScaleService;
import edu.whut.cs.bi.biz.service.IDiseaseTypeService;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;

import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 数据字典信息
 *
 * @author ruoyi
 */
@Controller
@RequestMapping("/biz/diseaseScale")
public class DiseaseScaleController extends BaseController
{
    private String prefix = "biz/disease/scale";

    @Resource
    private IDiseaseScaleService diseaseScaleService;

    @Resource
    private IDiseaseTypeService diseaseTypeService;

    @RequiresPermissions("biz:diseaseType:view")
    @GetMapping()
    public String diseaseScale()
    {
        return prefix + "/scale";
    }

    @PostMapping("/list")
    @RequiresPermissions("biz:diseaseType:list")
    @ResponseBody
    public TableDataInfo list(DiseaseScale diseaseScale)
    {
        startPage();
        List<DiseaseScale> list = diseaseScaleService.selectDiseaseScaleList(diseaseScale);
        return getDataTable(list);
    }

    @Log(title = "病害标度", businessType = BusinessType.EXPORT)
    @RequiresPermissions("biz:diseaseType:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(DiseaseScale diseaseScale)
    {
        List<DiseaseScale> list = diseaseScaleService.selectDiseaseScaleList(diseaseScale);
        ExcelUtil<DiseaseScale> util = new ExcelUtil<DiseaseScale>(DiseaseScale.class);
        return util.exportExcel(list, "病害标度");
    }

    /**
     * 新增病害标度
     */
    @RequiresPermissions("biz:diseaseType:add")
    @GetMapping("/add/{typeCode}")
    public String add(@PathVariable("typeCode") String typeCode, ModelMap mmap)
    {
        DiseaseType diseaseType = diseaseTypeService.selectDiseaseTypeByCode(typeCode);
        mmap.put("diseaseType", diseaseType);
        return prefix + "/add";
    }

    /**
     * 新增保存病害标度
     */
    @Log(title = "病害标度", businessType = BusinessType.INSERT)
    @RequiresPermissions("biz:diseaseType:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(DiseaseScale diseaseScale)
    {
        diseaseScale.setCreateBy(getLoginName());
        return toAjax(diseaseScaleService.insertDiseaseScale(diseaseScale));
    }

    /**
     * 修改病害标度
     */
    @RequiresPermissions("biz:diseaseType:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        mmap.put("diseaseScale", diseaseScaleService.selectDiseaseScaleById(id));
        return prefix + "/edit";
    }

    /**
     * 修改保存病害标度
     */
    @Log(title = "病害标度", businessType = BusinessType.UPDATE)
    @RequiresPermissions("biz:diseaseType:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(DiseaseScale diseaseScale)
    {
        diseaseScale.setUpdateBy(getLoginName());
        return toAjax(diseaseScaleService.updateDiseaseScale(diseaseScale));
    }

    @Log(title = "病害标度", businessType = BusinessType.DELETE)
    @RequiresPermissions("biz:diseaseType:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        diseaseScaleService.deleteDiseaseScaleByIds(ids);
        return success();
    }
}
