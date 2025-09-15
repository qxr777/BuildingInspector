package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

/**
 * 病害对比数据VO
 * 用于病害变化情况分析表格
 *
 * @author wanzheng
 */
@Data
public class DiseaseComparisonData {

    /** 桥梁名称(根节点名称) */
    private String bridgeName;

    /** 部位-第一列(第二层节点名称) */
    private String position1;

    /** 部位-第二列(第三层节点名称) */
    private String position2;

    /** 构件(第四层节点名称) */
    private String component;

    /** 病害种类(病害类型名称) */
    private String diseaseType;

    private int currentYear;

    private int lastYear;

    // ===== 2023年数据 =====
    /** 2023年数量(quantity之和) */
    private Integer quantity2023;

    /** 2023年病害程度描述 */
    private String severity2023;

    // ===== 2024年数据 =====
    /** 2024年数量(quantity之和) */
    private Integer quantity2024;

    /** 2024年病害程度描述 */
    private String severity2024;

    /** 发展情况(对比分析结果) */
    private String developmentStatus;

    /** 备注 */
    private String remarks;

    // ===== 用于表格合并的辅助字段 =====
    /** 是否是桥梁组的第一行 */
    private boolean isFirstInBridge;

    /** 是否是部位1组的第一行 */
    private boolean isFirstInPosition1;

    /** 是否是部位2组的第一行 */
    private boolean isFirstInPosition2;

    /** 是否是构件组的第一行 */
    private boolean isFirstInComponent;

    // ===== 用于合并计算的辅助字段 =====
    /** 桥梁组内的行数 */
    private int bridgeRowSpan;

    /** 部位1组内的行数 */
    private int position1RowSpan;

    /** 部位2组内的行数 */
    private int position2RowSpan;

    /** 构件组内的行数 */
    private int componentRowSpan;

    // ===== BiObject层级ID，用于数据查询 =====
    /** 根节点ID */
    private Long rootObjectId;

    /** 第二层节点ID */
    private Long level2ObjectId;

    /** 第三层节点ID */
    private Long level3ObjectId;

    /** 第四层节点ID */
    private Long level4ObjectId;

    /** 病害类型ID */
    private Long diseaseTypeId;
}