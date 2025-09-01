package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * @Author:wanzheng
 * @Date:2025/7/30 19:33
 * @Description:
 **/
@Data
public class ReportData extends BaseEntity {
    private Long id;

    private String key;

    private String value;

    private Long reportId;

    private Integer type;

    private Integer flag;
}
