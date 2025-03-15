package edu.whut.cs.bm.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bm.biz.domain.BmObject;
import edu.whut.cs.bm.biz.domain.Index;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import edu.whut.cs.bm.biz.service.IIndexService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 监测对象Controller
 * 
 * @author qixin
 * @date 2021-08-10
 */
@Controller
@RequestMapping("/biz/object")
public class BmObjectController extends BaseController
{
    private String prefix = "biz/object";

    @Autowired
    private IBmObjectService bmObjectService;

    @Autowired
    private IIndexService indexService;

    @RequiresPermissions("biz:object:view")
    @GetMapping()
    public String object()
    {
        return prefix + "/object";
    }

    /**
     * 查询监测对象树列表
     */
    @RequiresPermissions("biz:object:list")
    @PostMapping("/list")
    @ResponseBody
    public List<BmObject> list(BmObject bmObject)
    {
        List<BmObject> list = bmObjectService.selectBmObjectList(bmObject);
        return list;
    }

    /**
     * 导出监测对象列表
     */
    @RequiresPermissions("biz:object:export")
    @Log(title = "监测对象", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(BmObject bmObject)
    {
        List<BmObject> list = bmObjectService.selectBmObjectList(bmObject);
        ExcelUtil<BmObject> util = new ExcelUtil<BmObject>(BmObject.class);
        return util.exportExcel(list, "监测对象数据");
    }

    /**
     * 新增监测对象
     */
    @GetMapping(value = { "/add/{id}", "/add/" })
    public String add(@PathVariable(value = "id", required = false) Long id, ModelMap mmap)
    {
        if (StringUtils.isNotNull(id))
        {
            mmap.put("bmObject", bmObjectService.selectBmObjectById(id));
        }
        return prefix + "/add";
    }

    /**
     * 新增保存监测对象
     */
    @RequiresPermissions("biz:object:add")
    @Log(title = "监测对象", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid BmObject bmObject)
    {
        bmObject.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(bmObjectService.insertBmObject(bmObject));
    }

    /**
     * 修改监测对象
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        BmObject bmObject = bmObjectService.selectBmObjectById(id);
        mmap.put("bmObject", bmObject);
        return prefix + "/edit";
    }

    /**
     * 修改保存监测对象
     */
    @RequiresPermissions("biz:object:edit")
    @Log(title = "监测对象", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid BmObject bmObject)
    {
        bmObject.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(bmObjectService.updateBmObject(bmObject));
    }

    /**
     * 删除
     */
    @RequiresPermissions("biz:object:remove")
    @Log(title = "监测对象", businessType = BusinessType.DELETE)
    @GetMapping("/remove/{id}")
    @ResponseBody
    public AjaxResult remove(@PathVariable("id") Long id)
    {
        return toAjax(bmObjectService.deleteBmObjectById(id));
    }

    /**
     * 选择监测对象树
     */
    @GetMapping(value = { "/selectObjectTree/{id}", "/selectObjectTree/" })
    public String selectObjectTree(@PathVariable(value = "id", required = false) Long id, ModelMap mmap)
    {
        if (StringUtils.isNotNull(id))
        {
            mmap.put("bmObject", bmObjectService.selectBmObjectById(id));
        }
        return prefix + "/tree";
    }

    /**
     * 加载监测对象树列表
     */
    @GetMapping("/treeData")
    @ResponseBody
    public List<Ztree> treeData()
    {
        List<Ztree> ztrees = bmObjectService.selectBmObjectTree();
        return ztrees;
    }

    /**
     * 进入关联指标页
     */
    @GetMapping("/assignIndex/{objectId}")
    public String assignIndex(@PathVariable("objectId") Long objectId, ModelMap mmap)
    {
        BmObject bmObject = bmObjectService.selectBmObjectById(objectId);
        // 获取对象关联的指标列表
        List<Index> indices = indexService.selectIndexsByObjectId(objectId);
        mmap.put("object", bmObject);
        mmap.put("indexes", indices);
        return prefix + "/assignIndex";
    }

    /**
     * 对象关联指标
     */
    @RequiresPermissions("biz:object:edit")
    @Log(title = "监测对象管理", businessType = BusinessType.GRANT)
    @PostMapping("/assignIndex/insertObjectIndex")
    @ResponseBody
    public AjaxResult insertObjectIndex(Long objectId, Long[] indexIds)
    {
        bmObjectService.insertObjectAssign(objectId, indexIds);
        return success();
    }

    /**
     * 监测对象视频流
     */
    @GetMapping("/video/{id}")
    public String video(@PathVariable("id") Long id, ModelMap mmap)
    {
        BmObject bmObject = bmObjectService.selectBmObjectById(id);
        mmap.put("bmObject", bmObject);
        return prefix + "/video";
    }
}
