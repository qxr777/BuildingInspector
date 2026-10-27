package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 全桥技术状况结果（JTG/T 5230—2026 第 3.4.4、3.4.5 条）。
 *
 * <p>先按 3.4.4 以最差评定单元结果为全桥分层结果（单单元直通），
 * 再按 3.4.5 应用 16 项单项控制覆盖：任一命中则 BCL=5、BCI=min(分层BCI,40)。</p>
 */
@Data
public class BridgeAggregate {

    /** 单元合成方式 SINGLE_UNIT/WORST_UNIT（3.4.4） */
    private String combineMode;

    /** 最差评定单元节点ID（多单元时） */
    private Long worstUnitId;

    /** 分层综合等级（3.4.5 覆盖前） */
    private int layeredBcl;

    /** 分层综合 BCI（3.4.5 覆盖前） */
    private BigDecimal layeredBci;

    /** 最终全桥等级 */
    private int bcl;

    /** 最终全桥 BCI */
    private BigDecimal bci;

    /** 单项控制是否命中 */
    private boolean singleControlHit;

    /** 命中的单项控制编号（升序逗号串，如 "3,7"） */
    private String singleControlNos;

    /** 命中编号列表（升序） */
    private List<Integer> hitIndicatorNos;
}
