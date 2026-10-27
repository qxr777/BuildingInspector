package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 5230 评定单元结果 bi_assess_unit_result
 * （JTG/T 5230—2026 第 3.4.1~3.4.3 条；每次重算按 task_id 先删后插）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssessUnitResult extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 检测任务ID（bi_task.id） */
    private Long taskId;

    /** 评定单元节点ID（bi_object.id，UNIT） */
    private Long unitId;

    /** 桥型代码（B01~B21） */
    private String bridgeType;

    /** 单元技术状况指数 BCI（3.4.1~3.4.3） */
    private BigDecimal bci;

    /** 单元技术状况等级 1~5 */
    private Integer bcl;

    /** 合成方式：SINGLE 单跨 / EQUAL 同级均值 / INTERP 异级插值 */
    private String combineMode;

    /** 异级插值：最低桥跨 BSCI */
    private BigDecimal bsciMin;

    /** 异级插值：最高桥跨 BSCI */
    private BigDecimal bsciMax;

    /** 桥跨 BSCI 算术均值 */
    private BigDecimal bsciAvg;

    /** 最差等级档上限（90/75/60/40） */
    private BigDecimal bsciLim;

    /** 桥跨数 */
    private Integer spanCount;
}
