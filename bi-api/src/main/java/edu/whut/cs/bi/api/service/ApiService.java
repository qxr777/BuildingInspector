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

    AjaxResult generateUserDataPackage(SysUser user);

    AjaxResult uploadBridgeData( MultipartFile file);
}
