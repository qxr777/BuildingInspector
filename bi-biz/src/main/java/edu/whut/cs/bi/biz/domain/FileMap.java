package edu.whut.cs.bi.biz.domain;

import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * 文件管理对象 file_map
 *
 * @author zzzz
 * @date 2025-03-29
 */
@Data
@ToString
public class FileMap {
    private Integer id;
    private String oldName;
    private String newName;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String fileType; // 新增的文件类型字段
    private String url;
    private String attachmentRemark;

    private Long subjectId;
}
