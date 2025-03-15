package edu.whut.cs.bm.biz.controller;

import java.util.List;

import com.alibaba.fastjson.JSON;
import edu.whut.cs.bm.biz.domain.*;
import edu.whut.cs.bm.biz.service.IIndexService;
import edu.whut.cs.bm.biz.vo.IndexDataVo;
import edu.whut.cs.bm.common.constant.BizConstants;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bm.biz.service.IIndexDataService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 监测数据Controller
 * 
 * @author qixin
 * @date 2021-08-10
 */
@Controller
@RequestMapping("/biz/data")
public class IndexDataController extends BaseController
{
    private String prefix = "biz/data";

    @Autowired
    private IIndexDataService indexDataService;

    @Autowired
    private IIndexService indexService;

    @RequiresPermissions("biz:data:view")
    @GetMapping()
    public String data(ModelMap modelMap)
    {
        List<Index> list = indexService.selectIndexList(new Index());
        modelMap.put("list", list);
        return prefix + "/data";
    }

    /**
     * 查询监测数据列表
     */
    @RequiresPermissions("biz:data:list")
    @RequestMapping("/list")
    @ResponseBody
    public TableDataInfo list(IndexData indexData)
    {
        startPage();
        List<IndexData> list = indexDataService.selectIndexDataList(indexData);
        return getDataTable(list);
    }

    /**
     * 导出监测数据列表
     */
    @RequiresPermissions("biz:data:export")
    @Log(title = "监测数据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(IndexData indexData)
    {
        List<IndexData> list = indexDataService.selectIndexDataList(indexData);
        ExcelUtil<IndexData> util = new ExcelUtil<IndexData>(IndexData.class);
        return util.exportExcel(list, "监测数据数据");
    }

    /**
     * 新增监测数据
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存监测数据
     */
    @RequiresPermissions("biz:data:add")
    @Log(title = "监测数据", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(IndexData indexData)
    {
//        return toAjax(indexDataService.insertIndexData(indexData));
        return toAjax(indexDataService.insertIndexData(indexData.getObjectId(), indexData.getIndexId(),
                indexData.getValueStr(), BizConstants.CREATE_TYPE_MANUAL));
    }

    /**
     * 修改监测数据
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        IndexData indexData = indexDataService.selectIndexDataById(id);
        mmap.put("indexData", indexData);
        return prefix + "/edit";
    }

    /**
     * 修改保存监测数据
     */
    @RequiresPermissions("biz:data:edit")
    @Log(title = "监测数据", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(IndexData indexData)
    {
        return toAjax(indexDataService.updateIndexData(indexData));
    }

    /**
     * 删除监测数据
     */
    @RequiresPermissions("biz:data:remove")
    @Log(title = "监测数据", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(indexDataService.deleteIndexDataByIds(ids));
    }

    /**
     * @return 进入图表界面
     */
    @RequestMapping("/chart/{ids}/{startTime}/{endTime}")
    public String chart(@PathVariable("ids") String ids,
                        @PathVariable("startTime") String startTime,
                        @PathVariable("endTime") String endTime,
                        Model model) {
        List<IndexDataVo> indexDataVos = indexDataService.selectIndexDataByIds(ids, startTime, endTime);
        model.addAttribute("indexDataVos", indexDataVos);
        return prefix + "/chart";
    }

//    @RequestMapping("/chart")
//    public String chart(String ids, Model model) {
//        model.addAttribute("ids", ids);
//        System.out.println(ids);
//        return prefix + "/chart";
//    }


    @RequestMapping("/chart/list/{ids}/{startTime}/{endTime}")
    @ResponseBody
    public List<IndexDataVo> chartSearchByDate(@PathVariable("ids") String ids,
                        @PathVariable("startTime") String startTime,
                        @PathVariable("endTime") String endTime,
                        Model model) {
        List<IndexDataVo> indexDataVos = indexDataService.selectIndexDataByIds(ids, startTime, endTime);
        return indexDataVos;
    }
}
