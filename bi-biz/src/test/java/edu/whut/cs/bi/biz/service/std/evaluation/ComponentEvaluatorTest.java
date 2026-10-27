package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentEvaluatorTest {

    private ComponentEvaluator evaluator;
    private EvalWarnings warnings;

    @BeforeEach
    void setUp() {
        evaluator = new ComponentEvaluator(new EvaluationMath());
        warnings = new EvalWarnings();
    }

    private ComponentInput.ComponentInputBuilder base() {
        return ComponentInput.builder()
                .elementId(100L).elementUuid("el-1").elementName("1号板")
                .unitId(10L).spanId(20L).partId(30L)
                .partKey("slab").layerKey("superstructure");
    }

    @Test
    void noDiseaseNoManualInput_allZeroEcl1() {
        ComponentEval eval = evaluator.evaluate(base().build(), warnings);

        assertEquals(0, eval.getEddi());
        assertEquals(0, eval.getEsdi());
        assertEquals(0, eval.getEdi());
        assertEquals(0, eval.getEfi());
        assertEquals(0, eval.getEai());
        assertEquals(0, eval.getEci());
        assertEquals(1, eval.getEcl()); // 表 3.2.6：0/1 → 1 类
        assertEquals(ComponentEval.SOURCE_AUTO, eval.getEsdiSource());
        assertEquals(ComponentEval.SOURCE_AUTO, eval.getEddiSource());
    }

    @Test
    void esdiTakesMaxStructuralScaleCappedAt3() {
        ComponentEval eval = evaluator.evaluate(
                base().structuralScales(Arrays.asList(1, 3, 2, 3)).build(), warnings);

        assertEquals(3, eval.getEsdi());
        assertEquals(3, eval.getEdi());
        assertEquals(3, eval.getEci());
        assertEquals(3, eval.getEcl());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void manualEsdiOverrideBeatsDiseaseMax() {
        ComponentEval eval = evaluator.evaluate(base()
                .structuralScales(Collections.singletonList(1))
                .esdiOverride(3)
                .build(), warnings);

        assertEquals(3, eval.getEsdi());
        assertEquals(ComponentEval.SOURCE_MANUAL, eval.getEsdiSource());
    }

    @Test
    void absentPhantomComponent_ediFixedAt3() {
        ComponentEval eval = evaluator.evaluate(base()
                .elementId(null).elementUuid(null).partId(null)
                .absent(true)
                .structuralScales(Arrays.asList(1, 2))
                .efiManual(2).eaiManual(1) // 幻影件忽略一切分项
                .build(), warnings);

        assertTrue(eval.isAbsent());
        assertEquals(0, eval.getEsdi());
        assertEquals(0, eval.getEddi());
        assertEquals(3, eval.getEdi());
        assertEquals(0, eval.getEfi());
        assertEquals(0, eval.getEai());
        assertEquals(3, eval.getEci());
        assertEquals(3, eval.getEcl());
        assertEquals(ComponentEval.SOURCE_ABSENT, eval.getEsdiSource());
        assertEquals(ComponentEval.SOURCE_ABSENT, eval.getEddiSource());
    }

    @Test
    void ediIsMaxOfEddiAndEsdi() {
        ComponentEval eval = evaluator.evaluate(base()
                .eddiManual(2).structuralScales(Collections.singletonList(1))
                .build(), warnings);

        assertEquals(2, eval.getEddi());
        assertEquals(1, eval.getEsdi());
        assertEquals(2, eval.getEdi());
        assertEquals(ComponentEval.SOURCE_MANUAL, eval.getEddiSource());
    }

    @Test
    void mainPartAcceptsNegativeEai() {
        ComponentEval eval = evaluator.evaluate(base()
                .mainPart(true)
                .eddiManual(2).eaiManual(-1)
                .build(), warnings);

        assertEquals(-1, eval.getEai());
        assertEquals(1, eval.getEci()); // 2 - 1
        assertTrue(warnings.isEmpty());
    }

    @Test
    void nonMainPartEaiForcedToZeroWithWarning() {
        ComponentEval eval = evaluator.evaluate(base()
                .mainPart(false).eaiManual(1)
                .build(), warnings);

        assertEquals(0, eval.getEai());
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.getWarnings().get(0).contains("非主要部件"));
    }

    @Test
    void eciClampedAt5() {
        ComponentEval eval = evaluator.evaluate(base()
                .mainPart(true)
                .structuralScales(Collections.singletonList(3)) // EDI=3
                .efiManual(2)   // +2
                .eaiManual(1)   // +1 → 6 截断为 5
                .build(), warnings);

        assertEquals(5, eval.getEci());
        assertEquals(5, eval.getEcl());
    }

    @Test
    void eclMapping_eci1to4() {
        assertEquals(1, evaluator.evaluate(base().build(), warnings).getEcl());
        assertEquals(2, evaluator.evaluate(base().structuralScales(Arrays.asList(2)).build(),
                new EvalWarnings()).getEcl());
        assertEquals(4, evaluator.evaluate(base().mainPart(true)
                .structuralScales(Arrays.asList(3)).efiManual(1).build(),
                new EvalWarnings()).getEcl()); // 3+1=4
    }

    @Test
    void safetyAffectedFlagCarriedThrough() {
        ComponentEval without = evaluator.evaluate(base().build(), warnings);
        assertFalse(without.isSafetyAffected());

        ComponentEval with = evaluator.evaluate(base().safetyAffected(true).build(), warnings);
        assertTrue(with.isSafetyAffected());
    }
}
