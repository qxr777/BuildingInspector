package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.BridgeEvaluation;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.SpanComponentPart;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.dto.V2InputSaveDto;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BridgeEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.ComponentEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.SpanComponentPartMapper;
import edu.whut.cs.bi.biz.mapper.SpanEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.SpanPartDetailMapper;
import edu.whut.cs.bi.biz.mapper.V2AbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.V2ComponentInputMapper;
import edu.whut.cs.bi.biz.mapper.V2SingleControlMarkMapper;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.evaluation.BridgeEvaluator;
import edu.whut.cs.bi.biz.service.std.evaluation.ComponentEvaluator;
import edu.whut.cs.bi.biz.service.std.evaluation.EvalWarnings;
import edu.whut.cs.bi.biz.service.std.evaluation.SpanEvaluator;
import edu.whut.cs.bi.biz.service.std.evaluation.StructureResolver;
import edu.whut.cs.bi.biz.service.std.evaluation.WeightRedistributor;
import edu.whut.cs.bi.biz.service.std.evaluation.EvaluationMath;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.v2.V2TaskContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2EvaluationServiceImplTest {

    @Mock private BiObjectMapper biObjectMapper;
    @Mock private V2TaskContextResolver taskContextResolver;
    @Mock private SpanComponentPartMapper spanComponentPartMapper;
    @Mock private ComponentMapper componentMapper;
    @Mock private DiseaseMapper diseaseMapper;
    @Mock private V2ComponentInputMapper componentInputMapper;
    @Mock private V2AbsentMarkMapper absentMarkMapper;
    @Mock private V2SingleControlMarkMapper singleControlMarkMapper;
    @Mock private ComponentEvaluationMapper componentEvaluationMapper;
    @Mock private SpanPartDetailMapper spanPartDetailMapper;
    @Mock private SpanEvaluationMapper spanEvaluationMapper;
    @Mock private BridgeEvaluationMapper bridgeEvaluationMapper;
    @Mock private StandardCatalogLoader standardCatalogLoader;
    @Mock private StructureResolver structureResolver;

    private V2EvaluationServiceImpl service;

    private static final Long TASK_ID = 1L;
    private static final Long ROOT_ID = 100L;
    private static final Long SPAN_ID = 200L;
    private static final Long PART_ID = 300L;
    private static final Long COMPONENT_ID = 400L;
    private static final Long COMP_BIOBJECT_ID = 500L;

    @BeforeEach
    void setUp() {
        // 真实算法组件 + Mock 的持久层/解析器：端到端验证编排
        EvaluationMath math = new EvaluationMath();
        WeightRedistributor weightRedistributor = new WeightRedistributor();
        ComponentEvaluator componentEvaluator = new ComponentEvaluator(math);
        SpanEvaluator spanEvaluator = new SpanEvaluator(math);
        BridgeEvaluator bridgeEvaluator = new BridgeEvaluator(math);

        service = new V2EvaluationServiceImpl(
                biObjectMapper, taskContextResolver, spanComponentPartMapper,
                componentMapper, diseaseMapper, componentInputMapper, absentMarkMapper,
                singleControlMarkMapper, componentEvaluationMapper, spanPartDetailMapper,
                spanEvaluationMapper, bridgeEvaluationMapper, standardCatalogLoader,
                structureResolver, weightRedistributor, componentEvaluator,
                spanEvaluator, bridgeEvaluator);
    }

    private Task task(String stdVersion) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setStdVersion(stdVersion);
        return task;
    }

    private ResolvedUnit healthyUnit() {
        ResolvedUnit unit = new ResolvedUnit();
        unit.setRootObjectId(ROOT_ID);
        unit.setBridgeType("B03");

        ResolvedSpan span = new ResolvedSpan();
        span.setSpanId(SPAN_ID);
        span.setSpanNo(1);

        ResolvedPart part = new ResolvedPart();
        part.setPartId(PART_ID);
        part.setPartKey("mainGirder");
        part.setLayerKey("superstructure");
        part.setOmega(55);
        part.setOmegaEffective(new BigDecimal("55.0000"));
        part.setGamma(new BigDecimal("0.42"));
        span.getParts().add(part);

        unit.getSpans().add(span);
        return unit;
    }

    private SpanComponentPart anchor() {
        SpanComponentPart anchor = new SpanComponentPart();
        anchor.setComponentId(COMPONENT_ID);
        anchor.setSpanId(SPAN_ID);
        anchor.setPartId(PART_ID);
        return anchor;
    }

    private StdCatalog catalogWithMainParts() {
        StdCatalog catalog = new StdCatalog();
        catalog.getMainParts().put("B03",
                List.of("mainGirder", "bearing", "pierAbutment", "foundation"));
        return catalog;
    }

    private void stubTaskAndRoot() {
        Task t = task("5230-2026");
        when(taskContextResolver.require5230Task(TASK_ID)).thenReturn(t);
        when(taskContextResolver.unitRootId(t)).thenReturn(ROOT_ID);
    }

    private void stubCommonHealthy(ResolvedUnit unit) {
        stubTaskAndRoot();
        when(structureResolver.resolve(eq(ROOT_ID), any(EvalWarnings.class))).thenReturn(unit);
        when(spanComponentPartMapper.selectBySpanIds(any())).thenReturn(List.of(anchor()));

        Component component = new Component();
        component.setId(COMPONENT_ID);
        component.setBiObjectId(COMP_BIOBJECT_ID);
        when(componentMapper.selectComponentsByIds(any())).thenReturn(List.of(component));

        when(absentMarkMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(componentInputMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(singleControlMarkMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(diseaseMapper.selectTaskDiseases(eq(TASK_ID), any(), eq("1")))
                .thenReturn(new ArrayList<>());
        when(standardCatalogLoader.getCatalog()).thenReturn(catalogWithMainParts());
    }

    @Test
    void calculateHappyPathWritesAllFourTables() {
        ResolvedUnit unit = healthyUnit();
        stubCommonHealthy(unit);

        service.calculate(TASK_ID);

        // 四张结果表各先删一次
        verify(componentEvaluationMapper).deleteByTaskId(TASK_ID);
        verify(spanPartDetailMapper).deleteByTaskId(TASK_ID);
        verify(spanEvaluationMapper).deleteByTaskId(TASK_ID);
        verify(bridgeEvaluationMapper).deleteByTaskId(TASK_ID);

        // 单行表插入；批量表本场景构件存在 → 构件结果批量插入
        verify(componentEvaluationMapper).batchInsert(any());
        verify(spanPartDetailMapper).batchInsert(any());
        verify(spanEvaluationMapper).insert(any());
        verify(bridgeEvaluationMapper).insert(any());

        ArgumentCaptor<BridgeEvaluation> captor = ArgumentCaptor.forClass(BridgeEvaluation.class);
        verify(bridgeEvaluationMapper).insert(captor.capture());
        BridgeEvaluation row = captor.getValue();
        assertEquals(1, row.getBcl());
        assertEquals(new BigDecimal("100.0"), row.getBci());
        assertEquals("SINGLE", row.getCalcMode());
        assertEquals("B03", row.getBridgeType());
        assertEquals(SPAN_ID, row.getWorstSpanId());
    }

    @Test
    void non5230TaskRejectedWithNoWrites() {
        when(taskContextResolver.require5230Task(TASK_ID))
                .thenThrow(new ServiceException("非 5230 任务"));

        assertThrows(ServiceException.class, () -> service.calculate(TASK_ID));

        verify(bridgeEvaluationMapper, never()).insert(any());
        verify(spanEvaluationMapper, never()).insert(any());
        verify(bridgeEvaluationMapper, never()).deleteByTaskId(any());
    }

    @Test
    void calculatePropagatesResolverErrorAndWritesNothing() {
        stubTaskAndRoot();
        when(structureResolver.resolve(eq(ROOT_ID), any(EvalWarnings.class)))
                .thenThrow(new ServiceException("桥型结构不完整"));

        assertThrows(ServiceException.class, () -> service.calculate(TASK_ID));
        verify(bridgeEvaluationMapper, never()).insert(any());
        verify(bridgeEvaluationMapper, never()).deleteByTaskId(any());
    }

    @Test
    void getResultAssemblesFourGroups() {
        when(taskContextResolver.require5230Task(TASK_ID)).thenReturn(task("5230-2026"));
        when(bridgeEvaluationMapper.selectByTaskId(TASK_ID)).thenReturn(null);
        when(spanEvaluationMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(spanPartDetailMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(componentEvaluationMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());

        Map<String, Object> result = service.getResult(TASK_ID);
        assertEquals(4, result.size());
        assertTrue(result.containsKey("bridge"));
    }

    @Test
    void previewTreeReturnsStructureAndWarnings() {
        stubTaskAndRoot();
        ResolvedUnit unit = healthyUnit();
        when(structureResolver.resolve(eq(ROOT_ID), any(EvalWarnings.class))).thenReturn(unit);

        Map<String, Object> preview = service.previewTree(TASK_ID);
        assertEquals(unit, preview.get("structure"));
        assertTrue(preview.get("warnings") instanceof List);
    }

    @Test
    void getInputsReturnsThreeGroups() {
        when(taskContextResolver.require5230Task(TASK_ID)).thenReturn(task("5230-2026"));
        when(componentInputMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(absentMarkMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());
        when(singleControlMarkMapper.selectByTaskId(TASK_ID)).thenReturn(new ArrayList<>());

        V2InputSaveDto dto = service.getInputs(TASK_ID);
        assertEquals(0, dto.getComponents().size());
        assertEquals(0, dto.getAbsents().size());
        assertEquals(0, dto.getControls().size());
    }
}
