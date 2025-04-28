package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ruoyi.common.core.domain.TreeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.List;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
public class Property extends TreeEntity {

    private Long id;
    private String name;
    private String value;

    /**
     * 子对象
     */
    private List<Property> children;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("name", getName())
                .append("value", getValue())
                .append("parentId", getParentId())
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .append("createBy", getCreateBy())
                .append("updateBy", getUpdateBy())
                .append("ancestors", getAncestors())
                .append("orderNum", getOrderNum())
                .append("remark", getRemark())
                .toString();
    }
}
