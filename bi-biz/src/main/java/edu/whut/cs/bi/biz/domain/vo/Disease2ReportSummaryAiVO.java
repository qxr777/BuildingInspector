package edu.whut.cs.bi.biz.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.whut.cs.bi.biz.domain.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class Disease2ReportSummaryAiVO {

    /**
     * 病害id
     */
    private Long id;

    /**
     * 病害位置
     */
    private String position;

    /**
     * 病害位置编号
     */
    private Integer positionNumber;

    /**
     * 病害类型
     */
    private DiseaseType diseaseType;
    private Long diseaseTypeId;

    /**
     * 病害描述
     */
    private String description;

    /**
     * 病害数量
     */
    private int quantity;

    /**
     * 病害类型
     */
    private String type;

    /**
     * 病害性质（结构病害 / 非结构病害）
     */
    private String nature;

    /**
     * 病害成因
     */
    private String cause;

    /**
     * 维修建议
     */
    private String repairRecommendation;

    // 裂缝特征 (纵向、横向、斜向、L型、U型）
    private String crackType;

    // 发展趋势 （稳定、发展、新增、已维修、部分维修、未找到)
    private String developmentTrend;

    /**
     * 构件名称 （支持自定义）
     */
    private String biObjectName;

    /**
     * 年份  项目属性用于查询
     */
    private Integer year;


    /**
     * 病害详情
     */
    private List<DiseaseDetail> diseaseDetails;

}
