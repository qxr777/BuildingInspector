package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanEvaluatorTest {

    private SpanEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpanEvaluator(new EvaluationMath());
    }

    /** 附录 C.0.8-2 黄金向量：装配式空心板单跨（B02）。 */
    private static final String[][] C082_PARTS = {
            // partId, partKey, layer, omega, gamma
            {"1", "paving",          "deck",          "40", "0.15"},
            {"2", "expansionJoint",  "deck",          "25", "0.15"},
            {"3", "railing",         "deck",          "20", "0.15"},
            {"4", "drainage",        "deck",          "15", "0.15"},
            {"5", "slab",            "superstructure","60", "0.40"},
            {"6", "hinge",           "superstructure","25", "0.40"},
            {"7", "bearing",         "superstructure","15", "0.40"},
            {"8", "pierAbutment",    "substructure",  "60", "0.40"},
            {"9", "foundation",      "substructure",  "40", "0.40"},
            {"10", "riverbed",       "ancillary",     "35", "0.05"},
            {"11", "revetment",      "ancillary",     "35", "0.05"},
            {"12", "otherAncillary", "ancillary",     "30", "0.05"},
    };

    /** 与 C.0.8-2 报告一致的部件最差构件 ECI：CI(j)。 */
    private static final int[] C082_CI = {2, 3, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0};

    private ResolvedPart part(String id, String key, String layer, String omega, String gamma) {
        ResolvedPart p = new ResolvedPart();
        p.setPartId(Long.valueOf(id));
        p.setPartKey(key);
        p.setLayerKey(layer);
        p.setOmega(Integer.parseInt(omega));
        p.setGamma(new BigDecimal(gamma));
        p.setOmegaEffective(new BigDecimal(omega));
        return p;
    }

    private ComponentEval componentEval(long partId, int eci) {
        ComponentEval eval = new ComponentEval();
        eval.setPartId(partId);
        eval.setEci(eci);
        eval.setEcl(eci <= 1 ? 1 : eci);
        return eval;
    }

    private ResolvedSpan c082Span(List<ComponentEval> evals) {
        return c082Span(evals, true);
    }

    private ResolvedSpan c082Span(List<ComponentEval> evals, boolean withDisease) {
        ResolvedSpan span = new ResolvedSpan();
        span.setUnitId(10L);
        span.setSpanId(20L);
        for (int i = 0; i < C082_PARTS.length; i++) {
            String[] c = C082_PARTS[i];
            span.getParts().add(part(c[0], c[1], c[2], c[3], c[4]));
            if (withDisease && C082_CI[i] > 0) {
                evals.add(componentEval(Long.parseLong(c[0]), C082_CI[i]));
            }
        }
        return span;
    }

    @Test
    void goldenC082_singleSpanBsci853_level2() {
        // 官方算例：Σ CI·ω·γ/5 = 73.5/5 = 14.7，BSCI=85.3，2 类
        List<ComponentEval> evals = new ArrayList<>();
        ResolvedSpan span = c082Span(evals);

        SpanEval result = evaluator.evaluate(span, evals);

        assertEquals(new BigDecimal("85.3"), result.getBsci());
        assertEquals(2, result.getBscl());
        assertEquals(new BigDecimal("85.3"), result.getBsciRaw());
        assertEquals(2, result.getRawLevel());
        assertFalse(result.isOverride());
    }

    @Test
    void noDiseaseSpan_bsci100Level1() {
        List<ComponentEval> evals = new ArrayList<>();
        ResolvedSpan span = c082Span(evals, false); // 无任何构件病害 → CI 全部 0

        SpanEval result = evaluator.evaluate(span, evals);

        assertEquals(new BigDecimal("100.0"), result.getBsci());
        assertEquals(1, result.getBscl());
    }

    @Test
    void absentPhantomPartUsesPresetCi3() {
        // 幻影支座：无构件 eval，part.absent=true、ci 预置 3、ω=15、γ=0.40
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(1L);
        ResolvedPart phantomBearing = part("7", "bearing", "superstructure", "15", "0.40");
        phantomBearing.setAbsent(true);
        phantomBearing.setCi(3);
        span.getParts().add(phantomBearing);

        SpanEval result = evaluator.evaluate(span, new ArrayList<>());

        // 3*15*0.40/5 = 3.6 → BSCI=96.4
        assertEquals(new BigDecimal("96.4"), result.getBsci());
        assertFalse(result.isOverride());
    }

    @Test
    void safetyOverrideEcl4CapsBsciAt60() {
        List<ComponentEval> evals = new ArrayList<>();
        ResolvedSpan span = c082Span(evals);

        ComponentEval trigger = new ComponentEval();
        trigger.setPartId(5L); // slab 主梁
        trigger.setElementUuid("el-x");
        trigger.setElementName("1号板");
        trigger.setEci(4);
        trigger.setEcl(4);
        trigger.setMainPart(true);
        trigger.setSafetyAffected(true);
        evals.add(trigger);

        SpanEval result = evaluator.evaluate(span, evals);

        assertTrue(result.isOverride());
        assertEquals(4, result.getBscl());
        // 公式原值本身可能高于 60，覆盖为 min(原值,60)
        assertEquals(new BigDecimal("60.0"), result.getBsci());
        assertEquals(4, result.getOverrideLevel());
        assertTrue(result.getTrigger().contains("3.3.3"));
    }

    @Test
    void safetyOverrideEcl5CapsBsciAt40() {
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(1L);
        span.getParts().add(part("8", "pierAbutment", "substructure", "60", "0.40"));

        ComponentEval trigger = new ComponentEval();
        trigger.setPartId(8L);
        trigger.setEci(5);
        trigger.setEcl(5);
        trigger.setMainPart(true);
        trigger.setSafetyAffected(true);

        SpanEval result = evaluator.evaluate(span, new ArrayList<>(java.util.Collections.singletonList(trigger)));

        assertTrue(result.isOverride());
        assertEquals(5, result.getBscl());
        assertEquals(new BigDecimal("40.0"), result.getBsci());
    }

    @Test
    void nonMainPartDoesNotTriggerOverride() {
        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(1L);
        span.getParts().add(part("1", "paving", "deck", "40", "0.15"));

        ComponentEval notMain = new ComponentEval();
        notMain.setPartId(1L);
        notMain.setEci(4);
        notMain.setEcl(4);
        notMain.setMainPart(false); // 桥面铺装非主要部件
        notMain.setSafetyAffected(true);

        SpanEval result = evaluator.evaluate(span, new ArrayList<>(java.util.Collections.singletonList(notMain)));
        assertFalse(result.isOverride());
    }

    @Test
    void bsclBandBoundaries() {
        // (90,100]→1，(75,90]→2，(60,75]→3，(40,60]→4，[0,40]→5
        assertEquals(1, SpanEvaluator.bsclBand(new BigDecimal("90.1")));
        assertEquals(1, SpanEvaluator.bsclBand(new BigDecimal("100.0")));
        assertEquals(2, SpanEvaluator.bsclBand(new BigDecimal("90.0")));
        assertEquals(2, SpanEvaluator.bsclBand(new BigDecimal("75.1")));
        assertEquals(3, SpanEvaluator.bsclBand(new BigDecimal("75.0")));
        assertEquals(3, SpanEvaluator.bsclBand(new BigDecimal("60.1")));
        assertEquals(4, SpanEvaluator.bsclBand(new BigDecimal("60.0")));
        assertEquals(4, SpanEvaluator.bsclBand(new BigDecimal("40.1")));
        assertEquals(5, SpanEvaluator.bsclBand(new BigDecimal("40.0")));
        assertEquals(5, SpanEvaluator.bsclBand(new BigDecimal("0.0")));
    }
}
