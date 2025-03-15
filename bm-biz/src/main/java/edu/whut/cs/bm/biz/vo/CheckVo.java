package edu.whut.cs.bm.biz.vo;

/**
 * @author qixin on 2023/4/19.
 * @version 1.0
 */
public class CheckVo {
    private int alertLevel;
    private double score;

    public int getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(int alertLevel) {
        this.alertLevel = alertLevel;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public CheckVo(int alertLevel, double score) {
        this.alertLevel = alertLevel;
        this.score = score;
    }
}
