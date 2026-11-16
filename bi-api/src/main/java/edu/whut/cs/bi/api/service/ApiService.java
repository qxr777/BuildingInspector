package edu.whut.cs.bi.api.service;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author:wanzheng
 * @Date:2025/7/11 08:53
 * @Description:
 **/
public interface ApiService {
    /**
     * @param taskId 本次提交的检测任务ID；不为空时只处理该任务的病害，为空时按旧逻辑处理（兼容旧版平板）
     */
    AjaxResult uploadBridgeData(MultipartFile file, Long taskId);
}
