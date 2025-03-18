package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Attachment extends BaseEntity{

    /** 附件id */
    private Long id;
    /** 附件名称 */
    private String name;
    /** 关联主体id */
    private Long subjectId;
    /** 附件类型（1为病害附件，2为设备附件） */
    private Integer type;

    /**
     * TODO: 附件存储在MinIO 中
     */

}