package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 正式稿 JTG/T 5230—2026 权威目录：由 classpath std/5230-2026/*.yml 装配。
 * 是种子写库、App 模板包生成的唯一数据出口。
 */
@Data
public class StdCatalog {

    /** 标准版本标识，落库 std_version，如 5230-2026 */
    private String stdVersion;

    /** 标准名称 */
    private String name;

    /** 标准编号 */
    private String code;

    /** 发布日期 */
    private String publishedDate;

    /** 实施日期 */
    private String effectiveDate;

    /** 被替代标准 */
    private String replaces;

    /** 21 个桥型 */
    private List<StdBridgeType> bridgeTypes = new ArrayList<>();

    /** 病害类型（含标度） */
    private List<StdDiseaseType> diseaseTypes = new ArrayList<>();

    /** 可复用共享结构层（桥面系/下部结构/附属设施） */
    private List<StdLayerDef> sharedLayers = new ArrayList<>();

    /** 桥型模板定义（B01…），键为桥型代码 */
    private Map<String, StdBridgeTemplate> bridgeTemplates = new LinkedHashMap<>();

    /** 展开后的全部模板节点（含 B08–B21 根桩） */
    private List<StdTemplateNode> nodes = new ArrayList<>();

    /** 各桥型主要部件 key（表 3.3.3），键为桥型代码 */
    private Map<String, List<String>> mainParts = new LinkedHashMap<>();
}
