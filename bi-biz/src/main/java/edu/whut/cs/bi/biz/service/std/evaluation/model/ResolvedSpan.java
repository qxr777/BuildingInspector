package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析后的桥跨实例。
 */
@Data
public class ResolvedSpan {

    /** 桥跨节点ID */
    private Long spanId;

    /** 桥跨编号 */
    private Integer spanNo;

    /** 该跨全部部件 */
    private List<ResolvedPart> parts = new ArrayList<>();
}
