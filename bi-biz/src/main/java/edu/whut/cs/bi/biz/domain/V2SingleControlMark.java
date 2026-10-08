package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 5230-2026 单项控制指标标记 bi_v2_single_control_mark
 *
 * <p>检测员在 App 端录入、随 /api/v2/sync/upload 打包提交。3.4.5 列出的 16 项单项控制指标
 * （落梁、主梁控制截面全截面开裂等）。任一标记 {@code hit=1} 时全桥技术状况等级评定为 5 类；
 * 分层综合评定结果同时保留。componentId/spanId 均可空（不关联具体构件/桥跨）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class V2SingleControlMark extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联检测任务ID */
    private Long taskId;

    /** 单项控制指标序号 (1~16) */
    private Integer indicatorNo;

    /** 是否命中 (1=命中, 0=未命中) */
    private Integer hit;

    /** 关联构件ID (可空) */
    private Long componentId;

    /** 关联桥跨节点ID (可空) */
    private Long spanId;

    /** 判定依据/现场情况 */
    private String evidence;

    /** 离线记录唯一标识 (UUID) */
    private String offlineUuid;

    /** 是否为离线生成数据 (0:云端数据, 1:离线数据) */
    private Integer isOfflineData;

    /** 是否被App离线删除标记 (0:否, 1:是) */
    private Integer offlineDeleted;

    /** 构件离线UUID (同步辅助，可空；给定时反查 component serverId) */
    private String componentUuid;

    /** 桥跨离线UUID (同步辅助，可空；给定时反查 span serverId) */
    private String spanUuid;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("taskId", getTaskId())
            .append("indicatorNo", getIndicatorNo())
            .append("hit", getHit())
            .append("evidence", getEvidence())
            .toString();
    }
}
