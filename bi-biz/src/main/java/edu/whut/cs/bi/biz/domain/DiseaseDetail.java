package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class DiseaseDetail extends BaseEntity {

    // 主键Id
    private Long id;

    // 病害id
    private Long diseaseId;

    // 裂缝特征 (纵向、横向、斜向、L型、U型）
    private String crackType;

    // 参考面1位置
    private String reference1Location;

    // 距参考面1位置-起始位置
    private BigDecimal reference1LocationStart;

    // 距参考面1位置-终点位置
    private BigDecimal reference1LocationEnd;

    // 参考面2位置
    private String reference2Location;

    // 距参考面2位置-起始位置
    private BigDecimal reference2LocationStart;

    // 距参考面2位置-终点位置
    private BigDecimal reference2LocationEnd;

    // 长度
    private BigDecimal length;

    // 宽度
    private BigDecimal width;

    // 高度/深度
    private BigDecimal heightDepth;

    // 缝宽
    private BigDecimal crackWidth;

    // 面积_长
    private BigDecimal areaLength;

    // 面积_宽
    private BigDecimal areaWidth;

    // 体积
    private BigDecimal volume;

    // 角度
    private Integer angle;

    // 百分比
    private BigDecimal percentage;

    // 发展趋势 （稳定、发展、新增、已维修)
    private String developmentTrend;

    // 长度范围起点
    private BigDecimal lengthRangeStart;

    // 长度范围终点
    private BigDecimal lengthRangeEnd;

    // 宽度范围起点
    private BigDecimal widthRangeStart;

    // 宽度范围终点
    private BigDecimal widthRangeEnd;

    // 高度/深度范围起点
    private BigDecimal heightDepthRangeStart;

    // 高度/深度范围终点
    private BigDecimal heightDepthRangeEnd;

    // 缝宽范围起点
    private BigDecimal crackWidthRangeStart;

    // 缝宽范围终点
    private BigDecimal crackWidthRangeEnd;

    // 面积范围起点
    private BigDecimal areaRangeStart;

    // 面积范围终点
    private BigDecimal areaRangeEnd;

    // 体积范围起点
    private BigDecimal volumeRangeStart;

    // 体积范围终点
    private BigDecimal volumeRangeEnd;

    // 角度范围起点
    private BigDecimal angleRangeStart;

    // 角度范围终点
    private BigDecimal angleRangeEnd;

    // 百分比范围起点
    private BigDecimal percentageRangeStart;

    // 百分比范围终点
    private BigDecimal percentageRangeEnd;
}
