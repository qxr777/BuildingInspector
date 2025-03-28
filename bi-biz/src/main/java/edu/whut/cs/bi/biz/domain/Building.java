package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Building extends BaseEntity {
    /** 建筑id */
    private Long id;

    /** 建筑名称 */
    @Excel(name = "建筑名称")
    @NotBlank(message = "建筑名称不能为空")
    @Length(message = "建筑名称不能超过20个字符", max = 20)
    private String name;

    /** 建筑状态（0正常 1停用） */
    @Excel(name = "建筑状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 海拔高度 */
    private BigDecimal altitude;

    /** 地址 */
    @Excel(name = "地址")
    private String address;

    /** 区域 */
    @Excel(name = "区域")
    private String area;

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getRootObjectId() {
        return rootObjectId;
    }

    public void setRootObjectId(String rootObjectId) {
        this.rootObjectId = rootObjectId;
    }

    public String getRootPropertyId() {
        return rootPropertyId;
    }

    public void setRootPropertyId(String rootPropertyId) {
        this.rootPropertyId = rootPropertyId;
    }

    /** 管理部门 */
    @Excel(name = "管理部门")
    private String adminDept;

    /** 权重 */
    @Excel(name = "权重")
    private BigDecimal weight;

    /** 视频流来源 */
    @Excel(name = "视频流来源")
    private String videoFeed;

    /** 线路 */
    @Excel(name = "线路")
    private String line;

    /** 根对象ID */
    @Excel(name = "根对象ID")
    private String rootObjectId;

    /** 根属性ID */
    @Excel(name = "根属性ID")
    private String rootPropertyId;

    private BiObject rootObject;

    private Property rootProperty;

    /** 父BuildingID */
    private Long parentId;

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

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }
    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
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
    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getAddress()
    {
        return address;
    }
    public void setArea(String area)
    {
        this.area = area;
    }

    public String getArea()
    {
        return area;
    }
    public void setAdminDept(String adminDept)
    {
        this.adminDept = adminDept;
    }

    public String getAdminDept()
    {
        return adminDept;
    }
    public void setWeight(BigDecimal weight)
    {
        this.weight = weight;
    }

    public BigDecimal getWeight()
    {
        return weight;
    }

    public String getVideoFeed() {
        return videoFeed;
    }

    public void setVideoFeed(String videoFeed) {
        this.videoFeed = videoFeed;
    }

    public BiObject getRootObject() {
        return rootObject;
    }

    public void setRootObject(BiObject rootObject) {
        this.rootObject = rootObject;
    }

    public Property getRootProperty() {
        return rootProperty;
    }

    public void setRootProperty(Property rootProperty) {
        this.rootProperty = rootProperty;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .append("id", getId())
                .append("name", getName())
                .append("createBy", getCreateBy())
                .append("updateBy", getUpdateBy())
                .append("status", getStatus())
                .append("delFlag", getDelFlag())
                .append("longitude", getLongitude())
                .append("latitude", getLatitude())
                .append("altitude", getAltitude())
                .append("remark", getRemark())
                .append("address", getAddress())
                .append("area", getArea())
                .append("adminDept", getAdminDept())
                .append("videoFeed", getVideoFeed())
                .append("weight", getWeight())
                .toString();
    }
}