package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 结构物疾病附件打包实体对象 BuildingPackage
 * 参考 edu.whut.cs.bi.biz.domain.Package
 */
@Data
@EqualsAndHashCode(callSuper=true)
public class BuildingPackage extends BaseEntity {
    private Long id;

    private Long buildingId;

    private Long minioId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date packageTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String packageSize;

    /** 文件存储名称（非数据库字段，关联FileMap表的newName） */
    private String newName;

    /** 文件下载链接（非数据库字段） */
    private String url;

    /** 文件名称（非数据库字段，关联FileMap表的oldName） */
    private String fileName;

    /** 文件创建者（非数据库字段，关联FileMap表的createBy） */
    private String fileCreateBy;
}
