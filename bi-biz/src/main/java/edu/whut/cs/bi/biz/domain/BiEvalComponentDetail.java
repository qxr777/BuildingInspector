package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BiEvalComponentDetail extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private Long spanId;
    private Long componentId;
    private Integer edi;
    private Integer efi;
    private Integer eai;
}
