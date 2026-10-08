package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.service.std.evaluation.model.BridgeEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeEvaluatorTest {

    private BridgeEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BridgeEvaluator(new EvaluationMath());
    }

    private SpanEval span(long spanId, String bsci, int bscl) {
        SpanEval s = new SpanEval();
        s.setSpanId(spanId);
        s.setBsci(new BigDecimal(bsci));
        s.setBsciRaw(new BigDecimal(bsci));
        s.setBscl(bscl);
        s.setRawLevel(bscl);
        return s;
    }

    private V2SingleControlMark controlHit(int indicatorNo) {
        V2SingleControlMark mark = new V2SingleControlMark();
        mark.setIndicatorNo(indicatorNo);
        mark.setHit(1);
        return mark;
    }

    @Test
    void singleSpanPassesThrough() {
        BridgeEval eval = evaluator.evaluate(
                List.of(span(1L, "82.4", 3)), null);
        assertEquals("SINGLE", eval.getCalcMode());
        assertEquals(3, eval.getBcl());
        assertEquals(new BigDecimal("82.4"), eval.getBci());
        assertEquals(1L, eval.getWorstSpanId());
        assertFalse(eval.isSingleControlHit());
    }

    @Test
    void equalClassUsesArithmeticMean() {
        // 88/86/82 均 2 类 → 85.3
        BridgeEval eval = evaluator.evaluate(
                List.of(span(1L, "88", 2), span(2L, "86", 2), span(3L, "82", 2)), null);
        assertEquals("EQUAL", eval.getCalcMode());
        assertEquals(2, eval.getBcl());
        assertEquals(new BigDecimal("85.3"), eval.getBci());
        assertEquals(new BigDecimal("85.3"), eval.getBsciMean());
    }

    @Test
    void differentClassesUsesPiecewiseInterpolation() {
        // 92/84/58（1/2/4类）→ 59.2/4类
        BridgeEval eval = evaluator.evaluate(
                List.of(span(1L, "92", 1), span(2L, "84", 2), span(3L, "58", 4)), null);
        assertEquals("INTERP", eval.getCalcMode());
        assertEquals(4, eval.getBcl());
        assertEquals(new BigDecimal("59.2"), eval.getBci());
        assertEquals(new BigDecimal("58"), eval.getBsciMin());
        assertEquals(new BigDecimal("92"), eval.getBsciMax());
        assertEquals(new BigDecimal("78.0"), eval.getBsciMean());
        assertEquals(new BigDecimal("60"), eval.getBsciLim());
        assertEquals(4, eval.getBclLayered());
        assertEquals(new BigDecimal("59.2"), eval.getBciLayered());
        assertEquals(3L, eval.getWorstSpanId());
    }

    @Test
    void singleControlHitForcesClass5AndKeepsLayered() {
        BridgeEval eval = evaluator.evaluate(
                List.of(span(1L, "92", 1), span(2L, "84", 2), span(3L, "58", 4)),
                List.of(controlHit(13)));
        assertTrue(eval.isSingleControlHit());
        assertEquals(5, eval.getBcl());
        assertEquals("13", eval.getSingleControlNos());
        assertEquals(new BigDecimal("40.0"), eval.getBci());
        // 分层综合结果保留
        assertEquals(4, eval.getBclLayered());
        assertEquals(new BigDecimal("59.2"), eval.getBciLayered());
    }

    @Test
    void multipleHitsRecordedSorted() {
        BridgeEval eval = evaluator.evaluate(
                List.of(span(1L, "55", 4)),
                List.of(controlHit(16), controlHit(2)));
        assertEquals(5, eval.getBcl());
        assertEquals("2,16", eval.getSingleControlNos());
    }

    @Test
    void throwsWhenInterpDenominatorZero() {
        // BSCI 相同但等级不一致（异常数据）→ 式 3.4.3 分母为 0
        List<SpanEval> spans = List.of(span(1L, "70", 2), span(2L, "70", 4));
        assertThrows(ServiceException.class, () -> evaluator.evaluate(spans, null));
    }

    @Test
    void throwsOnEmptySpans() {
        assertThrows(ServiceException.class, () -> evaluator.evaluate(List.of(), null));
    }

    @Test
    void invalidIndicatorNumberRejected() {
        V2SingleControlMark bad = new V2SingleControlMark();
        bad.setIndicatorNo(17);
        bad.setHit(1);
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of(span(1L, "80", 3)), List.of(bad)));
    }
}
