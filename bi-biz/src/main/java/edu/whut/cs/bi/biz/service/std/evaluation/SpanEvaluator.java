package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.domain.SpanComponentPart;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 桥跨级评定（JTG/T 5230—2026 第 3.3 节）。
 *
 * <ul>
 *   <li>CI(j) = 部件 j 内全部构件（含应设未设幻影构件）ECI 最大值；</li>
 *   <li>BSCI = 100 − Σ CI·ω′·γ / 5（1 位小数）；BSCL 按表 3.3.2；</li>
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
     * @param span 解析后的桥跨
     * @param anchors 该跨的构件锚定记录
     * @param componentEvals 该跨涉及构件的评定结果
     * @param inputsByComponent 构件人工录入（查安全影响标记）
     * @param mainPartKeys 该桥型主要部件 key 集合
     */
    public SpanEval evaluate(ResolvedSpan span, List<SpanComponentPart> anchors,
                             List<ComponentEval> componentEvals,
                             Map<Long, V2ComponentInput> inputsByComponent,
                             Set<String> mainPartKeys) {
        Map<Long, ComponentEval> evalByComponent = new HashMap<>();
        for (ComponentEval eval : componentEvals) {
            evalByComponent.put(eval.getComponentId(), eval);
        }

        // CI 聚合（幻影部件已在 ResolvedPart.ci=3）
        for (ResolvedPart part : span.getParts()) {
            if (part.isAbsent()) {
                continue;
            }
            int ci = 0;
            if (anchors != null) {
                for (SpanComponentPart anchor : anchors) {
                    if (part.getPartId().equals(anchor.getPartId())) {
                        ComponentEval eval = evalByComponent.get(anchor.getComponentId());
                        if (eval != null) {
                            ci = Math.max(ci, eval.getEci());
                        }
                    }
                }
            }
            part.setCi(ci);
        }

        // BSCI 公式
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
        result.setSpanId(span.getSpanId());
        result.setBsciRaw(bsciRaw);
        result.setRawLevel(rawLevel);

        // 3.3.3 主要构件覆盖
        int overrideLevel = 0;
        Long triggerComponent = null;
        for (ComponentEval eval : componentEvals) {
            if (eval.getEcl() < 4 || !mainPartKeys.contains(eval.getPartKey())) {
                continue;
            }
            V2ComponentInput input = inputsByComponent.get(eval.getComponentId());
            if (input != null && Integer.valueOf(1).equals(input.getSafetyAffected())) {
                if (eval.getEcl() > overrideLevel) {
                    overrideLevel = eval.getEcl();
                    triggerComponent = eval.getComponentId();
                }
            }
        }

        result.setBsci(bsciRaw);
        result.setBscl(rawLevel);
        if (overrideLevel > 0) {
            BigDecimal cap = overrideLevel == 5 ? BigDecimal.valueOf(40) : BigDecimal.valueOf(60);
            BigDecimal finalBsci = bsciRaw.min(cap);
            int finalLevel = Math.max(rawLevel, overrideLevel);
            result.setOverride(true);
            result.setOverrideLevel(finalLevel);
            result.setTrigger("构件id=" + triggerComponent + " ECL=" + overrideLevel
                    + "类且标记影响桥梁安全，按3.3.3覆盖");
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
