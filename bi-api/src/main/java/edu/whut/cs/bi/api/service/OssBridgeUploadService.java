package edu.whut.cs.bi.api.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.api.config.OssBridgeUploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * OSS 临时 ZIP 对象的读取和清理。
 */
@Slf4j
@Service
public class OssBridgeUploadService {

    private final OssBridgeUploadProperties properties;

    private volatile OSS ossClient;

    public OssBridgeUploadService(OssBridgeUploadProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并标准化对象名。配置了临时上传前缀时，仅允许读取该前缀内的 ZIP 文件。
     *
     * @param objectName 平板上传 OSS 时使用的对象名
     * @return 去除首尾空白后的对象名
     */
    public String validateObjectName(String objectName) {
        if (isBlank(objectName)) {
            throw new ServiceException("objectName不能为空");
        }

        String normalized = objectName.trim();
        if (normalized.length() > 1024
                || normalized.startsWith("/")
                || normalized.contains("\\")
                || normalized.contains("../")
                || normalized.contains("/..")
                || containsControlCharacter(normalized)) {
            throw new ServiceException("objectName格式不合法");
        }
        if (!normalized.toLowerCase().endsWith(".zip")) {
            throw new ServiceException("OSS对象必须是ZIP格式文件");
        }

        String objectPrefix = normalizePrefix(properties.getObjectPrefix());
        if (!objectPrefix.isEmpty() && !normalized.startsWith(objectPrefix)) {
            throw new ServiceException("objectName不在允许的OSS临时上传目录内");
        }
        return normalized;
    }

    /**
     * 返回对象名最后一段，用于沿用既有的 buildingId[_year].zip 文件名解析规则。
     */
    public String getOriginalFileName(String objectName) {
        int lastSlash = objectName.lastIndexOf('/');
        return lastSlash < 0 ? objectName : objectName.substring(lastSlash + 1);
    }

    /**
     * 打开 OSS 对象流。关闭返回的流会同时关闭 OSSObject，避免 HTTP 连接泄漏。
     */
    public InputStream openObjectStream(String objectName) {
        OSSObject object;
        try {
            object = getOssClient().getObject(properties.getBucketName(), objectName);
        } catch (Exception e) {
            throw new ServiceException("从OSS读取压缩包失败：" + e.getMessage());
        }
        if (object == null || object.getObjectContent() == null) {
            throw new ServiceException("OSS中未找到指定压缩包");
        }
        return new OssObjectInputStream(object);
    }

    /**
     * ZIP 解析成功后删除临时对象，避免继续占用 OSS 存储空间。
     */
    public void deleteObject(String objectName) {
        try {
            getOssClient().deleteObject(properties.getBucketName(), objectName);
        } catch (Exception e) {
            log.error("删除OSS临时ZIP失败，bucket={}, objectName={}",
                    properties.getBucketName(), objectName, e);
            throw new ServiceException("ZIP已解析，但删除OSS临时文件失败：" + e.getMessage());
        }
    }

    private OSS getOssClient() {
        if (!properties.isEnabled()) {
            throw new ServiceException("OSS大文件上传未启用，请检查oss.bridge-upload.enabled配置");
        }
        validateConfiguration();

        OSS currentClient = ossClient;
        if (currentClient != null) {
            return currentClient;
        }
        synchronized (this) {
            if (ossClient == null) {
                ossClient = new OSSClientBuilder().build(
                        properties.getEndpoint(),
                        properties.getAccessKeyId(),
                        properties.getAccessKeySecret());
            }
            return ossClient;
        }
    }

    private void validateConfiguration() {
        if (isBlank(properties.getEndpoint())
                || isBlank(properties.getBucketName())
                || isBlank(properties.getAccessKeyId())
                || isBlank(properties.getAccessKeySecret())) {
            throw new ServiceException("OSS大文件上传配置不完整，请检查Endpoint、Bucket和AccessKey环境变量");
        }
    }

    private String normalizePrefix(String prefix) {
        if (isBlank(prefix)) {
            return "";
        }
        String normalized = prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @PreDestroy
    public void shutdown() {
        OSS currentClient = ossClient;
        if (currentClient != null) {
            currentClient.shutdown();
        }
    }

    private static class OssObjectInputStream extends FilterInputStream {

        private final OSSObject ossObject;
        private boolean closed;

        private OssObjectInputStream(OSSObject ossObject) {
            super(ossObject.getObjectContent());
            this.ossObject = ossObject;
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            // OSSObject.close() 会关闭 objectContent；不能再次关闭，避免重复释放 HTTP 连接。
            ossObject.close();
        }
    }
}
