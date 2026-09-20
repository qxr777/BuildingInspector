package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProgrammaticDiseaseSummaryServiceTest {

    private final ProgrammaticDiseaseSummaryService service = new ProgrammaticDiseaseSummaryService();

    @ParameterizedTest(name = "Excel row {0}: {1} {2} / {3} → {4}")
    @CsvFileSource(resources = "/disease-summary-position-rules.tsv", delimiter = '\t')
    public void followsEveryExcelPositionGroup(int sourceRow, String component, String first,
                                               String second, String expectedPosition) {
        assertEquals(component + "：" + expectedPosition + "剥落3处。",
                service.summarize(List.of(
                        disease(component, first, "剥落", null, 1, "处"),
                        disease(component, second, "剥落", null, 2, "处")), "上部承重构件"),
                "规则表第 " + sourceRow + " 行");
    }

    @Test
    public void upperBearingMergesMeasurementsWithoutChangingOriginalPositions() {
        Disease left = disease("小箱梁", "结构#左腹板", "裂缝", "纵向", 1, "条",
                detail("1", "0.10", null, null, null));
        Disease right = disease("小箱梁", "右腹板", "裂缝", "纵向", 1, "条",
                detail("2", "0.20", null, null, null));
        assertEquals("小箱梁：腹板纵向裂缝2条，总长度3m，宽度介于0.10～0.20mm。",
                service.summarize(List.of(left, right), "上部承重结构"));
        assertEquals("结构#左腹板", left.getPosition());
        assertEquals("右腹板", right.getPosition());
    }

    @Test
    public void boxInteriorExteriorAndDifferentCategoriesRemainSeparate() {
        List<Disease> diseases = List.of(
                disease("箱梁", "箱内左腹板", "剥落", null, 1, "处"),
                disease("箱梁", "箱内右腹板", "剥落", null, 2, "处"),
                disease("箱梁", "箱内左腹板", "露筋", null, 1, "处"),
                disease("箱梁", "箱外左腹板", "剥落", null, 4, "处"),
                disease("箱梁", "箱外右腹板", "剥落", null, 5, "处"),
                disease("箱梁", "箱内底板", "剥落", null, 1, "处"));
        assertEquals("箱梁：箱内腹板剥落3处；露筋1处；箱外腹板剥落9处；箱内底板剥落1处。",
                service.summarize(diseases, "上部承重构件"));
    }

    @Test
    public void unlistedStructuresAndPositionsAreNotMerged() {
        assertEquals("1）盖梁：左腹板剥落1处；右腹板剥落2处。\n2）小箱梁：左翼缘板剥落1处；右翼缘板剥落2处。",
                service.summarize(List.of(
                        disease("盖梁", "左腹板", "剥落", null, 1, "处"),
                        disease("盖梁", "右腹板", "剥落", null, 2, "处"),
                        disease("小箱梁", "左翼缘板", "剥落", null, 1, "处"),
                        disease("小箱梁", "右翼缘板", "剥落", null, 2, "处")), "上部承重构件"));
    }

    @Test
    public void upperBearingKeepsPositionGroups() {
        Disease bottom = disease("小箱梁", "底板", "连续梁桥裂缝", "纵向", 1, "条",
                detail("1", "0.10", null, null, null));
        Disease web = disease("小箱梁", "腹板", "连续梁桥裂缝", "纵向", 1, "条",
                detail("2", "0.20", null, null, null));

        assertEquals(
                "小箱梁：底板纵向裂缝1条，长度1m，宽度为0.10mm；"
                        + "腹板纵向裂缝1条，长度2m，宽度为0.20mm。",
                service.summarize(List.of(bottom, web), "上部承重构件"));
    }

    @Test
    public void generalSectionMergesSameCategoryAcrossPositions() {
        Disease first = disease("横隔板", "大桩号面", "裂缝", "竖向", 1, "条",
                detail("1", "0.10", null, null, null));
        Disease second = disease("横隔板", "小桩号面", "裂缝", "竖向", 1, "条",
                detail("2", "0.20", null, null, null));

        assertEquals(
                "横隔板：竖向裂缝2条，总长度3m，宽度介于0.10～0.20mm。",
                service.summarize(List.of(first, second), "上部一般构件"));
    }

    @Test
    public void lengthRangeProducesTruthfulTotalRange() {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setLengthRangeStart(new BigDecimal("2.00"));
        detail.setLengthRangeEnd(new BigDecimal("2.20"));
        Disease disease = disease("横隔板", "大桩号面", "裂缝", "竖向", 10, "条", detail);

        assertEquals(
                "横隔板：竖向裂缝10条，总长度介于20～22m。",
                service.summarize(List.of(disease), "上部一般构件"));
    }

    @Test
    public void averageAreaAndAngleAreWritten() {
        DiseaseDetail area = detail(null, null, "2", "0.5", null);
        area.setAreaIdentifier(1);
        Disease peeling = disease("小箱梁", "底板", "剥落", null, 3, "处", area);

        DiseaseDetail angle10 = detail(null, null, null, null, "10");
        DiseaseDetail angle20 = detail(null, null, null, null, "20");
        Disease support = disease("支座", "支座", "板式支座位置剪切超限", null, 2, "处",
                angle10, angle20);

        assertEquals(
                "小箱梁：剥落3处，总面积3m²。",
                service.summarize(List.of(peeling), "上部一般构件"));
        assertEquals(
                "支座：板式支座位置剪切超限2处，角度介于10～20°。",
                service.summarize(List.of(support), "支座"));
    }

    @Test
    public void unsupportedMeasurementsFallBackToQuantityOnly() {
        DiseaseDetail detail = new DiseaseDetail();
        detail.setHeightDepth(new BigDecimal("0.5"));
        detail.setDeformation(new BigDecimal("1.2"));
        Disease disease = disease("盖梁", "外侧面", "混凝土缺损", null, 2, "处", detail);

        assertEquals(
                "盖梁：混凝土缺损2处。",
                service.summarize(List.of(disease), "桥墩"));
    }

    @Test
    public void voidRatioIsWrittenAsDegreeRange() {
        DiseaseDetail ten = new DiseaseDetail();
        ten.setNumeratorRatio(10);
        ten.setDenominatorRatio(100);
        Disease first = disease("支座", "支座", "板式支座位置脱空", null, 1, "处", ten);

        DiseaseDetail twenty = new DiseaseDetail();
        twenty.setNumeratorRatio(20);
        twenty.setDenominatorRatio(100);
        Disease second = disease("支座", "支座", "板式支座位置脱空", null, 1, "处", twenty);

        assertEquals(
                "支座：板式支座位置脱空2处，程度介于10～20%。",
                service.summarize(List.of(first, second), "支座"));
    }

    @Test
    public void voidPercentInDescriptionIsUsedWhenDetailHasNoRatio() {
        Disease first = disease("支座", "支座", "板式支座位置脱空", null, 1, "处");
        first.setDescription("L-4-3-4#支座板式支座位置脱空1处，脱空20%");
        Disease second = disease("支座", "支座", "板式支座位置脱空", null, 1, "处");
        second.setDescription("L-40-40-3#支座板式支座位置脱空1处，上部脱空10%");

        assertEquals(
                "支座：板式支座位置脱空2处，程度介于10～20%。",
                service.summarize(List.of(first, second), "支座"));
    }

    @Test
    public void specialLayoutUnitsAreConvertedForReport() {
        DiseaseDetail detail = detail("1000", "12", "20", "30", null);
        Disease disease = disease("节段", "箱内底板", "裂缝", "纵向", 1, "条", detail);
        disease.setDescription("节段箱内底板纵向裂缝1条，L=1000mm，W=12mm，S=20×30cm²");

        assertEquals(
                "节段：箱内底板纵向裂缝1条，长度1m，面积0.06m²，宽度为12.00mm。",
                service.summarize(List.of(disease), "上部承重构件"));
    }

    @Test
    public void meshCrackUsesPlaceUnitEvenIfStoredAsStrip() {
        DiseaseDetail area = detail(null, null, "3.32", "0.45", null);
        Disease first = disease("盖梁", "外翼板", "网状裂缝", "网状", 1, "条", area);
        DiseaseDetail secondArea = detail(null, null, "1.20", "1.00", null);
        Disease second = disease("盖梁", "大桩号面", "网状裂缝", "网状", 1, "条", secondArea);

        assertEquals(
                "盖梁：网状裂缝2处，总面积2.694m²。",
                service.summarize(List.of(first, second), "桥墩"));
    }

    @Test
    public void crackCategoriesAreSummarizedBeforeOthers() {
        Disease peeling = disease("盖梁", "外侧面", "剥落", null, 2, "处",
                detail(null, null, "0.20", "0.10", null));
        Disease mesh = disease("盖梁", "大桩号面", "网状裂缝", "网状", 1, "处",
                detail(null, null, "1.20", "1.00", null));
        Disease vertical = disease("盖梁", "外侧面", "裂缝", "竖向", 1, "条",
                detail("0.22", "0.13", null, null, null));

        assertEquals(
                "盖梁：网状裂缝1处，面积1.2m²；竖向裂缝1条，长度0.22m，宽度为0.13mm；"
                        + "剥落2处，总面积0.02m²。",
                service.summarize(List.of(peeling, mesh, vertical), "桥墩"));
    }

    @Test
    public void otherCategoryMergesDescriptionsAcrossComponentNumbers() {
        Disease first = disease("盖梁", "顶面", "其他（最大标度3）", null, 1, "处");
        first.setDescription("L-30#盖梁顶面建渣堆积1处。");
        Disease second = disease("盖梁", "顶面", "其他（最大标度3）", null, 1, "处");
        second.setDescription("L-31#盖梁顶面建渣堆积1处");

        assertEquals(
                "盖梁：其他（最大标度3）2处，其中盖梁顶面建渣堆积2处。",
                service.summarize(List.of(first, second), "桥墩"));
    }

    @Test
    public void screenshotDescriptionsMergeIntoTenTopAndThreeSideOccurrences() {
        List<Disease> diseases = new java.util.ArrayList<>();
        for (String number : List.of("3", "7", "7", "23", "27", "51", "62", "78", "82", "86")) {
            Disease item = disease("盖梁", "顶面", "其他（最大标度3）", null, 1, "处");
            item.setDescription("L-" + number + "#盖梁顶面建渣堆积1处");
            diseases.add(item);
        }
        for (String number : List.of("65", "69", "73")) {
            Disease item = disease("盖梁", "大桩号面", "其他（最大标度3）", null, 1, "处");
            item.setDescription("L-" + number + "#盖梁大桩号面建渣堆积1处");
            diseases.add(item);
        }
        assertEquals("盖梁：其他（最大标度3）13处，其中盖梁顶面建渣堆积10处、盖梁大桩号面建渣堆积3处。",
                service.summarize(diseases, "桥墩"));
        assertEquals("L-3#盖梁顶面建渣堆积1处", diseases.get(0).getDescription());
    }

    @Test
    public void sumsExplicitCountsAndAcceptsFullWidthHashOrNoCode() {
        Disease first = disease("盖梁", "顶面", "其他", null, 2, "处");
        first.setDescription("L-3＃盖梁顶面建渣堆积 2 处。");
        Disease second = disease("盖梁", "顶面", "其他", null, 3, "处");
        second.setDescription("盖梁顶面建渣堆积3处；");
        assertEquals("盖梁：其他5处，其中盖梁顶面建渣堆积5处。",
                service.summarize(List.of(first, second), "桥墩"));
    }

    @Test
    public void preservesAmbiguousDescriptionsAndMissingDescriptions() {
        List<Disease> diseases = new java.util.ArrayList<>();
        List<String> descriptions = List.of("L-1#盖梁顶面建渣堆积2处",
                "L-2#盖梁顶面破损1处，长度2m", "L-3#顶面和L-4#侧面破损1处",
                "L-5#盖梁顶面建渣堆积1个", "L-6#盖梁顶面建渣堆积");
        for (String description : descriptions) {
            Disease item = disease("盖梁", "顶面", "其他", null, 1, "处");
            item.setDescription(description);
            diseases.add(item);
        }
        diseases.add(disease("盖梁", "顶面", "其他", null, 1, "处"));
        assertEquals("盖梁：其他6处，其中" + String.join("、", descriptions) + "。",
                service.summarize(diseases, "桥墩"));
    }

    private Disease disease(String component, String position, String typeName, String crackType,
                            int quantity, String unit, DiseaseDetail... details) {
        Disease disease = new Disease();
        disease.setBiObjectName(component);
        disease.setPosition(position);
        disease.setCrackType(crackType);
        disease.setQuantity(quantity);
        disease.setUnits(unit);
        DiseaseType type = new DiseaseType();
        type.setName(typeName);
        disease.setDiseaseType(type);
        disease.setType(typeName);
        disease.setDiseaseDetails(List.of(details));
        return disease;
    }

    private DiseaseDetail detail(String length, String width, String areaLength,
                                 String areaWidth, String angle) {
        DiseaseDetail detail = new DiseaseDetail();
        if (length != null) detail.setLength1(new BigDecimal(length));
        if (width != null) detail.setCrackWidth(new BigDecimal(width));
        if (areaLength != null) detail.setAreaLength(new BigDecimal(areaLength));
        if (areaWidth != null) detail.setAreaWidth(new BigDecimal(areaWidth));
        if (angle != null) detail.setAngle(Integer.valueOf(angle));
        return detail;
    }
}
