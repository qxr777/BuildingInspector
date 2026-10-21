package edu.whut.cs.bi.biz.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通道A 附件已传回查请求：按 uploadUuid 批量查询预上传结果
 */
@Data
public class UploadQueryDto implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 待查询的客户端幂等键列表（单次 ≤500） */
    private List<String> uploadUuids;
}
