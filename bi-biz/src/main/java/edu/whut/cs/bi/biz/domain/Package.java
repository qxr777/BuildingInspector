package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * @Author:wanzheng
 * @Date:2025/7/10 22:22
 * @Description:
 **/
@Data
public class Package extends BaseEntity {
    private Long id;

    private Long userId;

    private Long minioId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date packageTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String packageSize;

    /** 文件名称（非数据库字段，关联FileMap表的oldName） */
    private String fileName;

    /** 文件创建者（非数据库字段，关联FileMap表的createBy） */
    private String fileCreateBy;
}
