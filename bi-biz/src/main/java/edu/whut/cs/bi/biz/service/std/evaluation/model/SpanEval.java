package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 桥跨技术状况评定中间结果（含 3.3.3 覆盖前原值）。
 */
@Data
public class SpanEval {

    /** 桥跨节点ID */
    private Long spanId;

    /** 最终 BSCI */
    private BigDecimal bsci;

    /** 最终 BSCL (1~5) */
    private int bscl;

    /** 3.3.3 覆盖前 BSCI 公式原值 */
    private BigDecimal bsciRaw;

    /** 覆盖前等级 */
    private int rawLevel;

    /** 是否触发覆盖 */
    private boolean override;

    /** 覆盖后等级 */
    private Integer overrideLevel;

    /** 覆盖触发构件与依据 */
    private String trigger;
}
