package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.UnitEval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitEvaluatorTest {

    private UnitEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new UnitEvaluator(new EvaluationMath());
    }

    private SpanEval span(long id, String bsci, int bscl) {
        SpanEval eval = new SpanEval();
        eval.setSpanId(id);
        eval.setBsci(new BigDecimal(bsci));
        eval.setBscl(bscl);
        return eval;
    }

    @Test
    void singleSpanPassesThrough() {
        UnitEval unit = evaluator.evaluate(10L, "B02",
                new ArrayList<>(List.of(span(20L, "85.3", 2))));

        assertEquals(UnitEvaluator.MODE_SINGLE, unit.getCalcMode());
        assertEquals(new BigDecimal("85.3"), unit.getBci());
        assertEquals(2, unit.getBcl());
        assertEquals(10L, unit.getUnitId());
        assertEquals("B02", unit.getBridgeType());
        assertEquals(1, unit.getSpanCount());
        assertEquals(20L, unit.getWorstSpanId());
    }

    @Test
    void goldenC083_mainBridgeInterpolation_bci703Level3() {
        // 附录 C.0.8-3 主桥三跨：66.7(3类)/87.6(2类)/73.2(3类)
        // 均值 75.8，min 66.7/max 87.6/lim 75
        // BCI = 66.7 + (75-66.7)(75.8-66.7)/(87.6-66.7) = 70.3
        List<SpanEval> spans = new ArrayList<>(List.of(
                span(1L, "66.7", 3),
                span(2L, "87.6", 2),
                span(3L, "73.2", 3)));

        UnitEval unit = evaluator.evaluate(10L, "B02", spans);

        assertEquals(UnitEvaluator.MODE_INTERP, unit.getCalcMode());
        assertEquals(new BigDecimal("75.8"), unit.getBsciMean());
        assertEquals(new BigDecimal("66.7"), unit.getBsciMin());
        assertEquals(new BigDecimal("87.6"), unit.getBsciMax());
        assertEquals(new BigDecimal("75"), unit.getBsciLim());
        assertEquals(new BigDecimal("70.3"), unit.getBci());
        assertEquals(3, unit.getBcl());
        assertEquals(1L, unit.getWorstSpanId());
    }

    @Test
    void goldenC083_approachBridgeEqualClassMeanBci845() {
        // 附录 C.0.8-3 引桥 8 跨均为 2 类，BSCI 均值 84.5（4×84.4 + 4×84.6）
        List<SpanEval> spans = new ArrayList<>();
        for (long i = 1; i <= 4; i++) {
            spans.add(span(i, "84.4", 2));
        }
        for (long i = 5; i <= 8; i++) {
            spans.add(span(i, "84.6", 2));
        }

        UnitEval unit = evaluator.evaluate(11L, "B02", spans);

        assertEquals(UnitEvaluator.MODE_EQUAL, unit.getCalcMode());
        assertEquals(new BigDecimal("84.5"), unit.getBci());
        assertEquals(2, unit.getBcl());
        assertEquals(8, unit.getSpanCount());
    }

    @Test
    void interpZeroDenominatorThrows() {
        // BSCI 相同但等级不一致（正常分档不可能，防御数据异常）
        List<SpanEval> spans = new ArrayList<>(List.of(
                span(1L, "70.0", 3),
                span(2L, "70.0", 2)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> evaluator.evaluate(10L, "B02", spans));
        assertEquals(true, ex.getMessage().contains("分母为0"));
    }

    @Test
    void emptySpansThrow() {
        assertThrows(ServiceException.class,
                () -> evaluator.evaluate(10L, "B02", new ArrayList<>()));
    }
}
