package edu.whut.cs.bi.api.vo;

/**
 * 平板完成 OSS 上传后通知服务端解析的请求体。
 */
public class OssBridgeDataUploadRequest {

    private String objectName;

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }
}
