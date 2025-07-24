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
    private BigDecimal length1;
    private BigDecimal length2;
    private BigDecimal length3;

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

    // 面积标识符 区分“普通-0”、“平均-1”、“总计-2”
    private Integer areaIdentifier;

    // 变形/位移
    private BigDecimal deformation;

    // 角度
    private Integer angle;

    // 比例-分子
    private Integer numeratorRatio;

    // 比例-分母
    private Integer denominatorRatio;

    // 长度范围起点
    private BigDecimal lengthRangeStart;

    // 长度范围终点
    private BigDecimal lengthRangeEnd;

    // 宽度范围起点 （废弃）
    private BigDecimal widthRangeStart;

    // 宽度范围终点（废弃）
    private BigDecimal widthRangeEnd;

    // 高度/深度范围起点
    private BigDecimal heightDepthRangeStart;

    // 高度/深度范围终点
    private BigDecimal heightDepthRangeEnd;

    // 缝宽范围起点
    private BigDecimal crackWidthRangeStart;

    // 缝宽范围终点
    private BigDecimal crackWidthRangeEnd;

    // 面积范围起点（废弃）
    private BigDecimal areaRangeStart;

    // 面积范围终点（废弃）
    private BigDecimal areaRangeEnd;

    // 变形/位移范围起点
    private BigDecimal deformationRangeStart;

    // 变形/位移范围终点
    private BigDecimal deformationRangeEnd;

    // 角度范围起点
    private BigDecimal angleRangeStart;

    // 角度范围终点
    private BigDecimal angleRangeEnd;

    // 其他
    private String other;
}
