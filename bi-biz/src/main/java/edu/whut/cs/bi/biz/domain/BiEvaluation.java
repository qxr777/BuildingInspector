package edu.whut.cs.bi.biz.domain;

import java.math.BigDecimal;

/**
 *
 */
public class BiEvaluation {


    /**
     * 上部结构的等级，用于表示上部结构的评估等级。
     */
    public Integer superstructureLevel;

    /**
     * 上部结构的评分，用于表示上部结构的评估得分。
     */
    public BigDecimal superstructureScore;

    /**
     * 下部结构的等级，用于表示下部结构的评估等级。
     */
    public Integer substructureLevel;

    /**
     * 下部结构的评分，用于表示下部结构的评估得分。
     */
    public BigDecimal substructureScore;

    /**
     * 甲板系统的等级，用于表示甲板系统的评估等级。
     */
    public Integer deckSystemLevel;

    /**
     * 甲板系统的评分，用于表示甲板系统的评估得分。
     */
    public BigDecimal deckSystemScore;

    /**
     * 系统评分，用于表示整体系统的评估得分。
     */
    public BigDecimal systemScore;

    /**
     * 最差部分的等级，用于表示评估中最差部分的等级。
     */
    public Integer worstPartLevel;

    /**
     * 整体等级，用于表示整体评估的等级。
     */
    public Integer level;

    /**
     * 系统等级，用于表示系统评估的等级。
     */
    public Integer systemLevel;

    /**
     * 单一控制值，用于表示单一控制的评估结果。
     */
    public Integer singleControl;

    /**
     * 人为因素的等级，用于表示人为因素的评估等级。
     */
    public Integer manmadeLevel;


}