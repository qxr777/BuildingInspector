package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 构件得分
 */
@Data
public class Score extends BaseEntity {

    private BigDecimal score;

    /**
     * 构件，关联的构件
     */
    private Component component;
    private Long componentId;

    /**
     * 部件技术状况评定对象ID
     */
    private Long conditionId;

}