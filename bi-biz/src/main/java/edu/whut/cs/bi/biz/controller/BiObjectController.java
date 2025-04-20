package edu.whut.cs.bi.biz.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ruoyi.common.utils.ShiroUtils;
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
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.core.domain.Ztree;

/**
 * 对象Controller
 *
 * @author ruoyi
 * @date 2025-03-27
 */
@Controller
@RequestMapping("/biz/biobject")
public class BiObjectController extends BaseController {
    private String prefix = "biz/biobject";

    @Autowired
    private IBiObjectService biObjectService;

    @RequiresPermissions("biz:object:view")
    @GetMapping()
    public String object(Long rootObjectId, ModelMap mmap) {
        if (rootObjectId != null) {
            BiObject rootObject = biObjectService.selectBiObjectById(rootObjectId);
            if (rootObject != null) {
                mmap.put("rootObjectId", rootObjectId);
            }
        }
        return prefix + "/object";
    }

    @RequiresPermissions("biz:object:list")
    @GetMapping("/isCanAddDisease/{biObjectId}")
    @ResponseBody
    public Boolean isCanAddDisease(@PathVariable("biObjectId") Long biObjectId) {
        BiObject biObject = biObjectService.selectBiObjectById(biObjectId);

        if (biObject != null && biObject.getTemplateObjectId() != null) {
            return true;
        }
        return false;
    }

    /**
     * 查询对象树列表
     */
    @RequiresPermissions("biz:object:list")
    @PostMapping("/list")
    @ResponseBody
    public List<BiObject> list(BiObject biObject, Long rootObjectId) {
        // 查询根节点及其所有子节点
        BiObject rootNode = biObjectService.selectBiObjectById(rootObjectId);
        if (rootNode != null) {
            List<BiObject> list = biObjectService.selectBiObjectAndChildren(rootObjectId);
            // 如果有查询条件，进行过滤
            if (StringUtils.isNotEmpty(biObject.getName()) || StringUtils.isNotEmpty(biObject.getStatus())) {
                list = list.stream().filter(obj -> {
                    boolean match = true;
                    // 按名称过滤
                    if (StringUtils.isNotEmpty(biObject.getName())) {
                        match = match && obj.getName().contains(biObject.getName());
                    }
                    // 按状态过滤
                    if (StringUtils.isNotEmpty(biObject.getStatus())) {
                        match = match && biObject.getStatus().equals(obj.getStatus());
                    }
                    return match;
                }).collect(Collectors.toList());
            }
            return list;
        }
        return new ArrayList<>();
    }

    /**
     * 导出对象列表
     */
    @RequiresPermissions("biz:object:export")
    @Log(title = "对象", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(BiObject biObject) {
        List<BiObject> list = biObjectService.selectBiObjectList(biObject);
        ExcelUtil<BiObject> util = new ExcelUtil<BiObject>(BiObject.class);
        return util.exportExcel(list, "对象数据");
    }

    /**
     * 新增对象
     */
    @GetMapping(value = {"/add/{id}", "/add/"})
    public String add(@PathVariable(value = "id", required = false) Long id, ModelMap mmap) {
        if (StringUtils.isNotNull(id)) {
            mmap.put("biObject", biObjectService.selectBiObjectById(id));
        }
        return prefix + "/add";
    }

    /**
     * 新增保存对象
     */
    @RequiresPermissions("biz:object:add")
    @Log(title = "对象", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(BiObject biObject) {
        biObject.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(biObjectService.insertBiObject(biObject));
    }

    /**
     * 修改对象
     */
    @RequiresPermissions("biz:object:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        BiObject biObject = biObjectService.selectBiObjectById(id);
        mmap.put("biObject", biObject);
        return prefix + "/edit";
    }

    /**
     * 修改保存对象
     */
    @RequiresPermissions("biz:object:edit")
    @Log(title = "对象", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(BiObject biObject) {
        biObject.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(biObjectService.updateBiObject(biObject));
    }

    /**
     * 删除
     */
    @RequiresPermissions("biz:object:remove")
    @Log(title = "对象", businessType = BusinessType.DELETE)
    @GetMapping("/remove/{id}")
    @ResponseBody
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(biObjectService.deleteBiObjectById(id));
    }

    /**
     * 选择对象树
     */
    @GetMapping(value = {"/selectObjectTree/{id}", "/selectObjectTree/"})
    public String selectObjectTree(@PathVariable(value = "id", required = false) Long id, ModelMap mmap) {
        if (StringUtils.isNotNull(id)) {
            mmap.put("biObject", biObjectService.selectBiObjectById(id));
        }
        return prefix + "/tree";
    }

    /**
     * 加载对象树列表
     */
    @GetMapping("/treeData")
    @ResponseBody
    public List<Ztree> treeData(Long rootObjectId) {
        List<Ztree> ztrees = biObjectService.selectBiObjectTree(rootObjectId);
        return ztrees;
    }
}
