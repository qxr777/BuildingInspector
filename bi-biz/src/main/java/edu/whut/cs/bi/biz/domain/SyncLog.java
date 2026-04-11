package edu.whut.cs.bi.biz.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 离线同步日志记录
 *
 * @author QiXin
 * @date 2026/04/09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {
    private Long id;
    private String syncUuid;
    private Long userId;
    /** 状态: 0-进行中, 1-完成, 2-失败 */
    private Integer status;
    private String clientInfo;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
    private String remark;
}
