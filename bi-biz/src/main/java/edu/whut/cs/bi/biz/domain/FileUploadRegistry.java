package edu.whut.cs.bi.biz.domain;

import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * 通道A预上传幂等登记对象 bi_file_upload_registry
 *
 * <p>App 为"每次文件产出"生成 uploadUuid（拍照产出时生成；重拍换新 uploadUuid 但附件
 * offlineUuid 不变；超时/响应丢失重试复用）。带 uploadUuid 的预上传先查本表，命中即
 * 返回既有 minioId，不重复 putObject、不重复写 bi_file_map。</p>
 */
@Data
@ToString
public class FileUploadRegistry {
    private Long id;

    /** 客户端幂等键（UUID 去横线，32 位） */
    private String uploadUuid;

    /** 原图 bi_file_map.id */
    private Long fileMapId;

    /** 缩略图 bi_file_map.id，生成失败为 null */
    private Long thumbFileMapId;

    /** 客户端原始文件名 */
    private String originalName;

    /** 原图字节数 */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 上传用户ID */
    private Long userId;

    /** 上传账号 */
    private String createBy;

    /** 登记时间 */
    private Date createTime;
}
