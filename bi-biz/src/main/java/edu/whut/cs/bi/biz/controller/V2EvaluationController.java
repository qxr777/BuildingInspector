package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.enums.V2SingleIndicator;
import edu.whut.cs.bi.biz.service.IV2EvaluationService;
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
 * 新标 JTG/T 5230—2026 三级桥梁评定接口。
 * 与旧标 /biz/bievaluation 完全独立。
 */
@Controller
@RequestMapping("/biz/v2evaluation")
public class V2EvaluationController extends BaseController {

    @Autowired
    private IV2EvaluationService v2EvaluationService;

    /**
     * 预览评定单元解析结构（不落数据）
     */
    @RequiresPermissions("biz:v2evaluation:view")
    @GetMapping("/task/{taskId}/tree")
    @ResponseBody
    public AjaxResult tree(@PathVariable("taskId") Long taskId) {
        return AjaxResult.success(v2EvaluationService.previewTree(taskId));
    }

    /**
     * 查询当前人工评定输入
     */
    @RequiresPermissions("biz:v2evaluation:view")
    @GetMapping("/task/{taskId}/inputs")
    @ResponseBody
    public AjaxResult getInputs(@PathVariable("taskId") Long taskId) {
        return AjaxResult.success(v2EvaluationService.getInputs(taskId));
    }

    /**
     * 执行三级评定计算
     */
    @RequiresPermissions("biz:v2evaluation:calculate")
    @Log(title = "新标桥梁评定", businessType = BusinessType.OTHER)
    @PostMapping("/task/{taskId}/calculate")
    @ResponseBody
    public AjaxResult calculate(@PathVariable("taskId") Long taskId) {
        v2EvaluationService.calculate(taskId);
        return AjaxResult.success("评定计算成功");
    }

    /**
     * 查询全桥评定结果
     */
    @RequiresPermissions("biz:v2evaluation:view")
    @GetMapping("/task/{taskId}/result")
    @ResponseBody
    public AjaxResult result(@PathVariable("taskId") Long taskId) {
        return AjaxResult.success(v2EvaluationService.getResult(taskId));
    }

    /**
     * 16 项单项控制指标字典（序号 + 正式稿名称）
     */
    @RequiresPermissions("biz:v2evaluation:view")
    @GetMapping("/singleIndicators")
    @ResponseBody
    public AjaxResult singleIndicators() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (V2SingleIndicator indicator : V2SingleIndicator.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("no", indicator.getNo());
            row.put("name", indicator.getName());
            list.add(row);
        }
        return AjaxResult.success(list);
    }
}
