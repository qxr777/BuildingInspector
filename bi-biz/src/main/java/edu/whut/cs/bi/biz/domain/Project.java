package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Project extends BaseEntity {
    // 项目唯一标识符
    private Long id;
    // 项目名称
    private String name;
    // 项目年份
    private Integer year;
    /** 项目状态（0正常 1停用） */
    @Excel(name = "项目状态", readConverterExp = "0=正常,1=停用")
    private String status;
    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    // 项目所属部门，表示该项目由哪个部门负责管理
    private SysDept ownerDept;
    // 项目编号，用于唯一标识该项目
    private String code;
    // 项目开始日期，表示项目的启动时间
    private Date startDate;
    // 项目结束日期，表示项目的预计完成时间
    private Date endDate;
    // 合同金额，表示该项目的合同总金额
    private BigDecimal contractAmount;

    private List<SysUser> inspectors;

    // 项目受托部门
    private SysDept dept;
    // 项目受托部门id
    private Long deptId;
    // 报告编写人员
    private SysUser author;
    // 报告编写人员id
    private Long authorId;
    // 报告审核人员
    private SysUser reviewer;
    // 报告审核人员id
    private Long reviewerId;
    // 报告批准人员
    private SysUser approver;
    // 报告批准人员id
    private Long approverId;
    // 关联的建筑列表
    private List<Building> buildings;
    // 关联的任务列表
    private List<Task> tasks;
    // 关联的标准列表
    private List<Standard> standards;
    // 关联的设备列表
    private List<BiDevice> biDevices;


}