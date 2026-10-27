package edu.whut.cs.bi.biz.service.std.evaluation;

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
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.mapper.SpanEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.model.StdBridgeTemplate;
import edu.whut.cs.bi.biz.service.std.model.StdBridgeType;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.std.model.StdLayerDef;
import edu.whut.cs.bi.biz.service.std.model.StdPartDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Assess5230CalculateService} 装配单测：真实解析器 + 真实四级纯计算引擎，
 * Mapper 全 Mock。最小结构（单单元单跨、上部结构层、slab 实体 + bearing 应设未设幻影件）：
 * BSCI = 100 − 3×100×0.40/5 − 3×25×0.40/5 = 70.0（3 类）。
 */
@ExtendWith(MockitoExtension.class)
class Assess5230CalculateServiceTest {

    private static final long TASK_ID = 7001L;
    private static final long UNIT_ID = 1L;
    private static final long SPAN_ID = 10L;
    private static final long LAYER_ID = 11L;
    private static final long SLAB_PART_ID = 12L;
    private static final long ELEMENT_ID = 100L;

    @Mock private TaskMapper taskMapper;
    @Mock private BuildingMapper buildingMapper;
    @Mock private BiObjectMapper biObjectMapper;
    @Mock private BiTemplateObjectMapper biTemplateObjectMapper;
    @Mock private SpanElementPartMapper spanElementPartMapper;
    @Mock private ElementMapper elementMapper;
    @Mock private DiseaseMapper diseaseMapper;
    @Mock private DiseaseTypeMapper diseaseTypeMapper;
    @Mock private AssessManualInputMapper manualInputMapper;
    @Mock private AssessAbsentMarkMapper absentMarkMapper;
    @Mock private AssessSingleControlMarkMapper singleControlMarkMapper;
    @Mock private AssessElementResultMapper elementResultMapper;
    @Mock private AssessUnitResultMapper unitResultMapper;
    @Mock private AssessBridgeResultMapper bridgeResultMapper;
    @Mock private SpanEvaluationMapper spanEvaluationMapper;
    @Mock private StandardCatalogLoader standardCatalogLoader;

    private Assess5230CalculateService service;

    @BeforeEach
    void setUp() {
        EvaluationMath math = new EvaluationMath();
        UnitStructureResolver resolver = new UnitStructureResolver(
                biObjectMapper, biTemplateObjectMapper, standardCatalogLoader);
        service = new Assess5230CalculateService(
                taskMapper, buildingMapper, resolver, spanElementPartMapper, elementMapper,
                diseaseMapper, diseaseTypeMapper, manualInputMapper, absentMarkMapper,
                singleControlMarkMapper, elementResultMapper, unitResultMapper, bridgeResultMapper,
                spanEvaluationMapper, standardCatalogLoader,
                new WeightRedistributor(), new ComponentEvaluator(math),
                new SpanEvaluator(math), new UnitEvaluator(math), new BridgeAggregator(math));

        Task task = new Task();
        task.setId(TASK_ID);
        task.setStdVersion("5230-2026");
        task.setBuildingId(50L);
        when(taskMapper.selectTaskById(TASK_ID)).thenReturn(task);
        Building building = new Building();
        building.setId(50L);
        building.setNewRootObjectId(UNIT_ID);
        when(buildingMapper.selectBuildingById(50L)).thenReturn(building);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(UNIT_ID)).thenReturn(structureNodes());
        when(standardCatalogLoader.getCatalog()).thenReturn(buildCatalog());

        SpanElementPart anchor = new SpanElementPart();
        anchor.setId(9001L);
        anchor.setElementId(ELEMENT_ID);
        anchor.setSpanId(SPAN_ID);
        anchor.setPartId(SLAB_PART_ID);
        anchor.setIsShared(0);
        when(spanElementPartMapper.selectBySpanIds(anyList())).thenReturn(Collections.singletonList(anchor));

        Element element = new Element();
        element.setId(ELEMENT_ID);
        element.setOfflineUuid("elem-uuid-1");
        element.setName("1号板");
        when(elementMapper.selectByIds(anyList())).thenReturn(Collections.singletonList(element));

        Disease disease = new Disease();
        disease.setId(5001L);
        disease.setTaskId(TASK_ID);
        disease.setElementUuid("elem-uuid-1");
        disease.setParticipateAssess("1");
        disease.setLevel(2);
        disease.setDiseaseTypeId(801L);
        when(diseaseMapper.selectDiseaseListByTaskIds(anyList())).thenReturn(Collections.singletonList(disease));
        DiseaseType ediType = new DiseaseType();
        ediType.setId(801L);
        ediType.setStdVersion("5230-2026");
        ediType.setScaleType("EDI");
        when(diseaseTypeMapper.selectByIds(anyList())).thenReturn(Collections.singletonList(ediType));

