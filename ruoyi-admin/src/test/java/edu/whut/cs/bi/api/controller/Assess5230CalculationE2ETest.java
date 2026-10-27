package edu.whut.cs.bi.api.controller;

import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessBridgeResult;
import edu.whut.cs.bi.biz.domain.AssessElementResult;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.AssessUnitResult;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.domain.SpanElementPart;
import edu.whut.cs.bi.biz.domain.SpanEvaluation;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.AssessAbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.AssessBridgeResultMapper;
import edu.whut.cs.bi.biz.mapper.AssessElementResultMapper;
import edu.whut.cs.bi.biz.mapper.AssessManualInputMapper;
import edu.whut.cs.bi.biz.mapper.AssessSingleControlMarkMapper;
import edu.whut.cs.bi.biz.mapper.AssessUnitResultMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.mapper.SpanEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.std.evaluation.Assess5230CalculateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JTG/T 5230—2026 评定引擎 E2E：直接 mapper 建模最小新链
 * （UNIT(B01)→SPAN→LAYER(superstructure,γ0.40)→PART(slab,ω75)，part/layer key 走
 * propsJson，不依赖模板对象行），真实 YAML 目录 + 真实四级引擎 + 四张结果表落库。
 *
 * <p>覆盖：①病害 ESDI=2 + 人工 EFI=1 + bearing 应设未设幻影件 → BSCI=70.0/3类、
 * 四表内容与重算幂等；②3.3.3 主要构件 ECL4 且标记影响安全 → 60.0/4类覆盖留痕；
 * ③3.4.5 单项控制命中 → 全桥 5类/40分覆盖留痕；④B08 未完成桥型拒绝评定。
 * @Tag("E2E") 手工执行，需 docker MySQL 3307(bi_test，含第八·十一部分六表)。</p>
 */
@SpringBootTest(classes = RuoYiApplication.class)
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000"
})
@Tag("E2E")
@Transactional
public class Assess5230CalculationE2ETest {

    @Autowired
    private Assess5230CalculateService calculateService;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private BuildingMapper buildingMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private ElementMapper elementMapper;
    @Autowired
    private SpanElementPartMapper spanElementPartMapper;
    @Autowired
    private DiseaseTypeMapper diseaseTypeMapper;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private AssessManualInputMapper manualInputMapper;
    @Autowired
    private AssessAbsentMarkMapper absentMarkMapper;
    @Autowired
    private AssessSingleControlMarkMapper singleControlMarkMapper;
    @Autowired
    private AssessElementResultMapper elementResultMapper;
    @Autowired
    private AssessUnitResultMapper unitResultMapper;
    @Autowired
    private AssessBridgeResultMapper bridgeResultMapper;
    @Autowired
    private SpanEvaluationMapper spanEvaluationMapper;

    /** 最小新链上下文（单评定单元、单跨、上部结构层、slab 部件）。 */
    private static class Chain {
        private Long taskId;
        private Long unitId;
        private Long spanId;
        private Long slabPartId;
        private String elementUuid;
    }

    @Test
    @DisplayName("E2E-5230-001: 幻影支座链 - BSCI70/3类四表落库 + 重算幂等 + 结果回查")
    public void testPhantomBearingChain_persistsFourTablesAndIdempotent() {
        Chain chain = buildB01Chain();
        addElementAndEdiDisease(chain, 2);
        addManual(chain.taskId, chain.elementUuid, 1, null);
        addAbsent(chain.taskId, "bearing", "superstructure");

        // 结构预览不落数据
        Map<String, Object> preview = calculateService.previewTree(chain.taskId);
        assertEquals(chain.unitId, preview.get("rootObjectId"));
        assertEquals(1, ((List<?>) preview.get("units")).size());

        Map<String, Object> calc = calculateService.calculate(chain.taskId, "e2e-tester");
        assertEquals(1, calc.get("unitCount"));
        assertEquals(1, calc.get("spanCount"));
        assertEquals(2, calc.get("elementRowCount"));

        assertFourTables_bsci70_class3(chain);

        // 重算幂等：四张表先删后插，行数不翻倍
        Map<String, Object> recalc = calculateService.calculate(chain.taskId, "e2e-tester");
        assertEquals(2, recalc.get("elementRowCount"));
        assertFourTables_bsci70_class3(chain);

        // 结果回查端点装配
        Map<String, Object> result = calculateService.getResult(chain.taskId);
        assertNotNull(result.get("bridge"));
        assertEquals(1, ((List<?>) result.get("units")).size());
        assertEquals(1, ((List<?>) result.get("spans")).size());
        assertEquals(2, ((List<?>) result.get("elements")).size());

        // 输入回查
        Map<String, Object> inputs = calculateService.getInputs(chain.taskId);
        assertEquals(1, ((List<?>) inputs.get("manualInputs")).size());
        assertEquals(1, ((List<?>) inputs.get("absentMarks")).size());
    }

