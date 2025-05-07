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

    public String getParentObjectName() {
        return parentObjectName;
    }

    public void setParentObjectName(String parentObjectName) {
        this.parentObjectName = parentObjectName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
    }

    public Long getBiObjectId() {
        return biObjectId;
    }

    public void setBiObjectId(Long biObjectId) {
        this.biObjectId = biObjectId;
    }

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
