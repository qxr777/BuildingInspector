package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;

import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.service.IDiseaseTypeService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * 病害类型信息
 * 
 * @author chenwenqi
 */
@Controller
@RequestMapping("/biz/diseaseType")
public class DiseaseTypeController extends BaseController
{
    private String prefix = "biz/disease/type";

    @Resource
    private IDiseaseTypeService diseaseTypeService;

    @RequiresPermissions("biz:diseaseType:view")
    @GetMapping()
    public String dictType()
    {
        return prefix + "/type";
    }

    @PostMapping("/list")
    @RequiresPermissions("biz:diseaseType:list")
    @ResponseBody
    public TableDataInfo list(DiseaseType diseaseType)
    {
        startPage();
        List<DiseaseType> list = diseaseTypeService.selectDiseaseTypeList(diseaseType);
        return getDataTable(list);
    }

    @Log(title = "病害类型", businessType = BusinessType.EXPORT)
    @RequiresPermissions("biz:diseaseType:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(DiseaseType diseaseType)
    {

        List<DiseaseType> list = diseaseTypeService.selectDiseaseTypeList(diseaseType);
        ExcelUtil<DiseaseType> util = new ExcelUtil<DiseaseType>(DiseaseType.class);
        return util.exportExcel(list, "病害类型");
    }

    /**
     * 新增病害类型
     */
    @RequiresPermissions("biz:diseaseType:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存病害类型
     */
    @Log(title = "病害类型", businessType = BusinessType.INSERT)
    @RequiresPermissions("biz:diseaseType:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(DiseaseType diseaseType)
    {
        if (!diseaseTypeService.checkDiseaseTypeUnique(diseaseType))
        {
            return error("新增类型'" + diseaseType.getName() + "'失败，病害类型已存在");
        }
        diseaseType.setCreateBy(getLoginName());
        return toAjax(diseaseTypeService.insertDiseaseType(diseaseType));
    }

    /**
     * 修改病害类型
     */
    @RequiresPermissions("biz:diseaseType:edit")
    @GetMapping("/edit/{diseaseTypeId}")
    public String edit(@PathVariable("diseaseTypeId") Long diseaseTypeId, ModelMap mmap)
    {
        mmap.put("diseaseType", diseaseTypeService.selectDiseaseTypeById(diseaseTypeId));
        return prefix + "/edit";
    }

    /**
     * 修改保存病害类型
     */
    @Log(title = "病害类型", businessType = BusinessType.UPDATE)
    @RequiresPermissions("biz:diseaseType:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(DiseaseType diseaseType)
    {
        if (!diseaseTypeService.checkDiseaseTypeUnique(diseaseType))
        {
            return error("修改类型'" + diseaseType.getName() + "'失败，病害类型已存在");
        }
        diseaseType.setUpdateBy(getLoginName());
        return toAjax(diseaseTypeService.updateDiseaseType(diseaseType));
    }

    @Log(title = "病害类型", businessType = BusinessType.DELETE)
    @RequiresPermissions("biz:diseaseType:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        diseaseTypeService.deleteDiseaseTypeByIds(ids);
        return success();
    }

    /**
     * 查询病害类型详细
     */
    @RequiresPermissions("biz:diseaseType:list")
    @GetMapping("/detail/{diseaseTypeId}")
    public String detail(@PathVariable("diseaseTypeId") Long diseaseTypeId, ModelMap mmap)
    {
        mmap.put("diseaseType", diseaseTypeService.selectDiseaseTypeById(diseaseTypeId));
        return "biz/disease/scale/scale";
    }

    /**
     * 校验病害类型
     */
    @PostMapping("/checkDiseaseTypeUnique")
    @ResponseBody
    public boolean checkDiseaseTypeUnique(DiseaseType diseaseType)
    {
        return diseaseTypeService.checkDiseaseTypeUnique(diseaseType);
    }

    /**
     * 选择字典树
     */
    @GetMapping("/selectDictTree/{columnId}/{typeCode}")
    public String selectDeptTree(@PathVariable("columnId") Long columnId, @PathVariable("typeCode") String typeCode, ModelMap mmap)
    {
        mmap.put("columnId", columnId);
        mmap.put("diseaseType", diseaseTypeService.selectDiseaseTypeByCode(typeCode));
        return prefix + "/tree";
    }

    /**
     * 加载字典列表树
     */
    @GetMapping("/treeData")
    @ResponseBody
    public List<Ztree> treeData()
    {
        List<Ztree> ztrees = diseaseTypeService.selectDiseaseTypeTree(new DiseaseType());
        return ztrees;
    }

    @GetMapping("/readJson")
    public String readJson()
    {
        return prefix + "/readJson";
    }

    /**
     * 通过json文件添加
     */
    @PostMapping( "/readJson" )
    @ResponseBody
    @RequiresPermissions("bi:property:add")
    @Log(title = "读取属性json文件", businessType = BusinessType.INSERT)
    public Boolean readJsonFile(@RequestPart("file") MultipartFile file)
    {
        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setCreateBy(ShiroUtils.getLoginName());
        return diseaseTypeService.readJsonFile(file, diseaseType);
    }
}
