package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 构件与桥跨关联对象 bi_object_component
 * 2026标准：用于处理共享构件（如桥墩）与多个桥跨的评分关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BiObjectComponent extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 构件ID (对应 bi_component.id) */
    private Long componentId;

    /** 对象ID (对应 bi_object.id，主要用于桥跨) */
    private Long biObjectId;

    /** 构件在当前单元的权重 (0-1.0) */
    private java.math.BigDecimal weight;

    /** 离线唯一标识符 */
    private String offlineUuid;

    /** 是否为离线生成数据 (0:云端数据, 1:离线数据) */
    private Integer isOfflineData;

    /** 构件离线UUID (同步辅助) */
    private String componentUuid;

    /** 对象离线UUID (同步辅助) */
    private String objectUuid;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("componentId", getComponentId())
            .append("biObjectId", getBiObjectId())
            .append("weight", getWeight())
            .toString();
    }
}
