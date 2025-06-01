package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class Disease extends BaseEntity {

    /** 病害id */
    private Long id;

    /** 病害位置 */
    private String position;

    /** 病害类型 */
    private DiseaseType diseaseType;
    private Long diseaseTypeId;

    /** 病害描述 */
    private String description;

    /** 病害等级 */
    private int level;

    /** 病害数量 */
    private int quantity;

    /** 病害类型 */
    private String type;

    /** 病害性质 */
    private String nature;

    /** 是否参与评定  0是 1否*/
    private String participateAssess;

    /** 扣分 */
    private int deductPoints;

    /** 病害图片 */
    private List<String> images;

    /** 关联对象 */
    private Long biObjectId;
    private BiObject biObject;

    /** 关联项目 */
    private Project project;
    private Long projectId;

    /** 年份  项目属性用于查询*/
    private Integer year;

    /** 关联构建 */
    private Component component;
    private Long componentId;

    /** 关联检测任务 */
    private Task task;
    private Long taskId;

    /** 关联建筑 */
    private Building building;
    private Long buildingId;

    /** 附件列表 */
    private List<Attachment> attachments;

    /** 病害详情 */
     private List<DiseaseDetail> diseaseDetails;
}