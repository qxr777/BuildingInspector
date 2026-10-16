package edu.whut.cs.bi.biz.domain;

import lombok.Data;

import java.util.Date;

/**
 * 维修加固结构-病害类型绑定对象 bi_rss_disease_type
 *
 * @author QiXin
 * @date 2026/10/15
 */
@Data
public class RssDiseaseType {

    /** 主键 */
    private Long id;

    /** 维修加固结构ID（bi_repair_strengthening_structure.id） */
    private Long repairStrengtheningStructureId;

    /** 病害类型ID（bi_disease_type.id） */
    private Long diseaseTypeId;

    /** 创建时间 */
    private Date createTime;
}
