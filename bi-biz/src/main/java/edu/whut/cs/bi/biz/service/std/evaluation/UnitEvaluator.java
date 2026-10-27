package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.UnitEval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评定单元级评定（JTG/T 5230—2026 第 3.4.1~3.4.3 条）。
 *
 * <ul>
 *   <li>单跨：BCL/BCI 直通（3.4.1）；</li>
 *   <li>多跨等级一致：BCI 为各跨 BSCI 算术平均（3.4.2）；</li>
 *   <li>多跨等级不一致：BCL 取最差，BCI 按式 3.4.3 分段插值。</li>
 * </ul>
 * 多评定单元最差合成（3.4.4）与单项控制（3.4.5）在 {@link BridgeAggregator}。
 */
@Component
public class UnitEvaluator {

    public static final String MODE_SINGLE = "SINGLE";
    public static final String MODE_EQUAL = "EQUAL";
    public static final String MODE_INTERP = "INTERP";

    private final EvaluationMath math;

    public UnitEvaluator(EvaluationMath math) {
        this.math = math;
    }

    /**
     * 单元内多跨合成。
     *
     * @param unitId 评定单元节点ID
     * @param bridgeType 桥型代码
     * @param spans 各跨最终评定结果（已按跨顺序排列）
     */
    public UnitEval evaluate(Long unitId, String bridgeType, List<SpanEval> spans) {
        if (spans == null || spans.isEmpty()) {
            throw new ServiceException("评定单元(unitId=" + unitId + ")无桥跨评定结果，无法进行单元评定");
        }

        UnitEval eval = new UnitEval();
        eval.setUnitId(unitId);
        eval.setBridgeType(bridgeType);
        eval.setSpanCount(spans.size());

        List<BigDecimal> bscis = spans.stream().map(SpanEval::getBsci).collect(Collectors.toList());
        BigDecimal min = bscis.stream().min(Comparator.naturalOrder()).get();
        BigDecimal max = bscis.stream().max(Comparator.naturalOrder()).get();
        eval.setBsciMin(min);
        eval.setBsciMax(max);

        SpanEval worstSpan = spans.stream()
                .min(Comparator.comparing(SpanEval::getBsci).thenComparing(SpanEval::getSpanId))
                .get();
        eval.setWorstSpanId(worstSpan.getSpanId());

        boolean singleSpan = spans.size() == 1;
        boolean equalClass = spans.stream().map(SpanEval::getBscl).distinct().count() == 1;

        if (singleSpan) {
            eval.setCalcMode(MODE_SINGLE);
            SpanEval span = spans.get(0);
            eval.setBcl(span.getBscl());
            eval.setBci(span.getBsci());
            eval.setBsciMean(span.getBsci());
        } else if (equalClass) {
            eval.setCalcMode(MODE_EQUAL);
            BigDecimal mean = arithmeticMean(bscis);
            eval.setBsciMean(mean);
            eval.setBcl(spans.get(0).getBscl());
            eval.setBci(mean);
        } else {
            eval.setCalcMode(MODE_INTERP);
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
            eval.setBcl(worstClass);
            eval.setBci(bci);
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