    private void assertFourTables_bsci70_class3(Chain chain) {
        // 构件结果：真实 slab（ESDI=2 病害 + EFI=1 → ECI=3）+ bearing 幻影（EDI=3）
        List<AssessElementResult> elementRows = elementResultMapper.selectByTaskId(chain.taskId);
        assertEquals(2, elementRows.size());
        AssessElementResult slab = elementRows.stream()
                .filter(r -> chain.slabPartId.equals(r.getPartId())).findFirst().orElseThrow();
        assertEquals("slab", slab.getPartKey());
        assertEquals("superstructure", slab.getLayerKey());
        assertEquals(chain.elementUuid, slab.getElementUuid());
        assertEquals(2, slab.getEsdi());
        assertEquals(2, slab.getEdi());
        assertEquals(1, slab.getEfi());
        assertEquals(3, slab.getEci());
        assertEquals(3, slab.getEcl());
        assertEquals(1, slab.getIsMain());
        assertEquals(0, slab.getAbsentFlag());
        assertEquals("AUTO", slab.getEsdiSource());

        AssessElementResult bearing = elementRows.stream()
                .filter(r -> r.getAbsentFlag() == 1).findFirst().orElseThrow();
        assertEquals("bearing", bearing.getPartKey());
        assertEquals("superstructure", bearing.getLayerKey());
        assertNull(bearing.getPartId());
        assertNull(bearing.getElementUuid());
        assertEquals(1, bearing.getIsMain());
        assertEquals(3, bearing.getEdi());
        assertEquals(3, bearing.getEci());
        assertEquals(3, bearing.getEcl());
        assertEquals("ABSENT", bearing.getEsdiSource());

        // 桥跨：BSCI = 100 − 3×100×0.40/5(slab ω′重分配为100) − 3×25×0.40/5(幻影支座) = 70.0
        List<SpanEvaluation> spanRows = spanEvaluationMapper.selectByTaskId(chain.taskId);
        assertEquals(1, spanRows.size());
        SpanEvaluation span = spanRows.get(0);
        assertEquals(chain.unitId, span.getUnitId());
        assertEquals(chain.spanId, span.getSpanId());
        assertEquals("B01", span.getBridgeType());
        assertEquals(0, new BigDecimal("70.0").compareTo(span.getBsci()));
        assertEquals(3, span.getSpanLevel());
        assertEquals(0, new BigDecimal("70.0").compareTo(span.getBsciRaw()));
        assertEquals(3, span.getRawLevel());
        assertEquals(0, span.getOverrideFlag());
        assertEquals("5230-2026", span.getStdVersion());

        // 单元：单跨直通
        List<AssessUnitResult> unitRows = unitResultMapper.selectByTaskId(chain.taskId);
        assertEquals(1, unitRows.size());
        AssessUnitResult unit = unitRows.get(0);
        assertEquals(chain.unitId, unit.getUnitId());
        assertEquals("SINGLE", unit.getCombineMode());
        assertEquals(3, unit.getBcl());
        assertEquals(0, new BigDecimal("70.0").compareTo(unit.getBci()));
        assertEquals(1, unit.getSpanCount());

        // 全桥：无单项控制，直通 3 类
        AssessBridgeResult bridge = bridgeResultMapper.selectByTaskId(chain.taskId);
        assertNotNull(bridge);
        assertEquals(chain.unitId, bridge.getRootObjectId());
        assertEquals("SINGLE_UNIT", bridge.getCombineMode());
        assertEquals(3, bridge.getBcl());
        assertEquals(0, new BigDecimal("70.0").compareTo(bridge.getBci()));
        assertNull(bridge.getSingleControlHits());
        assertEquals("e2e-tester", bridge.getCreateBy());
    }

