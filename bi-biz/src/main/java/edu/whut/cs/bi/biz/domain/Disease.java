package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@EqualsAndHashCode(callSuper = true)
@Data
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

    /** 病害类型 */
//    private String type;   与对象属性diseaseType重复

    /** 关联对象 */
    private Long biObjectId;
    private BiObject biObject;

    /** 关联项目 */
    private Project project;
    private Long projectId;

    /** 关联构建 */
    private Component component;
    private Long componentId;

    /** 关联检测任务 */
    private Task task;
    private Long taskId;

    /** 关联建筑 */
    private Building building;
    private Long buildingId;

    /** 附件列表 */
    private List<Attachment> attachments;

}