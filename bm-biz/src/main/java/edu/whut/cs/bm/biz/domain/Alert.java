package edu.whut.cs.bm.biz.domain;

import edu.whut.cs.bm.common.constant.BizConstants;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 预警信息对象 bm_alert
 * 
 * @author qixin
 * @date 2021-08-13
 */
public class Alert extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 预警规则id */
    @Excel(name = "预警规则id")
    private Long alertRuleId;

    private AlertRule alertRule;

    /** 创建类型（0为系统，1为人工） */
    @Excel(name = "创建类型", readConverterExp = "0=为系统，1为人工")
    private String createType;

    /** 监测对象id */
    @Excel(name = "监测对象id")
    private Long objectId;
    private BmObject bmObject;

    /** 监测指标id */
    @Excel(name = "监测指标id")
    private Long indexId;
    private Index index;

    /** 测点式 */
    @Excel(name = "测点式")
    private String measurement;

    /** 监测数据id */
    private Long indexDataId;

    private IndexData indexData;

    /** 消息内容 */
    @Excel(name = "消息内容")
    private String message;

    /** 处理状态（0待处理 1已处理） */
    @Excel(name = "处理状态", readConverterExp = "0=待处理,1=已处理")
    private String status;

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
    public void setCreateType(String createType) 
    {
        this.createType = createType;
    }

    public String getCreateType() 
    {
        return createType;
    }
    public void setObjectId(Long objectId) 
    {
        this.objectId = objectId;
    }

    public Long getObjectId() 
    {
        return objectId;
    }
    public void setMeasurement(String measurement) 
    {
        this.measurement = measurement;
    }

    public String getMeasurement() 
    {
        return measurement;
    }
    public void setIndexDataId(Long indexDataId) 
    {
        this.indexDataId = indexDataId;
    }

    public Long getIndexDataId() 
    {
        return indexDataId;
    }
    public void setMessage(String message) 
    {
        this.message = message;
    }

    public String getMessage() 
    {
        return message;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    public Long getIndexId() {
        return indexId;
    }

    public void setIndexId(Long indexId) {
        this.indexId = indexId;
    }

    public IndexData getIndexData() {
        return indexData;
    }

    public void setIndexData(IndexData indexData) {
        this.indexData = indexData;
    }

    public AlertRule getAlertRule() {
        return alertRule;
    }

    public void setAlertRule(AlertRule alertRule) {
        this.alertRule = alertRule;
    }

    public BmObject getBmObject() {
        return bmObject;
    }

    public void setBmObject(BmObject object) {
        this.bmObject = object;
    }

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public String joinMessage() {
        String message = "";
        if (this.indexData != null) {
            message += "监测数据 " + indexData.getValueStr() + " 触发预警 ";
        }
        if (this.alertRule != null) {
            message += alertRule.getDescription();
        }

        return message;
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
            .append("createType", getCreateType())
            .append("objectId", getObjectId())
            .append("measurement", getMeasurement())
            .append("indexDataId", getIndexDataId())
            .append("message", getMessage())
            .append("status", getStatus())
            .append("remark", getRemark())
            .toString();
    }
}
