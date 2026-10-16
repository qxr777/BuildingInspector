package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 构件-桥跨-部件映射对象 bi_span_element_part
 *
 * <p>2026 新标（JTG/T 5230—2026）锚定关系：一个构件可能跨越多个桥跨，且在同一桥跨内
 * 归属某个具体部件节点。该表记录「构件(bi_element) ↔ 桥跨(SPAN) ↔ 部件(PART)」的多对多
 * 锚定，由 App 端离线建模产生、同步上传。与旧 bi_span_component_part 并存、旧表冻结。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpanElementPart extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 构件ID (对应 bi_element.id) */
    private Long elementId;

    /** 桥跨节点ID (对应 bi_object.id, node_type=SPAN) */
    private Long spanId;

    /** 该构件在此跨内所属部件节点ID (对应 bi_object.id, node_type=PART) */
    private Long partId;

    /** 是否共用构件 (相邻跨均计入, 1=共用, 0=不共用) */
    private Integer isShared;

    /** 离线记录唯一标识 (UUID) */
    private String offlineUuid;

    /** 是否为离线生成数据 (0:云端数据, 1:离线数据) */
    private Integer isOfflineData;

    /** 是否被App离线删除标记 (0:否, 1:是) */
    private Integer offlineDeleted;

    /** 构件离线UUID (同步辅助，反查 element serverId) */
    private String elementUuid;

    /** 桥跨离线UUID (同步辅助，反查 span serverId) */
    private String spanUuid;

    /** 部件离线UUID (同步辅助，反查 part serverId) */
    private String partUuid;
}
