package edu.whut.cs.bm.biz.service.impl;

import java.util.ArrayList;
import java.util.List;
import com.ruoyi.common.utils.DateUtils;
//import com.ruoyi.framework.websocket.WebSocketUsers;
import edu.whut.cs.bm.biz.domain.*;
import edu.whut.cs.bm.biz.mapper.*;
import edu.whut.cs.bm.biz.vo.CheckVo;
import edu.whut.cs.bm.common.constant.BizConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bm.biz.service.IAlertRuleService;
import com.ruoyi.common.core.text.Convert;

/**
 * 预警规则Service业务层处理
 *
 * @author qixin
 * @date 2021-08-13
 */
@Service
public class AlertRuleServiceImpl implements IAlertRuleService
{
    @Autowired
    private AlertRuleMapper alertRuleMapper;

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private IndexDataMapper indexDataMapper;

    @Autowired
    private ObjectIndexAlertRuleMapper objectIndexAlertRuleMapper;

    @Autowired
    private AlertRulePlanMapper alertRulePlanMapper;

    /**
     * 查询预警规则
     *
     * @param id 预警规则ID
     * @return 预警规则
     */
    @Override
    public AlertRule selectAlertRuleById(Long id)
    {
        return alertRuleMapper.selectAlertRuleById(id);
    }

    /**
     * 查询预警规则列表
     *
     * @param alertRule 预警规则
     * @return 预警规则
     */
    @Override
    public List<AlertRule> selectAlertRuleList(AlertRule alertRule)
    {
        return alertRuleMapper.selectAlertRuleList(alertRule);
    }

    /**
     * 新增预警规则
     *
     * @param alertRule 预警规则
     * @return 结果
     */
    @Override
    public int insertAlertRule(AlertRule alertRule)
    {
        alertRule.setCreateTime(DateUtils.getNowDate());
        alertRule.setDescription(alertRule.joinDescription());
        return alertRuleMapper.insertAlertRule(alertRule);
    }

    /**
     * 修改预警规则
     *
     * @param alertRule 预警规则
     * @return 结果
     */
    @Override
    public int updateAlertRule(AlertRule alertRule)
    {
        alertRule.setUpdateTime(DateUtils.getNowDate());
        alertRule.setDescription(alertRule.joinDescription());
        return alertRuleMapper.updateAlertRule(alertRule);
    }

    /**
     * 删除预警规则对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteAlertRuleByIds(String ids)
    {
        return alertRuleMapper.deleteAlertRuleByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除预警规则信息
     *
     * @param id 预警规则ID
     * @return 结果
     */
    @Override
    public int deleteAlertRuleById(Long id)
    {
        return alertRuleMapper.deleteAlertRuleById(id);
    }

    @Override
    public CheckVo check(IndexData indexData) {
        int theSeriousestLevel = 0;
        CheckVo theSeriousestVo = null;
        List<CheckVo> checkVoList = new ArrayList<>();
        Long indexId = indexData.getIndexId();
        Long objectId = indexData.getObjectId();
        List<AlertRule> alertRules = this.selectByObjectIndex(objectId, indexId);
        for (AlertRule alertRule : alertRules) {
            CheckVo checkVo = null;
            switch (alertRule.getType()) {
                case BizConstants.ALERT_RULE_TYPE_THRESHOLD :
                    checkVo = checkThreshold(alertRule, indexData);
                    break;
                case BizConstants.ALERT_RULE_TYPE_RELATIVE :
                    checkVo = checkRelative(alertRule, indexData);
                    break;
                default:
            }
            if (checkVo != null) {
                Alert alert = new Alert();
                alert.setAlertRuleId(alertRule.getId());
                alert.setAlertRule(alertRule);
                alert.setCreateType("" + BizConstants.CREATE_TYPE_SYSTEM);
                alert.setObjectId(indexData.getObjectId());
                alert.setIndexId(indexData.getIndexId());
                alert.setIndexDataId(indexData.getId());
                alert.setIndexData(indexData);
                alert.setMeasurement(indexData.getMeasurement());
                alert.setMessage(alert.joinMessage());
                alert.setStatus(BizConstants.ALERT_STATUS_WARNING);
                alertMapper.insertAlert(alert);
                checkVoList.add(checkVo);

                //通过websocket推送预警消息
                alert = alertMapper.selectAlertById(alert.getId());
                StringBuffer sb = new StringBuffer();
                sb.append(alert.getCreateTime());
                sb.append("\n");
                sb.append(BizConstants.ALERT_LEVEL_NAME_ARRAY[alert.getAlertRule().getAlertLevel()]);
                sb.append("\n");
                sb.append(alert.getBmObject().getName());
                sb.append("\n");
                sb.append(alert.getIndex().getName());
                sb.append("\n");
                sb.append(alert.getMessage());
                sb.append("\n");
                sb.append("--------------------------------------------------------------------------------------------------------------------------------------");
//                WebSocketUsers.sendMessageToUsersByText(sb.toString());
            }
        }
        for (CheckVo checkVo : checkVoList) {
            if(checkVo.getAlertLevel() > theSeriousestLevel) {
                theSeriousestLevel = checkVo.getAlertLevel();
                theSeriousestVo = checkVo;
            }
        }
        return theSeriousestVo;
    }

