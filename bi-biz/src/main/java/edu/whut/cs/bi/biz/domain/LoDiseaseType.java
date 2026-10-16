package edu.whut.cs.bi.biz.domain;

import lombok.Data;

import java.util.Date;

/**
 * 叶子部件-病害类型绑定对象 bi_lo_disease_type
 *
 * @author QiXin
 * @date 2026/10/15
 */
@Data
public class LoDiseaseType {

    /** 主键 */
    private Long id;

    /** 叶子部件ID（bi_leaf_object.id） */
    private Long leafObjectId;

    /** 病害类型ID（bi_disease_type.id） */
    private Long diseaseTypeId;

    /** 创建时间 */
    private Date createTime;
}
