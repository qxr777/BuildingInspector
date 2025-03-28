package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * @author QiXin
 * @date 2025/3/28
 */
public class Component extends BaseEntity {
    private BiObject biObject;
    /** 构件编号 */
    @Excel(name = "构件编号")
    @NotBlank(message = "构件编号不能为空")
    private String code;
    /** 构件名称 */
    @Excel(name = "构件名称")
    @NotBlank(message = "构件名称不能为空")
    @Length(message = "构件名称不能超过20个字符", max = 20)
    private String name;

    public BiObject getBiObject() {
        return biObject;
    }

    public void setBiObject(BiObject biObject) {
        this.biObject = biObject;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
