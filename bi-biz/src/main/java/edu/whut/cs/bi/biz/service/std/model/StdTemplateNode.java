package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 展开后的模板节点：已是“某桥型命名空间”下的实际节点，可直接按 nodeCode 幂等写库。
 * 父链（parentCode）在同桥型内自顶向下，写库时解析为数字 id。
 */
@Data
public class StdTemplateNode {

    /** 版本内自然键，如 B01.part.slab */
    private String nodeCode;

    /** 节点名称 */
    private String name;

    /** 父节点自然键；根节点为 null */
    private String parentCode;

    /** 同级排序 */
    private Integer orderNum;

    /** 节点角色：ROOT / LAYER / PART */
    private String role;

    /** 所属桥型；根/层/部件都回填，便于过滤 */
    private String bridgeType;

    /** 结构层影响系数 γ（仅 LAYER） */
    private BigDecimal gamma;

    /** 部件权重 ω（仅 PART） */
    private Integer omega;

    /** 类别（i），可空 */
    private String categoryI;

    /** props JSON 原文 */
    private String props;

    /** 状态（0 正常 1 停用） */
    private String status;

    /** 该节点绑定的病害类型代码（仅 PART，已按逻辑部件键解析） */
    private List<String> diseaseTypeCodes = new ArrayList<>();
}
