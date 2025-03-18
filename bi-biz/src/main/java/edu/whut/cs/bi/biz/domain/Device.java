package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Device extends BaseEntity {
    // 设备唯一标识符
    private Long id;
    // 设备名称
    private String name;
    // 设备型号
    private String model;
    // 设备用途
    private String purpose;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}