package edu.whut.cs.bm.biz.controller;

import java.util.List;

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
import edu.whut.cs.bm.biz.domain.Index;
import edu.whut.cs.bm.biz.service.IIndexService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

import javax.validation.Valid;

/**
 * 监测指标Controller
 * 
 * @author qixin
 * @date 2021-08-10
 */
@Controller
@RequestMapping("/biz/index")
public class IndexController extends BaseController
{
    private String prefix = "biz/index";

    @Autowired
    private IIndexService indexService;

    @RequiresPermissions("biz:index:view")
    @GetMapping()
    public String index()
    {
        return prefix + "/index";
    }

    /**
     * 查询监测指标列表
     */
    @RequiresPermissions("biz:index:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Index index)
    {
        startPage();
        List<Index> list = indexService.selectIndexList(index);
        return getDataTable(list);
    }

    /**
     * 导出监测指标列表
     */
    @RequiresPermissions("biz:index:export")
    @Log(title = "监测指标", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Index index)
    {
        List<Index> list = indexService.selectIndexList(index);
        ExcelUtil<Index> util = new ExcelUtil<Index>(Index.class);
        return util.exportExcel(list, "监测指标数据");
    }

    /**
     * 新增监测指标
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存监测指标
     */
    @RequiresPermissions("biz:index:add")
    @Log(title = "监测指标", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid Index index)
    {
        index.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(indexService.insertIndex(index));
    }

    /**
     * 修改监测指标
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Index index = indexService.selectIndexById(id);
        mmap.put("index", index);
        return prefix + "/edit";
    }

    /**
     * 修改保存监测指标
     */
    @RequiresPermissions("biz:index:edit")
    @Log(title = "监测指标", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid Index index)
    {
        index.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(indexService.updateIndex(index));
    }

    /**
     * 删除监测指标
     */
    @RequiresPermissions("biz:index:remove")
    @Log(title = "监测指标", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(indexService.deleteIndexByIds(ids));
    }
}
