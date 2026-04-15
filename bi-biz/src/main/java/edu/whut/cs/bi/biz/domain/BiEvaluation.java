package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.math.BigDecimal;

/**
 *  桥幅的技术状况评定
 */
@Data
@EqualsAndHashCode(callSuper = true)
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

    /** 评价对象类型: BRIDGE(全桥), SPAN(桥跨) */
    private String targetType;

    /** 评价对象ID: building_id 或 bi_object_id */
    private Long targetId;

    /** 附属构造评分 (FSCI 2026新标) */
    private BigDecimal acciScore;

    /** 附属构造等级 (2026新标) */
    private Integer acciLevel;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("targetType", getTargetType())
                .append("targetId", getTargetId())
                .append("superstructureLevel", getSuperstructureLevel())
                .append("superstructureScore", getSuperstructureScore())
                .append("substructureLevel", getSubstructureLevel())
                .append("substructureScore", getSubstructureScore())
                .append("deckSystemLevel", getDeckSystemLevel())
                .append("deckSystemScore", getDeckSystemScore())
                .append("acciLevel", getAcciLevel())
                .append("acciScore", getAcciScore())
                .append("systemScore", getSystemScore())
                .append("systemLevel", getSystemLevel())
                .append("worstPartLevel", getWorstPartLevel())
                .append("level", getLevel())
                .append("singleControl", getSingleControl())
                .append("manmadeLevel", getManmadeLevel())
                .append("taskId", getTaskId())
                .toString();
    }
}