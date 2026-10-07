package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/4/9
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DiseaseScale extends BaseEntity {

    /**
     * 该类用于表示病害标度的相关信息，对应 《JTGT H21-2011公路桥梁技术状况评定标准》 中的大量病害类型表。
     * <p>
     * 包含以下属性：
     * - id: 唯一标识符，用于区分不同的病害标度。
     * - diseaseTypeId: 病害类型的唯一标识符，用于关联病害类型。
     * - scale: 病害的严重程度等级，通常用整数表示。
     * - qualitativeDescription: 病害的定性描述，提供对病害性质的非量化描述。
     * - quantitativeDescription: 病害的定量描述，提供对病害性质的量化描述。
     */
    private Long id; // 唯一标识符

    private String typeCode; // 病害类型编码

    private Integer scale; // 病害的严重程度等级

    private String qualitativeDescription; // 病害的定性描述

    private String quantitativeDescription; // 病害的定量描述

    /**
     * 病害类型状态（0正常 1停用）
     */
    private String status;

    /**
     * 所属标准版本（如 H21-2011 / 5230-2026）；为空视同旧标准 H21-2011
     */
    private String stdVersion;

    /**
     * 结构化指标键（如 crack_width），为数据驱动评定准备；可空
     */
    private String metricKey;

    /**
     * 结构化区间下界（含），可空
     */
    private BigDecimal valueLower;

    /**
     * 结构化区间上界（含），可空
     */
    private BigDecimal valueUpper;

    /**
     * 计量单位，可空
     */
    private String unit;
}
