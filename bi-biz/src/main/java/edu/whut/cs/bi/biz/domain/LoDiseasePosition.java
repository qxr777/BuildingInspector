package edu.whut.cs.bi.biz.domain;

import lombok.Data;

import java.util.Date;

/**
 * 叶子部件-病害位置对象 bi_lo_disease_position
 * （position 直接存位置字符串，ref1/ref2 为参考面候选；不设位置字典）
 *
 * @author QiXin
 * @date 2026/10/15
 */
@Data
public class LoDiseasePosition {

    /** 主键 */
    private Long id;

    /** 叶子部件ID（bi_leaf_object.id） */
    private Long leafObjectId;

    /** 病害位置字符串（如：腹板/顶板支点附近） */
    private String position;

    /** 参考面1候选字符串（逗号分隔，如：顶面,底面,腹板） */
    private String ref1;

    /** 参考面2候选字符串（逗号分隔，如：上游侧,下游侧） */
    private String ref2;

    /** 创建时间 */
    private Date createTime;
}
