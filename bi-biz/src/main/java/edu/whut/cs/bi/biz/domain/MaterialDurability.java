package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 材料耐久性分类字典对象 bi_material_durability
 * （JTG/T 5230—2026 第4章：4.1 配筋混凝土构件 / 4.2 钢构件 / 4.3 圬工构件）
 *
 * @author QiXin
 * @date 2026/10/15
 */
@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class MaterialDurability extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 材料分类编码 */
    private String code;

    /** 材料耐久性分类名称 */
    private String name;

    /** 显示顺序 */
    private Integer orderNum;

    /** 所属标准版本 */
    private String stdVersion;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;
}
