package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 解析后的部件实例（桥跨 × 部件）。
 *
 * <p>承载附录 B 原始权重 ω、结构层 γ；CI 在构件聚合阶段填入，
 * omegaEffective 在 B.1.3 权重再分配阶段填入。</p>
 */
@Data
public class ResolvedPart {

    /** 部件节点ID */
    private Long partId;

    /** 部件 key */
    private String partKey;

    /** 所属结构层 key */
    private String layerKey;

    /** 附录 B 原始权重 ω */
    private Integer omega;

    /** 结构层影响系数 γ */
    private BigDecimal gamma;

    /** 部件最差构件标度 CI（含幻影构件） */
    private int ci;

    /** B.1.3 再分配后生效权重 */
    private BigDecimal omegaEffective;

    /** 是否应设未设（幻影构件） */
    private boolean absent;
}
