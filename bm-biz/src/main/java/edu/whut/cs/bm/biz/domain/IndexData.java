package edu.whut.cs.bm.biz.domain;

import java.math.BigDecimal;

import edu.whut.cs.bm.common.constant.BizConstants;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 监测数据对象 bm_index_data
 *
 * @author qixin
 * @date 2021-08-10
 */
public class IndexData extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 对象id */
    @Excel(name = "对象id")
    private Long objectId;

    private BmObject bmObject;

    /** 指标id */
    @Excel(name = "指标id")
    private Long indexId;

    private Index index;

    /** 指标数据类型 */
    @Excel(name = "指标数据类型")
    private Integer indexDataType;

    /** 数值属性值 */
    @Excel(name = "数值属性值")
    private BigDecimal numericValue;

    /** 二元属性值 */
    @Excel(name = "二元属性值")
    private Integer binaryValue;

    /** 序数属性值 */
    @Excel(name = "序数属性值")
    private Integer ordinalValue;

    /** 标称属性值 */
    @Excel(name = "标称属性值")
    private String nominalValue;

    /** 值字符串 */
    private String valueStr;

    /** 创建类型（0为系统，1为人工） */
    @Excel(name = "创建类型", readConverterExp = "0=为系统，1为人工")
    private Integer createType;

    /** 是否预警（0为正常，1为一级预警，2为二级预警，3为三级预警，4为四级预警） */
    @Excel(name = "是否预警", readConverterExp = "0=为正常，1为一级预警，2为二级预警，3为三级预警，4为四级预警")
    private Integer isAlert;

    /** 测点 */
    @Excel(name = "测点")
    private String measurement;

    @Excel(name = "评分")
    private Double score;

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

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public BmObject getBmObject() {
        return bmObject;
    }

    public void setBmObject(BmObject bmObject) {
        this.bmObject = bmObject;
    }

    public void setIndexDataType(Integer indexDataType)
    {
        this.indexDataType = indexDataType;
    }

    public Integer getIndexDataType()
    {
        return indexDataType;
    }
    public void setNumericValue(BigDecimal numericValue)
    {
        this.numericValue = numericValue;
    }

    public BigDecimal getNumericValue()
    {
        return numericValue;
    }
    public void setBinaryValue(Integer binaryValue)
    {
        this.binaryValue = binaryValue;
    }

    public Integer getBinaryValue()
    {
        return binaryValue;
    }
    public void setOrdinalValue(Integer ordinalValue)
    {
        this.ordinalValue = ordinalValue;
    }

    public Integer getOrdinalValue()
    {
        return ordinalValue;
    }
    public void setNominalValue(String nominalValue)
    {
        this.nominalValue = nominalValue;
    }

    public String getNominalValue()
    {
        return nominalValue;
    }
    public void setCreateType(Integer createType)
    {
        this.createType = createType;
    }

    public Integer getCreateType()
    {
        return createType;
    }
    public void setIsAlert(Integer isAlert)
    {
        this.isAlert = isAlert;
    }

    public Integer getIsAlert()
    {
        return isAlert;
    }
    public void setMeasurement(String measurement)
    {
        this.measurement = measurement;
    }

    public String getMeasurement()
    {
        return measurement;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("createBy", getCreateBy())
            .append("updateBy", getUpdateBy())
            .append("objectId", getObjectId())
            .append("indexId", getIndexId())
//            .append("indexDataType", getIndexDataType())
//            .append("numericValue", getNumericValue())
//            .append("binaryValue", getBinaryValue())
//            .append("ordinalValue", getOrdinalValue())
//            .append("nominalValue", getNominalValue())
            .append("createType", getCreateType())
            .append("isAlert", getIsAlert())
            .append("measurement", getMeasurement())
                .append("valueStr", getValueStr())
            .toString();
    }

    public String getValueStr() {
        return valueStr;
    }

    public void setValueStr(String valueStr) {
        this.valueStr = valueStr;
    }

    public String joinValueStr() {
        String result = "";
        if (indexDataType != null && index != null) {
            switch (indexDataType.intValue()) {
//                case BizConstants.INDEX_TYPE_DATA_TYPE_NUMERIC:
//                    result += numericValue + index.getUnit();
//                    break;
                case BizConstants.INDEX_TYPE_DATA_TYPE_BINARY:
                    result += binaryValue == 1 ? "是" : "否";
                    break;
                case BizConstants.INDEX_TYPE_DATA_TYPE_NOMINAL:
                    result += nominalValue;
                    break;
                case BizConstants.INDEX_TYPE_DATA_TYPE_ORDINAL:
                    String[] optionArray = index.getOptions().split(BizConstants.ORDINAL_DATA_TYPE_SEPARATOR);
                    result += optionArray[ordinalValue] + index.getUnit();
                    break;
                default:
                    result += numericValue + " " + index.getUnit();
            }
        }
        return result;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
