package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

/**
 * 部件定义（共享层或某桥型上部结构中的一个部件）。
 */
@Data
public class StdPartDef {

    /** 部件逻辑键，如 paving / bearing / slab */
    private String key;

    /** 部件名称 */
    private String name;

    /** 部件权重 ω（同层内合计 100） */
    private Integer omega;
}
