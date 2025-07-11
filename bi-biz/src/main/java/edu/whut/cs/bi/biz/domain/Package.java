package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * @Author:wanzheng
 * @Date:2025/7/10 22:22
 * @Description:
 **/
@Data
public class Package {
    private Long id;

    private Long userId;

    private Long minioId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date packageTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
