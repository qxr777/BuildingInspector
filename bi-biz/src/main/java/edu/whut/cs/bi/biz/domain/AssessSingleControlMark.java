package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 5230 单项控制指标命中标记 bi_assess_single_control_mark（标准 3.4.5，共 16 项）。
 *
 * <p>每任务每指标至多一行；任一指标命中，全桥技术状况等级直接定为 5 类、
 * BCI=min(分层BCI,40)。证据桥跨/构件可空。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssessSingleControlMark extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 检测任务ID */
    private Long taskId;

    /** 单项控制指标编号 1~16（对应 3.4.5） */
    private Integer indicatorNo;

    /** 是否命中（1=命中；未命中可不录） */
    private Integer hit;

    /** 证据桥跨节点ID（可空） */
    private Long spanId;

    /** 证据桥跨离线UUID（同步锚定） */
    private String spanUuid;

    /** 证据构件ID（可空） */
    private Long elementId;

    /** 证据构件离线UUID（同步锚定） */
    private String elementUuid;

    /** 命中依据/描述 */
    private String evidence;

    /** 离线记录唯一标识(UUID) */
    private String offlineUuid;

    /** 0=云端,1=离线生成 */
    private Integer isOfflineData;

    /** 0=正常,1=已删除（同步物理删） */
    private Integer offlineDeleted;
}
