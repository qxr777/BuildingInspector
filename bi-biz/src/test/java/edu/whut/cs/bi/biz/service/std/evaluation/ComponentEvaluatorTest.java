package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentEvaluatorTest {

    private ComponentEvaluator evaluator;
    private EvalWarnings warnings;

    @BeforeEach
    void setUp() {
        evaluator = new ComponentEvaluator(new EvaluationMath());
        warnings = new EvalWarnings();
    }

    private Disease disease(int level) {
        Disease d = new Disease();
        d.setLevel(level);
        return d;
    }

    private V2ComponentInput input(Integer eddi, Integer efi, Integer eai) {
        V2ComponentInput i = new V2ComponentInput();
        i.setEddi(eddi);
        i.setEfi(efi);
        i.setEai(eai);
        return i;
    }

    @Test
    void allZeroWhenNoDiseaseAndNoInput() {
        ComponentEval eval = evaluator.evaluate(1L, "mainGirder", null, null, true, warnings);
        assertEquals(0, eval.getEsdi());
        assertEquals(0, eval.getEddi());
        assertEquals(0, eval.getEdi());
        assertEquals(0, eval.getEfi());
        assertEquals(0, eval.getEai());
        assertEquals(0, eval.getEci());
        assertEquals(1, eval.getEcl());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void esdiTakesMaxDiseaseLevel() {
        ComponentEval eval = evaluator.evaluate(1L, "mainGirder",
                Arrays.asList(disease(1), disease(3), disease(2)), null, true, warnings);
        assertEquals(3, eval.getEsdi());
        assertEquals(3, eval.getEdi());
        assertEquals(3, eval.getEci());
        assertEquals(3, eval.getEcl());
    }

    @Test
    void ediUsesEddiWhenGreaterThanEsdi() {
        ComponentEval eval = evaluator.evaluate(1L, "mainGirder",
                Arrays.asList(disease(1)), input(2, 0, 0), true, warnings);
        assertEquals(1, eval.getEsdi());
        assertEquals(2, eval.getEddi());
        assertEquals(2, eval.getEdi());
        assertEquals(2, eval.getEcl());
    }

    @Test
    void eciCapsAtFive() {
        // EDI 3 + EFI 2 + EAI 1 = 6 → 5
        ComponentEval eval = evaluator.evaluate(1L, "mainGirder",
                Arrays.asList(disease(3)), input(0, 2, 1), true, warnings);
        assertEquals(5, eval.getEci());
        assertEquals(5, eval.getEcl());
    }

    @Test
    void eaiNegativeReducesEci() {
        // EDI 3 + EAI -1 = 2
        ComponentEval eval = evaluator.evaluate(1L, "mainGirder",
                Arrays.asList(disease(3)), input(0, 0, -1), true, warnings);
        assertEquals(-1, eval.getEai());
        assertEquals(2, eval.getEci());
        assertEquals(2, eval.getEcl());
    }

    @Test
    void eciFloorsAtZeroWithNegativeEai() {
        // EDI 0 + EAI -1 = -1 → clamp 0
        ComponentEval eval = evaluator.evaluate(1L, "mainGirder",
                null, input(0, 0, -1), true, warnings);
        assertEquals(0, eval.getEci());
        assertEquals(1, eval.getEcl());
    }

    @Test
    void eaiForcedZeroForNonMainPart() {
        ComponentEval eval = evaluator.evaluate(1L, "diaphragm",
                null, input(0, 0, 1), false, warnings);
        assertEquals(0, eval.getEai());
        assertEquals(0, eval.getEci());
        assertEquals(1, warnings.getWarnings().size());
    }

    @Test
    void eclOneForEciZeroOrOne() {
        assertEquals(1, evaluator.evaluate(1L, "mainGirder",
                Arrays.asList(disease(1)), null, true, warnings).getEcl());
    }
}
