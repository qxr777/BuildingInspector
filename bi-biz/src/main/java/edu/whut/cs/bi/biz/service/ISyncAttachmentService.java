package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.vo.BatchUploadResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadItemResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadStatusVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 通道A 附件幂等预上传服务。
 *
 * <p>App 为"每次文件产出"生成并持久化 uploadUuid：同一 uploadUuid 的重试/超时恢复/并发
 * 双发只产生一份原图（及缩略图），重复请求直接返回既有 minioId（status=exists），不重复
 * 占用上行流量；重拍必须换新 uploadUuid（附件自身 offlineUuid 不变）。</p>
 */
public interface ISyncAttachmentService {

    /**
     * 单个附件幂等预上传：原图存 MinIO + 登记 bi_file_map，并同步生成缩略图。
     * 本方法不向调用方抛异常，所有失败收敛进返回值（status=failed）。
     */
    UploadItemResultVo preUpload(String uploadUuid, MultipartFile file);

    /**
     * 批量幂等预上传，逐文件容错（单项失败不影响同批其他项）。
     *
     * @param uploadUuids 与 files 等长、按下标对齐的幂等键
     * @param files       文件数组
     */
    BatchUploadResultVo preUploadBatch(String[] uploadUuids, MultipartFile[] files);

    /**
     * 按 uploadUuid 批量回查已传文件；未登记的键返回 uploaded=false。
     * 用于同步前跳过已传文件 / 上传响应丢失后的零流量恢复。
     */
    List<UploadStatusVo> queryUploaded(List<String> uploadUuids);
}