    private CheckVo checkRelative(AlertRule alertRule, IndexData indexData) {
        boolean result = false;
        double indexValue = indexData.getNumericValue().doubleValue();
        double relativeValue = alertRule.getRelativeValue().doubleValue();
        IndexData previousIndexData = indexDataMapper.selectPreviousIndexData(indexData.getObjectId(), indexData.getIndexId(), alertRule.getRelativePreviousPeriod());
        double previousIndexValue = previousIndexData!= null ? previousIndexData.getNumericValue().doubleValue() : indexValue;
        double changeValue = Math.abs(indexValue - previousIndexValue);
        double changePercent = Math.abs((indexValue - previousIndexValue) / previousIndexValue) * 100;
        double change = changeValue;
        if (alertRule.getRelativeChangeType() == BizConstants.ALERT_RULE_RELATIVE_CHANGE_TYPE_RATE) {
            change = changePercent;
        }
        switch (alertRule.getRelativeOperatorType()) {
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_GREATER_THAN:
                result = change > relativeValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_EQUAL_TO_OR_GREATER_THAN:
                result = change >= relativeValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_EQUAL_TO_OR_LESS_THAN:
                result = change <= relativeValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_LESS_THAN:
                result = change < relativeValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_EQUAL_TO:
                result = change == relativeValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_NOT_EQUAL_TO:
                result = change != relativeValue ? true : false;
                break;
            default:
        }
        return result ? new CheckVo(alertRule.getAlertLevel(), BizConstants.ALERT_LEVEL_SCORE_RANGE[alertRule.getAlertLevel() - 1]) : null;
    }

    private CheckVo checkThreshold(AlertRule alertRule, IndexData indexData) {
        double score = 100;
        boolean result = false;
        double indexValue = indexData.getIndexDataType() == BizConstants.INDEX_TYPE_DATA_TYPE_ORDINAL ?
                indexData.getOrdinalValue().intValue() :
                indexData.getIndexDataType() == BizConstants.INDEX_TYPE_DATA_TYPE_BINARY ? indexData.getBinaryValue() : indexData.getNumericValue().doubleValue();
        double thresholdValue = alertRule.getThresholdValue() != null ? alertRule.getThresholdValue().doubleValue() : 0;
        double thresholdLower = alertRule.getThresholdLower() != null ? alertRule.getThresholdLower().doubleValue() : 0;
        double thresholdUpper = alertRule.getThresholdUpper() != null ? alertRule.getThresholdUpper().doubleValue() : 0;
        switch (alertRule.getThresholdOperatorType()) {
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_GREATER_THAN:
                result = indexValue > thresholdValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_EQUAL_TO_OR_GREATER_THAN:
                result = indexValue >= thresholdValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_EQUAL_TO_OR_LESS_THAN:
                result = indexValue <= thresholdValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_LESS_THAN:
                result = indexValue < thresholdValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_EQUAL_TO:
                result = indexValue == thresholdValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_NOT_EQUAL_TO:
                result = indexValue != thresholdValue ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_INSIDE_RANGE:
                result = (indexValue <= thresholdUpper && indexValue >= thresholdLower) ? true : false;
                break;
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_OUTSIDE_RANGE:
                result = (indexValue > thresholdUpper || indexValue < thresholdLower) ? true : false;
                break;
            default:
        }
        return result ? score(alertRule, indexData) : null;
    }

