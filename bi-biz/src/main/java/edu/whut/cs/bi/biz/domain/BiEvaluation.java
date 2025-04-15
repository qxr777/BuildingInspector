package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 *  桥幅的技术状况评定
 */
@Data
public class BiEvaluation extends BaseEntity {

    /**
     * 实体的唯一标识符，用于唯一标识该实体。
     */
    private Long Id;
    /**
     * 上部结构的等级，用于表示上部结构的评估等级。
     */
    private Integer superstructureLevel;

    /**
     * 上部结构的评分，用于表示上部结构的评估得分。
     */
    private BigDecimal superstructureScore;

    /**
     * 下部结构的等级，用于表示下部结构的评估等级。
     */
    private Integer substructureLevel;

    /**
     * 下部结构的评分，用于表示下部结构的评估得分。
     */
    private BigDecimal substructureScore;

    /**
     * 桥面系统的等级，用于表示甲板系统的评估等级。
     */
    private Integer deckSystemLevel;

    /**
     * 桥面系统的评分，用于表示甲板系统的评估得分。
     */
    private BigDecimal deckSystemScore;

    /**
     * 系统评分，用于表示整体系统的评估得分。
     */
    private BigDecimal systemScore;

    /**
     * 最差部分的等级，用于表示评估中最差部分的等级。
     */
    private Integer worstPartLevel;

    /**
     * 整体等级，用于表示整体评估的等级。
     */
    private Integer level;

    /**
     * 系统等级，用于表示系统评估的等级。
     */
    private Integer systemLevel;

    /**
     * 单一控制值，用于表示单一控制的评估结果。
     */
    private Integer singleControl;

    /**
     * 人为因素的等级，用于表示人为因素的评估等级。
     */
    private Integer manmadeLevel;

    /**
     * 表示当前任务对象的引用。
     * 该变量用于存储和管理与当前任务相关的数据和操作。
     */
    private Task task;
    private Long taskId;

}