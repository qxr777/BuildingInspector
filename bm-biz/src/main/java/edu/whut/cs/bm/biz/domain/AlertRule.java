package edu.whut.cs.bm.biz.domain;

import java.math.BigDecimal;

import edu.whut.cs.bm.common.constant.BizConstants;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * 预警规则对象 bm_alert_rule
 *
 * @author qixin
 * @date 2021-08-13
 */
public class AlertRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 规则类型（0阈值 1相对变化 2缺数据） */
    @Excel(name = "规则类型", readConverterExp = "0=阈值,1=相对变化,2=缺数据")
    private String type;

    /** 阈值 */
    private BigDecimal thresholdValue;

    /** 阈值下限 */
    private BigDecimal thresholdLower;

    /** 阈值上限 */
    private BigDecimal thresholdUpper;

    /** 相对变化时限 */
    private Long relativePreviousPeriod;

    /** 缺数据时限 */
    private Long deadmanMissingPeriod;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 监测指标id */
    private Long indexId;

    private Index index;

    /** 阈值关系类型 */
    private int thresholdOperatorType;

    /** 预警规则名称 */
    @Excel(name = "预警规则名称")
    @NotBlank(message = "预警规则名称不能为空")
    @Length(message = "预警规则名称不能大于50个字符", max = 50)
    private String name;

    /** 相对变化类型（0变化量 1变化率） */
    private int relativeChangeType;

    /**  */
    private int relativeOperatorType;

    /** 相对变化阈值 */
    private BigDecimal relativeValue;

    /** 规则描述 */
    @Excel(name = "规则描述")
    private String description;

    /** 预警级别（1为一级预警，2为二级预警，3为三级预警，4为四级预警） */
    @Excel(name = "预警级别", readConverterExp = "1为一级预警，2为二级预警，3为三级预警，4为四级预警")
    private Integer alertLevel;

    /** 指标数据与评分相关性 */
    @Excel(name = "评分相关性", readConverterExp = "1为正相关，2为反相关")
    private Integer correlationDataScore;

    /** 对象指标是否关联此预警规则标识 默认不关联 */
    private boolean flag = false;

    public static final String THRESHOLD_RULE_DESCRIPTION_TEMPLATE = "监测值 {thresholdOperatorType} [{thresholdValue}]";
    public static final String RELATIVE_RULE_DESCRIPTION_TEMPLATE = "与前 {relativePreviousPeriod} 比较，{relativeChangeType} {relativeOperatorType} [{relativeValue}]";
    public static final String DEADMAN_RULE_DESCRIPTION_TEMPLATE = "缺数据 超过 {deadmanMissingPeriod} ";

    public String joinDescription() {
        String result = "";
        switch (this.type) {
            case BizConstants.ALERT_RULE_TYPE_THRESHOLD:
                result = THRESHOLD_RULE_DESCRIPTION_TEMPLATE;
                result = result.replace("{thresholdOperatorType}", BizConstants.ALERT_RULE_OPERATOR_TYPE_CH_ARRAY[this.thresholdOperatorType]);
                result = result.replace("{thresholdValue}", this.joinThresholdValueStr());
                break;
            case BizConstants.ALERT_RULE_TYPE_RELATIVE:
                result = RELATIVE_RULE_DESCRIPTION_TEMPLATE;
                result = result.replace("{relativePreviousPeriod}", normalizePeroid(this.relativePreviousPeriod));
                result = result.replace("{relativeChangeType}", BizConstants.ALERT_RULE_RELATIVE_CHANGE_TYPE_CH_ARRAY[this.relativeChangeType]);
                result = result.replace("{relativeOperatorType}", BizConstants.ALERT_RULE_OPERATOR_TYPE_CH_ARRAY[this.relativeOperatorType]);
                result = result.replace("{relativeValue}", "" + this.relativeValue);
                break;
            case BizConstants.ALERT_RULE_TYPE_DEADMAN:
                result = DEADMAN_RULE_DESCRIPTION_TEMPLATE;
                result = result.replace("{deadmanMissingPeriod}", normalizePeroid(this.deadmanMissingPeriod));
                break;
            default :
                result = this.description;
        }
        return result;
    }

    private String normalizePeroid(Long period) {
        String result = "";
        if (period >= 24 * 60 * 60) {
            result = period / (24 * 60 * 60) + "d";
        } else if (period >= 60 * 60){
            result = period / (60 * 60) + "h";
        } else {
            result = period / 60 + "m";
        }
        return result;
    }

    private String joinThresholdValueStr() {
        if (this.thresholdOperatorType >= BizConstants.ALERT_RULE_OPERATOR_TYPE_INSIDE_RANGE) {
            return this.thresholdLower + ", " + this.thresholdUpper;
        } else {
            return "" + this.thresholdValue;
        }
    }

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setType(String type)
    {
        this.type = type;
    }

    public String getType()
    {
        return type;
    }
    public void setThresholdValue(BigDecimal thresholdValue)
    {
        this.thresholdValue = thresholdValue;
    }

    public BigDecimal getThresholdValue()
    {
        return thresholdValue;
    }
    public void setThresholdLower(BigDecimal thresholdLower)
    {
        this.thresholdLower = thresholdLower;
    }

    public BigDecimal getThresholdLower()
    {
        return thresholdLower;
    }
    public void setThresholdUpper(BigDecimal thresholdUpper)
    {
        this.thresholdUpper = thresholdUpper;
    }

    public BigDecimal getThresholdUpper()
    {
        return thresholdUpper;
    }
    public void setRelativePreviousPeriod(Long relativePreviousPeriod)
    {
        this.relativePreviousPeriod = relativePreviousPeriod;
    }

    public Long getRelativePreviousPeriod()
    {
        return relativePreviousPeriod;
    }
    public void setDeadmanMissingPeriod(Long deadmanMissingPeriod)
    {
        this.deadmanMissingPeriod = deadmanMissingPeriod;
    }

    public Long getDeadmanMissingPeriod()
    {
        return deadmanMissingPeriod;
    }
    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }
    public void setIndexId(Long indexId)
    {
        this.indexId = indexId;
    }

    public Long getIndexId()
    {
        return indexId;
    }
    public void setThresholdOperatorType(int thresholdOperatorType)
    {
        this.thresholdOperatorType = thresholdOperatorType;
    }

    public int getThresholdOperatorType()
    {
        return thresholdOperatorType;
    }
    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }
    public void setRelativeChangeType(int relativeChangeType)
    {
        this.relativeChangeType = relativeChangeType;
    }

    public int getRelativeChangeType()
    {
        return relativeChangeType;
    }
    public void setRelativeOperatorType(int relativeOperatorType)
    {
        this.relativeOperatorType = relativeOperatorType;
    }

    public int getRelativeOperatorType()
    {
        return relativeOperatorType;
    }
    public void setRelativeValue(BigDecimal relativeValue)
    {
        this.relativeValue = relativeValue;
    }

    public BigDecimal getRelativeValue()
    {
        return relativeValue;
    }
    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public Integer getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(Integer alertLevel) {
        this.alertLevel = alertLevel;
    }

    public Integer getCorrelationDataScore() {
        return correlationDataScore;
    }

    public void setCorrelationDataScore(Integer correlationDataScore) {
        this.correlationDataScore = correlationDataScore;
    }
    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("createBy", getCreateBy())
            .append("updateBy", getUpdateBy())
            .append("type", getType())
            .append("thresholdValue", getThresholdValue())
            .append("thresholdLower", getThresholdLower())
            .append("thresholdUpper", getThresholdUpper())
            .append("relativePreviousPeriod", getRelativePreviousPeriod())
            .append("deadmanMissingPeriod", getDeadmanMissingPeriod())
            .append("status", getStatus())
            .append("indexId", getIndexId())
            .append("thresholdOperatorType", getThresholdOperatorType())
            .append("name", getName())
            .append("relativeChangeType", getRelativeChangeType())
            .append("relativeOperatorType", getRelativeOperatorType())
            .append("relativeValue", getRelativeValue())
            .append("description", getDescription())
            .toString();
    }
}
