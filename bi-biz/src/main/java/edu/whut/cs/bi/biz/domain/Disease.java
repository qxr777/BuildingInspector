package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

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
    private String type;

    /** 病害描述 */
    private String description;

    /** 病害趋势 */
    private String trend;

    /** 病害等级 */
    private int level;

    /** 病害数量 */
    private int quantity;

    /** 关联项目 */
    private Project project;
    private Long projectId;

    /** 关联对象 */
    private BiObject biObject;
    private Long biObjectId;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public BiObject getBiObject() {
        return biObject;
    }

    public void setBiObject(BiObject biObject) {
        this.biObject = biObject;
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
}