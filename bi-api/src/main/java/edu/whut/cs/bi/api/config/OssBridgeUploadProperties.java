package edu.whut.cs.bi.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大型桥梁数据 ZIP 的 OSS 临时中转配置。
 *
 * <p>AccessKey 必须由部署环境注入，不能写入代码库或客户端。</p>
 */
@Component
@ConfigurationProperties(prefix = "oss.bridge-upload")
public class OssBridgeUploadProperties {

    /** 是否启用 OSS 临时中转上传。 */
    private boolean enabled;

    /** OSS 公网 Endpoint。 */
    private String endpoint = "https://oss-cn-beijing.aliyuncs.com";

    /** OSS 内网 Endpoint，仅同地域阿里云运行环境可用。 */
    private String internalEndpoint = "https://oss-cn-beijing-internal.aliyuncs.com";

    /** 是否使用内网 Endpoint；本地或非同地域环境必须保持 false。 */
    private boolean useInternalEndpoint;

    /** 用于暂存 ZIP 的 Bucket。 */
    private String bucketName = "bi-upload-large";

    /** AccessKey ID，由环境变量注入。 */
    private String accessKeyId;

    /** AccessKey Secret，由环境变量注入。 */
    private String accessKeySecret;

    /**
     * 可选的对象名前缀，例如 bridge-upload/。空值表示该 Bucket 下所有 ZIP 均可处理。
     */
    private String objectPrefix = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return useInternalEndpoint ? internalEndpoint : endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getInternalEndpoint() {
        return internalEndpoint;
    }

    public void setInternalEndpoint(String internalEndpoint) {
        this.internalEndpoint = internalEndpoint;
    }

    public boolean isUseInternalEndpoint() {
        return useInternalEndpoint;
    }

    public void setUseInternalEndpoint(boolean useInternalEndpoint) {
        this.useInternalEndpoint = useInternalEndpoint;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getAccessKeyId() {
        // 兼容现有部署使用的通用环境变量；也可使用
        // OSS_BRIDGE_UPLOAD_ACCESS_KEY_ID（对应本配置属性）显式注入。
        return hasText(accessKeyId) ? accessKeyId : System.getenv("OSS_ACCESS_KEY_ID");
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        // 兼容现有部署使用的通用环境变量；也可使用
        // OSS_BRIDGE_UPLOAD_ACCESS_KEY_SECRET（对应本配置属性）显式注入。
        return hasText(accessKeySecret) ? accessKeySecret : System.getenv("OSS_ACCESS_KEY_SECRET");
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public void setObjectPrefix(String objectPrefix) {
        this.objectPrefix = objectPrefix;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
