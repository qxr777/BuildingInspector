package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 桥跨级评定（JTG/T 5230—2026 第 3.3 节）。
 *
 * <ul>
 *   <li>CI(j) = 部件 j 内全部构件（含应设未设幻影构件）ECI 最大值；</li>
 *   <li>BSCI = 100 − Σ CI·ω′·γ / 5（中间过程 6 位小数，结果 1 位）；BSCL 按表 3.3.2；</li>
 *   <li>3.3.3：主要构件 ECL 4/5 类且评定人标记影响桥梁安全时，
 *       桥跨按最差构件定级、BSCI 取档上限（4类60/5类40），与公式值取更差。</li>
 * </ul>
 */
@Component
public class SpanEvaluator {

    private final EvaluationMath math;

    public SpanEvaluator(EvaluationMath math) {
        this.math = math;
    }

    /**
     * 评定单个桥跨。部件 CI 原地写回 span。
     *
     * @param span 解析后的桥跨（B.1.3 权重重分配已完成，幻影部件 absent=true、ci 已预置 3）
     * @param componentEvals 该跨涉及的全部构件评定结果（共用构件在本跨各计一行）
     */
    public SpanEval evaluate(ResolvedSpan span, List<ComponentEval> componentEvals) {
        // CI 聚合：幻影部件 ci=3 由装配阶段预置，跳过；真实部件按 partId 匹配取最差构件 ECI
        for (ResolvedPart part : span.getParts()) {
            if (part.isAbsent()) {
                continue;
            }
            int ci = 0;
            for (ComponentEval eval : componentEvals) {
                if (part.getPartId() != null && part.getPartId().equals(eval.getPartId())) {
                    ci = Math.max(ci, eval.getEci());
                }
            }
            part.setCi(ci);
        }

        // BSCI 公式：BSCI = 100 − Σ CI·ω′·γ/5
        BigDecimal sum = BigDecimal.ZERO;
        for (ResolvedPart part : span.getParts()) {
            if (part.getCi() == 0) {
                continue;
            }
            BigDecimal term = BigDecimal.valueOf(part.getCi())
                    .multiply(part.getOmegaEffective())
                    .multiply(part.getGamma())
                    .divide(BigDecimal.valueOf(5), 6, BigDecimal.ROUND_HALF_UP);
            sum = sum.add(term);
        }
        BigDecimal bsciRaw = math.scale1(BigDecimal.valueOf(100).subtract(sum));
        int rawLevel = bsclBand(bsciRaw);

        SpanEval result = new SpanEval();
        result.setUnitId(span.getUnitId());
        result.setSpanId(span.getSpanId());
        result.setBsciRaw(bsciRaw);
        result.setRawLevel(rawLevel);
        result.setBsci(bsciRaw);
        result.setBscl(rawLevel);

        // 3.3.3 主要构件覆盖：ECL 4/5 + 主要部件 + 人工标记影响桥梁安全
        int overrideLevel = 0;
        ComponentEval trigger = null;
        for (ComponentEval eval : componentEvals) {
            if (eval.isAbsent() || eval.getEcl() < 4 || !eval.isMainPart()
                    || !eval.isSafetyAffected()) {
                continue;
            }
            if (eval.getEcl() > overrideLevel) {
                overrideLevel = eval.getEcl();
                trigger = eval;
            }
        }

        if (trigger != null) {
            BigDecimal cap = overrideLevel == 5 ? BigDecimal.valueOf(40) : BigDecimal.valueOf(60);
            BigDecimal finalBsci = bsciRaw.min(cap);
            int finalLevel = Math.max(rawLevel, overrideLevel);
            result.setOverride(true);
            result.setOverrideLevel(finalLevel);
            result.setTrigger("构件(uuid=" + trigger.getElementUuid() + ", 名称=" + trigger.getElementName()
                    + ") ECL=" + overrideLevel + "类且标记影响桥梁安全，按3.3.3覆盖");
            result.setBsci(math.scale1(finalBsci));
            result.setBscl(finalLevel);
        }
        return result;
    }

    /**
     * 表 3.3.2：(90,100]→1，(75,90]→2，(60,75]→3，(40,60]→4，[0,40]→5。
     * 90.0 属 2 类，40.0 属 5 类。
     */
    public static int bsclBand(BigDecimal bsci) {
        if (bsci.compareTo(BigDecimal.valueOf(90)) > 0) {
            return 1;
        }
        if (bsci.compareTo(BigDecimal.valueOf(75)) > 0) {
            return 2;
        }
        if (bsci.compareTo(BigDecimal.valueOf(60)) > 0) {
            return 3;
        }
        if (bsci.compareTo(BigDecimal.valueOf(40)) > 0) {
            return 4;
        }
        return 5;
    }
}
