package edu.whut.cs.bi.biz.domain.dto;

import lombok.Data;

/**
 * 病害成因/处治建议匹配请求。传 diseaseId 时从库中读取病害，其余非空字段覆盖库中值；
 * 不传 diseaseId 时 biObjectId 和 type 必填。
 */
@Data
public class CauseMatchQuery {

    private Long diseaseId;

    private Long biObjectId;

    private String biObjectName;

    /** 病害类型，可带“5.1.1-2-3#”编码前缀 */
    private String type;

    private String crackType;

    private String position;

    private String description;

    /** 评定标度，用于截取对应标度的处治建议 */
    private Integer level;
}
