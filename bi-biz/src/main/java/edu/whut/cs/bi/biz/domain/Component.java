package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * @author QiXin
 * @date 2025/3/28
 */
@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class Component extends BaseEntity {
    /**
     * 构件id
     */
    private Long id;

    private BiObject biObject;
    /**
     * 构件编号
     */
    @Excel(name = "构件编号")
    @NotBlank(message = "构件编号不能为空")
    private String code;
    /**
     * 构件名称
     */
    @Excel(name = "构件名称")
    @NotBlank(message = "构件名称不能为空")
    @Length(message = "构件名称不能超过20个字符", max = 20)
    private String name;

    /**
     * 关联对象对象ID
     */
    @Excel(name = "关联对象ID")
    private Long biObjectId;

    /**
     * 对象状态（0正常 1停用）
     */
    @Excel(name = "构件状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;

    private String parentObjectName;

    private String grandObjectName;
}
