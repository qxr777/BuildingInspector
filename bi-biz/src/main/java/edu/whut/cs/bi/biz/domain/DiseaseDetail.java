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

    // 面积
    private BigDecimal area;

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

    // 缝宽范围起点
    private BigDecimal crackWidthRangeStart;

    // 缝宽范围终点
    private BigDecimal crackWidthRangeEnd;

}
