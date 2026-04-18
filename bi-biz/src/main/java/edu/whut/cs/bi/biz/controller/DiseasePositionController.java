package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.DiseasePosition;
import edu.whut.cs.bi.biz.service.IDiseasePositionService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.List;

/**
 * 病害位置管理
 */
@Controller
@RequestMapping("/biz/diseasePosition")
public class DiseasePositionController extends BaseController {

    private String prefix = "biz/disease/position";

    @Resource
    private IDiseasePositionService diseasePositionService;

    @RequiresPermissions("biz:diseasePosition:view")
    @GetMapping()
    public String diseasePosition() {
        return prefix + "/position";
    }

    @RequiresPermissions("biz:diseasePosition:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(DiseasePosition diseasePosition) {
        startPage();
        List<DiseasePosition> list = diseasePositionService.selectDiseasePositionList(diseasePosition);
        return getDataTable(list);
    }

    @RequiresPermissions("biz:diseasePosition:add")
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    @Log(title = "病害位置", businessType = BusinessType.INSERT)
    @RequiresPermissions("biz:diseasePosition:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(DiseasePosition diseasePosition) {
        diseasePosition.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(diseasePositionService.insertDiseasePosition(diseasePosition));
    }

    @RequiresPermissions("biz:diseasePosition:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        mmap.put("diseasePosition", diseasePositionService.selectDiseasePositionById(id));
        return prefix + "/edit";
    }

    @Log(title = "病害位置", businessType = BusinessType.UPDATE)
    @RequiresPermissions("biz:diseasePosition:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(DiseasePosition diseasePosition) {
        diseasePosition.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(diseasePositionService.updateDiseasePosition(diseasePosition));
    }

    @Log(title = "病害位置", businessType = BusinessType.DELETE)
    @RequiresPermissions("biz:diseasePosition:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(diseasePositionService.deleteDiseasePositionByIds(ids));
    }
}
