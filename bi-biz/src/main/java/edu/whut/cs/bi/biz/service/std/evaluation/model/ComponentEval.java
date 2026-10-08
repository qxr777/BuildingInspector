package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

/**
 * 构件技术状况评定中间结果（五分项 + ECI/ECL）。
 */
@Data
public class ComponentEval {

    /** 构件ID */
    private Long componentId;

    /** 所属部件 key */
    private String partKey;

    /** 结构性缺损标度 ESDI (0~3) */
    private int esdi;

    /** 耐久性缺损标度 EDDI (0~2) */
    private int eddi;

    /** 缺损状况标度 EDI (0~3) */
    private int edi;

    /** 功能状况标度 EFI (0~2) */
    private int efi;

    /** 影响状况标度 EAI (-1/0/1) */
    private int eai;

    /** 构件技术状况标度 ECI (0~5) */
    private int eci;

    /** 构件技术状况等级 ECL (1~5) */
    private int ecl;
}
