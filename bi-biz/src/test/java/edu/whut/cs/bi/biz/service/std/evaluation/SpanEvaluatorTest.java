package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.domain.SpanComponentPart;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanEvaluatorTest {

    private SpanEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpanEvaluator(new EvaluationMath());
    }

    private Set<String> mainPartKeys() {
        return new HashSet<>(List.of("mainGirder", "bearing", "pierAbutment", "foundation"));
    }

    private ResolvedPart part(long partId, String key, String layer, int omega, String gamma) {
        ResolvedPart p = new ResolvedPart();
        p.setPartId(partId);
        p.setPartKey(key);
        p.setLayerKey(layer);
        p.setOmega(omega);
        p.setOmegaEffective(BigDecimal.valueOf(omega).setScale(4));
        p.setGamma(new BigDecimal(gamma));
        return p;
    }

    private SpanComponentPart anchor(long componentId, long spanId, long partId) {
        SpanComponentPart a = new SpanComponentPart();
        a.setComponentId(componentId);
        a.setSpanId(spanId);
        a.setPartId(partId);
        return a;
    }

    private ComponentEval eval(long componentId, String partKey, int eci) {
        ComponentEval e = new ComponentEval();
        e.setComponentId(componentId);
        e.setPartKey(partKey);
        e.setEci(eci);
        e.setEcl(eci <= 1 ? 1 : eci);
        return e;
    }

    private V2ComponentInput safety(long componentId, int affected) {
        V2ComponentInput input = new V2ComponentInput();
        input.setComponentId(componentId);
        input.setSafetyAffected(affected);
        return input;
    }

    @Test
    void baselineBBsci576AndClass4() {
        long spanId = 100L;
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(spanId);

        // (partId, key, layer, omega, gamma)
        span.getParts().add(part(1, "paving", "deck", 40, "0.15"));
        span.getParts().add(part(2, "expansionJoint", "deck", 25, "0.15"));
        span.getParts().add(part(3, "railing", "deck", 20, "0.15"));
        span.getParts().add(part(4, "drainage", "deck", 15, "0.15"));
        span.getParts().add(part(5, "mainGirder", "superstructure", 55, "0.42"));
        span.getParts().add(part(6, "diaphragm", "superstructure", 20, "0.42"));
        span.getParts().add(part(7, "bearing", "superstructure", 25, "0.42"));
        span.getParts().add(part(8, "pierAbutment", "substructure", 60, "0.38"));
        span.getParts().add(part(9, "foundation", "substructure", 40, "0.38"));
        span.getParts().add(part(10, "riverbed", "ancillary", 35, "0.05"));
        span.getParts().add(part(11, "revetment", "ancillary", 35, "0.05"));
        span.getParts().add(part(12, "otherAncillary", "ancillary", 30, "0.05"));

        // CI>0 的部件各挂一个构件：paving2/railing1，主梁3/横隔2/支座1，墩台3/基础2，其他1
        int[][] ciRows = {{1, 2}, {3, 1}, {5, 3}, {6, 2}, {7, 1}, {8, 3}, {9, 2}, {12, 1}};
        List<SpanComponentPart> anchors = new ArrayList<>();
        List<ComponentEval> evals = new ArrayList<>();
        for (int[] row : ciRows) {
            long partId = row[0];
            long componentId = 1000 + partId;
            anchors.add(anchor(componentId, spanId, partId));
            String partKey = span.getParts().stream()
                    .filter(p -> p.getPartId() == partId).findFirst().get().getPartKey();
            evals.add(eval(componentId, partKey, row[1]));
        }

        SpanEval result = evaluator.evaluate(span, anchors, evals,
                Map.of(), mainPartKeys());

        assertEquals(new BigDecimal("57.6"), result.getBsci());
        assertEquals(4, result.getBscl());
        assertEquals(new BigDecimal("57.6"), result.getBsciRaw());
        assertEquals(4, result.getRawLevel());
        assertFalse(result.isOverride());
    }

    @Test
    void bsclBandBoundaries() {
        assertEquals(1, SpanEvaluator.bsclBand(new BigDecimal("90.1")));
        assertEquals(2, SpanEvaluator.bsclBand(new BigDecimal("90.0")));
        assertEquals(2, SpanEvaluator.bsclBand(new BigDecimal("75.1")));
        assertEquals(3, SpanEvaluator.bsclBand(new BigDecimal("75.0")));
        assertEquals(3, SpanEvaluator.bsclBand(new BigDecimal("60.1")));
        assertEquals(4, SpanEvaluator.bsclBand(new BigDecimal("60.0")));
        assertEquals(4, SpanEvaluator.bsclBand(new BigDecimal("40.1")));
        assertEquals(5, SpanEvaluator.bsclBand(new BigDecimal("40.0")));
        assertEquals(1, SpanEvaluator.bsclBand(new BigDecimal("100.0")));
        assertEquals(5, SpanEvaluator.bsclBand(new BigDecimal("0.0")));
    }

    /** 单部件主梁桥跨：ECI 4 时 raw=100−4·55·.42/5=81.5（2类），覆盖到 4类/60。 */
    private ResolvedSpan singleMainGirderSpan(int eci) {
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(100L);
        span.getParts().add(part(5, "mainGirder", "superstructure", 55, "0.42"));
        return span;
    }

    @Test
    void overrideWhenMainComponentUnsafeAtClass4() {
        ResolvedSpan span = singleMainGirderSpan(4);
        List<SpanComponentPart> anchors = List.of(anchor(1005L, 100L, 5L));
        List<ComponentEval> evals = List.of(eval(1005L, "mainGirder", 4));
        Map<Long, V2ComponentInput> inputs = Map.of(1005L, safety(1005L, 1));

        SpanEval result = evaluator.evaluate(span, anchors, evals, inputs, mainPartKeys());
        assertTrue(result.isOverride());
        assertEquals(4, result.getBscl());
        assertEquals(new BigDecimal("60.0"), result.getBsci());
        assertEquals(new BigDecimal("81.5"), result.getBsciRaw());
        assertEquals(2, result.getRawLevel());
    }

    @Test
    void noOverrideWhenSafetyNotMarked() {
        ResolvedSpan span = singleMainGirderSpan(4);
        List<SpanComponentPart> anchors = List.of(anchor(1005L, 100L, 5L));
        List<ComponentEval> evals = List.of(eval(1005L, "mainGirder", 4));

        SpanEval result = evaluator.evaluate(span, anchors, evals, Map.of(), mainPartKeys());
        assertFalse(result.isOverride());
        assertEquals(new BigDecimal("81.5"), result.getBsci());
        assertEquals(2, result.getBscl());
    }

    @Test
    void noOverrideForNonMainComponent() {
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(100L);
        span.getParts().add(part(6, "diaphragm", "superstructure", 20, "0.42"));
        List<SpanComponentPart> anchors = List.of(anchor(1006L, 100L, 6L));
        List<ComponentEval> evals = List.of(eval(1006L, "diaphragm", 5));
        Map<Long, V2ComponentInput> inputs = Map.of(1006L, safety(1006L, 1));

        SpanEval result = evaluator.evaluate(span, anchors, evals, inputs, mainPartKeys());
        assertFalse(result.isOverride());
    }

    @Test
    void overrideTakesWorstWhenBoth4And5Triggered() {
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(100L);
        span.getParts().add(part(5, "mainGirder", "superstructure", 55, "0.42"));
        span.getParts().add(part(7, "bearing", "superstructure", 25, "0.42"));

        List<SpanComponentPart> anchors = List.of(
                anchor(1005L, 100L, 5L), anchor(1007L, 100L, 7L));
        List<ComponentEval> evals = List.of(
                eval(1005L, "mainGirder", 4), eval(1007L, "bearing", 5));
        Map<Long, V2ComponentInput> inputs = Map.of(
                1005L, safety(1005L, 1), 1007L, safety(1007L, 1));

        SpanEval result = evaluator.evaluate(span, anchors, evals, inputs, mainPartKeys());
        assertTrue(result.isOverride());
        assertEquals(5, result.getBscl());
        assertEquals(new BigDecimal("40.0"), result.getBsci());
    }
}
