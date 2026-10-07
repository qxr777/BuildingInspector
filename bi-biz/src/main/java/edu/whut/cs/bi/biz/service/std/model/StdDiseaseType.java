package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 病害类型（正式稿某个部件病害表中的一行 / 一个评定指标）。
 */
@Data
public class StdDiseaseType {

    /** 病害类型代码（版本内唯一） */
    private String code;

    /** 病害（评定指标）名称 */
    private String name;

    /** 所属部件的逻辑键，可同时属于多个部件（如完全相同的跨型通用缺损） */
    private List<String> parts = new ArrayList<>();

    /** 最小标度（"—"不适用时抬高下界） */
    private Integer minScale;

    /** 最大标度 */
    private Integer maxScale;

    /** 同类病害合并阈值，可空 */
    private Integer threshold;

    /** App 勾选字段位掩码（参考面/长度/面积等），可空 */
    private Integer selectColumn;

    /** 各标度定义 */
    private List<StdDiseaseScale> scales = new ArrayList<>();
}
