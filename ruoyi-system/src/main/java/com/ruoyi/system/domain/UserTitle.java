package com.ruoyi.system.domain;

import lombok.Data;

import java.util.Date;

@Data
public class UserTitle {
    private Long id;

    private Long userId;

    private String title;

    private String serialNumber;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    private String remark;
}
