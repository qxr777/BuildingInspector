package edu.whut.cs.bi.biz.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ID 映射记录，用于离线 UUID 与服务端 ID 的对应
 *
 * @author QiXin
 * @date 2026/04/09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdMapping {
    private Long id;
    private String entityType;
    private String offlineUuid;
    private Long serverId;
    private String syncUuid;
    private LocalDateTime createTime;
}
