package edu.whut.cs.bi.biz.domain;

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
    // 任务状态
    private Integer status;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}