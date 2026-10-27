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
import edu.whut.cs.bi.biz.service.std.evaluation.Assess5230InputService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * JTG/T 5230—2026 评定人工判定直连录入。
 *
 * <p>本期仅提供人工判定三表的写入契约（不含输入页面）：与 sync/upload 离线通道
 * (assessManualInputs/assessAbsentMarks/assessSingleControlMarks 三数组) 等价的
 * 在线通道。评定计算/结果查询端点在评定引擎装配阶段于本控制器续增。</p>
 */
@Controller
@RequestMapping("/biz/assess5230")
public class Assess5230Controller extends BaseController {

    @Autowired
    private Assess5230InputService assess5230InputService;

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
}
