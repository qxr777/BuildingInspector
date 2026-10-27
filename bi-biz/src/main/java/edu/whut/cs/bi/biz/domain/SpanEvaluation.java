package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 桥跨技术状况评定结果 bi_span_evaluation（JTG/T 5230—2026 第 3.3 节）。
 *
 * <p>新标 5230 引擎复用表：基础列为 5.2 建表列，unit_id 为第八·十一部分补列；
 * bsci_raw/raw_level/override_flag/override_level/override_trigger 五列（9.1 节）
 * 为 3.3.3 覆盖留痕，冻结保留，由 5230 引擎重算先删后插。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpanEvaluation extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 桥跨节点ID（bi_object.id，SPAN） */
    private Long spanId;

    /** 关联检测任务 */
    private Long taskId;

    /** 评定单元节点ID（bi_object.id，UNIT；多评定单元 3.4.4） */
    private Long unitId;

    /** 桥型代码 */
    private String bridgeType;

    /** 桥跨技术状况指数 BSCI（最终） */
    private BigDecimal bsci;

    /** 桥跨等级（1~5） */
    private Integer spanLevel;

    /** EDI 分项（留痕可空） */
    private BigDecimal ediScore;

    /** EFI 分项（留痕可空） */
    private BigDecimal efiScore;

    /** EAI 分项（留痕可空） */
    private BigDecimal eaiScore;

    /** 评定标准版本 */
    private String stdVersion;

    // ---- 9.1 节 3.3.3 覆盖留痕五列（冻结） ----

    /** 覆盖前 BSCI 公式原值 */
    private BigDecimal bsciRaw;

    /** 覆盖前等级 */
    private Integer rawLevel;

    /** 是否触发覆盖（0/1） */
    private Integer overrideFlag;

    /** 覆盖后等级 */
    private Integer overrideLevel;

    /** 覆盖触发构件与依据 */
    private String overrideTrigger;
}
