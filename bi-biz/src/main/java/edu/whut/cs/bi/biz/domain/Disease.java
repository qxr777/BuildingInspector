package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.math.BigDecimal;

/**
 * @author QiXin
 * @date 2025/3/17
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class Disease extends BaseEntity {

    /** 病害id */
    private Long id;

    /** 病害位置 */
    private String position;

    /** 病害位置编号 */
    private Integer positionNumber;

    /** 病害类型 */
    private DiseaseType diseaseType;
    private Long diseaseTypeId;

    /** 病害描述 */
    private String description;

    /** 病害等级 */
    private int level;

    /** 病害数量 */
    private int quantity;

    /** 单位 */
    private String units;

    /** 病害类型 */
    private String type;

    /** 病害性质（结构病害 / 非结构病害） */
    private String nature;

    /** 病害成因  */
    private String cause;

    /** 维修建议 */
    private String repairRecommendation;

    /** 检测方法 */
    private String detectionMethod;

    /** 是否参与评定  0是 1否*/
    private String participateAssess;

    // 裂缝特征 (纵向、横向、斜向、L型、U型）
    private String crackType;

    // 发展趋势 （稳定、发展、新增、已维修、部分维修、未找到)
    private String developmentTrend;

    /** 扣分 */
    private int deductPoints;

    /** 病害图片 */
    private List<String> images;

    @JsonProperty("ADImgs")
    private List<String> ADImgs;

    /** 关联对象 */
    private Long biObjectId;
    private BiObject biObject;

    /** 构件名称 （支持自定义） */
    private String biObjectName;

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

    /** 标记 */
    private Integer commitType;

    /** 本地ID */
    private Long localId;

    private Integer attachmentCount;
}