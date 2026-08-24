package edu.whut.cs.bi.api.service;

import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author:wanzheng
 * @Date:2025/7/11 08:53
 * @Description:
 **/
public interface ApiService {
    AjaxResult uploadBridgeData(MultipartFile file);

    /**
     * 从 OSS 临时中转区读取桥梁 ZIP 并复用普通上传的解析流程。
     * 成功后会删除对应 OSS 对象。
     */
    AjaxResult uploadBridgeDataFromOss(String objectName);
}
