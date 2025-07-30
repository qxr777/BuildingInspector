package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * @Author:wanzheng
 * @Date:2025/7/30 19:25
 * @Description:报告模版实体类
 **/
@Data
public class ReportTemplate extends BaseEntity {
    private Long id;

    private String name;

    private Integer isActive;

    private String fileUrl;

    private Integer version;

    private Integer flag;

    private Long minioId;
}
