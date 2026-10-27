package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 5230 构件级人工判定录入 bi_assess_manual_input
 *
 * <p>JTG/T 5230—2026 评定中无法由采集数据自动推导的检查人判定（第 3.2 节）：
 * EDDI 耐久性标度、ESDI 人工覆盖、EFI 功能标度、EAI 影响标度及 3.3.3 安全影响标志。
 * 每任务每构件至多一行；经 sync/upload 与 /biz/assess5230 两条通道写入。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssessManualInput extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 检测任务ID */
    private Long taskId;

    /** 构件ID（bi_element.id；服务端按 elementUuid 解析回填） */
    private Long elementId;

    /** 构件离线UUID（bi_element.offline_uuid） */
    private String elementUuid;

    /** 人工 EDDI 耐久性标度 0/1/2（3.2.2；空=未录入按0） */
    private Integer eddi;

    /** 人工 ESDI 结构性标度 0~3（3.2.3；非空时覆盖病害自动 max） */
    private Integer esdiOverride;

    /** 人工 EFI 功能标度 0/1/2（3.2.4；空=未录入按0） */
    private Integer efi;

    /** 人工 EAI 影响标度 -1/0/1（3.2.5；仅主要构件可取非0） */
    private Integer eai;

    /** 缺损是否影响桥梁安全（3.3.3；1=是 0=否） */
    private Integer safetyAffected;

    /** 判定依据/备注 */
    private String evidence;

    /** 离线记录唯一标识(UUID) */
    private String offlineUuid;

    /** 0=云端,1=离线生成 */
    private Integer isOfflineData;

    /** 0=正常,1=已删除（同步物理删） */
    private Integer offlineDeleted;
}
