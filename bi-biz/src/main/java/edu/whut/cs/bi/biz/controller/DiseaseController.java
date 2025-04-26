package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import edu.whut.cs.bi.biz.service.ITaskService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 病害Controller
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
@Controller
@RequestMapping("/biz/disease")
public class DiseaseController extends BaseController
{
    private String prefix = "biz/disease";

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private ITaskService taskService;

    @Resource
    private IBiObjectService biObjectService;

    @RequiresPermissions("biz:disease:view")
    @GetMapping()
    public String disease()
    {
        return prefix + "/disease";
    }

    /**
     * 查询病害列表
     */
    @RequiresPermissions("bi:disease:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Disease disease)
    {
        startPage();
        List<Disease> properties = diseaseService.selectDiseaseList(disease);
        return getDataTable(properties);
    }

    /**
     * 导出病害列表
     */
    @RequiresPermissions("bi:disease:export")
    @Log(title = "病害", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Disease disease)
    {
        List<Disease> list = diseaseService.selectDiseaseList(disease);
        ExcelUtil<Disease> util = new ExcelUtil<Disease>(Disease.class);
        return util.exportExcel(list, "病害数据");
    }

    /**
     * 新增病害
     */
    @GetMapping(value = { "/add/{taskId}/{biObjectId}" })
    public String add(@PathVariable("taskId") Long taskId, @PathVariable("biObjectId") Long biObjectId, ModelMap mmap)
    {
        if (taskId != null) {
            mmap.put("task", taskService.selectTaskById(taskId));
        }
        if (biObjectId != null) {
            mmap.put("biObject", biObjectService.selectBiObjectById(biObjectId));
        }

        return prefix + "/add";
    }

    /**
     * 新增保存病害
     */
    @RequiresPermissions("bi:disease:add")
    @Log(title = "病害", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid Disease disease)
    {
        disease.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(diseaseService.insertDisease(disease));
    }


    /**
     * 修改病害
     */
    @RequiresPermissions("bi:disease:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Disease disease = diseaseService.selectDiseaseById(id);
        mmap.put("disease", disease);
        mmap.put("biObject", disease.getBiObject());
        return prefix + "/edit";
    }

    /**
     * 修改保存病害
     */
    @RequiresPermissions("bi:disease:edit")
    @Log(title = "病害", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid Disease disease)
    {
        disease.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(diseaseService.updateDisease(disease));
    }

    /**
     * 删除
     */
    @RequiresPermissions("bi:disease:remove")
    @Log(title = "病害", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(diseaseService.deleteDiseaseByIds(ids));
    }

    /**
     * 修改病害
     */
    @RequiresPermissions("bi:disease:list")
    @GetMapping("/showDiseaseDetail/{id}")
    public String showDiseaseDetail(@PathVariable("id") Long id, ModelMap mmap)
    {
        Disease disease = diseaseService.selectDiseaseById(id);
        mmap.put("disease", disease);
        mmap.put("biObject", disease.getBiObject());

        return prefix + "/detail";
    }

    /**
     * 构件扣分
     */
    @RequiresPermissions("bi:disease:add")
    @GetMapping("/computeDeductPoints")
    @ResponseBody
    public AjaxResult computeDeductPoints(int maxScale, int scale) {

        if (StringUtils.isNull(maxScale) || StringUtils.isNull(scale)) {
            return AjaxResult.error("参数错误");
        }

        return AjaxResult.success(diseaseService.computeDeductPoints(maxScale, scale));
    }

}
