package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 通道A 单个附件幂等预上传结果
 */
@Data
public class UploadItemResultVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 状态：success 本次新传成功 / exists 幂等命中未重复上传 / failed 本项失败 */
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_EXISTS = "exists";
    public static final String STATUS_FAILED = "failed";

    /** 客户端幂等键（归一化后，去横线小写） */
    private String uploadUuid;

    /** 原图 bi_file_map.id */
    private Long minioId;

    /** 缩略图 bi_file_map.id，未生成时为 null */
    private Long thumbMinioId;

    /** success / exists / failed */
    private String status;

    /** status=failed 时的中文原因 */
    private String error;

    public static UploadItemResultVo success(String uploadUuid, Long minioId, Long thumbMinioId, boolean exists) {
        UploadItemResultVo vo = new UploadItemResultVo();
        vo.setUploadUuid(uploadUuid);
        vo.setMinioId(minioId);
        vo.setThumbMinioId(thumbMinioId);
        vo.setStatus(exists ? STATUS_EXISTS : STATUS_SUCCESS);
        return vo;
    }

    public static UploadItemResultVo failed(String uploadUuid, String error) {
        UploadItemResultVo vo = new UploadItemResultVo();
        vo.setUploadUuid(uploadUuid);
        vo.setStatus(STATUS_FAILED);
        vo.setError(error);
        return vo;
    }
}
