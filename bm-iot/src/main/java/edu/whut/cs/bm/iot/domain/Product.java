package edu.whut.cs.bm.iot.domain;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import javax.validation.constraints.NotBlank;


/**
 * 物联网产品对象 bm_product
 *
 * @author qixin
 * @date 2021-08-04
 */
public class Product extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 产品名称 */
    @Excel(name = "产品名称")
    @NotBlank(message = "产品名称不能为空")
    private String name;

    /** 产品型号 */
    @Excel(name = "产品型号")
    @NotBlank(message = "产品型号不能为空")
    private String model;

    /** 产品图片 */
    @Excel(name = "产品图片")
    private String imgUrl;

    /** 数据通道管理信息 */
    private List<Channel> channelList;

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
    public void setModel(String model)
    {
        this.model = model;
    }

    public String getModel()
    {
        return model;
    }
    public void setImgUrl(String imgUrl)
    {
        this.imgUrl = imgUrl;
    }

    public String getImgUrl()
    {
        return imgUrl;
    }

    public List<Channel> getChannelList()
    {
        return channelList;
    }

    public void setChannelList(List<Channel> channelList)
    {
        this.channelList = channelList;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("name", getName())
            .append("model", getModel())
            .append("imgUrl", getImgUrl())
            .append("channelList", getChannelList())
            .toString();
    }
}
