package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * @author QiXin
 * @date 2025/4/9
 */
public class DiseaseScale extends BaseEntity {

/**
 * 该类用于表示病害标度的相关信息，对应 《JTGT H21-2011公路桥梁技术状况评定标准》 中的大量病害类型表。
 *
 * 包含以下属性：
 * - id: 唯一标识符，用于区分不同的病害标度。
 * - diseaseTypeId: 病害类型的唯一标识符，用于关联病害类型。
 * - scale: 病害的严重程度等级，通常用整数表示。
 * - qualitativeDescription: 病害的定性描述，提供对病害性质的非量化描述。
 * - quantitativeDescription: 病害的定量描述，提供对病害性质的量化描述。
 */
private Long id; // 唯一标识符

private Long diseaseTypeId; // 病害类型的唯一标识符

private Integer scale; // 病害的严重程度等级

private String qualitativeDescription; // 病害的定性描述

private String quantitativeDescription; // 病害的定量描述

}