    private CheckVo score(AlertRule alertRule, IndexData indexData) {
        CheckVo scoreVo = null;
        double indexValue = indexData.getIndexDataType() == BizConstants.INDEX_TYPE_DATA_TYPE_ORDINAL ?
                indexData.getOrdinalValue().intValue() :
                indexData.getIndexDataType() == BizConstants.INDEX_TYPE_DATA_TYPE_BINARY ? indexData.getBinaryValue() : indexData.getNumericValue().doubleValue();
        double thresholdLower = alertRule.getThresholdLower() != null ? alertRule.getThresholdLower().doubleValue() : 0;
        double thresholdUpper = alertRule.getThresholdUpper() != null ? alertRule.getThresholdUpper().doubleValue() : 0;
        switch (alertRule.getThresholdOperatorType()) {
            case BizConstants.ALERT_RULE_OPERATOR_TYPE_INSIDE_RANGE:
                double y0 = BizConstants.ALERT_LEVEL_SCORE_RANGE[alertRule.getAlertLevel() - 1];
                double y1 = BizConstants.ALERT_LEVEL_SCORE_RANGE[alertRule.getAlertLevel()];
                if (alertRule.getCorrelationDataScore() != null && alertRule.getCorrelationDataScore() == BizConstants.ALERT_RULE_CORRELATION_DATA_SCORE_NEGATIVE) {
                    y1 = BizConstants.ALERT_LEVEL_SCORE_RANGE[alertRule.getAlertLevel() - 1];
                    y0 = BizConstants.ALERT_LEVEL_SCORE_RANGE[alertRule.getAlertLevel()];

                }
                double interpolation = interpolate(thresholdLower, thresholdUpper, y0, y1, indexValue);
                scoreVo = new CheckVo(alertRule.getAlertLevel(), interpolation);
                break;
            default :
                scoreVo = new CheckVo(alertRule.getAlertLevel(),BizConstants.ALERT_LEVEL_SCORE_RANGE[alertRule.getAlertLevel() - 1]);
        }
        return scoreVo;
    }

    /**
     * Lagrange线性插值
     * @param x0
     * @param x1
     * @param y0
     * @param y1
     * @param x 待插值点
     * @return
     */
    private double interpolate(double x0, double x1, double y0, double y1, double x) {
        double y = y0 * (x - x1) / (x0 - x1) + y1 * (x - x0) / (x1 - x0);
        return y;
    }

    @Override
    public List<AlertRule> selectByObjectIndex4Assign(Long objectId, Long indexId) {
        AlertRule queryAlertRule = new AlertRule();
        queryAlertRule.setIndexId(indexId);
        queryAlertRule.setStatus(BizConstants.STATUS_ACTIVE);
        List<AlertRule> alertRules = alertRuleMapper.selectAlertRuleList(queryAlertRule);
        ObjectIndexAlertRule queryObjectIndexAlertRule = new ObjectIndexAlertRule();
        queryObjectIndexAlertRule.setObjectId(objectId);
        queryObjectIndexAlertRule.setIndexId(indexId);
        List<ObjectIndexAlertRule> objectIndexAlertRuleList = objectIndexAlertRuleMapper.selectObjectIndexAlertRuleList(queryObjectIndexAlertRule);
        for (AlertRule alertRule : alertRules) {
            for (ObjectIndexAlertRule objectIndexAlertRule : objectIndexAlertRuleList) {
                if (alertRule.getId().longValue() == objectIndexAlertRule.getAlertRuleId().longValue()) {
                    alertRule.setFlag(true);
                    break;
                }
            }
        }
        return alertRules;
    }

    @Override
    public List<AlertRule> selectByObjectIndex(Long objectId, Long indexId) {
        List<AlertRule> resultList = new ArrayList<AlertRule>();
        AlertRule queryAlertRule = new AlertRule();
        queryAlertRule.setIndexId(indexId);
        queryAlertRule.setStatus(BizConstants.STATUS_ACTIVE);
        List<AlertRule> alertRules = alertRuleMapper.selectAlertRuleList(queryAlertRule);
        ObjectIndexAlertRule queryObjectIndexAlertRule = new ObjectIndexAlertRule();
        queryObjectIndexAlertRule.setObjectId(objectId);
        queryObjectIndexAlertRule.setIndexId(indexId);
        List<ObjectIndexAlertRule> objectIndexAlertRuleList = objectIndexAlertRuleMapper.selectObjectIndexAlertRuleList(queryObjectIndexAlertRule);
        for (AlertRule alertRule : alertRules) {
            for (ObjectIndexAlertRule objectIndexAlertRule : objectIndexAlertRuleList) {
                if (alertRule.getId().longValue() == objectIndexAlertRule.getAlertRuleId().longValue()) {
                    resultList.add(alertRule);
                    break;
                }
            }
        }
        return resultList;
    }

    @Override
    public int insertAlertRulePlans(Long alertRuleId, Long[] planIds) {
        alertRulePlanMapper.deleteByAlertRuleId(alertRuleId);
        AlertRulePlan alertRulePlan;
        for (Long planId : planIds) {
            alertRulePlan = new AlertRulePlan();
            alertRulePlan.setAlertRuleId(alertRuleId);
            alertRulePlan.setPlanId(planId);
            alertRulePlanMapper.insertAlertRulePlan(alertRulePlan);
        }
        return planIds.length;
    }
}
