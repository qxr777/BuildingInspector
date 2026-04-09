package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DiseaseDetail extends BaseEntity {

    /**
     * 步长
     */
    private Integer step;

    /**
     * 详情id
     */
    private Long id;

    /**
     * 病害id
     */
    private Long diseaseId;

    /**
     * 长度最小值
     */
    private BigDecimal lengthMin;

    /**
     * 长度最大值
     */
    private BigDecimal lengthMax;

    /**
     * 长度分段
     */
    private String lengthRange;

    /**
     * 宽度最小值
     */
    private BigDecimal widthMin;

    /**
     * 宽度最大值
     */
    private BigDecimal widthMax;

    /**
     * 宽度分段
     */
    private String widthRange;

    /**
     * 深度最小值
     */
    private BigDecimal depthMin;

    /**
     * 深度最大值
     */
    private BigDecimal depthMax;

    /**
     * 深度分段
     */
    private String depthRange;

    /**
     * 面积最小值
     */
    private BigDecimal areaMin;

    /**
     * 面积最大值
     */
    private BigDecimal areaMax;

    /**
     * 面积分段
     */
    private String areaRange;

    /**
     * 角度
     */
    private BigDecimal angle;

    /**
     * 角度分段开始
     */
    private BigDecimal angleRangeStart;

    /**
     * 角度分段结束
     */
    private BigDecimal angleRangeEnd;

    /**
     * 其他
     */
    private String other;

    /** 离线记录唯一标识(UUID) */
    private String offlineUuid;

    /** 是否为离线同步数据 (0:否, 1:是) */
    private Integer isOfflineData;
}
