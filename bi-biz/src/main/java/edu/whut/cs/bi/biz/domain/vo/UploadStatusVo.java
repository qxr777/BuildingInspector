package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 通道A 附件已传回查结果（响应丢失后 App 跳过重传用）
 */
@Data
public class UploadStatusVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户端幂等键 */
    private String uploadUuid;

    /** 是否已成功登记 */
    private Boolean uploaded;

    /** 原图 bi_file_map.id，未登记时为 null */
    private Long minioId;

    /** 缩略图 bi_file_map.id，未登记或未生成时为 null */
    private Long thumbMinioId;
}
