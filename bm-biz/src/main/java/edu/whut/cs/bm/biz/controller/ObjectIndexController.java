package edu.whut.cs.bm.biz.controller;

import java.util.List;

import edu.whut.cs.bm.biz.domain.*;
import edu.whut.cs.bm.biz.service.*;
import edu.whut.cs.bm.common.constant.BizConstants;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 监测对象指标Controller
 *
 * @author qixin
 * @date 2021-08-11
 */
@Controller
@RequestMapping("/biz/objectIndex")
public class ObjectIndexController extends BaseController {
    private String prefix = "biz/objectIndex";

    @Autowired
    private IObjectIndexService objectIndexService;

    @Autowired
    private IIndexDataService indexDataService;

    @Autowired
    private IIndexService indexService;

    @Autowired
    private IObjectIndexAlertRuleService objectIndexAlertRuleService;

    @Autowired
    private IAlertRuleService alertRuleService;

    @RequiresPermissions("biz:objectIndex:view")
    @GetMapping()
    public String objectIndex(
            @RequestParam(value = "objectId", required = false, defaultValue = "1") Long objectId,
            ModelMap modelMap) {
        List<Index> list = indexService.selectIndexList(new Index());
        modelMap.put("list", list);
        modelMap.put("objectId", objectId);
        return prefix + "/objectIndex";
    }

    /**
     * 查询监测对象指标列表
     */
    @RequiresPermissions("biz:objectIndex:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ObjectIndex objectIndex) {
        startPage();
        List<ObjectIndex> list = objectIndexService.selectObjectIndexList(objectIndex);
        return getDataTable(list);
    }

    /**
     * 导出监测对象指标列表
     */
    @RequiresPermissions("biz:objectIndex:export")
    @Log(title = "监测对象指标", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(ObjectIndex objectIndex) {
        List<ObjectIndex> list = objectIndexService.selectObjectIndexList(objectIndex);
        ExcelUtil<ObjectIndex> util = new ExcelUtil<ObjectIndex>(ObjectIndex.class);
        return util.exportExcel(list, "监测对象评估数据");
    }

    /**
     * 新增监测对象指标
     */
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存监测对象指标
     */
    @RequiresPermissions("biz:objectIndex:add")
    @Log(title = "监测对象指标", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(ObjectIndex objectIndex) {
        return toAjax(objectIndexService.insertObjectIndex(objectIndex));
    }

    /**
     * 修改监测对象指标
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        ObjectIndex objectIndex = objectIndexService.selectObjectIndexById(id);
        mmap.put("objectIndex", objectIndex);
        return prefix + "/edit";
    }

    /**
     * 修改保存监测对象指标
     */
    @RequiresPermissions("biz:objectIndex:edit")
    @Log(title = "监测对象指标", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(ObjectIndex objectIndex) {
        return toAjax(objectIndexService.updateObjectIndex(objectIndex));
    }

    /**
     * 录入新的监测指标数据
     */
    @GetMapping("/appendData/{id}")
    public String appendData(@PathVariable("id") Long id, ModelMap mmap) {
        ObjectIndex objectIndex = objectIndexService.selectObjectIndexById(id);
        mmap.put("objectIndex", objectIndex);
        return prefix + "/appendData";
    }

    /**
     * 保存录入的新监测指标数据
     */
    @RequiresPermissions("biz:objectIndex:edit")
    @Log(title = "录入的新监测指标数据", businessType = BusinessType.INSERT)
    @PostMapping("/appendData")
    @ResponseBody
    public AjaxResult appendDataSave(ObjectIndex objectIndex) {
        return toAjax(indexDataService.insertIndexData(objectIndex.getObjectId(), objectIndex.getIndexId(),
                objectIndex.getValueStr(), BizConstants.CREATE_TYPE_MANUAL));
    }

    /**
     * 删除监测对象评估
     */
    @RequiresPermissions("biz:objectIndex:remove")
    @Log(title = "监测对象评估", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(objectIndexService.deleteObjectIndexByIds(ids));
    }

    /**
     * 进入关联预警规则页
     */
    @GetMapping("/assignAlertRule/{objectIndexId}")
    public String assignAlertRule(@PathVariable("objectIndexId") Long objectIndexId, ModelMap mmap)
    {
        ObjectIndex objectIndex = objectIndexService.selectObjectIndexById(objectIndexId);
        BmObject bmObject = objectIndex.getObject();
        Index index = objectIndex.getIndex();
        // 获取对象指标关联的预警规则列表
        List<AlertRule> alertRules = alertRuleService.selectByObjectIndex4Assign(bmObject.getId(), index.getId());
        mmap.put("object", bmObject);
        mmap.put("index", index);
        mmap.put("alertRules", alertRules);
        return prefix + "/assignAlertRule";
    }

    /**
     * 对象指标关联预警规则
     */
    @RequiresPermissions("biz:objectIndex:edit")
    @Log(title = "对象指标关联预警规则", businessType = BusinessType.GRANT)
    @PostMapping("/assignAlertRule/insertObjectIndexAlertRule")
    @ResponseBody
    public AjaxResult insertObjectIndexRule(Long objectId, Long indexId, Long[] alertRuleIds)
    {
        objectIndexAlertRuleService.insertObjectIndexAlertRules(objectId, indexId, alertRuleIds);
        return success();
    }
}
