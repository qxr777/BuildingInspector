package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 病害详情对象 bi_disease_detail
 * 
 * @author QiXin
 * @date 2025/3/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DiseaseDetail extends BaseEntity {

    /** 步长 */
    private Integer step;

    /** 详情id */
    private Long id;

    /** 病害id */
    private Long diseaseId;

    /** 参考面1位置 */
    private String reference1Location;

    /** 参考面1起始位置 */
    private BigDecimal reference1LocationStart;

    /** 参考面1结束位置 */
    private BigDecimal reference1LocationEnd;

    /** 参考面2位置 */
    private String reference2Location;

    /** 参考面2起始位置 */
    private BigDecimal reference2LocationStart;

    /** 参考面2结束位置 */
    private BigDecimal reference2LocationEnd;

    /** 长度1 */
    private BigDecimal length1;

    /** 长度2 */
    private BigDecimal length2;

    /** 长度3 */
    private BigDecimal length3;

    /** 宽度 */
    private BigDecimal width;

    /** 高度/深度 */
    private BigDecimal heightDepth;

    /** 裂缝宽度 */
    private BigDecimal crackWidth;

    /** 面积-长度 */
    private BigDecimal areaLength;

    /** 面积-宽度 */
    private BigDecimal areaWidth;

    /** 面积标识符 */
    private Integer areaIdentifier;

    /** 变形 */
    private BigDecimal deformation;

    /** 角度 */
    private Integer angle;

    /** 比例分子 */
    private Integer numeratorRatio;

    /** 比例分母 */
    private Integer denominatorRatio;

    /** 长度分段起始 */
    private BigDecimal lengthRangeStart;

    /** 长度分段结束 */
    private BigDecimal lengthRangeEnd;

    /** 宽度分段起始 */
    private BigDecimal widthRangeStart;

    /** 宽度分段结束 */
    private BigDecimal widthRangeEnd;

    /** 高度/深度分段起始 */
    private BigDecimal heightDepthRangeStart;

    /** 高度/深度分段结束 */
    private BigDecimal heightDepthRangeEnd;

    /** 裂缝宽度分段起始 */
    private BigDecimal crackWidthRangeStart;

    /** 裂缝宽度分段结束 */
    private BigDecimal crackWidthRangeEnd;

    /** 面积分段起始 */
    private BigDecimal areaRangeStart;

    /** 面积分段结束 */
    private BigDecimal areaRangeEnd;

    /** 变形分段起始 */
    private BigDecimal deformationRangeStart;

    /** 变形分段结束 */
    private BigDecimal deformationRangeEnd;

    /** 角度分段起始 */
    private BigDecimal angleRangeStart;

    /** 角度分段结束 */
    private BigDecimal angleRangeEnd;

    /** 长度最小值 (保留兼容) */
    private BigDecimal lengthMin;

    /** 长度最大值 (保留兼容) */
    private BigDecimal lengthMax;

    /** 长度分段 (保留兼容) */
    private String lengthRange;

    /** 宽度最小值 (保留兼容) */
    private BigDecimal widthMin;

    /** 宽度最大值 (保留兼容) */
    private BigDecimal widthMax;

    /** 宽度分段 (保留兼容) */
    private String widthRange;

    /** 深度最小值 (保留兼容) */
    private BigDecimal depthMin;

    /** 深度最大值 (保留兼容) */
    private BigDecimal depthMax;

    /** 深度分段 (保留兼容) */
    private String depthRange;

    /** 面积最小值 (保留兼容) */
    private BigDecimal areaMin;

    /** 面积最大值 (保留兼容) */
    private BigDecimal areaMax;

    /** 面积分段 (保留兼容) */
    private String areaRange;

    /** 其他 */
    private String other;

    /** 离线记录唯一标识(UUID) */
    private String offlineUuid;

    /** 所属病害离线UUID */
    @com.alibaba.fastjson.annotation.JSONField(name = "diseaseUuid")
    @com.fasterxml.jackson.annotation.JsonProperty("diseaseUuid")
    private String diseaseUuid;

    /** 是否为离线同步数据 (0:否, 1:是) */
    private Integer isOfflineData;
}
