package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 5230 构件级评定结果明细 bi_assess_element_result
 * （JTG/T 5230—2026 第 3.2 节；每次重算按 task_id 先删后插）。
 *
 * <p>共用构件在所属每个桥跨各一行；应设未设幻影件 part_id/element_uuid 为空、absent_flag=1。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssessElementResult extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 检测任务ID（bi_task.id） */
    private Long taskId;

    /** 评定单元节点ID（bi_object.id，UNIT） */
    private Long unitId;

    /** 桥跨节点ID（bi_object.id，SPAN） */
    private Long spanId;

    /** 部件节点ID（bi_object.id，PART；幻影件为空） */
    private Long partId;

    /** 部件键（模板 partKey） */
    private String partKey;

    /** 结构层键（deck/superstructure/substructure/ancillary） */
    private String layerKey;

    /** 构件离线UUID（幻影件为空） */
    private String elementUuid;

    /** 构件名称 */
    private String elementName;

    /** 是否主要部件构件（表 3.3.3 清单） */
    private Integer isMain;

    /** 是否共用构件（bi_span_element_part.is_shared） */
    private Integer isShared;

    /** 是否应设未设幻影构件（3.2.2-5） */
    private Integer absentFlag;

    /** EDDI 耐久性标度 */
    private Integer eddi;

    /** ESDI 结构性标度 */
    private Integer esdi;

    /** EDI=max(EDDI,ESDI)；幻影件固定 3 */
    private Integer edi;

    /** EFI 功能标度 */
    private Integer efi;

    /** EAI 影响标度(-1/0/1) */
    private Integer eai;

    /** ECI=EDI+EFI+EAI（截断到[0,5]） */
    private Integer eci;

    /** ECL 构件等级 1~5（表 3.2.6） */
    private Integer ecl;

    /** EDDI 来源：AUTO/MANUAL */
    private String eddiSource;

    /** ESDI 来源：AUTO/MANUAL/ABSENT */
    private String esdiSource;
}
