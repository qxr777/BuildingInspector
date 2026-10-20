package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * 该 PART 节点绑定的叶子部件 code 列表（一个 PART 可绑多个叶子）。
     * 缺省为空，表示按 1:1 绑定 key 同名叶子。
     * 例：B07 钢主梁 PART 绑 [steelMainGirder, mainGirder]（正式稿表 6.2.15/6.2.16）。
     */
    private List<String> leaves = new ArrayList<>();

    /** 实际生效的叶子 code 列表：显式 leaves 优先，否则退化为单值 [key]。 */
    public List<String> effectiveLeafCodes() {
        return leaves == null || leaves.isEmpty() ? Collections.singletonList(key) : leaves;
    }
}
