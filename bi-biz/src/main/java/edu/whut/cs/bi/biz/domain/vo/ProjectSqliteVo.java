package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 项目 SQLite 同步信息 VO
 */
@Data
public class ProjectSqliteVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 下载地址 */
    private String url;

    /** 生成时间（时间戳） */
    private Date timestamp;
}
