package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AssessInputRowValidator} 逐行值域校验单测：sync/upload 与直连 REST 共用的入口契约。
 */
class AssessInputRowValidatorTest {

    private final AssessInputRowValidator validator = new AssessInputRowValidator();

    // ---------------- 构件级人工判定 ----------------

    @Test
    void manual_validRow_defaultsSafetyAffectedAndKeepsNullScales() {
        AssessManualInput row = new AssessManualInput();
        row.setTaskId(1L);
        row.setElementUuid("elem-uuid-1");

        List<String> errors = validator.validateManualInput(row);

        assertTrue(errors.isEmpty());
        assertEquals(0, row.getSafetyAffected());
        assertNull(row.getEddi());
        assertNull(row.getEsdiOverride());
        assertNull(row.getEfi());
        assertNull(row.getEai());
    }

    @Test
    void manual_boundaryValuesPass() {
        AssessManualInput row = new AssessManualInput();
        row.setTaskId(1L);
        row.setElementUuid("elem-uuid-1");
        row.setEddi(2);
        row.setEsdiOverride(3);
        row.setEfi(2);
        row.setEai(-1);
        row.setSafetyAffected(1);

        List<String> errors = validator.validateManualInput(row);

        assertTrue(errors.isEmpty());
        assertEquals(2, row.getEddi());
        assertEquals(3, row.getEsdiOverride());
        assertEquals(-1, row.getEai());
    }

    @Test
    void manual_missingTaskAndElementReported() {
        List<String> errors = validator.validateManualInput(new AssessManualInput());

        assertTrue(errors.stream().anyMatch(m -> m.contains("taskId")));
        assertTrue(errors.stream().anyMatch(m -> m.contains("elementUuid")));
    }

    @Test
    void manual_outOfRangeScalesAndBadSafetyReported() {
        AssessManualInput row = new AssessManualInput();
        row.setTaskId(1L);
        row.setElementUuid("elem-uuid-1");
        row.setEddi(3);
        row.setEsdiOverride(4);
        row.setEfi(3);
        row.setEai(2);
        row.setSafetyAffected(9);

        List<String> errors = validator.validateManualInput(row);

        assertEquals(5, errors.size());
        assertTrue(errors.stream().anyMatch(m -> m.startsWith("eddi")));
        assertTrue(errors.stream().anyMatch(m -> m.startsWith("esdiOverride")));
        assertTrue(errors.stream().anyMatch(m -> m.startsWith("efi")));
        assertTrue(errors.stream().anyMatch(m -> m.startsWith("eai")));
        assertTrue(errors.stream().anyMatch(m -> m.contains("safetyAffected")));
    }

    // ---------------- 应设未设标记 ----------------

    @Test
    void absent_validRowWithNullScopes() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setTaskId(1L);
        row.setPartKey("bearing");

        List<String> errors = validator.validateAbsent(row);

        assertTrue(errors.isEmpty());
        assertNull(row.getUnitId());
        assertNull(row.getSpanId());
    }

    @Test
    void absent_layerWhitelistEnforced() {
        AssessAbsentMark ok = new AssessAbsentMark();
        ok.setTaskId(1L);
        ok.setPartKey("bearing");
        for (String layer : new String[]{"deck", "superstructure", "substructure", "ancillary"}) {
            ok.setLayerKey(layer);
            assertTrue(validator.validateAbsent(ok).isEmpty());
        }

        AssessAbsentMark bad = new AssessAbsentMark();
        bad.setTaskId(1L);
        bad.setPartKey("bearing");
        bad.setLayerKey("foundation");
        List<String> errors = validator.validateAbsent(bad);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("layerKey"));
    }

    @Test
    void absent_missingPartKeyReported() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setTaskId(1L);
        List<String> errors = validator.validateAbsent(row);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("partKey"));
    }

    // ---------------- 单项控制 ----------------

    @Test
    void singleControl_validRowDefaultsHit() {
        AssessSingleControlMark row = new AssessSingleControlMark();
        row.setTaskId(1L);
        row.setIndicatorNo(16);

        List<String> errors = validator.validateSingleControl(row);

        assertTrue(errors.isEmpty());
        assertEquals(1, row.getHit());
    }

    @Test
    void singleControl_indicatorRangeOneToSixteen() {
        AssessSingleControlMark row = new AssessSingleControlMark();
        row.setTaskId(1L);
        row.setIndicatorNo(0);
        row.setHit(0);
        List<String> errors = validator.validateSingleControl(row);
        assertTrue(errors.stream().anyMatch(m -> m.contains("指标序号")));

        row.setIndicatorNo(17);
        errors = validator.validateSingleControl(row);
        assertTrue(errors.stream().anyMatch(m -> m.contains("指标序号")));
    }

    @Test
    void singleControl_badHitReported() {
        AssessSingleControlMark row = new AssessSingleControlMark();
        row.setTaskId(1L);
        row.setIndicatorNo(1);
        row.setHit(2);
        List<String> errors = validator.validateSingleControl(row);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("hit"));
    }

    // ---------------- evidence 截断（三表共用） ----------------

    @Test
    void evidenceTruncatedTo500WithWarning() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append('x');
        }
        AssessManualInput row = new AssessManualInput();
        row.setTaskId(1L);
        row.setElementUuid("elem-uuid-1");
        row.setEvidence(sb.toString());

        List<String> errors = validator.validateManualInput(row);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("已截断"));
        assertEquals(500, row.getEvidence().length());
    }
}
