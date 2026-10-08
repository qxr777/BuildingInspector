package edu.whut.cs.bi.biz.service.std.evaluation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 评定过程中的非阻断告警集合（fallback 解析、标度强制修正、病害忽略计数等）。
 * 随 previewTree / result 返回给评定人与管理员，不写入结果表。
 */
@Data
public class EvalWarnings {

    private final List<String> warnings = new ArrayList<>();

    public void add(String message) {
        warnings.add(message);
    }

    public boolean isEmpty() {
        return warnings.isEmpty();
    }
}
