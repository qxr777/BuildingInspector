package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 5230 全桥评定结果 bi_assess_bridge_result
 * （JTG/T 5230—2026 第 3.4.4/3.4.5 条；每次重算按 task_id 先删后插）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssessBridgeResult extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 检测任务ID（bi_task.id） */
    private Long taskId;

    /** 桥梁新树根节点ID（bi_building.new_root_object_id） */
    private Long rootObjectId;

    /** 全桥技术状况指数 BCI（最终） */
    private BigDecimal bci;

    /** 全桥技术状况等级 1~5（最终） */
    private Integer bcl;

    /** 单元合成：SINGLE_UNIT 单单元 / WORST_UNIT 多单元最差（3.4.4） */
    private String combineMode;

    /** 最差评定单元节点ID（多单元时） */
    private Long worstUnitId;

    /** 单项控制覆盖前分层 BCI（3.4.5 留痕） */
    private BigDecimal layeredBci;

    /** 单项控制覆盖前分层等级 */
    private Integer layeredBcl;

    /** 命中的单项控制编号逗号串（如 3,7） */
    private String singleControlHits;

    /** 评定标准版本 */
    private String stdVersion;
}
