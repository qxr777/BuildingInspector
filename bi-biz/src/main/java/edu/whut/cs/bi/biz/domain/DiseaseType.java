package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.*;

/**
 * 
 */
public class DiseaseType extends BaseEntity {

    /**
     * 病害类型实体类，用于表示病害类型的基本信息。
     */
    private Long id; // 实体唯一标识符，由数据库自动生成

    private String objectName; // 对象名称，用于标识该实体的类型或用途

    /**
     * 病害类型编号，用于唯一标识该病害类型。
     * 该编号通常由系统生成或用户定义，确保每种病害类型具有唯一性。
     */
    private String code;

    /**
     * 病害类型名称，描述该病害的具体名称。
     * 该名称用于直观地表示病害的类型，便于用户识别和理解。
     */
    private String name;

    /**
     * 病害的最大等级，表示该病害类型的最高严重程度。
     * 该值用于定义病害的严重程度范围，通常与病害的评估标准相关。
     */
    private Integer maxLevel;

    /**
     * 病害的最小等级，表示该病害类型的最低严重程度。
     * 该值用于定义病害的严重程度范围，通常与病害的评估标准相关。
     */
    private Integer minLevel;



}