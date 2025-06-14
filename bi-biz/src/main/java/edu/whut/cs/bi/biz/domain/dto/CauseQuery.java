package edu.whut.cs.bi.biz.domain.dto;

import lombok.Data;

/**
 * 成因分析的请求参数
 */
@Data
public class CauseQuery {

    private Long objectId;

    /**
     * 桥梁模板
     */
    private String template;

    /**
     * 病害对象parentName
     */
    private String parentObject;

    /**
     * 病害对象name
     */
    private String object;

    private String type;

    private String description;

    private String position;

    private String area;

    private Integer level;

    private String componentCode;

    private String componentName;

    private String trend;

}
