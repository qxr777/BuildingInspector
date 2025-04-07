package edu.whut.cs.bi.biz.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ProjectUserAssignment implements Serializable {

    private static final long serialVersionUID = -3153671084264547817L;

    /**
     * 项目id
     */
    private Long projectId;

    /**
     * 检测人员id
     */
    private List<Long> inspectorIds;

    /**
     * 报告编辑人员id
     */
    private Long authorId;

    /**
     * 审核人员id
     */
    private Long reviewerId;

    /**
     * 审核人员id
     */
    private Long approverId;
}
