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
    /** 附件类型（null为病害附件，2为设备附件 , 5 为标准文档 6正立面照片 7病害ad图片 1普通病害图片） */
    private Integer type;
    /** minio 的文件唯一标识 */
    private Long minioId;



    /**
     * TODO: 附件存储在MinIO 中
     */

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getMinioId() {
        return minioId;
    }

    public void setMinioId(Long minioId) {
        this.minioId = minioId;
    }
}