    @Test
    @DisplayName("E2E-5230-002: 3.3.3 - 主要构件ECL4且标记影响安全，覆盖为4类/60分，原始值冻结")
    public void testSafetyAffectedMainComponent_overridesToClass4() {
        Chain chain = buildB01Chain();
        addElementAndEdiDisease(chain, 2);
        // EDI=2 + EFI=2 → ECI=4/ECL4，主要构件 slab，标记影响安全；无幻影件
        addManual(chain.taskId, chain.elementUuid, 2, 1);

        Map<String, Object> calc = calculateService.calculate(chain.taskId, "e2e-tester");
        assertEquals(1, calc.get("elementRowCount"));

        AssessElementResult slab = elementResultMapper.selectByTaskId(chain.taskId).get(0);
        assertEquals(4, slab.getEci());
        assertEquals(4, slab.getEcl());
        assertEquals(1, slab.getIsMain());

        // 公式原始值 100 − 4×100×0.40/5 = 68.0（3类），覆盖取档上限 60.0/4类
        SpanEvaluation span = spanEvaluationMapper.selectByTaskId(chain.taskId).get(0);
        assertEquals(0, new BigDecimal("68.0").compareTo(span.getBsciRaw()));
        assertEquals(3, span.getRawLevel());
        assertEquals(1, span.getOverrideFlag());
        assertEquals(4, span.getOverrideLevel());
        assertEquals(0, new BigDecimal("60.0").compareTo(span.getBsci()));
        assertEquals(4, span.getSpanLevel());
        assertNotNull(span.getOverrideTrigger());
        assertTrue(span.getOverrideTrigger().contains("3.3.3"));

        AssessUnitResult unit = unitResultMapper.selectByTaskId(chain.taskId).get(0);
        assertEquals(4, unit.getBcl());
        assertEquals(0, new BigDecimal("60.0").compareTo(unit.getBci()));

        AssessBridgeResult bridge = bridgeResultMapper.selectByTaskId(chain.taskId);
        assertEquals(4, bridge.getBcl());
        assertEquals(0, new BigDecimal("60.0").compareTo(bridge.getBci()));
        assertNull(bridge.getSingleControlHits());
    }

    @Test
    @DisplayName("E2E-5230-003: 3.4.5 - 单项控制命中，全桥覆盖为5类/40分，分层值留痕")
    public void testSingleControlHit_overridesBridgeToClass5() {
        Chain chain = buildB01Chain();
        addElementAndEdiDisease(chain, 2);
        addManual(chain.taskId, chain.elementUuid, 1, null);
        addAbsent(chain.taskId, "bearing", "superstructure");
        addSingleControlHit(chain.taskId, 7);

        calculateService.calculate(chain.taskId, "e2e-tester");

        // 桥跨仍是公式值 70.0/3类
        SpanEvaluation span = spanEvaluationMapper.selectByTaskId(chain.taskId).get(0);
        assertEquals(0, new BigDecimal("70.0").compareTo(span.getBsci()));
        assertEquals(3, span.getSpanLevel());

        AssessBridgeResult bridge = bridgeResultMapper.selectByTaskId(chain.taskId);
        assertEquals(5, bridge.getBcl());
        assertEquals(0, new BigDecimal("40.0").compareTo(bridge.getBci()));
        assertEquals(3, bridge.getLayeredBcl());
        assertEquals(0, new BigDecimal("70.0").compareTo(bridge.getLayeredBci()));
        assertEquals("7", bridge.getSingleControlHits());
    }

    @Test
    @DisplayName("E2E-5230-004: B08 桥型未完成模板，拒绝评定且不落结果")
    public void testStructureIncompleteBridgeType_rejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        BiObject unit = new BiObject();
        unit.setName("E2E-B08单元-" + suffix);
        unit.setParentId(0L);
        unit.setNodeType("UNIT");
        unit.setBridgeType("B08");
        unit.setOfflineUuid("e2e-b08-unit-" + suffix);
        unit.setStatus("0");
        biObjectMapper.insertBiObject(unit);

        Building building = new Building();
        building.setName("E2E-B08桥-" + suffix);
        building.setNewRootObjectId(unit.getId());
        buildingMapper.insertBuilding(building);

