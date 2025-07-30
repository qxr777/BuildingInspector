package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * @Author:wanzheng
 * @Date:2025/7/29 19:42
 * @Description: 检测报告实体类
 **/
@Data
public class Report extends BaseEntity {
    private Long id;

    private String name;

    private Integer status;

    private Long reviewerId;

    private Long approverId;

    private Long reportTemplateId;

    private Long projectId;

    private String buildingIds;

    private Integer flag;
}
