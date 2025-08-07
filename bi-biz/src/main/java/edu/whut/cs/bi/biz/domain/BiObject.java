package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.TreeEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class BiObject extends TreeEntity {
    /**
     * 对象id
     */
    private Long id;

    /**
     * 对象名称
     */
    @Excel(name = "对象名称")
    @NotBlank(message = "对象名称不能为空")
    @Length(message = "对象名称不能超过20个字符", max = 100)
    private String name;

    /**
     * 祖级列表
     */
    private String ancestors;

    /**
     * 对象状态（0正常 1停用）
     */
    @Excel(name = "对象状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 海拔高度
     */
    private BigDecimal altitude;

    /**
     * 位置
     */
    @Excel(name = "位置")
    private String position;

    /**
     * 区域
     */
    @Excel(name = "区域")
    private String area;

    /**
     * 管理部门
     */
    @Excel(name = "管理部门")
    private String adminDept;

    /**
     * 权重
     */
    @Excel(name = "权重")
    private BigDecimal weight;

    /**
     * 视频流来源
     */
    @Excel(name = "视频流来源")
    private String videoFeed;

    /**
     * 附加属性2
     */
    @Excel(name = "附加属性")
    private String props;

    /**
     * 对应的模板对象ID
     */
    private Long templateObjectId;

    /**
     * 子对象
     */
    private List<BiObject> children = new ArrayList<BiObject>();

    private List<Component> comments = new ArrayList<Component>();

    private List<DiseaseType>  diseaseTypes = new ArrayList<>();

    /** 现状图 */
    private List<String> photo;

    /** 现状图对应备注 */
    private List<String> information;

    /**
     * 构件数量
     */
    @Excel(name = "构件数量")
    private int count;

    public List<String> getInformation() {
        return information;
    }

    public void setInformation(List<String> information) {
        this.information = information;
    }

    public List<String> getPhoto() {
        return photo;
    }

    public void setPhoto(List<String> photo) {
        this.photo = photo;
    }

    public String getProps() {
        return props;
    }

    public void setProps(String props) {
        this.props = props;
    }

    public List<Component> getComments() {
        return comments;
    }

    public List<DiseaseType> getDiseaseTypes() {
        return diseaseTypes;
    }

    public void setDiseaseTypes(List<DiseaseType> diseaseTypes) {
        this.diseaseTypes = diseaseTypes;
    }

    public void setComments(List<Component> comments) {
        this.comments = comments;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public List<BiObject> getChildren() {
        return children;
    }

    public void setChildren(List<BiObject> children) {
        this.children = children;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setAncestors(String ancestors) {
        this.ancestors = ancestors;
    }

    public String getAncestors() {
        return ancestors;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
    }

    public String getDelFlag() {
        return delFlag;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setAltitude(BigDecimal altitude) {
        this.altitude = altitude;
    }

    public BigDecimal getAltitude() {
        return altitude;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getPosition() {
        return position;
    }

    public String getArea() {
        return area;
    }

    public void setAdminDept(String adminDept) {
        this.adminDept = adminDept;
    }

    public String getAdminDept() {
        return adminDept;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public String getVideoFeed() {
        return videoFeed;
    }

    public void setVideoFeed(String videoFeed) {
        this.videoFeed = videoFeed;
    }

    public Long getTemplateObjectId() {
        return templateObjectId;
    }

    public void setTemplateObjectId(Long templateObjectId) {
        this.templateObjectId = templateObjectId;
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
                .append("ancestors", getAncestors())
                .append("orderNum", getOrderNum())
                .append("status", getStatus())
                .append("delFlag", getDelFlag())
                .append("longitude", getLongitude())
                .append("latitude", getLatitude())
                .append("altitude", getAltitude())
                .append("remark", getRemark())
                .append("parentId", getParentId())
                .append("position", getPosition())
                .append("area", getArea())
                .append("adminDept", getAdminDept())
                .append("videoFeed", getVideoFeed())
                .append("weight", getWeight())
                .append("templateObjectId", getTemplateObjectId())
                .toString();
    }
}
