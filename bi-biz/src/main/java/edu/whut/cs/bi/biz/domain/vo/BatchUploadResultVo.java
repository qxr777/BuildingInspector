package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通道A 附件批量幂等预上传结果（逐文件容错，单项失败不影响同批其他文件）
 */
@Data
public class BatchUploadResultVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 批内总项数 */
    private Integer total = 0;

    /** 本次新传成功数 */
    private Integer successCount = 0;

    /** 幂等命中（未重复上传）数 */
    private Integer existsCount = 0;

    /** 失败数 */
    private Integer failureCount = 0;

    /** 逐项结果，顺序与请求 files 下标一致 */
    private List<UploadItemResultVo> items = new ArrayList<>();

    public void addItem(UploadItemResultVo item) {
        items.add(item);
        total++;
        if (UploadItemResultVo.STATUS_SUCCESS.equals(item.getStatus())) {
            successCount++;
        } else if (UploadItemResultVo.STATUS_EXISTS.equals(item.getStatus())) {
            existsCount++;
        } else {
            failureCount++;
        }
    }
}
