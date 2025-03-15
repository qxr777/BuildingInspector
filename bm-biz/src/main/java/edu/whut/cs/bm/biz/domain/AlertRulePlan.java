package edu.whut.cs.bm.biz.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 预警规则与预案关联对象 bm_alert_rule_plan
 * 
 * @author qixin
 * @date 2021-10-08
 */
public class AlertRulePlan extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /**  */
    @Excel(name = "")
    private Long alertRuleId;

    /**  */
    @Excel(name = "")
    private Long planId;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setAlertRuleId(Long alertRuleId) 
    {
        this.alertRuleId = alertRuleId;
    }

    public Long getAlertRuleId() 
    {
        return alertRuleId;
    }
    public void setPlanId(Long planId) 
    {
        this.planId = planId;
    }

    public Long getPlanId() 
    {
        return planId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("createBy", getCreateBy())
            .append("updateBy", getUpdateBy())
            .append("alertRuleId", getAlertRuleId())
            .append("planId", getPlanId())
            .toString();
    }
}
