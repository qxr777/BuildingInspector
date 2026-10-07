package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 病害标度（正式稿病害表的一个标度单元格）。
 * 定性/定量描述照录；能解析为干净数值区间的同时填结构化字段，否则留空，以文本为准。
 */
@Data
public class StdDiseaseScale {

    /** 标度（正式稿含 0 标度） */
    private Integer scale;

    /** 定性描述 */
    private String qualitative;

    /** 定量描述（照录原文） */
    private String quantitative;

    /** 结构化指标键（如 crack_width），为第 3 步数据驱动评定准备；可空 */
    private String metricKey;

    /** 区间下界（含），可空 */
    private BigDecimal valueLower;

    /** 区间上界（含），可空 */
    private BigDecimal valueUpper;

    /** 计量单位，可空 */
    private String unit;
}
