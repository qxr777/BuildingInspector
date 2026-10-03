package edu.whut.cs.bi.api.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.ruoyi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OSS 临时 ZIP 对象的流式 MultipartFile 适配与清理。
 *
 * <p>该组件让 OSS 对象可以直接交给既有的
 * {@code ApiService.uploadBridgeData(MultipartFile)} 处理，不需要改动旧上传解析代码。</p>
 */
@Slf4j
@Component
public class OssBridgeUploadUtil {

    @Value("${oss.bridge-upload.enabled:false}")
    private boolean enabled;

    @Value("${oss.bridge-upload.endpoint:https://oss-cn-beijing.aliyuncs.com}")
    private String endpoint;

    @Value("${oss.bridge-upload.internal-endpoint:https://oss-cn-beijing-internal.aliyuncs.com}")
    private String internalEndpoint;

    @Value("${oss.bridge-upload.use-internal-endpoint:false}")
    private boolean useInternalEndpoint;

    @Value("${oss.bridge-upload.bucket-name:bi-upload-large}")
    private String bucketName;

    @Value("${oss.bridge-upload.object-prefix:}")
    private String objectPrefix;

    @Value("${oss.bridge-upload.access-key-id:}")
    private String accessKeyId;

    @Value("${oss.bridge-upload.access-key-secret:}")
    private String accessKeySecret;

    private volatile OSS ossClient;

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

        String normalizedObjectPrefix = normalizePrefix(objectPrefix);
        if (!normalizedObjectPrefix.isEmpty() && !normalized.startsWith(normalizedObjectPrefix)) {
            throw new ServiceException("objectName不在允许的OSS临时上传目录内");
        }
        return normalized;
    }

    /**
     * 创建一个不会把 OSS ZIP 全量读入内存的 MultipartFile。
     * 每次 getInputStream() 都会从 OSS 获取新的对象流。
     */
    public MultipartFile createMultipartFile(String objectName) {
        return new OssMultipartFile(objectName);
    }

    /**
     * ZIP 解析成功后，在事务提交后删除临时对象，避免继续占用 OSS 存储空间。
     */
    public void deleteObjectAfterTransactionCommit(String objectName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteObjectSafely(objectName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                deleteObjectSafely(objectName);
            }
        });
    }

    private InputStream openObjectStream(String objectName) {
        OSSObject object;
        try {
            object = getOssClient().getObject(bucketName, objectName);
        } catch (Exception e) {
            throw new ServiceException("从OSS读取压缩包失败：" + e.getMessage());
        }
        if (object == null || object.getObjectContent() == null) {
            throw new ServiceException("OSS中未找到指定压缩包");
        }
        return new OssObjectInputStream(object);
    }

    private long getObjectSize(String objectName) {
        try {
            return getOssClient().getObjectMetadata(bucketName, objectName).getContentLength();
        } catch (Exception e) {
            throw new ServiceException("从OSS读取压缩包失败：" + e.getMessage());
        }
    }

    private void deleteObjectSafely(String objectName) {
        try {
            getOssClient().deleteObject(bucketName, objectName);
        } catch (Exception e) {
            // 数据已经提交，删除失败不能再影响上传结果；记录对象名以便人工或定时清理。
            log.error("数据库已提交，但删除OSS临时ZIP失败，bucket={}, objectName={}",
                    bucketName, objectName, e);
        }
    }

    private OSS getOssClient() {
        if (!enabled) {
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
                        useInternalEndpoint ? internalEndpoint : endpoint,
                        getAccessKeyId(),
                        getAccessKeySecret());
            }
            return ossClient;
        }
    }

    private void validateConfiguration() {
        if (isBlank(useInternalEndpoint ? internalEndpoint : endpoint)
                || isBlank(bucketName)
                || isBlank(getAccessKeyId())
                || isBlank(getAccessKeySecret())) {
            throw new ServiceException("OSS大文件上传配置不完整，请检查Endpoint、Bucket和AccessKey环境变量");
        }
    }

    private String getOriginalFileName(String objectName) {
        int lastSlash = objectName.lastIndexOf('/');
        return lastSlash < 0 ? objectName : objectName.substring(lastSlash + 1);
    }

    private String getAccessKeyId() {
        return hasText(accessKeyId) ? accessKeyId : System.getenv("OSS_ACCESS_KEY_ID");
    }

    private String getAccessKeySecret() {
        return hasText(accessKeySecret) ? accessKeySecret : System.getenv("OSS_ACCESS_KEY_SECRET");
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

    private boolean hasText(String value) {
        return !isBlank(value);
    }

    @PreDestroy
    public void shutdown() {
        OSS currentClient = ossClient;
        if (currentClient != null) {
            currentClient.shutdown();
        }
    }

    private final class OssMultipartFile implements MultipartFile {

        private final String objectName;
        private final String originalFileName;

        private OssMultipartFile(String objectName) {
            this.objectName = objectName;
            this.originalFileName = getOriginalFileName(objectName);
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFileName;
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            return getSize() == 0;
        }

        @Override
        public long getSize() {
            return getObjectSize(objectName);
        }

        /**
         * 为防止大 ZIP 被一次性读入 JVM 堆内存，不支持 getBytes()；旧上传解析仅使用 getInputStream()。
         */
        @Override
        public byte[] getBytes() throws IOException {
            throw new IOException("OSS大文件仅支持流式读取，请使用getInputStream()");
        }

        @Override
        public InputStream getInputStream() {
            return openObjectStream(objectName);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            transferTo(dest.toPath());
        }

        @Override
        public void transferTo(Path dest) throws IOException, IllegalStateException {
            try (InputStream inputStream = getInputStream()) {
                Files.copy(inputStream, dest);
            }
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
