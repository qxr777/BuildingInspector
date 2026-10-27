package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 5230 应设未设部件标记 bi_assess_absent_mark（标准 3.2.2-5）。
 *
 * <p>应设未设部件没有 PART 实例节点与构件，按评定单元/桥跨 + 模板 partKey 锚定：
 * unitId 为空=全桥各评定单元；spanId 为空=该单元各跨。引擎评定时注入幻影构件（EDI=3、
 * 保留原 ω 不参与 B.1.3 重分配）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssessAbsentMark extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 检测任务ID */
    private Long taskId;

    /** 评定单元节点ID（bi_object.id,UNIT；空=全桥各单元） */
    private Long unitId;

    /** 评定单元离线UUID（同步锚定，服务端解析回填 unitId） */
    private String unitUuid;

    /** 桥跨节点ID（bi_object.id,SPAN；空=该单元各跨） */
    private Long spanId;

    /** 桥跨离线UUID（同步锚定，服务端解析回填 spanId） */
    private String spanUuid;

    /** 应设未设部件键（模板 partKey，如 bearing/expansionJoint） */
    private String partKey;

    /** 所属结构层键（deck/superstructure/substructure/ancillary） */
    private String layerKey;

    /** 判定依据/备注 */
    private String evidence;

    /** 离线记录唯一标识(UUID) */
    private String offlineUuid;

    /** 0=云端,1=离线生成 */
    private Integer isOfflineData;

    /** 0=正常,1=已删除（同步物理删） */
    private Integer offlineDeleted;
}
