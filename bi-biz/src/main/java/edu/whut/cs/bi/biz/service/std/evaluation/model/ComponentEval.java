package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

/**
 * 构件技术状况评定结果（JTG/T 5230—2026 第 3.2 节，五分项 + ECI/ECL）。
 *
 * <p>同时承载落库 bi_assess_element_result 所需的结构锚点与来源标记；
 * 共用构件在所属每个桥跨各产生一个 ComponentEval。</p>
 */
@Data
public class ComponentEval {

    /** 评定单元节点ID */
    private Long unitId;

    /** 桥跨节点ID */
    private Long spanId;

    /** 部件节点ID（幻影构件为 null） */
    private Long partId;

    /** 部件键 */
    private String partKey;

    /** 结构层键 */
    private String layerKey;

    /** 构件ID（幻影构件为 null） */
    private Long elementId;

    /** 构件离线UUID（幻影构件为 null） */
    private String elementUuid;

    /** 构件名称 */
    private String elementName;

    /** 是否主要部件构件 */
    private boolean mainPart;

    /** 是否共用构件 */
    private boolean shared;

    /** 是否应设未设幻影构件（3.2.2-5） */
    private boolean absent;

    /** 耐久性缺损标度 EDDI (0~2) */
    private int eddi;

    /** 结构性缺损标度 ESDI (0~3) */
    private int esdi;

    /** 缺损状况标度 EDI=max(EDDI,ESDI) (0~3) */
    private int edi;

    /** 功能状况标度 EFI (0~2) */
    private int efi;

    /** 影响状况标度 EAI (-1/0/1) */
    private int eai;

    /** 构件技术状况标度 ECI (0~5) */
    private int eci;

    /** 构件技术状况等级 ECL (1~5，表 3.2.6) */
    private int ecl;

    /** EDDI 来源：AUTO（缺省0/未来字典自动）/ MANUAL（人工录入） */
    private String eddiSource;

    /** ESDI 来源：AUTO（病害 max）/ MANUAL（人工覆盖）/ ABSENT（应设未设幻影） */
    private String esdiSource;

    /** 人工标记的缺损是否影响桥梁安全（3.3.3） */
    private boolean safetyAffected;

    public static final String SOURCE_AUTO = "AUTO";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_ABSENT = "ABSENT";
}
