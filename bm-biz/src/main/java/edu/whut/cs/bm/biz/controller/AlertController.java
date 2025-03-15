package edu.whut.cs.bm.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bm.biz.domain.Alert;
import edu.whut.cs.bm.biz.domain.Plan;
import edu.whut.cs.bm.biz.service.IAlertService;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import edu.whut.cs.bm.biz.service.IPlanService;
import edu.whut.cs.bm.biz.vo.AlertMessageVo;
import edu.whut.cs.bm.common.constant.BizConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警信息Controller
 *
 * @author qixin
 * @date 2023-09-25
 */
@Api("用户信息管理")
@Controller
@RequestMapping("/biz/alert")
public class AlertController extends BaseController
{
    private String prefix = "biz/alert";

    @Autowired
    private IAlertService alertService;

    @Autowired
    private IPlanService planService;

    @Autowired
    private IBmObjectService bmObjectService;

    @RequiresPermissions("biz:alert:view")
    @GetMapping()
    public String alert()
    {
        return prefix + "/alert";
    }

    /**
     * 查询预警信息列表
     */
//    @RequiresPermissions("biz:alert:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Alert alert)
    {
        startPage();
        List<Alert> list = alertService.selectAlertList(alert);
        return getDataTable(list);
    }

    @ApiOperation("获取预警详细")
    @ApiImplicitParam(name = "alertId", value = "alertId", required = true, dataType = "int", paramType = "path")
    @GetMapping("/api/{alertId}")
    @ResponseBody
    public Alert getAlert(@PathVariable Long alertId)
    {
            return alertService.selectAlertById(alertId);
    }

    /**
     * 查询预警信息列表API
     */
    @ApiOperation("获取预警列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "lastSecond",value = "最近预警列表" , dataType = "Integer", paramType = "query", defaultValue = "6000"),
            @ApiImplicitParam(name = "message",value = "消息内容" , dataType = "String", paramType = "query")
    })
    @GetMapping("/api/list")
    @ResponseBody
    public List<AlertMessageVo> list4api(Integer lastSecond, String message) {
        Alert queryAlert = new Alert();
        queryAlert.setMessage(message);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("lastSecond",lastSecond);
        queryAlert.setParams(params);
        List<Alert> list = alertService.selectAlertList(queryAlert);
        List<AlertMessageVo> messageVoList = new ArrayList<AlertMessageVo>();
        for (Alert curAlert : list) {
            AlertMessageVo alertMessageVo = new AlertMessageVo();
            alertMessageVo.setObjectId(curAlert.getBmObject().getId());
            alertMessageVo.setObject(curAlert.getBmObject().getName());
            alertMessageVo.setIndex(curAlert.getIndex() != null ? curAlert.getIndex().getName() : "");
            alertMessageVo.setMeasurement(curAlert.getMeasurement());
            alertMessageVo.setMessage(curAlert.getMessage());
            alertMessageVo.setAlertId(curAlert.getId());
            alertMessageVo.setCreateTime(curAlert.getCreateTime());
            messageVoList.add(alertMessageVo);
        }
        return messageVoList;
    }

    /**
     * 导出预警信息列表
     */
    @RequiresPermissions("biz:alert:export")
    @Log(title = "预警信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Alert alert)
    {
        List<Alert> list = alertService.selectAlertList(alert);
        ExcelUtil<Alert> util = new ExcelUtil<Alert>(Alert.class);
        return util.exportExcel(list, "预警信息数据");
    }

    /**
     * 新增预警信息
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存预警信息
     */
    @RequiresPermissions("biz:alert:add")
    @Log(title = "预警信息", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Alert alert)
    {
        return toAjax(alertService.insertAlert(alert));
    }

    @Log(title = "预警信息", businessType = BusinessType.INSERT)
    @PostMapping(path = "/api", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public AjaxResult addSave4api(Alert alert, @RequestParam("file") MultipartFile file) throws IOException
    {
        // 上传文件路径
        String filePath = RuoYiConfig.getUploadPath();
        // 上传并返回新文件名称
        String fileName = FileUploadUtils.upload(filePath, file);

        alert.setCreateType(BizConstants.CREATE_TYPE_SYSTEM + "");
        alert.setStatus(BizConstants.ALERT_STATUS_WARNING);
        String remark = "<p><img src=\"{fileUrl}\" style=\"width: 573px;\">";
        remark = remark.replace("{fileUrl}", fileName);
        alert.setRemark(remark);

        return toAjax(alertService.insertAlert(alert));
    }

    /**
     * 修改预警信息
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Alert alert = alertService.selectAlertById(id);
        String ancestorNames = bmObjectService.getAncestorNames(alert.getObjectId());
        mmap.put("alert", alert);
        mmap.put("ancestorNames", ancestorNames);
        return prefix + "/edit";
    }

    /**
     * 修改保存预警信息
     */
    @RequiresPermissions("biz:alert:edit")
    @Log(title = "预警信息", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Alert alert)
    {
        return toAjax(alertService.updateAlert(alert));
    }

    /**
     * 删除预警信息
     */
    @RequiresPermissions("biz:alert:remove")
    @Log(title = "预警信息", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(alertService.deleteAlertByIds(ids));
    }

    /**
     * 进入推荐抢修抢建方案页
     */
    @GetMapping("/recommendPlan/{alertId}")
    public String recommendPlan(@PathVariable("alertId") Long alertId, ModelMap mmap)
    {
        Alert alert = alertService.selectAlertById(alertId);
        // 获取推荐的抢修抢建方案
        List<Plan> plans = planService.selectByAlertRule4Assign(alert.getAlertRuleId());
        List<Plan> recommendPlans = new ArrayList<Plan>();
        for (Plan plan : plans) {
            if (plan.isFlag()) {
                recommendPlans.add(plan);
            }
        }
        mmap.put("alert", alert);
        mmap.put("plans", recommendPlans);
        return prefix + "/recommendPlan";
    }

    /**
     * 进入抢修抢建方案详情页
     */
    @GetMapping("/showPlan/{planId}")
    public String shownPlan(@PathVariable("planId") Long planId, ModelMap mmap)
    {
        Plan plan = planService.selectPlanById(planId);
        mmap.put("plan", plan);
        return prefix + "/planDetail";
    }
}
