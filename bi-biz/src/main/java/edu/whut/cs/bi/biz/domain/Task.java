package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.core.domain.entity.SysUser;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Task extends BaseEntity {
    // 关联的建筑
    private Building building;
    private Long buildingId;
    // 关联的项目
    private Project project;
    private Long projectId;
    // 负责的检查人员
    private SysUser inspector;
    /** 项目状态（0正常 1停用） */
    @Excel(name = "任务状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Building getBuilding() {
        return building;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }

    public SysUser getInspector() {
        return inspector;
    }

    public void setInspector(SysUser inspector) {
        this.inspector = inspector;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}