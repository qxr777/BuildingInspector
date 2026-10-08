package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.domain.enums.V2SingleIndicator;
import edu.whut.cs.bi.biz.service.std.evaluation.model.BridgeEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全桥级评定（JTG/T 5230—2026 第 3.4 节）。
 *
 * <ul>
 *   <li>单跨：BCL/BCI 直通；</li>
 *   <li>多跨等级一致：BCI 为各跨 BSCI 算术平均；</li>
 *   <li>多跨等级不一致：BCL 取最差，BCI 按式 3.4.3 分段插值；</li>
 *   <li>3.4.5 单项控制任一命中：BCL=5，分层综合结果同时保留。</li>
 * </ul>
 */
@Component
public class BridgeEvaluator {

    private final EvaluationMath math;

    public BridgeEvaluator(EvaluationMath math) {
        this.math = math;
    }

    /**
     * 评定全桥。
     *
     * @param spans 各跨最终评定结果（按跨顺序）
     * @param controlMarks 单项控制标记
     */
    public BridgeEval evaluate(List<SpanEval> spans, List<V2SingleControlMark> controlMarks) {
        if (spans == null || spans.isEmpty()) {
            throw new ServiceException("无桥跨评定结果，无法进行全桥评定");
        }

        BridgeEval eval = new BridgeEval();

        List<BigDecimal> bscis = spans.stream().map(SpanEval::getBsci).collect(Collectors.toList());
        BigDecimal min = bscis.stream().min(Comparator.naturalOrder()).get();
        BigDecimal max = bscis.stream().max(Comparator.naturalOrder()).get();

        SpanEval worstSpan = spans.stream()
                .min(Comparator.comparing(SpanEval::getBsci).thenComparing(SpanEval::getSpanId))
                .get();
        eval.setWorstSpanId(worstSpan.getSpanId());
        eval.setBsciMin(min);
        eval.setBsciMax(max);

        boolean singleSpan = spans.size() == 1;
        boolean equalClass = spans.stream().map(SpanEval::getBscl).distinct().count() == 1;

        if (singleSpan) {
            eval.setCalcMode("SINGLE");
            SpanEval span = spans.get(0);
            eval.setBclLayered(span.getBscl());
            eval.setBciLayered(span.getBsci());
            eval.setBsciMean(span.getBsci());
        } else if (equalClass) {
            eval.setCalcMode("EQUAL");
            BigDecimal mean = arithmeticMean(bscis);
            eval.setBsciMean(mean);
            eval.setBclLayered(spans.get(0).getBscl());
            eval.setBciLayered(mean);
        } else {
            eval.setCalcMode("INTERP");
            BigDecimal mean = arithmeticMean(bscis);
            eval.setBsciMean(mean);

            int worstClass = spans.stream().map(SpanEval::getBscl).max(Comparator.naturalOrder()).get();
            BigDecimal lim = bsciLimit(worstClass);
            eval.setBsciLim(lim);

            if (max.compareTo(min) == 0) {
                // 正常情况下等级不同则 max>min（互斥有序分档），出现即数据异常
                throw new ServiceException(
                        "式3.4.3分母为0：各跨BSCI同为 " + min + " 但等级不一致，请检查桥跨评定数据");
            }
            BigDecimal bci = min.add(lim.subtract(min)
                    .multiply(mean.subtract(min))
                    .divide(max.subtract(min), 6, BigDecimal.ROUND_HALF_UP));
            bci = math.scale1(bci);
            eval.setBclLayered(worstClass);
            eval.setBciLayered(bci);
        }

        // 3.4.5 单项控制覆盖
        List<Integer> hitNos = new ArrayList<>();
        if (controlMarks != null) {
            for (V2SingleControlMark mark : controlMarks) {
                if (Integer.valueOf(1).equals(mark.getHit())) {
                    // 校验序号合法（非法序号由保存校验拦截，此处防御）
                    V2SingleIndicator.valueOfNo(mark.getIndicatorNo());
                    if (!hitNos.contains(mark.getIndicatorNo())) {
                        hitNos.add(mark.getIndicatorNo());
                    }
                }
            }
        }
        hitNos.sort(Comparator.naturalOrder());

        eval.setBcl(eval.getBclLayered());
        eval.setBci(eval.getBciLayered());
        if (!hitNos.isEmpty()) {
            eval.setSingleControlHit(true);
            eval.setSingleControlNos(hitNos.stream().map(String::valueOf).collect(Collectors.joining(",")));
            eval.setBcl(5);
            // 与 BCL=5 保持一致：不高于 5 类档上限 40
            eval.setBci(math.scale1(eval.getBciLayered().min(BigDecimal.valueOf(40))));
        }
        return eval;
    }

    private BigDecimal arithmeticMean(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return math.scale1(sum.divide(BigDecimal.valueOf(values.size()), 6, BigDecimal.ROUND_HALF_UP));
    }

    /** 最差等级对应的 BSCI 限制值：2类90 / 3类75 / 4类60 / 5类40。 */
    private BigDecimal bsciLimit(int worstClass) {
        switch (worstClass) {
            case 2:
                return BigDecimal.valueOf(90);
            case 3:
                return BigDecimal.valueOf(75);
            case 4:
                return BigDecimal.valueOf(60);
            case 5:
                return BigDecimal.valueOf(40);
            default:
                throw new ServiceException("式3.4.3不支持的最差等级: " + worstClass);
        }
    }
}
