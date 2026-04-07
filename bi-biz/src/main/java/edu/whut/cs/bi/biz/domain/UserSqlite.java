package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 用户级 SQLite 离线包记录 bi_user_sqlite
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserSqlite extends BaseEntity {
    private Long id;
    private Long userId;
    private Long minioId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date packageTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    private String packageSize;
    private String delFlag;

    // 辅助字段
    private String url;
    private String newName;
}
