package edu.whut.cs.bi.biz.domain;

import lombok.Builder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.TreeEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 桥梁构件模版对象 bi_template_object
 *
 * @author wanzheng
 * @date 2025-04-02
 */
public class BiTemplateObject extends TreeEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 对象ID
     */
    private Long id;

    /**
     * 对象名称
     */
    @Excel(name = "对象名称")
    private String name;

    /**
     * 对象状态（0正常 1停用）
     */
    @Excel(name = "对象状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /**
     * 删除标志（0存在 2删除）
     */
    private String delFlag;

    /**
     * 子模板对象
     */
    private List<BiTemplateObject> children = new ArrayList<BiTemplateObject>();

    public List<BiTemplateObject> getChildren() {
        return children;
    }

    public void setChildren(List<BiTemplateObject> children) {
        this.children = children;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
    }

    public String getDelFlag() {
        return delFlag;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("name", getName())
                .append("parentId", getParentId())
                .append("ancestors", getAncestors())
                .append("orderNum", getOrderNum())
                .append("status", getStatus())
                .append("delFlag", getDelFlag())
                .append("remark", getRemark())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .toString();
    }
}
