package edu.whut.cs.bi.biz.service.std.evaluation.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析后的评定单元（UNIT）：一个桥型模板下的全部桥跨与部件。
 */
@Data
public class ResolvedUnit {

    /** UNIT 节点ID */
    private Long rootObjectId;

    /** 桥型代码 */
    private String bridgeType;

    /** 全部桥跨 */
    private List<ResolvedSpan> spans = new ArrayList<>();

    /**
     * 收集全单元部件 key（按首次出现顺序，部件在各跨同构）。
     */
    public List<String> allPartKeys() {
        List<String> keys = new ArrayList<>();
        for (ResolvedSpan span : spans) {
            for (ResolvedPart part : span.getParts()) {
                if (!keys.contains(part.getPartKey())) {
                    keys.add(part.getPartKey());
                }
            }
        }
        return keys;
    }

    /**
     * 按 key 查找部件（任一跨内的模板信息）。
     */
    public ResolvedPart findPart(String partKey) {
        for (ResolvedSpan span : spans) {
            for (ResolvedPart part : span.getParts()) {
                if (part.getPartKey().equals(partKey)) {
                    return part;
                }
            }
        }
        return null;
    }
}
