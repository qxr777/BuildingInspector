package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 单个构件的评定输入（JTG/T 5230—2026 第 3.2 节）。
 *
 * <p>纯计算输入模型，与持久化 domain 解耦：由服务层从 bi_element / bi_span_element_part /
 * bi_disease（新链 element_uuid 锚定）/ bi_assess_manual_input 装配。应设未设部件在
 * 3.2.2-5 下没有构件实例，以 {@code absent=true} 的幻影输入表达。</p>
 */
@Data
@Builder
public class ComponentInput {

    /** 构件ID（幻影构件为 null） */
    private Long elementId;

    /** 构件离线UUID（bi_element.offline_uuid；幻影构件为 null） */
    private String elementUuid;

    /** 构件名称 */
    private String elementName;

    /** 评定单元节点ID（bi_object.id，UNIT） */
    private Long unitId;

    /** 桥跨节点ID（bi_object.id，SPAN） */
    private Long spanId;

    /** 部件节点ID（bi_object.id，PART；幻影构件为 null） */
    private Long partId;

    /** 部件键（模板 partKey） */
    private String partKey;

    /** 结构层键（deck/superstructure/substructure/ancillary） */
    private String layerKey;

    /** 是否主要部件构件（表 3.3.3 清单） */
    private boolean mainPart;

    /** 是否共用构件（相邻跨/拼宽共用，每跨各计一次） */
    private boolean shared;

    /** 是否应设未设幻影构件（3.2.2-5，EDI 直接取 3） */
    private boolean absent;

    /**
     * 参与评定的结构性病害标度序列（ESDI 取 max，封顶 3）。
     * 服务层已过滤：participate_assess='1'、病害类型 std_version='5230-2026'、scale_type='EDI'。
     */
    private List<Integer> structuralScales;

    /** 人工 EDDI 耐久性标度 0/1/2（3.2.2；null 视同未录入按 0，来源记 AUTO） */
    private Integer eddiManual;

    /** 人工 ESDI 覆盖标 0~3（3.2.3；非 null 时覆盖病害自动 max） */
    private Integer esdiOverride;

    /** 人工 EFI 功能标度 0/1/2（3.2.4；null 按 0） */
    private Integer efiManual;

    /** 人工 EAI 影响标度 -1/0/1（3.2.5；仅主要构件可取非 0） */
    private Integer eaiManual;

    /** 是否影响桥梁安全（3.3.3 人工判定；true 且本构件 ECL 4/5 时触发桥跨覆盖） */
    private Boolean safetyAffected;
}
