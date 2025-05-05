package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Score;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.service.IScoreService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/4/17 18:19
 * @Description:
 **/
@Controller
@RequestMapping("/biz/bievaluation")
public class BiEvaluationController extends BaseController {
    private String prefix = "biz/bievaluation";

    @Autowired
    private IBiEvaluationService biEvaluationService;

    @Autowired
    private IConditionService conditionService;

    @Autowired
    private IScoreService scoreService;

    @Autowired
    private IBiObjectService biObjectService;

    @RequiresPermissions("biz:evaluation:view")
    @GetMapping()
    public String evaluation() {
        return prefix + "/biEvaluation";
    }

    /**
     * 查询桥梁技术状况评定列表
     */
    @RequiresPermissions("biz:evaluation:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(BiEvaluation biEvaluation) {
        startPage();
        List<BiEvaluation> list = biEvaluationService.selectBiEvaluationList(biEvaluation);
        return getDataTable(list);
    }

    /**
     * 计算评定结果
     */
    @RequiresPermissions("biz:evaluation:calculate")
    @Log(title = "桥梁技术状况评定", businessType = BusinessType.OTHER)
    @GetMapping("/calculate/{taskId}/{rootObjectId}")
    @ResponseBody
    public AjaxResult calculate(@PathVariable("taskId") Long taskId, @PathVariable("rootObjectId") Long rootObjectId) {
        try {
            BiEvaluation evaluation = biEvaluationService.calculateBiEvaluation(taskId, rootObjectId);
            return AjaxResult.success("评定计算成功", evaluation);
        } catch (Exception e) {
            return AjaxResult.error("评定计算失败：" + e.getMessage());
        }
    }

    /**
     * 评定详情页面
     */
    @RequiresPermissions("biz:evaluation:detail")
    @GetMapping("/detail/{taskId}")
    public String detail(@PathVariable("taskId") Long taskId, ModelMap mmap) {
        // 获取评定结果
        BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(taskId);
        if (evaluation != null) {
            mmap.put("evaluation", evaluation);

            // 获取各部分的Condition列表
            List<Condition> conditions = conditionService.selectConditionsByBiEvaluationId(evaluation.getId());
            List<Condition> superConditions = new ArrayList<>();
            List<Condition> subConditions = new ArrayList<>();
            List<Condition> deckConditions = new ArrayList<>();
            if (conditions != null && !conditions.isEmpty()) {
                // 根据部件类型分类
                conditions.forEach(condition -> {
                    if (condition.getBiObjectId() != null) {
                        BiObject biObject = biObjectService.selectDirectParentById(condition.getBiObjectId());
                        String name = "";
                        if (biObject != null) {
                            name = biObject.getName();
                        }
                        if (name.startsWith("上部")) {
                            superConditions.add(condition);
                        } else if (name.startsWith("下部")) {
                            subConditions.add(condition);
                        } else if (name.startsWith("桥面")) {
                            deckConditions.add(condition);
                        }
                    }
                });
            }
            mmap.put("superConditions", superConditions);
            mmap.put("subConditions", subConditions);
            mmap.put("deckConditions", deckConditions);
        }

        return prefix + "/detail";
    }

    /**
     * 检查是否存在评定数据
     */
    @RequiresPermissions("biz:evaluation:detail")
    @GetMapping("/check/{taskId}")
    @ResponseBody
    public AjaxResult check(@PathVariable("taskId") Long taskId) {
        BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(taskId);
        return AjaxResult.success(evaluation != null);
    }


    /**
     * 获取构件评分列表
     */
    @GetMapping("/score/list")
    @ResponseBody
    public TableDataInfo scoreList(@RequestParam("conditionId") Long conditionId) {
        startPage();
        Score score = new Score();
        score.setConditionId(conditionId);
        List<Score> list = scoreService.selectScoreList(score);
        return getDataTable(list);
    }
}
