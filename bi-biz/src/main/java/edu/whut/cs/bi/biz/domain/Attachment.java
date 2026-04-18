package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Attachment extends BaseEntity{

    /** 附件id */
    private Long id;
    /** 附件名称 */
    private String name;
    /** 关联主体id */
    private Long subjectId;
    /** 附件类型（null为病害附件，2为设备附件 , 5 为标准文档 6正立面照片 7病害ad图片 1普通病害图片 8部件当前照片） */
    private Integer type;
    /** minio 的文件唯一标识 */
    private Long minioId;
    /** 缩略图minioId */
    private Long thumbMinioId;

    /** 离线记录唯一标识(UUID) */
    private String offlineUuid;

    /** 关联主体的离线UUID */
    private String offlineSubjectUuid;

    /** 是否为离线同步数据 (0:否, 1:是) */
    private Integer isOfflineData;

    /** 是否被App离线删除标记 (0:否, 1:是) */
    private Integer offlineDeleted;

    /**
     * TODO: 附件存储在MinIO 中
     */

}