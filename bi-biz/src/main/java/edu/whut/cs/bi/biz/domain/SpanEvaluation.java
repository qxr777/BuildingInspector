package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.math.BigDecimal;

/**
 * 桥跨技术状况评定 bi_span_evaluation（新标 JTG/T 5230—2026）
 *
 * <p>记录每个桥跨的 BSCI、等级与构件三分项；当主要构件触发 3.3.3 覆盖时，
 * 保留公式原值（bsciRaw/rawLevel）与覆盖信息（overrideXxx）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpanEvaluation extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 桥跨节点ID (bi_object.id) */
    private Long spanId;

    /** 关联检测任务ID */
    private Long taskId;

    /** 桥型代码 */
    private String bridgeType;

    /** 桥跨技术状况指数 BSCI（最终，含 3.3.3 覆盖） */
    private BigDecimal bsci;

    /** 桥跨等级 (1~5) */
    private Integer spanLevel;

    /** EDI 分项 */
    private BigDecimal ediScore;

    /** EFI 分项 */
    private BigDecimal efiScore;

    /** EAI 分项 */
    private BigDecimal eaiScore;

    /** 3.3.3 覆盖前 BSCI 公式原值 */
    private BigDecimal bsciRaw;

    /** 覆盖前由公式原值判定的等级 */
    private Integer rawLevel;

    /** 是否触发 3.3.3 覆盖 (1=是, 0=否) */
    private Integer overrideFlag;

    /** 覆盖后桥跨等级 */
    private Integer overrideLevel;

    /** 覆盖触发构件与依据说明 */
    private String overrideTrigger;

    /** 评定标准版本 */
    private String stdVersion;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("spanId", getSpanId())
            .append("taskId", getTaskId())
            .append("bsci", getBsci())
            .append("spanLevel", getSpanLevel())
            .append("overrideFlag", getOverrideFlag())
            .toString();
    }
}
