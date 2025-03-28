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
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.service.IPropertyIndexService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

import static com.ruoyi.common.utils.PageUtils.startPage;

/**
 * 属性Controller
 */
@Controller
@RequestMapping("/biz/propertyIndex")
public class PropertyIndexController extends BaseController
{
    private String prefix = "biz/propertyIndex";

    @Resource
    private IPropertyService propertyService;

    @Resource
    private IPropertyIndexService propertyIndexService;

    @RequiresPermissions("biz:property:view")
    @GetMapping()
    public String propertyIndex()
    {
        return prefix + "/propertyIndex";
    }

    /**
     * 查询属性树列表
     */
    @RequiresPermissions("bi:property:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Property property)
    {
        startPage();
        List<Property> properties = propertyIndexService.selectPropertyList(property);
        return getDataTable(properties);
    }

    /**
     * 导出属性列表
     */
    @RequiresPermissions("bi:property:export")
    @Log(title = "属性", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Property property)
    {
        List<Property> list = propertyService.selectPropertyList(property);
        ExcelUtil<Property> util = new ExcelUtil<Property>(Property.class);
        return util.exportExcel(list, "属性数据");
    }

    /**
     * 新增属性
     */
    @GetMapping(value = { "/add/{id}", "/add" })
    public String add(@PathVariable(value = "id", required = false) Long id, ModelMap mmap)
    {
        if (StringUtils.isNotNull(id))
        {
            mmap.put("property", propertyService.selectPropertyById(id));
        }
        return prefix + "/add";
    }

    /**
     * 新增保存属性
     */
    @RequiresPermissions("bi:property:add")
    @Log(title = "属性", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid Property property)
    {
        property.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(propertyService.insertProperty(property));
    }


    /**
     * 修改属性
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Property property = propertyService.selectPropertyById(id);
        // 补充父节点名称
        if (property.getParentId() != null) {
            Property parent = propertyService.selectPropertyById(property.getParentId());
            property.setParentName(parent.getName());
        }


        mmap.put("property", property);
        return prefix + "/edit";
    }

    /**
     * 修改保存属性
     */
    @RequiresPermissions("bi:property:edit")
    @Log(title = "属性", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid Property property)
    {
        property.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(propertyService.updateProperty(property));
    }

    /**
     * 删除
     */
    @RequiresPermissions("bi:property:remove")
    @Log(title = "属性", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(propertyService.deletePropertyByIds(ids));
    }

    /**
     * 选择属性树
     */
    @GetMapping(value = { "/selectObjectTree/{id}", "/selectObjectTree/" })
    public String selectObjectTree(@PathVariable(value = "id", required = false) Long id, ModelMap mmap)
    {
        if (StringUtils.isNotNull(id))
        {
            mmap.put("property", propertyService.selectPropertyById(id));
        }
        return prefix + "/tree";
    }

    /**
     * 加载属性树列表
     */
    @GetMapping("/treeData")
    @ResponseBody
    public List<Ztree> treeData(String name)
    {
        List<Ztree> ztrees = propertyIndexService.selectPropertyTree(name);
        return ztrees;
    }

}
