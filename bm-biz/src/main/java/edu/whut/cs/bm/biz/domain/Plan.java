package edu.whut.cs.bm.biz.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * 抢修抢建方案对象 bm_plan
 * 
 * @author qixin
 * @date 2021-08-09
 */
public class Plan extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 方案标题 */
    @Excel(name = "方案标题")
    @NotBlank(message = "方案标题不能为空")
    @Length(message = "方案标题不能超过50个字符", max = 50)
    private String title;

    /** 方案内容 */
    @Excel(name = "方案内容")
    @NotBlank(message = "方案内容不能为空")
//    @Length(message = "方案内容不能超过50个字符", max = 50)
    private String content;

    /** 预警规则是否关联此抢修抢建方案 临时属性 默认不关联 */
    private boolean flag = false;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setTitle(String title) 
    {
        this.title = title;
    }

    public String getTitle() 
    {
        return title;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("id", getId())
            .append("createBy", getCreateBy())
            .append("updateBy", getUpdateBy())
            .append("title", getTitle())
            .append("content", getContent())
            .toString();
    }
}
