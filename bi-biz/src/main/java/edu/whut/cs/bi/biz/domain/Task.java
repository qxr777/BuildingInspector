package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.core.domain.entity.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class Task extends BaseEntity {
    /**
     * 任务ID
     */
    private Long id;

    // 关联的建筑
    private Building building;
    private Long buildingId;
    // 关联的项目
    private Project project;
    private Long projectId;
    // 负责的检查人员
    private List<SysUser> inspectors;
    /** 项目状态（0正常 1停用） */
    @Excel(name = "任务状态", readConverterExp = "0=正常,1=停用")
    private String status;

    // 业务实体可见性控制
    String select;

    // 业务实体根据部门查询项目
    Long selectDeptId;

    /** 桥梁技术状况评定结果 */
    private Integer evaluationResult;

    /** 1 已提交  0 未提交 */
    private String type;
}