package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.enums.SingleIndicator;
import edu.whut.cs.bi.biz.service.std.evaluation.Assess5230CalculateService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JTG/T 5230—2026 后端评定引擎。
 *
 * <p>人工判定三表（bi_assess_manual_input / bi_assess_absent_mark /
 * bi_assess_single_control_mark）的唯一写入通道是 App 经 sync/upload 同步
 * （assessManualInputs/assessAbsentMarks/assessSingleControlMarks 三数组），
 * 本类不提供在线录入；只提供结构预览、输入回查、计算触发、结果查询等引擎端点。
 * calculate 由后端内部触发，App 同步只落数据不自动计算。</p>
 */
@Controller
@RequestMapping("/biz/assess5230")
public class Assess5230Controller extends BaseController {

    @Autowired
    private Assess5230CalculateService assess5230CalculateService;

    // ---------------- 评定引擎 ----------------

    /** 结构解析预览（不落数据；返回 units 解析树 + 非阻断 warnings）。 */
    @RequiresPermissions("biz:assess5230:view")
    @GetMapping("/task/{taskId}/tree")
    @ResponseBody
    public AjaxResult tree(@PathVariable("taskId") Long taskId) {
        try {
            return AjaxResult.success(assess5230CalculateService.previewTree(taskId));
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 回查任务当前人工判定三表输入。 */
    @RequiresPermissions("biz:assess5230:view")
    @GetMapping("/task/{taskId}/inputs")
    @ResponseBody
    public AjaxResult inputs(@PathVariable("taskId") Long taskId) {
        try {
            return AjaxResult.success(assess5230CalculateService.getInputs(taskId));
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 执行三级评定计算（幂等可重算，四张结果表按 task_id 先删后插）。 */
    @RequiresPermissions("biz:assess5230:calculate")
    @Log(title = "5230桥梁评定计算", businessType = BusinessType.OTHER)
    @PostMapping("/task/{taskId}/calculate")
    @ResponseBody
    public AjaxResult calculate(@PathVariable("taskId") Long taskId) {
        try {
            return AjaxResult.success(
                    assess5230CalculateService.calculate(taskId, ShiroUtils.getLoginName()));
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 查询最近一次评定结果（bridge/units/spans/elements 四层）。 */
    @RequiresPermissions("biz:assess5230:view")
    @GetMapping("/task/{taskId}/result")
    @ResponseBody
    public AjaxResult result(@PathVariable("taskId") Long taskId) {
        try {
            return AjaxResult.success(assess5230CalculateService.getResult(taskId));
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 16 项单项控制指标字典（序号 + 正式稿名称）。 */
    @RequiresPermissions("biz:assess5230:view")
    @GetMapping("/singleIndicators")
    @ResponseBody
    public AjaxResult singleIndicators() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SingleIndicator indicator : SingleIndicator.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("no", indicator.getNo());
            row.put("name", indicator.getName());
            list.add(row);
        }
        return AjaxResult.success(list);
    }
}
