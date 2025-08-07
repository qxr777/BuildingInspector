package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * @Author:wanzheng
 * @Date:2025/7/30 19:30
 * @Description:模版变量表
 **/
@Data
public class TemplateVariable extends BaseEntity {
    private Long id;

    private String name;

    private Integer type;

    private Long reportTemplateId;

    private Integer flag;
}