        AssessManualInput manual = new AssessManualInput();
        manual.setTaskId(TASK_ID);
        manual.setElementUuid("elem-uuid-1");
        manual.setEfi(1);
        when(manualInputMapper.selectByTaskId(TASK_ID)).thenReturn(Collections.singletonList(manual));

        AssessAbsentMark absentMark = new AssessAbsentMark();
        absentMark.setId(6001L);
        absentMark.setTaskId(TASK_ID);
        absentMark.setPartKey("bearing");
        absentMark.setLayerKey("superstructure");
        when(absentMarkMapper.selectByTaskId(TASK_ID)).thenReturn(Collections.singletonList(absentMark));

        when(bridgeResultMapper.selectByTaskId(TASK_ID)).thenReturn(new AssessBridgeResult());
    }

    @Test
    void calculate_phantomBearingChain_persistsFourTablesWithBsci70() {
        when(singleControlMarkMapper.selectByTaskId(TASK_ID)).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.calculate(TASK_ID, "tester");

        assertEquals(1, result.get("unitCount"));
        assertEquals(1, result.get("spanCount"));
        assertEquals(2, result.get("elementRowCount"));

        // 四表均先按任务清理
        verify(elementResultMapper).deleteByTaskId(TASK_ID);
        verify(spanEvaluationMapper).deleteByTaskId(TASK_ID);
        verify(unitResultMapper).deleteByTaskId(TASK_ID);
        verify(bridgeResultMapper).deleteByTaskId(TASK_ID);

        // 构件结果：真实 slab（ESDI=2 病害 + EFI=1 → ECI=3）+ bearing 幻影（EDI=3）
        ArgumentCaptor<List<AssessElementResult>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(elementResultMapper).batchInsert(rowsCaptor.capture());
        List<AssessElementResult> rows = rowsCaptor.getValue();
        assertEquals(2, rows.size());
        AssessElementResult slab = rows.get(0);
        assertEquals(SLAB_PART_ID, slab.getPartId());
        assertEquals("slab", slab.getPartKey());
        assertEquals("elem-uuid-1", slab.getElementUuid());
        assertEquals(2, slab.getEsdi());
        assertEquals(2, slab.getEdi());
        assertEquals(1, slab.getEfi());
        assertEquals(3, slab.getEci());
        assertEquals(3, slab.getEcl());
        assertEquals("AUTO", slab.getEsdiSource());
        assertEquals(0, slab.getAbsentFlag());
        assertEquals(1, slab.getIsMain());

        AssessElementResult bearing = rows.get(1);
        assertNull(bearing.getPartId());
        assertNull(bearing.getElementUuid());
        assertEquals("bearing", bearing.getPartKey());
        assertEquals("superstructure", bearing.getLayerKey());
        assertEquals(1, bearing.getAbsentFlag());
        assertEquals(3, bearing.getEdi());
        assertEquals(3, bearing.getEci());
        assertEquals(3, bearing.getEcl());
        assertEquals("ABSENT", bearing.getEsdiSource());

        // 桥跨：BSCI=70.0、3类
        ArgumentCaptor<SpanEvaluation> spanCaptor = ArgumentCaptor.forClass(SpanEvaluation.class);
        verify(spanEvaluationMapper).insertSpanEvaluation(spanCaptor.capture());
        SpanEvaluation spanRow = spanCaptor.getValue();
        assertEquals(UNIT_ID, spanRow.getUnitId());
        assertEquals(SPAN_ID, spanRow.getSpanId());
        assertEquals(0, new BigDecimal("70.0").compareTo(spanRow.getBsci()));
        assertEquals(3, spanRow.getSpanLevel());
        assertEquals("5230-2026", spanRow.getStdVersion());
        assertEquals(0, spanRow.getOverrideFlag());

        // 单元：单跨直通
        ArgumentCaptor<AssessUnitResult> unitCaptor = ArgumentCaptor.forClass(AssessUnitResult.class);
        verify(unitResultMapper).insertAssessUnitResult(unitCaptor.capture());
        assertEquals("SINGLE", unitCaptor.getValue().getCombineMode());
        assertEquals(3, unitCaptor.getValue().getBcl());
        assertEquals(0, new BigDecimal("70.0").compareTo(unitCaptor.getValue().getBci()));

        // 全桥：无单项控制，直通 3 类
        ArgumentCaptor<AssessBridgeResult> bridgeCaptor = ArgumentCaptor.forClass(AssessBridgeResult.class);
        verify(bridgeResultMapper).insertAssessBridgeResult(bridgeCaptor.capture());
        AssessBridgeResult bridgeRow = bridgeCaptor.getValue();
        assertEquals(UNIT_ID, bridgeRow.getRootObjectId());
        assertEquals("SINGLE_UNIT", bridgeRow.getCombineMode());
        assertEquals(3, bridgeRow.getBcl());
        assertEquals(0, new BigDecimal("70.0").compareTo(bridgeRow.getBci()));
        assertNull(bridgeRow.getSingleControlHits());
        assertEquals("tester", bridgeRow.getCreateBy());
    }

    @Test
    void calculate_singleControlHit_overridesToClass5Bci40() {
        AssessSingleControlMark hit = new AssessSingleControlMark();
        hit.setTaskId(TASK_ID);
        hit.setIndicatorNo(7);
        hit.setHit(1);
        when(singleControlMarkMapper.selectByTaskId(TASK_ID)).thenReturn(Collections.singletonList(hit));

        service.calculate(TASK_ID, "tester");

        ArgumentCaptor<AssessBridgeResult> captor = ArgumentCaptor.forClass(AssessBridgeResult.class);
        verify(bridgeResultMapper).insertAssessBridgeResult(captor.capture());
        AssessBridgeResult row = captor.getValue();
        assertEquals(5, row.getBcl());
        assertEquals(0, new BigDecimal("40.0").compareTo(row.getBci()));
        assertEquals(3, row.getLayeredBcl());
        assertEquals(0, new BigDecimal("70.0").compareTo(row.getLayeredBci()));
        assertEquals("7", row.getSingleControlHits());
    }

    // ---------- 夹具 ----------

    private List<BiObject> structureNodes() {
        BiObject unit = new BiObject();
        unit.setId(UNIT_ID);
        unit.setParentId(0L);
        unit.setNodeType("UNIT");
        unit.setBridgeType("B01");

        BiObject span = new BiObject();
        span.setId(SPAN_ID);
        span.setParentId(UNIT_ID);
        span.setNodeType("SPAN");
        span.setSpanNo(1);

        BiObject layer = new BiObject();
        layer.setId(LAYER_ID);
        layer.setParentId(SPAN_ID);
        layer.setNodeType("LAYER");
        layer.setOrderNum(0);
        layer.setPropsJson("{\"layer\":\"superstructure\"}");
        layer.setGamma(new BigDecimal("0.40"));

        BiObject slab = new BiObject();
        slab.setId(SLAB_PART_ID);
        slab.setParentId(LAYER_ID);
        slab.setNodeType("PART");
        slab.setName("实心板或空心板");
        slab.setOmega(75);

        return new ArrayList<>(Arrays.asList(unit, span, layer, slab));
    }

    private StdCatalog buildCatalog() {
        StdCatalog catalog = new StdCatalog();
        StdBridgeType b01 = new StdBridgeType();
        b01.setCode("B01");
        b01.setStructureComplete(true);
        catalog.getBridgeTypes().add(b01);

        StdBridgeTemplate template = new StdBridgeTemplate();
        template.setCode("B01");
        template.getGamma().put("deck", new BigDecimal("0.15"));
        template.getGamma().put("superstructure", new BigDecimal("0.40"));
        template.getGamma().put("substructure", new BigDecimal("0.40"));
        template.getGamma().put("ancillary", new BigDecimal("0.05"));
        StdPartDef slab = new StdPartDef();
        slab.setKey("slab");
        slab.setName("实心板或空心板");
        slab.setOmega(75);
        StdPartDef bearing = new StdPartDef();
        bearing.setKey("bearing");
        bearing.setName("支座");
        bearing.setOmega(25);
        template.getSuperstructureParts().addAll(Arrays.asList(slab, bearing));
        catalog.getBridgeTemplates().put("B01", template);

        StdLayerDef emptyDeck = new StdLayerDef();
        emptyDeck.setKey("deck");
        catalog.getSharedLayers().add(emptyDeck);
        StdLayerDef emptySub = new StdLayerDef();
        emptySub.setKey("substructure");
        catalog.getSharedLayers().add(emptySub);

        catalog.getMainParts().put("B01", Arrays.asList("slab", "bearing", "pierAbutment", "foundation"));
        return catalog;
    }
}
