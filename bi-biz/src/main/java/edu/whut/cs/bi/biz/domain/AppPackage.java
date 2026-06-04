package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 移动端安装包。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppPackage extends BaseEntity {
    private Long id;

    private String version;

    private Long minioId;

    private String apkName;

    private String packageSize;

    /**
     * 1 发布，0 未发布。
     */
    private String isPublish;

    private String delFlag;

    private String url;
}
