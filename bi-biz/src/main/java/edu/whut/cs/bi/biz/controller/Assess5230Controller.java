package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.enums.SingleIndicator;
import edu.whut.cs.bi.biz.service.std.evaluation.Assess5230CalculateService;
import edu.whut.cs.bi.biz.service.std.evaluation.Assess5230InputService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JTG/T 5230—2026 评定：人工判定直连录入 + 后端评定引擎。
 *
 * <p>人工判定三表的写入契约（不含输入页面）：与 sync/upload 离线通道
 * (assessManualInputs/assessAbsentMarks/assessSingleControlMarks 三数组) 等价的
 * 在线通道；另有结构预览、输入回查、计算触发、结果查询四个引擎端点。</p>
 */
@Controller
@RequestMapping("/biz/assess5230")
public class Assess5230Controller extends BaseController {

    @Autowired
    private Assess5230InputService assess5230InputService;

    @Autowired
    private Assess5230CalculateService assess5230CalculateService;

    /** 构件级人工判定 upsert（按 taskId + elementUuid 自然键）。 */
    @RequiresPermissions("biz:assess5230:edit")
    @Log(title = "5230构件人工判定", businessType = BusinessType.UPDATE)
    @PostMapping("/manual-input/upsert")
    @ResponseBody
    public AjaxResult upsertManualInput(@RequestBody AssessManualInput row) {
        try {
            AssessManualInput saved = assess5230InputService.upsertManualInput(row, ShiroUtils.getLoginName());
            return AjaxResult.success(saved);
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 应设未设部件标记保存（id 非空更新；否则按 task+unit+span+partKey 去重新增）。 */
    @RequiresPermissions("biz:assess5230:edit")
    @Log(title = "5230应设未设部件标记", businessType = BusinessType.UPDATE)
    @PostMapping("/absent-mark/save")
    @ResponseBody
    public AjaxResult saveAbsentMark(@RequestBody AssessAbsentMark row) {
        try {
            AssessAbsentMark saved = assess5230InputService.saveAbsentMark(row, ShiroUtils.getLoginName());
            return AjaxResult.success(saved);
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 单项控制命中标记 upsert（按 taskId + indicatorNo 自然键）。 */
    @RequiresPermissions("biz:assess5230:edit")
    @Log(title = "5230单项控制标记", businessType = BusinessType.UPDATE)
    @PostMapping("/single-control/upsert")
    @ResponseBody
    public AjaxResult upsertSingleControl(@RequestBody AssessSingleControlMark row) {
        try {
            AssessSingleControlMark saved = assess5230InputService.upsertSingleControl(row, ShiroUtils.getLoginName());
            return AjaxResult.success(saved);
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @RequiresPermissions("biz:assess5230:edit")
    @Log(title = "5230构件人工判定", businessType = BusinessType.DELETE)
    @PostMapping("/manual-input/remove/{id}")
    @ResponseBody
    public AjaxResult removeManualInput(@PathVariable("id") Long id) {
        return toAjax(assess5230InputService.deleteManualInput(id));
    }

    @RequiresPermissions("biz:assess5230:edit")
    @Log(title = "5230应设未设部件标记", businessType = BusinessType.DELETE)
    @PostMapping("/absent-mark/remove/{id}")
    @ResponseBody
    public AjaxResult removeAbsentMark(@PathVariable("id") Long id) {
        return toAjax(assess5230InputService.deleteAbsentMark(id));
    }

    @RequiresPermissions("biz:assess5230:edit")
    @Log(title = "5230单项控制标记", businessType = BusinessType.DELETE)
    @PostMapping("/single-control/remove/{id}")
    @ResponseBody
    public AjaxResult removeSingleControl(@PathVariable("id") Long id) {
        return toAjax(assess5230InputService.deleteSingleControlMark(id));
    }

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
