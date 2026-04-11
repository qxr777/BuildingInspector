package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 病害位置字典实体
 * 对应表 bi_disease_position
 *
 * @author QiXin
 * @date 2026/04/11
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DiseasePosition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 位置名称（如：顶面、底面、侧面） */
    private String name;

    /** 位置编码 */
    private String code;

    /** 附加属性（JSON） */
    private String props;

    /** 参考面1 */
    private String ref1;

    /** 参考面2 */
    private String ref2;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;
}
