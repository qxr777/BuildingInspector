package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;

/**
 * 病害类型
 *
 * @author: chenwenqi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DiseaseType extends BaseEntity {

    /**
     * 病害类型实体类，用于表示病害类型的基本信息。
     */
    private Long id; // 实体唯一标识符，由数据库自动生成

    /**
     * 病害类型编号，用于唯一标识该病害类型。
     * 该编号与《JTGT H21-2011公路桥梁技术状况评定标准》病害类型表编号一致，确保每种病害类型具有唯一性。
     */
    private String code;

    /**
     * 病害类型名称，描述该病害的具体名称。
     * 该名称与《JTGT H21-2011公路桥梁技术状况评定标准》病害类型表名一致，便于用户识别和理解。
     */
    private String name;

    /**
     * 病害的最大标度，表示该病害类型的最高严重程度。
     * 该值用于定义病害的严重程度范围，通常与病害的评估标准相关。
     */
    private Integer maxScale;

    /**
     * 病害的最小标度，表示该病害类型的最低严重程度。
     * 该值用于定义病害的严重程度范围，通常与病害的评估标准相关。
     */
    private Integer minScale;

    /**
     * 病害类型状态（0正常 1停用）
     */
    private String status;

    private Integer selectColumn;

    // 关联的病害标度
    private List<DiseaseScale> diseaseScales;

}