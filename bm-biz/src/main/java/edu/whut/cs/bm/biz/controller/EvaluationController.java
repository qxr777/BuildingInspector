package edu.whut.cs.bm.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bm.biz.domain.Evaluation;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import edu.whut.cs.bm.biz.service.IEvaluationService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 健康评估Controller
 * @author qixin on 2021/10/9.
 * @version 1.0
 */
@Controller
@RequestMapping("/biz/evaluation")
public class EvaluationController extends BaseController {
    private String prefix = "biz/evaluation";

    @Autowired
    private IBmObjectService bmObjectService;

    @Autowired
    private IEvaluationService evaluationService;

    @RequiresPermissions("biz:evaluation:view")
    @GetMapping("/overview")
    public String overview(ModelMap modelMap)
    {
//        Long[] objectIds = {2L, 136L, 144L, 166L, 185L, 221L};
        Long[] objectIds = {2L, 3L, 4L, 5L, 6L, 7L};
        Evaluation[] evaluations = new Evaluation[6];
        for (int i = 0; i < objectIds.length; i++) {
            Evaluation evaluation = bmObjectService.evaluate(objectIds[i]);
            evaluations[i] = evaluation;
        }
        modelMap.put("evaluation0", evaluations[0]);
        modelMap.put("evaluation1", evaluations[1]);
        modelMap.put("evaluation2", evaluations[2]);
        modelMap.put("evaluation3", evaluations[3]);
        modelMap.put("evaluation4", evaluations[4]);
        modelMap.put("evaluation5", evaluations[5]);
        modelMap.put("evaluations", evaluations);
        return prefix + "/cards";
    }

    @RequiresPermissions("biz:evaluation:view")
    @GetMapping()
    public String evaluation()
    {
        return prefix + "/evaluation";
    }

    /**
     * 查询对象健康评估列表
     */
    @RequiresPermissions("biz:evaluation:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Evaluation evaluation)
    {
        startPage();
        List<Evaluation> list = evaluationService.selectEvaluationList(evaluation);
        return getDataTable(list);
    }

    /**
     * 导出评估数据列表
     */
    @RequiresPermissions("biz:evaluation:export")
    @Log(title = "评估数据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Evaluation evaluation)
    {
        List<Evaluation> list = evaluationService.selectEvaluationList(evaluation);
        ExcelUtil<Evaluation> util = new ExcelUtil<Evaluation>(Evaluation.class);
        return util.exportExcel(list, "评估数据数据");
    }

    /**
     * 新增评估数据
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存评估数据
     */
    @RequiresPermissions("biz:evaluation:add")
    @Log(title = "评估数据", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Evaluation evaluation)
    {
        return toAjax(evaluationService.insertEvaluation(evaluation));
    }

    /**
     * 修改评估数据
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Evaluation evaluation = evaluationService.selectEvaluationById(id);
        mmap.put("evaluation", evaluation);
        return prefix + "/edit";
    }

    /**
     * 修改保存评估数据
     */
    @RequiresPermissions("biz:evaluation:edit")
    @Log(title = "评估数据", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Evaluation evaluation)
    {
        return toAjax(evaluationService.updateEvaluation(evaluation));
    }

    /**
     * 删除评估数据
     */
    @RequiresPermissions("biz:evaluation:remove")
    @Log(title = "评估数据", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(evaluationService.deleteEvaluationByIds(ids));
    }
}
