package edu.whut.cs.bm.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author qixin on 2021/8/6.
 * @version 1.0
 */
@Builder
@Data//setter getter toString
@NoArgsConstructor//无参构造
@AllArgsConstructor//全参构造
public class DeviceDto {
    private String id;

    private String productName;

    private String deviceName;

    private String brokerUsername;

    private String secret;

    private String status;

    private boolean connected;

    private String clientId;

    private String ipaddress;

    private long connectedAt;

    private long disconnectedAt;

    private String deviceStatus;

    private long lastStatusUpdatedAt;
}
