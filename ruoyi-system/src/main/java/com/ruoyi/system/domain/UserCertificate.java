package com.ruoyi.system.domain;

import lombok.Data;

import java.util.Date;

@Data
public class UserCertificate {

    private Long id;

    private Long userId;

    private String certificate;

    private String serialNumber;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    private String remark;
}
