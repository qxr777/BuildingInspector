package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 全桥技术状况评定中间结果（分层综合结果 + 单项控制覆盖后最终结果）。
 */
@Data
public class BridgeEval {

    /** 多跨聚合方式 SINGLE/EQUAL/INTERP */
    private String calcMode;

    /** 各跨 BSCI 最小值 */
    private BigDecimal bsciMin;

    /** 各跨 BSCI 最大值 */
    private BigDecimal bsciMax;

    /** 各跨 BSCI 算术平均值 */
    private BigDecimal bsciMean;

    /** BSCI 限制值（最差等级 90/75/60/40） */
    private BigDecimal bsciLim;

    /** 分层综合 BCL（覆盖前） */
    private int bclLayered;

    /** 分层综合 BCI */
    private BigDecimal bciLayered;

    /** 最终 BCL（含单项控制） */
    private int bcl;

    /** 最终 BCI */
    private BigDecimal bci;

    /** 单项控制是否命中 */
    private boolean singleControlHit;

    /** 命中的单项控制序号（逗号分隔） */
    private String singleControlNos;

    /** 最差桥跨节点ID */
    private Long worstSpanId;
}
