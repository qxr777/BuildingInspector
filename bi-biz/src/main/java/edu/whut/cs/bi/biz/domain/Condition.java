package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 *
 */
public class Condition extends BaseEntity {

    /**
     * 实体的唯一标识符，用于唯一标识该实体。
     */
    private Long Id;

    /**
     * 权重值，用于表示该实体的权重。
     * 该值通常用于计算或评估实体的重要性。
     */
    private BigDecimal weight;

    /**
     * 评分值，用于表示该实体的评分。
     * 该值通常用于评估实体的质量或状态。
     */
    private BigDecimal score;

    /**
     * 等级值，用于表示该实体的等级。
     * 该值通常用于描述实体的严重程度或优先级。
     */
    private Integer level;

    /**
     * 组件数量，用于表示该实体相关的组件数量。
     * 该值通常用于统计或描述实体的复杂性。
     */
    private Integer componentsCount;


}