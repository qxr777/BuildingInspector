package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.AppPackage;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.mapper.AppPackageMapper;
import edu.whut.cs.bi.biz.service.IAppPackageService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.DecimalFormat;
import java.util.List;

/**
 * 移动端安装包Service实现。
 */
@Service
public class AppPackageServiceImpl implements IAppPackageService {
    @Autowired
    private AppPackageMapper appPackageMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private MinioConfig minioConfig;

    @Override
    public AppPackage selectAppPackageById(Long id) {
        return appPackageMapper.selectAppPackageById(id);
    }

    @Override
    public List<AppPackage> selectAppPackageList(AppPackage appPackage) {
        return appPackageMapper.selectAppPackageList(appPackage);
    }

    @Override
    @Transactional
    public int insertAppPackage(AppPackage appPackage, MultipartFile file) {
        validateAppPackage(appPackage);
        validateApkFile(file, true);

        FileMap fileMap = fileMapService.handleFileUpload(file);
        fillFileInfo(appPackage, fileMap, file.getSize());
        appPackage.setCreateBy(ShiroUtils.getLoginName());
        appPackage.setCreateTime(DateUtils.getNowDate());
        normalizePublish(appPackage);
        if ("1".equals(appPackage.getIsPublish())) {
            appPackageMapper.clearPublished(null);
        }
        return appPackageMapper.insertAppPackage(appPackage);
    }

    @Override
    @Transactional
    public int updateAppPackage(AppPackage appPackage, MultipartFile file) {
        validateAppPackage(appPackage);
        AppPackage old = appPackageMapper.selectAppPackageById(appPackage.getId());
        if (old == null) {
            throw new RuntimeException("安装包记录不存在");
        }

        Long oldMinioId = old.getMinioId();
        boolean hasNewFile = file != null && !file.isEmpty();
        if (hasNewFile) {
            validateApkFile(file, true);
            FileMap fileMap = fileMapService.handleFileUpload(file);
            fillFileInfo(appPackage, fileMap, file.getSize());
        }
        appPackage.setUpdateBy(ShiroUtils.getLoginName());
        appPackage.setUpdateTime(DateUtils.getNowDate());
        normalizePublish(appPackage);
        if ("1".equals(appPackage.getIsPublish())) {
            appPackageMapper.clearPublished(appPackage.getId());
        }
        int rows = appPackageMapper.updateAppPackage(appPackage);
        if (rows > 0 && hasNewFile && oldMinioId != null) {
            fileMapService.deleteFileMapById(oldMinioId);
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteAppPackageByIds(String ids) {
        String[] idArray = Convert.toStrArray(ids);
        List<Long> minioIds = appPackageMapper.selectMinioIdsByIds(idArray);
        int rows = appPackageMapper.deleteAppPackageByIds(idArray);
        if (rows > 0 && minioIds != null) {
            for (Long minioId : minioIds) {
                if (minioId != null) {
                    fileMapService.deleteFileMapById(minioId);
                }
            }
        }
        return rows;
    }

    @Override
    public AjaxResult getPublishedAppUpdate() {
        AppPackage appPackage = appPackageMapper.selectPublishedAppPackage();
        if (appPackage == null || appPackage.getMinioId() == null) {
            return AjaxResult.error("暂无已发布的移动端安装包");
        }
        FileMap fileMap = fileMapService.selectFileMapById(appPackage.getMinioId());
        if (fileMap == null || fileMap.getNewName() == null) {
            return AjaxResult.error("移动端安装包文件不存在");
        }
        String url = buildMinioUrl(fileMap.getNewName());
        return AjaxResult.success()
                .put("packageSize", appPackage.getPackageSize())
                .put("version", appPackage.getVersion())
                .put("apkName", appPackage.getApkName())
                .put("url", url);
    }

    private void validateAppPackage(AppPackage appPackage) {
        if (appPackage == null || appPackage.getVersion() == null || appPackage.getVersion().trim().isEmpty()) {
            throw new RuntimeException("app版本号不能为空");
        }
    }

    private void validateApkFile(MultipartFile file, boolean required) {
        if (file == null || file.isEmpty()) {
            if (required) {
                throw new RuntimeException("app文件不能为空");
            }
            return;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".apk")) {
            throw new RuntimeException("只允许上传apk安装包");
        }
    }

    private void fillFileInfo(AppPackage appPackage, FileMap fileMap, long fileSize) {
        appPackage.setMinioId(Long.valueOf(fileMap.getId()));
        appPackage.setApkName(fileMap.getOldName());
        appPackage.setPackageSize(formatFileSize(fileSize));
    }

    private void normalizePublish(AppPackage appPackage) {
        appPackage.setIsPublish("1".equals(appPackage.getIsPublish()) ? "1" : "0");
    }

    private String formatFileSize(long bytes) {
        double sizeInMB = bytes / 1024.0 / 1024.0;
        return new DecimalFormat("0.##").format(sizeInMB) + "MB";
    }

    private String buildMinioUrl(String newName) {
        return minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" + newName.substring(0, 2) + "/" + newName;
    }
}
