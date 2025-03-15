package edu.whut.cs.bm.biz.task;

import edu.whut.cs.bm.biz.domain.Alert;
import edu.whut.cs.bm.biz.domain.AlertRule;
import edu.whut.cs.bm.biz.domain.ObjectIndex;
import edu.whut.cs.bm.biz.domain.ObjectIndexAlertRule;
import edu.whut.cs.bm.biz.service.IAlertService;
import edu.whut.cs.bm.biz.service.IObjectIndexAlertRuleService;
import edu.whut.cs.bm.biz.service.IObjectIndexService;
import edu.whut.cs.bm.common.constant.BizConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @author qixin on 2021/10/10.
 * @version 1.0
 */
@Component("deadmanAlertTask")
public class DeadmanAlertTask {
    @Autowired
    private IObjectIndexService objectIndexService;

    @Autowired
    private IObjectIndexAlertRuleService objectIndexAlertRuleService;

    @Autowired
    private IAlertService alertService;

    /**
     * 定时检查 监测对象指标是否触发 缺数据 预警规则
     */
    public void regularCheck() {
        AlertRule queryAlertRule = new AlertRule();
        queryAlertRule.setType(BizConstants.ALERT_RULE_TYPE_DEADMAN);
        queryAlertRule.setStatus(BizConstants.STATUS_ACTIVE);
        ObjectIndexAlertRule queryObjectIndexAlertRule = new ObjectIndexAlertRule();
        queryObjectIndexAlertRule.setAlertRule(queryAlertRule);
        List<ObjectIndexAlertRule> objectIndexAlertRuleList = objectIndexAlertRuleService.selectObjectIndexAlertRuleList(queryObjectIndexAlertRule);
        for (ObjectIndexAlertRule objectIndexAlertRule : objectIndexAlertRuleList) {
            Long deadmanMissingPeriod = objectIndexAlertRule.getAlertRule().getDeadmanMissingPeriod();
            ObjectIndex queryObjectIndex = new ObjectIndex();
            queryObjectIndex.setObjectId(objectIndexAlertRule.getObjectId());
            queryObjectIndex.setIndexId(objectIndexAlertRule.getIndexId());
            List<ObjectIndex> objectIndexList = objectIndexService.selectObjectIndexList(queryObjectIndex);
            for (ObjectIndex objectIndex : objectIndexList) {
                if (objectIndex.getIndexData() != null) {
                    Date dataCreateTime = objectIndex.getIndexData().getCreateTime();
                    if (System.currentTimeMillis() - dataCreateTime.getTime() > deadmanMissingPeriod * 1000) {
                        Alert alert = new Alert();
                        alert.setAlertRuleId(objectIndexAlertRule.getAlertRuleId());
                        alert.setAlertRule(objectIndexAlertRule.getAlertRule());
                        alert.setCreateType("" + BizConstants.CREATE_TYPE_SYSTEM);
                        alert.setObjectId(objectIndex.getObjectId());
                        alert.setIndexId(objectIndex.getIndexId());
                        alert.setMessage(alert.joinMessage());
                        alert.setStatus(BizConstants.ALERT_STATUS_WARNING);
                        alertService.insertAlert(alert);
                    }
                }
            }
        }

    }
}
