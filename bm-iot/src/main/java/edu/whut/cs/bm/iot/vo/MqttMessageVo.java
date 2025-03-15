package edu.whut.cs.bm.iot.vo;

import lombok.Data;

/**
 * @author qixin on 2022/6/29.
 * @version 1.0
 */
@Data
public class MqttMessageVo {
    private String username;
    private String topic;
    private String qos;
    private String payload;
    private String clientid;
}
