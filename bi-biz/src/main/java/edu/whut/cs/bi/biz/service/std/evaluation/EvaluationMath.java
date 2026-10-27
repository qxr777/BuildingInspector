package edu.whut.cs.bi.biz.service.std.evaluation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 评定计算公共数值规则（JTG/T 5230—2026）。
 */
@Component
public class EvaluationMath {

    /** 全标准统一保留 1 位小数、四舍五入。 */
    private static final int SCALE = 1;

    /**
     * 统一舍入：保留 1 位小数（HALF_UP）。
     */
    public BigDecimal scale1(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 构件标度 ECI 截断到 [0,5]。
     */
    public int clampEci(int raw) {
        return Math.max(0, Math.min(5, raw));
    }

    /**
     * double 转 BigDecimal（不经 new BigDecimal(double) 的二进制误差）。
     */
    public BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