        Task task = new Task();
        task.setBuildingId(building.getId());
        task.setStdVersion("5230-2026");
        taskMapper.insertTask(task);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> calculateService.calculate(task.getId(), "e2e-tester"));
        assertTrue(ex.getMessage().contains("B08"));
        assertTrue(ex.getMessage().contains("暂不支持新标评定"));

        assertEquals(0, elementResultMapper.selectByTaskId(task.getId()).size());
        assertEquals(0, spanEvaluationMapper.selectByTaskId(task.getId()).size());
        assertEquals(0, unitResultMapper.selectByTaskId(task.getId()).size());
        assertNull(bridgeResultMapper.selectByTaskId(task.getId()));
    }

    // ---------- 建模辅助 ----------

    private Chain buildB01Chain() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Chain chain = new Chain();

        BiObject unit = new BiObject();
        unit.setName("E2E-B01单元-" + suffix);
        unit.setParentId(0L);
        unit.setNodeType("UNIT");
        unit.setBridgeType("B01");
        unit.setOfflineUuid("e2e-b01-unit-" + suffix);
        unit.setStatus("0");
        biObjectMapper.insertBiObject(unit);
        chain.unitId = unit.getId();

        BiObject span = new BiObject();
        span.setName("第1跨");
        span.setParentId(unit.getId());
        span.setNodeType("SPAN");
        span.setSpanNo(1);
        span.setOfflineUuid("e2e-b01-span-" + suffix);
        span.setStatus("0");
        biObjectMapper.insertBiObject(span);
        chain.spanId = span.getId();

        BiObject layer = new BiObject();
        layer.setName("上部结构");
        layer.setParentId(span.getId());
        layer.setNodeType("LAYER");
        layer.setOrderNum(0);
        layer.setPropsJson("{\"layer\":\"superstructure\"}");
        layer.setGamma(new BigDecimal("0.40"));
        layer.setStatus("0");
        biObjectMapper.insertBiObject(layer);

        BiObject slab = new BiObject();
        slab.setName("实心板或空心板");
        slab.setParentId(layer.getId());
        slab.setNodeType("PART");
        slab.setOrderNum(0);
        slab.setPropsJson("{\"part\":\"slab\"}");
        slab.setOmega(75);
        slab.setOfflineUuid("e2e-b01-part-" + suffix);
        slab.setStatus("0");
        biObjectMapper.insertBiObject(slab);
        chain.slabPartId = slab.getId();

        Building building = new Building();
        building.setName("E2E-B01桥-" + suffix);
        building.setNewRootObjectId(unit.getId());
        building.setOfflineUuid("e2e-b01-building-" + suffix);
        buildingMapper.insertBuilding(building);

        Task task = new Task();
        task.setBuildingId(building.getId());
        task.setStdVersion("5230-2026");
        taskMapper.insertTask(task);
        chain.taskId = task.getId();

        return chain;
    }

    private void addElementAndEdiDisease(Chain chain, int diseaseLevel) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String elementUuid = "e2e-elem-" + suffix;
        chain.elementUuid = elementUuid;

        DiseaseType ediType = new DiseaseType();
        ediType.setCode("E2E-EDI-" + suffix);
        ediType.setName("E2E结构性缺损");
        ediType.setMaxScale(3);
        ediType.setStdVersion("5230-2026");
        ediType.setScaleType("EDI");
        diseaseTypeMapper.insertDiseaseType(ediType);

        Element element = new Element();
        element.setCode("E2E-EL-" + suffix);
        element.setName("1号板");
        element.setBiObjectId(chain.slabPartId);
        element.setOfflineUuid(elementUuid);
        elementMapper.insertElement(element);

        SpanElementPart anchor = new SpanElementPart();
        anchor.setElementId(element.getId());
        anchor.setSpanId(chain.spanId);
        anchor.setPartId(chain.slabPartId);
        anchor.setElementUuid(elementUuid);
        anchor.setIsShared(0);
        spanElementPartMapper.insertSpanElementPart(anchor);

        Disease disease = new Disease();
        disease.setTaskId(chain.taskId);
        disease.setDiseaseTypeId(ediType.getId());
        disease.setElementUuid(elementUuid);
        disease.setLevel(diseaseLevel);
        disease.setParticipateAssess("1");
        disease.setDescription("E2E 结构性缺损");
        diseaseMapper.insertDisease(disease);
    }

    private void addManual(Long taskId, String elementUuid, Integer efi, Integer safetyAffected) {
        AssessManualInput manual = new AssessManualInput();
        manual.setTaskId(taskId);
        manual.setElementUuid(elementUuid);
        manual.setEfi(efi);
        if (safetyAffected != null) {
            manual.setSafetyAffected(safetyAffected);
        }
        manualInputMapper.insertAssessManualInput(manual);
    }

    private void addAbsent(Long taskId, String partKey, String layerKey) {
        AssessAbsentMark mark = new AssessAbsentMark();
        mark.setTaskId(taskId);
        mark.setPartKey(partKey);
        mark.setLayerKey(layerKey);
        absentMarkMapper.insertAssessAbsentMark(mark);
    }

    private void addSingleControlHit(Long taskId, Integer indicatorNo) {
        AssessSingleControlMark mark = new AssessSingleControlMark();
        mark.setTaskId(taskId);
        mark.setIndicatorNo(indicatorNo);
        mark.setHit(1);
        singleControlMarkMapper.insertAssessSingleControlMark(mark);
    }
}
