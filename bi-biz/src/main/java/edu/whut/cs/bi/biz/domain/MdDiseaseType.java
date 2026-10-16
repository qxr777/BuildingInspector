package edu.whut.cs.bi.biz.domain;

import lombok.Data;

import java.util.Date;

/**
 * 材料耐久性-病害类型绑定对象 bi_md_disease_type
 *
 * @author QiXin
 * @date 2026/10/15
 */
@Data
public class MdDiseaseType {

    /** 主键 */
    private Long id;

    /** 材料耐久性分类ID（bi_material_durability.id） */
    private Long materialDurabilityId;

    /** 病害类型ID（bi_disease_type.id） */
    private Long diseaseTypeId;

    /** 创建时间 */
    private Date createTime;
}
