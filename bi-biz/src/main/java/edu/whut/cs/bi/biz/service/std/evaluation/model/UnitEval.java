package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评定单元技术状况结果（JTG/T 5230—2026 第 3.4.1~3.4.3 条）。
 *
 * <p>一个评定单元内多跨的三式合成：单跨直通 / 多跨同级算术均值 / 多跨异级分段插值。
 * 多评定单元（3.1.2）时每个 UNIT 各产出一个 UnitEval，再由全桥聚合按 3.4.4 取最差。</p>
 */
@Data
public class UnitEval {

    /** 评定单元节点ID */
    private Long unitId;

    /** 桥型代码（B01~B21） */
    private String bridgeType;

    /** 多跨合成方式 SINGLE/EQUAL/INTERP */
    private String calcMode;

    /** 各跨 BSCI 最小值 */
    private BigDecimal bsciMin;

    /** 各跨 BSCI 最大值 */
    private BigDecimal bsciMax;

    /** 各跨 BSCI 算术平均值 */
    private BigDecimal bsciMean;

    /** 异级插值时最差等级档上限（90/75/60/40） */
    private BigDecimal bsciLim;

    /** 单元等级（3.4.1~3.4.3 合成结果，3.4.5 覆盖前） */
    private int bcl;

    /** 单元 BCI（3.4.1~3.4.3 合成结果，3.4.5 覆盖前） */
    private BigDecimal bci;

    /** 最差桥跨节点ID */
    private Long worstSpanId;

    /** 桥跨数 */
    private int spanCount;
}
