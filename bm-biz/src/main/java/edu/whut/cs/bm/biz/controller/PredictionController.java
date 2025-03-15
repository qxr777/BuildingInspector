package edu.whut.cs.bm.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bm.biz.domain.Alert;
import edu.whut.cs.bm.biz.domain.BmObject;
import edu.whut.cs.bm.biz.domain.Evaluation;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import edu.whut.cs.bm.biz.service.IEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author qixin on 2021/10/31.
 * @version 1.0
 */
@Controller
@RequestMapping("/biz/prediction")
public class PredictionController {
    private String prefix = "biz/prediction";
    @Autowired
    private IEvaluationService evaluationService;

    @GetMapping("/objectPredict")
    public String objectPrediction(Long objectId, String fragment, ModelMap mmap)
    {
        if (objectId == null) {
            objectId = 1L;  // 工程总体
        }
        if (fragment == null) {
            fragment = "";
        } else {
            fragment = "::" + fragment;
        }
        List<Evaluation> list = evaluationService.predict(objectId, 5);
        mmap.put("evaluations", list);
        return prefix + "/objectPredict" + fragment;
    }

    @Log(title = "预测信息", businessType = BusinessType.EXPORT)
    @PostMapping("/objectPredict")
    @ResponseBody
    public AjaxResult exportPrediction(Long objectId, String fragment, ModelMap mmap)
    {
        if (objectId == null) {
            objectId = 1L;  // 工程总体
        }
        List<Evaluation> list = evaluationService.predict(objectId, 5);
        ExcelUtil<Evaluation> util = new ExcelUtil<Evaluation>(Evaluation.class);
        return util.exportExcel(list, "预测信息数据");
    }

}
