package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.DiseaseType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DroneDiseaseImportParserTest {

    @Test
    public void rejectsWrongTemplateHeaders() {
        List<String> wrong = Arrays.asList(DroneDiseaseImportParser.EXPECTED_HEADERS);
        wrong = new ArrayList<>(wrong);
        wrong.set(1, "桥梁名称");

        List<String> errors = DroneDiseaseImportParser.validateHeaders(wrong);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("表头第2列"));
        assertTrue(errors.get(0).contains("无人机病害导入模板"));
    }

    @Test
    public void acceptsExpectedTemplateHeaders() {
        List<String> headers = Arrays.asList(DroneDiseaseImportParser.EXPECTED_HEADERS);
        assertTrue(DroneDiseaseImportParser.validateHeaders(headers).isEmpty());
    }

    @Test
    public void keepsOnlyColumnsEnabledByDiseaseType() {
        DroneDiseaseImportParser.Input input = baseInput();
        input.deformation = "0.01";
        input.heightDepth = "0.10";
        input.areaLength = "1.2";
        input.areaWidth = "0.3";

        DroneDiseaseImportParser.Output output = DroneDiseaseImportParser.parse(input);

        assertTrue(output.errors.isEmpty(), output.errors::toString);
        assertEquals(new BigDecimal("0.50"), output.detail.getLength1());
        assertEquals(new BigDecimal("0.04"), output.detail.getCrackWidth());
        assertEquals(new BigDecimal("0.10"), output.detail.getHeightDepth());
        assertEquals(new BigDecimal("1.2"), output.detail.getAreaLength());
        assertEquals(Integer.valueOf(0), output.detail.getAreaIdentifier());
        assertEquals("4#节段", output.detail.getReference1Location());
        assertEquals(new BigDecimal("0.8"), output.detail.getReference1LocationStart());
        assertNull(output.detail.getReference1LocationEnd());
        assertNull(output.detail.getDeformation());
        assertEquals("1", output.participateAssess);
        assertEquals(2, output.level);
    }

    @Test
    public void acceptsReferenceEndWhenProvided() {
        DroneDiseaseImportParser.Input input = baseInput();
        input.reference1End = "1.2";

        DroneDiseaseImportParser.Output output = DroneDiseaseImportParser.parse(input);

        assertTrue(output.errors.isEmpty(), output.errors::toString);
        assertEquals(new BigDecimal("1.2"), output.detail.getReference1LocationEnd());
    }

    @Test
    public void recordsZeroLevelWhenDiseaseDoesNotParticipate() {
        DroneDiseaseImportParser.Input input = baseInput();
        input.participate = "否";
        input.level = "";

        DroneDiseaseImportParser.Output output = DroneDiseaseImportParser.parse(input);

        assertTrue(output.errors.isEmpty(), output.errors::toString);
        assertEquals("0", output.participateAssess);
        assertEquals(0, output.level);
    }

    @Test
    public void usesRangesWhenQuantityReachesThreshold() {
        DroneDiseaseImportParser.Input input = baseInput();
        input.quantity = "10";
        input.length = "0.50~1.20";
        input.crackWidth = "0.04~0.15";

        DroneDiseaseImportParser.Output output = DroneDiseaseImportParser.parse(input);

        assertTrue(output.errors.isEmpty(), output.errors::toString);
        assertEquals(new BigDecimal("0.50"), output.detail.getLengthRangeStart());
        assertEquals(new BigDecimal("0.04"), output.detail.getCrackWidthRangeStart());
        assertNull(output.detail.getLength1());
    }

    private DroneDiseaseImportParser.Input baseInput() {
        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setName("裂缝");
        diseaseType.setSelectColumn(1887);
        diseaseType.setMinScale(1);
        diseaseType.setMaxScale(4);
        diseaseType.setThreshold(10);

        DroneDiseaseImportParser.Input input = new DroneDiseaseImportParser.Input();
        input.rowNumber = 2;
        input.diseaseType = diseaseType;
        input.participate = "是";
        input.quantity = "1";
        input.units = "条";
        input.level = "2";
        input.crackFeature = "纵向";
        input.length = "0.50";
        input.crackWidth = "0.04";
        input.heightDepth = "0.10";
        input.areaLength = "1.20";
        input.areaWidth = "0.30";
        input.angle = "15";
        input.reference1Name = "4#节段";
        input.reference1Start = "0.8";
        input.reference2Name = "左侧腹板";
        input.reference2Start = "3.5";
        input.developmentTrend = "新增";
        input.nature = "非结构病害";
        input.description = "纵向裂缝1条";
        return input;
    }
}
