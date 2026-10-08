package edu.whut.cs.bi.biz.service.v2;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.std.evaluation.StructureResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2InputRowValidatorTest {

    private static final Long PART_ID = 300L;

    @Mock private BiObjectMapper biObjectMapper;
    @Mock private StructureResolver structureResolver;

    private V2InputRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new V2InputRowValidator(biObjectMapper, structureResolver);
    }

    private V2ComponentInput componentRow() {
        V2ComponentInput row = new V2ComponentInput();
        row.setComponentId(400L);
        return row;
    }

    @Test
    void nullScalesAndSafetyAreDefaulted() {
        V2ComponentInput row = componentRow();

        List<String> errors = validator.validateComponent(row);

        assertTrue(errors.isEmpty());
        assertEquals(0, row.getEddi());
        assertEquals(0, row.getEfi());
        assertEquals(0, row.getEai());
        assertEquals(0, row.getSafetyAffected());
    }

    @Test
    void componentScalesAcceptBoundariesIncludingNegativeEai() {
        V2ComponentInput row = componentRow();
        row.setEddi(2);
        row.setEfi(2);
        row.setEai(-1);

        List<String> errors = validator.validateComponent(row);
        assertTrue(errors.isEmpty());
    }

    @Test
    void componentScalesOutOfRangeAreReported() {
        V2ComponentInput row = componentRow();
        row.setEddi(3);
        row.setEfi(-1);
        row.setEai(-2);

        List<String> errors = validator.validateComponent(row);
        assertEquals(3, errors.size());
    }

    @Test
    void componentMissingIdAndBadSafetyAreReported() {
        V2ComponentInput row = new V2ComponentInput();
        row.setSafetyAffected(9);

        List<String> errors = validator.validateComponent(row);
        assertEquals(2, errors.size());
    }

    @Test
    void absentWithNullSpanAndTemplatePartPasses() {
        V2AbsentMark row = new V2AbsentMark();
        row.setPartId(PART_ID);
        BiObject partNode = new BiObject();
        partNode.setId(PART_ID);
        when(biObjectMapper.selectBiObjectById(PART_ID)).thenReturn(partNode);
        when(structureResolver.isTemplatePart(partNode, "B03")).thenReturn(true);

        List<String> errors = validator.validateAbsent(row, "B03");
        assertTrue(errors.isEmpty());
    }

    @Test
    void absentMissingPartIdIsRejected() {
        V2AbsentMark row = new V2AbsentMark();

        List<String> errors = validator.validateAbsent(row, "B03");
        assertEquals(1, errors.size());
    }

    @Test
    void absentNonTemplatePartIsRejected() {
        V2AbsentMark row = new V2AbsentMark();
        row.setPartId(PART_ID);
        BiObject partNode = new BiObject();
        partNode.setId(PART_ID);
        when(biObjectMapper.selectBiObjectById(PART_ID)).thenReturn(partNode);
        when(structureResolver.isTemplatePart(partNode, "B03")).thenReturn(false);

        List<String> errors = validator.validateAbsent(row, "B03");
        assertEquals(1, errors.size());
    }

    @Test
    void controlIndicatorsAccept1And16() {
        assertTrue(validator.validateControl(control(1)).isEmpty());
        assertTrue(validator.validateControl(control(16)).isEmpty());
    }

    @Test
    void controlIndicatorsReject0And17() {
        assertEquals(1, validator.validateControl(control(0)).size());
        assertEquals(1, validator.validateControl(control(17)).size());
    }

    @Test
    void controlNullHitDefaultsAndBadHitReported() {
        V2SingleControlMark row = control(1);
        row.setHit(null);
        assertTrue(validator.validateControl(row).isEmpty());
        assertEquals(0, row.getHit());

        V2SingleControlMark bad = control(2);
        bad.setHit(5);
        assertEquals(1, validator.validateControl(bad).size());
    }

    @Test
    void longEvidenceIsTruncatedTo500() {
        V2SingleControlMark row = control(1);
        row.setEvidence("x".repeat(600));

        assertTrue(validator.validateControl(row).isEmpty());
        assertEquals(500, row.getEvidence().length());
    }

    private V2SingleControlMark control(int indicatorNo) {
        V2SingleControlMark row = new V2SingleControlMark();
        row.setIndicatorNo(indicatorNo);
        row.setHit(1);
        return row;
    }
}
