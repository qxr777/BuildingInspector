package edu.whut.cs.bi.biz.service;

import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.domain.AppPackage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 移动端安装包Service。
 */
public interface IAppPackageService {
    AppPackage selectAppPackageById(Long id);

    List<AppPackage> selectAppPackageList(AppPackage appPackage);

    int insertAppPackage(AppPackage appPackage, MultipartFile file);

    int updateAppPackage(AppPackage appPackage, MultipartFile file);

    int deleteAppPackageByIds(String ids);

    AjaxResult getPublishedAppUpdate();
}
