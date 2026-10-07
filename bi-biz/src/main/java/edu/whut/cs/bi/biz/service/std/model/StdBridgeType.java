package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

/**
 * 桥型定义（meta.yml 中 bridgeTypes 的一项）。
 */
@Data
public class StdBridgeType {

    /** 桥型代码，如 B01 */
    private String code;

    /** 桥型名称 */
    private String name;

    /** 桥型大类，如 梁桥/拱桥/刚构桥/缆索承重桥 */
    private String category;

    /** 本批是否已完成结构树（false 时仅落根节点桩，停用不可选） */
    private boolean structureComplete;
}
