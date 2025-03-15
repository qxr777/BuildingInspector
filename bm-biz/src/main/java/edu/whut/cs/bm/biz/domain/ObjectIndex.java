package edu.whut.cs.bm.biz.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.hibernate.validator.constraints.Range;

import java.math.BigDecimal;

/**
 * 监测对象评估对象 bm_object_index
 *
 * @author qixin
 * @date 2021-08-11
 */
public class ObjectIndex extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 对象id */
    @Excel(name = "对象id")
    private Long objectId;
    /** 对象 */
    @Excel(name = "对象")
    private BmObject object;
    /** 指标 */
    @Excel(name = "指标")
    private Index index;
    /** 最近指标数据 */
    @Excel(name = "最近指标数据")
    private IndexData indexData;

    /** 指标id */
    @Excel(name = "指标id")
    private Long indexId;

    /** 测点表达式 */
    @Excel(name = "测点表达式")
    private String measurement;

    /** 最近指标数据id */
    @Excel(name = "最近指标数据id")
    private Long lastIndexDataId;

    private String valueStr;

    /** 评估权重值 */
    @Excel(name = "评估权重值")
    @Range(min=0, max=1, message="评估权重大于等于0，小于等于1！")
    private BigDecimal weight;

    private int countOfRule;  // 关联预警规则的数量

    private String converter;  // 原始数据转换器，eg：DisplacementRelativeValueConverter

    private String convertParams;    // 数据转换所需参数，eg：T1:=2.3;;T2:=5.889

    public String getConverter() {
        return converter;
    }

    public void setConverter(String converter) {
        this.converter = converter;
    }

    public String getConvertParams() {
        return convertParams;
    }

    public void setConvertParams(String convertParams) {
        this.convertParams = convertParams;
    }

    public int getCountOfRule() {
        return countOfRule;
    }

    public void setCountOfRule(int countOfRule) {
        this.countOfRule = countOfRule;
    }

    public String getValueStr() {
        return valueStr;
    }

    public void setValueStr(String valueStr) {
        this.valueStr = valueStr;
    }

    public IndexData getIndexData() {
        return indexData;
    }

    public void setIndexData(IndexData indexData) {
        this.indexData = indexData;
    }

    public BmObject getObject() {
        return object;
    }

    public void setObject(BmObject object) {
        this.object = object;
    }

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
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
    public void setMeasurement(String measurement)
    {
        this.measurement = measurement;
    }

    public String getMeasurement()
    {
        return measurement;
    }
    public void setLastIndexDataId(Long lastIndexDataId)
    {
        this.lastIndexDataId = lastIndexDataId;
    }

    public Long getLastIndexDataId()
    {
        return lastIndexDataId;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("objectId", getObjectId())
            .append("indexId", getIndexId())
            .append("measurement", getMeasurement())
            .append("lastIndexDataId", getLastIndexDataId())
            .toString();
    }
}
