package edu.whut.cs.bm.biz.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 关联预警规则对象 bm_object_index_alert_rule
 * 
 * @author qixin
 * @date 2021-08-14
 */
public class ObjectIndexAlertRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 监测对象id */
    @Excel(name = "监测对象id")
    private Long objectId;

    /** 监测指标id */
    @Excel(name = "监测指标id")
    private Long indexId;

    /** 预警规则id */
    @Excel(name = "预警规则id")
    private Long alertRuleId;

    private AlertRule alertRule;

    public AlertRule getAlertRule() {
        return alertRule;
    }

    public void setAlertRule(AlertRule alertRule) {
        this.alertRule = alertRule;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setObjectId(Long objectId) 
    {
        this.objectId = objectId;
    }

    public Long getObjectId() 
    {
        return objectId;
    }
    public void setIndexId(Long indexId) 
    {
        this.indexId = indexId;
    }

    public Long getIndexId() 
    {
        return indexId;
    }
    public void setAlertRuleId(Long alertRuleId) 
    {
        this.alertRuleId = alertRuleId;
    }

    public Long getAlertRuleId() 
    {
        return alertRuleId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("objectId", getObjectId())
            .append("indexId", getIndexId())
            .append("alertRuleId", getAlertRuleId())
            .toString();
    }
}
