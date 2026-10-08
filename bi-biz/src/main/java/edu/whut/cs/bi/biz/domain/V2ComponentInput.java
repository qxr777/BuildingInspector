package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 5230-2026 构件人工评定输入 bi_v2_component_input
 *
 * <p>检测员在 App 端按构件录入、随 /api/v2/sync/upload 打包提交：EDDI（耐久性，第 4 章）、
 * EFI（功能，第 14 章）、EAI（影响，第 15 章），以及该主要构件缺损是否影响桥梁安全（3.3.3）。
 * ESDI 由结构性病害标度自动计算，不在此录入。task_id 为下发只读整数直通。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class V2ComponentInput extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联检测任务ID */
    private Long taskId;

    /** 构件ID (bi_component.id) */
    private Long componentId;

    /** 耐久性缺损标度 EDDI (0/1/2) */
    private Integer eddi;

    /** 功能状况标度 EFI (0/1/2) */
    private Integer efi;

    /** 影响状况标度 EAI (-1/0/1) */
    private Integer eai;

    /** 该主要构件缺损是否影响桥梁安全 (3.3.3, 1=是, 0=否) */
    private Integer safetyAffected;

    /** 离线记录唯一标识 (UUID) */
    private String offlineUuid;

    /** 是否为离线生成数据 (0:云端数据, 1:离线数据) */
    private Integer isOfflineData;

    /** 是否被App离线删除标记 (0:否, 1:是) */
    private Integer offlineDeleted;

    /** 构件离线UUID (同步辅助，反查 component serverId) */
    private String componentUuid;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("taskId", getTaskId())
            .append("componentId", getComponentId())
            .append("eddi", getEddi())
            .append("efi", getEfi())
            .append("eai", getEai())
            .append("safetyAffected", getSafetyAffected())
            .toString();
    }
}
