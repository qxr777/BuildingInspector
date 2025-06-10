package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class Building extends BaseEntity {
    /**
     * 建筑id
     */
    private Long id;

    /**
     * 建筑名称
     */
    @Excel(name = "建筑名称")
    @NotBlank(message = "建筑名称不能为空")
    @Length(message = "建筑名称不能超过20个字符", max = 20)
    private String name;

    /**
     * 类型（0组合桥 1桥幅）
     */
    @Excel(name = "类型", readConverterExp = "0=组合桥,1=桥幅")
    private String isLeaf;

    /**
     * 模板ID
     */
    private Long templateId;

    /**
     * 根对象ID
     */
    private Long rootObjectId;

    /**
     * 状态（0正常 1停用）
     */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 海拔高度
     */
    private BigDecimal altitude;

    /**
     * 地址
     */
    @Excel(name = "地址")
    private String address;

    /**
     * 区域
     */
    @Excel(name = "区域")
    private String area;

    /**
     * 管理部门
     */
    @Excel(name = "管理部门")
    private String adminDept;

    /**
     * 权重
     */
    @Excel(name = "权重")
    private BigDecimal weight;

    /**
     * 视频流来源
     */
    @Excel(name = "视频流来源")
    private String videoFeed;

    /**
     * 线路
     */
    @Excel(name = "线路")
    private String line;

    /**
     * 根属性ID
     */
    @Excel(name = "根属性ID")
    private Long rootPropertyId;

    private BiObject rootObject;

    private Property rootProperty;

    /**
     * 父BuildingID
     */
    private Long parentId;

    /** 父桥名称 */
    private String parentName;

    /** 父对象ID (BiObject的parent_id) */
    private Long parentObjectId;

    /** 桥梁编号 */
    private String buildingCode;

    /** 路线编号 */
    private String routeCode;

    /** 路线名称 */
    private String routeName;

    /** 桥位桩号 */
    private String bridgePileNumber;

    /** 桥梁长度 */
    private String bridgeLength;

    /**
     * 桥梁类型（1梁式桥 2拱桥 3悬索桥 4斜拉桥）
     */
    private Integer bridgeType;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .append("id", getId())
                .append("name", getName())
                .append("createBy", getCreateBy())
                .append("updateBy", getUpdateBy())
                .append("status", getStatus())
                .append("delFlag", getDelFlag())
                .append("longitude", getLongitude())
                .append("latitude", getLatitude())
                .append("altitude", getAltitude())
                .append("remark", getRemark())
                .append("address", getAddress())
                .append("area", getArea())
                .append("adminDept", getAdminDept())
                .append("videoFeed", getVideoFeed())
                .append("weight", getWeight())
                .toString();
    }
}