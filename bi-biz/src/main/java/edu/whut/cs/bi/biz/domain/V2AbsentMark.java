package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 5230-2026 应设未设部件标记 bi_v2_absent_mark
 *
 * <p>检测员在 App 端录入、随 /api/v2/sync/upload 打包提交。相关标准或设计文件规定应设置、
 * 实际未设置的部件（3.2.2-5）：幻影构件 EDI=3，部件保留权重、不参与 B.1.3 再分配。
 * {@code spanId} 为 {@code null} 表示该部件全桥所有跨均应设而未设；非空表示仅指定桥跨。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class V2AbsentMark extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联检测任务ID */
    private Long taskId;

    /** 桥跨节点ID；null = 全桥所有跨 */
    private Long spanId;

    /** 应设未设部件节点ID (bi_object.id, node_type=PART) */
    private Long partId;

    /** 离线记录唯一标识 (UUID) */
    private String offlineUuid;

    /** 是否为离线生成数据 (0:云端数据, 1:离线数据) */
    private Integer isOfflineData;

    /** 是否被App离线删除标记 (0:否, 1:是) */
    private Integer offlineDeleted;

    /** 桥跨离线UUID (同步辅助；null=全桥所有跨，不反查 span) */
    private String spanUuid;

    /** 部件离线UUID (同步辅助，反查 part serverId) */
    private String partUuid;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("taskId", getTaskId())
            .append("spanId", getSpanId())
            .append("partId", getPartId())
            .toString();
    }
}
