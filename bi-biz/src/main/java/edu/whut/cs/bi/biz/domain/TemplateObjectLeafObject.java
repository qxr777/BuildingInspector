package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 桥型模板 PART 节点-叶子部件字典多对多绑定 bi_template_object_leaf_object
 *
 * <p>2026 新标（JTG/T 5230—2026）：桥型模板（bi_template_object）中一个 PART 节点
 * 可对应多个附录B 叶子部件（bi_leaf_object）。App 离线录入时，由实例部件节点的
 * template_object_id 经本表取该部件全部叶子，再查各叶子的病害类型/位置/材料/加固绑定。
 * 随 common.db 下发。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateObjectLeafObject extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 模板 PART 节点ID (bi_template_object.id, node_type=PART) */
    private Long templateObjectId;

    /** 叶子部件字典ID (bi_leaf_object.id) */
    private Long leafObjectId;
}
