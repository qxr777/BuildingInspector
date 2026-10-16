package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 维修加固结构字典对象 bi_repair_strengthening_structure
 * （JTG/T 5230—2026 第13章：13.2 增大截面 / 13.3 粘贴钢板 / 13.4 粘贴纤维复合材料 / 13.5 增设体外预应力）
 *
 * @author QiXin
 * @date 2026/10/15
 */
@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class RepairStrengtheningStructure extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 加固结构编码 */
    private String code;

    /** 加固构造名称（标准13.2~13.5四类） */
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
