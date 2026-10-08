package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.math.BigDecimal;

/**
 * 全桥技术状况评定结果 bi_bridge_evaluation（新标 JTG/T 5230—2026）
 *
 * <p>最终 BCL/BCI（含单项控制覆盖），同时保留分层综合结果（bclLayered/bciLayered）、
 * 多跨聚合参数（min/max/mean/lim、calcMode）与命中的单项控制项。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BridgeEvaluation extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联检测任务ID */
    private Long taskId;

    /** 评定单元 UNIT 节点ID */
    private Long rootObjectId;

    /** 桥型代码 */
    private String bridgeType;

    /** 全桥技术状况指数 BCI（最终） */
    private BigDecimal bci;

    /** 全桥技术状况等级 (1~5，含单项控制) */
    private Integer bcl;

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

    /** 分层综合评定等级（单项控制覆盖前） */
    private Integer bclLayered;

    /** 分层综合评定指数 */
    private BigDecimal bciLayered;

    /** 单项控制是否命中 (1=命中) */
    private Integer singleControlHit;

    /** 命中的单项控制项序号（逗号分隔） */
    private String singleControlNos;

    /** 最差桥跨节点ID */
    private Long worstSpanId;

    /** 评定标准版本 */
    private String stdVersion;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("taskId", getTaskId())
            .append("bcl", getBcl())
            .append("bci", getBci())
            .append("singleControlHit", getSingleControlHit())
            .toString();
    }
}
