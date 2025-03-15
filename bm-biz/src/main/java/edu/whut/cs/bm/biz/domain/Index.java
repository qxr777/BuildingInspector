package edu.whut.cs.bm.biz.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import javax.validation.constraints.*;

/**
 * 监测指标对象 bm_index
 *
 * @author qixin
 * @date 2021-08-10
 */
public class Index extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 指标名称 */
    @Excel(name = "指标名称")
    @NotBlank(message = "指标名称不能为空")
    private String name;

    /** 数据类型 */
    @Excel(name = "数据类型")
    private Integer dataType;

    /** 序数选项 */
    @Excel(name = "序数选项")
    private String options;

    /** 数值下限 */
    @Excel(name = "数值下限")
    private BigDecimal min;

    /** 数值上限 */
    @Excel(name = "数值上限")
    private BigDecimal max;

    /** 监测精度要求 */
    @Excel(name = "监测精度要求")
    private String precisionDemand;

    /** 分辨率要求 */
    @Excel(name = "分辨率要求")
    private String resolutionDemand;

    /** 数值单位 */
    @Excel(name = "数值单位")
    private String unit;

    /** 小数位数 */
    @Excel(name = "小数位数")
    @Digits(integer = 9, fraction = 0, message = "请输入正确的数字")
    private Integer decimalPlace;

    public Integer getDecimalPlace() {
        return decimalPlace;
    }

    public void setDecimalPlace(Integer decimalPlace) {
        this.decimalPlace = decimalPlace;
    }

    /** 对象是否关联此指标标识 默认不存在 */
    private boolean flag = false;

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
    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }
    public void setDataType(Integer dataType)
    {
        this.dataType = dataType;
    }

    public Integer getDataType()
    {
        return dataType;
    }
    public void setOptions(String options)
    {
        this.options = options;
    }

    public String getOptions()
    {
        return options;
    }
    public void setMin(BigDecimal min)
    {
        this.min = min;
    }

    public BigDecimal getMin()
    {
        return min;
    }
    public void setMax(BigDecimal max)
    {
        this.max = max;
    }

    public BigDecimal getMax()
    {
        return max;
    }
    public void setPrecisionDemand(String precisionDemand)
    {
        this.precisionDemand = precisionDemand;
    }

    public String getPrecisionDemand()
    {
        return precisionDemand;
    }
    public void setResolutionDemand(String resolutionDemand)
    {
        this.resolutionDemand = resolutionDemand;
    }

    public String getResolutionDemand()
    {
        return resolutionDemand;
    }
    public void setUnit(String unit)
    {
        this.unit = unit;
    }

    public String getUnit()
    {
        return unit;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("createBy", getCreateBy())
            .append("updateBy", getUpdateBy())
            .append("name", getName())
            .append("dataType", getDataType())
            .append("options", getOptions())
            .append("min", getMin())
            .append("max", getMax())
            .append("precisionDemand", getPrecisionDemand())
            .append("resolutionDemand", getResolutionDemand())
            .append("unit", getUnit())
            .toString();
    }
}
