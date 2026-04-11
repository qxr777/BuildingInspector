package edu.whut.cs.bi.biz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线同步结果 VO
 *
 * @author QiXin
 * @date 2026/04/09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultVo {

    /** 成功保存的记录总数 */
    private int successCount;

    /** 失败的记录总数 */
    private int failureCount;

    /** ID 映射列表 (Offline UUID -> Server ID) */
    @Builder.Default
    private List<SyncIdMapping> idMappings = new ArrayList<>();

    /** 错误信息列表 */
    @Builder.Default
    private List<SyncError> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SyncIdMapping {
        /** 实体类型 (Building, BiObject, etc.) */
        private String entityType;
        /** 离线端生成的 UUID */
        private String offlineUuid;
        /** 服务端分配的长整型 ID */
        private Long serverId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SyncError {
        /** 实体类型 */
        private String entityType;
        /** 离线 UUID */
        private String offlineUuid;
        /** 错误提示 */
        private String message;
    }

    public void addMapping(String type, String uuid, Long id) {
        if (idMappings == null) idMappings = new ArrayList<>();
        idMappings.add(new SyncIdMapping(type, uuid, id));
        successCount++;
    }

    public void addError(String type, String uuid, String msg) {
        if (errors == null) errors = new ArrayList<>();
        errors.add(new SyncError(type, uuid, msg));
        failureCount++;
    }
}
