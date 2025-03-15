package edu.whut.cs.bm.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author qixin on 2021/7/14.
 * @version 1.0
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private String id;

    private String messageId;
    private String productName;
    private String deviceName;
    private String channelName;
    private String payload;
    private long sentAt;
}
