package edu.whut.cs.bm.iot.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 数据通道管理对象 bm_channel
 * 
 * @author qixin
 * @date 2021-08-04
 */
public class Channel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 通道名称 */
    @Excel(name = "通道名称")
    private String name;

    /** 通道类型 */
    @Excel(name = "通道类型")
    private Long type;

    /** 数据单位 */
    @Excel(name = "数据单位")
    private String unit;

    /**  */
    @Excel(name = "")
    private Long productId;

    public void setName(String name) 
    {
        this.name = name;
    }

    public String getName() 
    {
        return name;
    }
    public void setType(Long type) 
    {
        this.type = type;
    }

    public Long getType() 
    {
        return type;
    }
    public void setUnit(String unit) 
    {
        this.unit = unit;
    }

    public String getUnit() 
    {
        return unit;
    }
    public void setProductId(Long productId) 
    {
        this.productId = productId;
    }

    public Long getProductId() 
    {
        return productId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("name", getName())
            .append("type", getType())
            .append("unit", getUnit())
            .append("productId", getProductId())
            .toString();
    }
}
