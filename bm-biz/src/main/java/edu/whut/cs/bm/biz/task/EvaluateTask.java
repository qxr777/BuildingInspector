package edu.whut.cs.bm.biz.task;

import edu.whut.cs.bm.biz.domain.BmObject;
import edu.whut.cs.bm.biz.domain.Evaluation;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import edu.whut.cs.bm.biz.service.IEvaluationService;
import edu.whut.cs.bm.common.constant.BizConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author qixin on 2021/10/31.
 * @version 1.0
 */
@Component("evaluateTask")
public class EvaluateTask {
    @Autowired
    private IBmObjectService bmObjectService;

    @Autowired
    private IEvaluationService evaluationService;

    public void evaluateAllObjects() {
        BmObject queryObject = new BmObject();
        queryObject.setStatus(BizConstants.STATUS_ACTIVE);
        List<BmObject> objectList = bmObjectService.selectBmObjectList(queryObject);
        for (BmObject object : objectList) {
            Evaluation evaluation = bmObjectService.evaluate(object.getId());
            evaluationService.insertEvaluation(evaluation);
        }
    }

}
