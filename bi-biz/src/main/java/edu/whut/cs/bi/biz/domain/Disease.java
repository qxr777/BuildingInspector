package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;
import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Disease extends BaseEntity {

    /** 病害id */
    private Long id;

    /** 病害位置 */
    private String position;

    /** 病害类型 */
    private DiseaseType diseaseType;
    private Long diseaseTypeId;

    /** 病害描述 */
    private String description;

    /** 病害趋势 */
    private String trend;

    /** 病害等级 */
    private int level;

    /** 病害数量 */
    private int quantity;

    /**
     * 病害的长度数值
     * 使用BigDecimal类型保证高精度计算需求
     */
    private BigDecimal length;

    /**
     * 病害的宽度数值
     * 使用BigDecimal类型保证高精度计算需求
     */
    private BigDecimal width;

    /**
     * 病害的高度/深度数值
     * 根据病害类型决定是垂直高度还是纵深深度
     */
    private BigDecimal heightOrDepth;

    /**
     * 缝隙/间隙的宽度数值
     * 用于病害结构中间隙的测量场景
     */
    private BigDecimal slitWidth;

    /**
     * 病害的面积数值
     * 可能通过病害的长宽等参数计算得出或直接输入
     */
    private BigDecimal area;



    /** 关联项目 */
    private Project project;
    private Long projectId;

    /** 关联构建 */
    private Component component;
    private Long componentId;

    /** 关联建筑 */
    private Building building;
    private Long buildingId;

    /** 附件列表 */
    private List<Attachment> attachments;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String trend) {
        this.trend = trend;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Building getBuilding() {
        return building;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public BigDecimal getLength() {
        return length;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public BigDecimal getHeightOrDepth() {
        return heightOrDepth;
    }

    public void setHeightOrDepth(BigDecimal heightOrDepth) {
        this.heightOrDepth = heightOrDepth;
    }

    public BigDecimal getSlitWidth() {
        return slitWidth;
    }

    public void setSlitWidth(BigDecimal slitWidth) {
        this.slitWidth = slitWidth;
    }

    public BigDecimal getArea() {
        return area;
    }

    public void setArea(BigDecimal area) {
        this.area = area;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(Long buildingId) {
        this.buildingId = buildingId;
    }

}