package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiseaseComparisonServiceTest {

    @Test
    void quantityUsesDiseaseQuantityInsteadOfRecordCount() {
        Disease first = disease(2);
        Disease second = disease(3);

        assertEquals(5, DiseaseComparisonService.sumQuantity(List.of(first, second)));
    }

    @Test
    void lengthRangeUsesMidpointAndDoesNotInventZeroWidth() throws Exception {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setLengthRangeStart(new BigDecimal("2"));
        detail.setLengthRangeEnd(new BigDecimal("4"));

        Disease disease = disease(4);
        disease.setDiseaseDetails(List.of(detail));

        assertEquals("总长度12.00m", severity(disease));
    }

    @Test
    void widthRangeDoesNotCreateLengthData() throws Exception {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setCrackWidthRangeStart(new BigDecimal("0.10"));
        detail.setCrackWidthRangeEnd(new BigDecimal("0.20"));

        Disease disease = disease(2);
        disease.setDiseaseDetails(List.of(detail));

        assertEquals("宽度介于0.10-0.20mm之间", severity(disease));
    }

    @Test
    void exactLengthIncludesAllThreeLengthFields() throws Exception {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setLength1(new BigDecimal("1.1"));
        detail.setLength2(new BigDecimal("2.2"));
        detail.setLength3(new BigDecimal("3.3"));

        Disease disease = disease(1);
        disease.setDiseaseDetails(List.of(detail));

        assertEquals("总长度6.60m", severity(disease));
    }

    @Test
    void averageAreaIsMultipliedByQuantityOnce() throws Exception {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setAreaLength(new BigDecimal("0.5"));
        detail.setAreaWidth(new BigDecimal("0.2"));
        detail.setAreaIdentifier(1);

        Disease disease = disease(3);
        disease.setDiseaseDetails(List.of(detail));

        assertEquals("面积0.3000㎡", severity(disease));
    }

    @Test
    void exactLengthDoesNotAlsoAddRangeLength() throws Exception {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setLength1(new BigDecimal("3"));
        detail.setLengthRangeStart(new BigDecimal("2"));
        detail.setLengthRangeEnd(new BigDecimal("4"));

        Disease disease = disease(1);
        disease.setDiseaseDetails(List.of(detail));

        assertEquals("总长度3.00m", severity(disease));
    }

    @Test
    void developmentStatusKeepsAllSupportedTrendStatesAndQuantities() throws Exception {
        Method method = DiseaseComparisonService.class.getDeclaredMethod(
                "generateDevelopmentStatus", List.class, List.class, List.class, List.class, List.class);
        method.setAccessible(true);

        String status = (String) method.invoke(new DiseaseComparisonService(),
                List.of(disease(1)),
                List.of(disease(3)),
                List.of(disease(2)),
                List.of(disease(4)),
                List.of(disease(5)));

        assertEquals("修复1条，部分维修2条，未找到4条，发展3条，新增5条", status);
    }

    @Test
    void averageLengthFromImportedDescriptionIsAppliedToQuantity() throws Exception {
        DiseaseDetail measured = new DiseaseDetail();
        measured.setLength1(new BigDecimal("1.00"));
        DiseaseDetail misplaced = new DiseaseDetail();
        misplaced.setLength1(new BigDecimal("0.30"));

        Disease disease = disease(8);
        disease.setDescription("横向裂缝8条，L均=0.50m，W=0.10mm");
        disease.setDiseaseDetails(List.of(measured, misplaced, new DiseaseDetail()));

        assertEquals("总长度4.00m", severity(disease));
    }

    @Test
    void averageAreaMarkerFallsBackWhenImportedIdentifierIsOrdinary() throws Exception {
        DiseaseDetail detail = area("0.12", "0.10", 0);
        Disease disease = disease(4);
        disease.setDescription("剥落4处，S均=0.12×0.10m²");
        disease.setDiseaseDetails(List.of(detail, new DiseaseDetail()));

        assertEquals("面积0.0480㎡", severity(disease));
    }

    @Test
    void explicitAreaMultiplierUsesStatedSizedCount() throws Exception {
        Disease disease = disease(3);
        disease.setDescription("剥落3处，S=0.38×0.28m²×2");
        disease.setDiseaseDetails(List.of(area("0.38", "0.28", 0), new DiseaseDetail()));

        assertEquals("面积0.2128㎡", severity(disease));
    }

    @Test
    void indexedAreaIsNotInventedForMissingDetails() throws Exception {
        Disease disease = disease(3);
        disease.setDescription("剥落3处，S_1=0.25×0.20m²");
        disease.setDiseaseDetails(List.of(area("0.25", "0.20", 0), new DiseaseDetail()));

        assertEquals("面积0.0500㎡", severity(disease));
    }

    private static Disease disease(int quantity) {
        Disease disease = new Disease();
        disease.setQuantity(quantity);
        return disease;
    }

    private static DiseaseDetail area(String length, String width, int identifier) {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setAreaLength(new BigDecimal(length));
        detail.setAreaWidth(new BigDecimal(width));
        detail.setAreaIdentifier(identifier);
        return detail;
    }

    private static String severity(Disease disease) throws Exception {
        DiseaseComparisonService service = new DiseaseComparisonService();
        Method method = DiseaseComparisonService.class
                .getDeclaredMethod("generateSeverityDescription", List.class);
        method.setAccessible(true);
        return (String) method.invoke(service, List.of(disease));
    }
}
