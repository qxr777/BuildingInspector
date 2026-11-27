package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Score;

import java.util.List;

/**
 * 一次部件评定的内存结果。replaceScores 为 true 时，保存阶段先删除该部件原有构件得分再写入 scores。
 */
public class PreparedCondition {
    private final Condition condition;
    private final List<Score> scores;
    private final boolean replaceScores;

    public PreparedCondition(Condition condition, List<Score> scores, boolean replaceScores) {
        this.condition = condition;
        this.scores = scores;
        this.replaceScores = replaceScores;
    }

    public Condition getCondition() {
        return condition;
    }

    public List<Score> getScores() {
        return scores;
    }

    public boolean isReplaceScores() {
        return replaceScores;
    }
}
