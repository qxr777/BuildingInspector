package edu.whut.cs.bm.iot.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 物联网设备对象 bm_device
 *
 * @author qixin
 * @date 2021-08-04
 */
public class Device extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 设备名称 */
    @Excel(name = "设备名称")
    private String name;

    /** 设备编号 */
    @Excel(name = "设备编号")
    private String sn;

    /** 物理地址 */
    @Excel(name = "物理地址")
    private String address;

    /** 设备图片 */
    @Excel(name = "设备图片")
    private String imgUrl;

    /**  */
    @Excel(name = "")
    private Long productId;

    /** 产品名称 */
    @Excel(name = "产品名称")
    private String productName;

    /** 设备状态 */
    @Excel(name = "设备状态")
    private String status;

    /** 是否在线 */
    @Excel(name = "是否在线")
    private Integer connected;

    /** 连接时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "连接时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date connectedAt;

    /** 断开时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "断开时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date disconnectedAt;

    /** 状态最新更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "状态最新更新时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date lastStatusUpdateAt;

    /** 当前运行状态 */
    @Excel(name = "当前运行状态")
    private String deviceStatus;

    /** 经度 */
    @Excel(name = "经度")
    private BigDecimal longitude;

    /** 纬度 */
    @Excel(name = "纬度")
    private BigDecimal latitude;

    /** 海拔高度 */
    @Excel(name = "海拔高度")
    private BigDecimal altitude;

    /** 连接密钥 */
    @Excel(name = "连接密钥")
    private String secret;

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
    public void setSn(String sn)
    {
        this.sn = sn;
    }

    public String getSn()
    {
        return sn;
    }
    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getAddress()
    {
        return address;
    }
    public void setImgUrl(String imgUrl)
    {
        this.imgUrl = imgUrl;
    }

    public String getImgUrl()
    {
        return imgUrl;
    }
    public void setProductId(Long productId)
    {
        this.productId = productId;
    }

    public Long getProductId()
    {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }
    public void setConnected(Integer connected)
    {
        this.connected = connected;
    }

    public Integer getConnected()
    {
        return connected;
    }
    public void setConnectedAt(Date connectedAt)
    {
        this.connectedAt = connectedAt;
    }

    public Date getConnectedAt()
    {
        return connectedAt;
    }
    public void setDisconnectedAt(Date disconnectedAt)
    {
        this.disconnectedAt = disconnectedAt;
    }

    public Date getDisconnectedAt()
    {
        return disconnectedAt;
    }
    public void setLastStatusUpdateAt(Date lastStatusUpdateAt)
    {
        this.lastStatusUpdateAt = lastStatusUpdateAt;
    }

    public Date getLastStatusUpdateAt()
    {
        return lastStatusUpdateAt;
    }
    public void setDeviceStatus(String deviceStatus)
    {
        this.deviceStatus = deviceStatus;
    }

    public String getDeviceStatus()
    {
        return deviceStatus;
    }
    public void setLongitude(BigDecimal longitude)
    {
        this.longitude = longitude;
    }

    public BigDecimal getLongitude()
    {
        return longitude;
    }
    public void setLatitude(BigDecimal latitude)
    {
        this.latitude = latitude;
    }

    public BigDecimal getLatitude()
    {
        return latitude;
    }
    public void setAltitude(BigDecimal altitude)
    {
        this.altitude = altitude;
    }

    public BigDecimal getAltitude()
    {
        return altitude;
    }
    public void setSecret(String secret)
    {
        this.secret = secret;
    }

    public String getSecret()
    {
        return secret;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("name", getName())
            .append("sn", getSn())
            .append("address", getAddress())
            .append("imgUrl", getImgUrl())
            .append("productId", getProductId())
            .append("status", getStatus())
            .append("connected", getConnected())
            .append("connectedAt", getConnectedAt())
            .append("disconnectedAt", getDisconnectedAt())
            .append("lastStatusUpdateAt", getLastStatusUpdateAt())
            .append("deviceStatus", getDeviceStatus())
            .append("longitude", getLongitude())
            .append("latitude", getLatitude())
            .append("altitude", getAltitude())
            .append("secret", getSecret())
            .toString();
    }
}
