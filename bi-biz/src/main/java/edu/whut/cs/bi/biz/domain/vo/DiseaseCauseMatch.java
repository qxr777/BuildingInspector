package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

/**
 * 病害成因/处治建议匹配结果。cause、suggestion 为 Excel 原文；未匹配或原文为空时为 null，由使用方决定提示方式。
 */
@Data
public class DiseaseCauseMatch {

    public static final String MATCHED = "MATCHED";
    /** 候选为空，或大模型判断没有对应条目 */
    public static final String NO_MATCH = "NO_MATCH";
    /** 桥梁模板不在知识库覆盖范围内 */
    public static final String UNSUPPORTED = "UNSUPPORTED";
    /** 数据缺失或大模型调用失败 */
    public static final String FAILED = "FAILED";

    public static final String CAUSE_PLACEHOLDER = "需补充成因分析";
    public static final String SUGGESTION_PLACEHOLDER = "需补充处治建议";

    private String status;

    private String cause;

    /** 按评定标度截取后的处治建议 */
    private String suggestion;

    /** 处治建议原文（含全部标度） */
    private String fullSuggestion;

    private Integer level;

    private String entryId;

    private String sheet;

    private String category;

    private String component;

    private String defectType;

    private String defectName;

    private String condition;

    /** 大模型给出的匹配或不匹配理由；失败时为错误信息 */
    private String reason;

    private int candidateCount;

    public boolean isMatched() {
        return MATCHED.equals(status);
    }

    public static DiseaseCauseMatch of(String status, String reason, int candidateCount) {
        DiseaseCauseMatch match = new DiseaseCauseMatch();
        match.setStatus(status);
        match.setReason(reason);
        match.setCandidateCount(candidateCount);
        return match;
    }
}
