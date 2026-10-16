package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 叶子部件字典对象 bi_leaf_object
 * （JTG/T 5230—2026 附录B 各桥型末端部件，跨桥型去重；bi_object.leaf_object_id 引用）
 *
 * @author QiXin
 * @date 2026/10/15
 */
@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class LeafObject extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 叶子部件编码（对齐种子 parts 键，如 slab/bearing/pierAbutment） */
    private String code;

    /** 叶子部件名称（如：支座/桥墩或桥台/墩台基础） */
    private String name;

    /** 所属结构层：deck 桥面系/superstructure 上部结构/substructure 下部结构/ancillary 附属构造 */
    private String structureLayer;

    /** 显示顺序 */
    private Integer orderNum;

    /** 所属标准版本 */
    private String stdVersion;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;
}
