package edu.whut.cs.bm.iot.vo;

import lombok.Data;

/**
 * @author qixin on 2022/6/29.
 * @version 1.0
 */
@Data
public class EmqXEventVo {
    private String username;
    private String event;
    private String clientid;
}
