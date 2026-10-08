package edu.whut.cs.bi.biz.domain;

import lombok.Data;

/**
 * 5230-2026 构件技术状况评定结果 bi_v2_component_evaluation
 *
 * <p>构件五分项（ESDI/EDDI/EDI/EFI/EAI）、合成标度 ECI 与等级 ECL。</p>
 */
@Data
public class ComponentEvaluation {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联检测任务ID */
    private Long taskId;

    /** 构件ID */
    private Long componentId;

    /** 所属部件 key */
    private String partKey;

    /** 结构性缺损标度 ESDI (0~3) */
    private Integer esdi;

    /** 耐久性缺损标度 EDDI (0~2) */
    private Integer eddi;

    /** 缺损状况标度 EDI (0~3) */
    private Integer edi;

    /** 功能状况标度 EFI (0~2) */
    private Integer efi;

    /** 影响状况标度 EAI (-1/0/1) */
    private Integer eai;

    /** 构件技术状况标度 ECI (0~5) */
    private Integer eci;

    /** 构件技术状况等级 ECL (1~5) */
    private Integer ecl;
}
