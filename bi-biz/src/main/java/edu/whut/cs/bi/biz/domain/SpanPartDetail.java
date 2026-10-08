package edu.whut.cs.bi.biz.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 5230-2026 桥跨×部件评定明细 bi_v2_span_part_detail
 *
 * <p>记录每个桥跨内每个部件的最差构件标度 CI、附录 B 原始权重 ω、
 * B.1.3 再分配后生效权重、结构层 γ，以及应设未设标记。</p>
 */
@Data
public class SpanPartDetail {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联检测任务ID */
    private Long taskId;

    /** 桥跨节点ID */
    private Long spanId;

    /** 部件节点ID */
    private Long partId;

    /** 结构层 key (deck/superstructure/substructure/ancillary) */
    private String layerKey;

    /** 部件 key */
    private String partKey;

    /** 部件最差构件标度 CI (0~5) */
    private Integer ci;

    /** 附录 B 原始权重 ω (0~100) */
    private Integer omegaOriginal;

    /** B.1.3 再分配后生效权重 */
    private BigDecimal omegaEffective;

    /** 结构层影响系数 γ */
    private BigDecimal gamma;

    /** 是否应设未设（幻影构件, 1=是） */
    private Integer absentFlag;
}
