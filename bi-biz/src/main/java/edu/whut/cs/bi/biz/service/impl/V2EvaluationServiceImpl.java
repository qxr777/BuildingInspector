package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BridgeEvaluation;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.ComponentEvaluation;
import edu.whut.cs.bi.biz.domain.SpanComponentPart;
import edu.whut.cs.bi.biz.domain.SpanEvaluation;
import edu.whut.cs.bi.biz.domain.SpanPartDetail;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.domain.dto.V2InputSaveDto;
import edu.whut.cs.bi.biz.mapper.BridgeEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ComponentEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.SpanComponentPartMapper;
import edu.whut.cs.bi.biz.mapper.SpanEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.SpanPartDetailMapper;
import edu.whut.cs.bi.biz.mapper.V2AbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.V2ComponentInputMapper;
import edu.whut.cs.bi.biz.mapper.V2SingleControlMarkMapper;
import edu.whut.cs.bi.biz.service.IV2EvaluationService;
import edu.whut.cs.bi.biz.service.v2.V2TaskContextResolver;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.evaluation.BridgeEvaluator;
import edu.whut.cs.bi.biz.service.std.evaluation.ComponentEvaluator;
import edu.whut.cs.bi.biz.service.std.evaluation.EvalWarnings;
import edu.whut.cs.bi.biz.service.std.evaluation.SpanEvaluator;
import edu.whut.cs.bi.biz.service.std.evaluation.StructureResolver;
import edu.whut.cs.bi.biz.service.std.evaluation.WeightRedistributor;
import edu.whut.cs.bi.biz.service.std.evaluation.model.BridgeEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import edu.whut.cs.bi.biz.domain.Disease;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 新标 JTG/T 5230—2026 三级评定服务实现。
 * 旧标 H21 评定链路零接触。
 */
@Service
public class V2EvaluationServiceImpl implements IV2EvaluationService {

    private static final String STD_VERSION = "5230-2026";

    private final BiObjectMapper biObjectMapper;
    private final V2TaskContextResolver taskContextResolver;
    private final SpanComponentPartMapper spanComponentPartMapper;
    private final ComponentMapper componentMapper;
    private final DiseaseMapper diseaseMapper;
    private final V2ComponentInputMapper componentInputMapper;
    private final V2AbsentMarkMapper absentMarkMapper;
    private final V2SingleControlMarkMapper singleControlMarkMapper;
    private final ComponentEvaluationMapper componentEvaluationMapper;
    private final SpanPartDetailMapper spanPartDetailMapper;
    private final SpanEvaluationMapper spanEvaluationMapper;
    private final BridgeEvaluationMapper bridgeEvaluationMapper;
    private final StandardCatalogLoader standardCatalogLoader;
    private final StructureResolver structureResolver;
    private final WeightRedistributor weightRedistributor;
    private final ComponentEvaluator componentEvaluator;
    private final SpanEvaluator spanEvaluator;
    private final BridgeEvaluator bridgeEvaluator;

    public V2EvaluationServiceImpl(BiObjectMapper biObjectMapper,
                                   V2TaskContextResolver taskContextResolver,
                                   SpanComponentPartMapper spanComponentPartMapper,
                                   ComponentMapper componentMapper,
                                   DiseaseMapper diseaseMapper,
                                   V2ComponentInputMapper componentInputMapper,
                                   V2AbsentMarkMapper absentMarkMapper,
                                   V2SingleControlMarkMapper singleControlMarkMapper,
                                   ComponentEvaluationMapper componentEvaluationMapper,
                                   SpanPartDetailMapper spanPartDetailMapper,
                                   SpanEvaluationMapper spanEvaluationMapper,
                                   BridgeEvaluationMapper bridgeEvaluationMapper,
                                   StandardCatalogLoader standardCatalogLoader,
                                   StructureResolver structureResolver,
                                   WeightRedistributor weightRedistributor,
                                   ComponentEvaluator componentEvaluator,
                                   SpanEvaluator spanEvaluator,
                                   BridgeEvaluator bridgeEvaluator) {
        this.standardCatalogLoader = standardCatalogLoader;
        this.structureResolver = structureResolver;
        this.biObjectMapper = biObjectMapper;
        this.taskContextResolver = taskContextResolver;
        this.spanComponentPartMapper = spanComponentPartMapper;
        this.componentMapper = componentMapper;
        this.diseaseMapper = diseaseMapper;
        this.componentInputMapper = componentInputMapper;
        this.absentMarkMapper = absentMarkMapper;
        this.singleControlMarkMapper = singleControlMarkMapper;
        this.componentEvaluationMapper = componentEvaluationMapper;
        this.spanPartDetailMapper = spanPartDetailMapper;
        this.spanEvaluationMapper = spanEvaluationMapper;
        this.bridgeEvaluationMapper = bridgeEvaluationMapper;
        this.weightRedistributor = weightRedistributor;
        this.componentEvaluator = componentEvaluator;
        this.spanEvaluator = spanEvaluator;
        this.bridgeEvaluator = bridgeEvaluator;
    }

    @Override
    public Map<String, Object> previewTree(Long taskId) {
        Long rootId = unitRootId(load5230Task(taskId));
        EvalWarnings warnings = new EvalWarnings();
        ResolvedUnit unit = structureResolver.resolve(rootId, warnings);
        Map<String, Object> result = new HashMap<>();
        result.put("structure", unit);
        result.put("warnings", warnings.getWarnings());
        return result;
    }

    @Override
    public V2InputSaveDto getInputs(Long taskId) {
        load5230Task(taskId);
        V2InputSaveDto dto = new V2InputSaveDto();
        dto.setComponents(componentInputMapper.selectByTaskId(taskId));
        dto.setAbsents(absentMarkMapper.selectByTaskId(taskId));
        dto.setControls(singleControlMarkMapper.selectByTaskId(taskId));
        return dto;
    }

    @Override
    @Transactional
    public void calculate(Long taskId) {
        Task task = load5230Task(taskId);
        Long rootId = unitRootId(task);
        EvalWarnings warnings = new EvalWarnings();

        // 1. 结构解析
        ResolvedUnit unit = structureResolver.resolve(rootId, warnings);

        // 2. 锚定记录与构件
        List<Long> spanIds = new ArrayList<>();
        for (ResolvedSpan span : unit.getSpans()) {
            spanIds.add(span.getSpanId());
        }
        List<SpanComponentPart> anchors = spanComponentPartMapper.selectBySpanIds(spanIds);
        if (anchors == null) {
            anchors = new ArrayList<>();
        }

        Set<Long> componentIds = new HashSet<>();
        for (SpanComponentPart anchor : anchors) {
            componentIds.add(anchor.getComponentId());
        }
        Map<Long, Component> componentById = new HashMap<>();
        if (!componentIds.isEmpty()) {
            for (Component component : componentMapper.selectComponentsByIds(new ArrayList<>(componentIds))) {
                componentById.put(component.getId(), component);
            }
        }

        // partId → partKey（部件同构，任一跨内的对应部件）
        Map<Long, String> partKeyByPartId = new HashMap<>();
        for (ResolvedSpan span : unit.getSpans()) {
            for (ResolvedPart part : span.getParts()) {
                partKeyByPartId.put(part.getPartId(), part.getPartKey());
            }
        }

        // 3. 应设未设标记（幻影部件）
        List<V2AbsentMark> absentMarks = absentMarkMapper.selectByTaskId(taskId);
        if (absentMarks != null) {
            for (V2AbsentMark mark : absentMarks) {
                BiObject partNode = biObjectMapper.selectBiObjectById(mark.getPartId());
                if (partNode == null) {
                    throw new ServiceException("应设未设部件节点不存在: partId=" + mark.getPartId());
                }
                List<ResolvedSpan> targets;
                if (mark.getSpanId() == null) {
                    targets = unit.getSpans();
                } else {
                    targets = new ArrayList<>();
                    for (ResolvedSpan span : unit.getSpans()) {
                        if (mark.getSpanId().equals(span.getSpanId())) {
                            targets.add(span);
                        }
                    }
                    if (targets.isEmpty()) {
                        throw new ServiceException("应设未设标记引用的桥跨不在评定单元内: spanId=" + mark.getSpanId());
                    }
                }
                for (ResolvedSpan span : targets) {
                    ResolvedPart existing = null;
                    for (ResolvedPart part : span.getParts()) {
                        if (partNode.getId().equals(part.getPartId())) {
                            existing = part;
                            break;
                        }
                    }
                    if (existing != null) {
                        existing.setAbsent(true);
                        existing.setCi(3);
                    } else {
                        ResolvedPart phantom = structureResolver.phantomPart(partNode, unit.getBridgeType());
                        span.getParts().add(phantom);
                        partKeyByPartId.put(phantom.getPartId(), phantom.getPartKey());
                    }
                }
            }
        }

        // 4. 实际设置集合（锚定为准）+ 构件 partKey
        Set<String> presentKeys = new HashSet<>();
        Map<Long, Set<String>> partKeysByComponent = new HashMap<>();
        for (SpanComponentPart anchor : anchors) {
            String partKey = partKeyByPartId.get(anchor.getPartId());
            if (partKey == null) {
                throw new ServiceException(
                        "锚定记录(partId=" + anchor.getPartId() + ")无法解析部件 key");
            }
            presentKeys.add(WeightRedistributor.key(anchor.getSpanId(), partKey));
            partKeysByComponent.computeIfAbsent(anchor.getComponentId(), k -> new HashSet<>()).add(partKey);
        }

        // 5. 病害（5230-2026，参与评定）
        Set<Long> biObjectIds = new HashSet<>();
        for (Component component : componentById.values()) {
            if (component.getBiObjectId() != null) {
                biObjectIds.add(component.getBiObjectId());
            }
        }
        List<Disease> allDiseases;
        if (biObjectIds.isEmpty()) {
            allDiseases = new ArrayList<>();
        } else {
            allDiseases = diseaseMapper.selectTaskDiseases(taskId, new ArrayList<>(biObjectIds), "1");
            if (allDiseases == null) {
                allDiseases = new ArrayList<>();
            }
        }
        Map<Long, List<Disease>> diseasesByComponent = new HashMap<>();
        int danglingDiseases = 0;
        for (Disease disease : allDiseases) {
            if (disease.getComponentId() == null
                    || !componentIds.contains(disease.getComponentId())) {
                danglingDiseases++;
                continue;
            }
            diseasesByComponent.computeIfAbsent(disease.getComponentId(), k -> new ArrayList<>()).add(disease);
        }
        if (danglingDiseases > 0) {
            warnings.add("有 " + danglingDiseases + " 条病害无法对应到锚定构件，未计入 ESDI");
        }

        // 6. 人工录入 + 主要部件
        Map<Long, V2ComponentInput> inputByComponent = new HashMap<>();
        for (V2ComponentInput input : componentInputMapper.selectByTaskId(taskId)) {
            inputByComponent.put(input.getComponentId(), input);
        }
        List<String> mainParts = standardCatalogLoader.getCatalog()
                .getMainParts().get(unit.getBridgeType());
        Set<String> mainPartKeys = mainParts == null ? new HashSet<>() : new HashSet<>(mainParts);

        // 7. 构件级评定（每个构件一次）
        List<ComponentEval> allComponentEvals = new ArrayList<>();
        for (Long componentId : componentIds) {
            Set<String> keys = partKeysByComponent.getOrDefault(componentId, new HashSet<>());
            boolean isMain = false;
            for (String key : keys) {
                if (mainPartKeys.contains(key)) {
                    isMain = true;
                }
            }
            String partKey = keys.iterator().hasNext() ? keys.iterator().next() : null;
            ComponentEval eval = componentEvaluator.evaluate(componentId, partKey,
                    diseasesByComponent.get(componentId), inputByComponent.get(componentId),
                    isMain, warnings);
            allComponentEvals.add(eval);
        }

        // 8. B.1.3 权重再分配 → 9. 桥跨级评定
        weightRedistributor.applyB13(unit, presentKeys, warnings);

        Map<Long, ComponentEval> evalByComponentId = new HashMap<>();
        for (ComponentEval eval : allComponentEvals) {
            evalByComponentId.put(eval.getComponentId(), eval);
        }
        List<SpanEval> spanEvals = new ArrayList<>();
        for (ResolvedSpan span : unit.getSpans()) {
            List<SpanComponentPart> spanAnchors = new ArrayList<>();
            Set<Long> spanComponentIdsSet = new HashSet<>();
            for (SpanComponentPart anchor : anchors) {
                if (span.getSpanId().equals(anchor.getSpanId())) {
                    spanAnchors.add(anchor);
                    spanComponentIdsSet.add(anchor.getComponentId());
                }
            }
            List<ComponentEval> spanComponentEvals = new ArrayList<>();
            for (Long componentId : spanComponentIdsSet) {
                ComponentEval eval = evalByComponentId.get(componentId);
                if (eval != null) {
                    spanComponentEvals.add(eval);
                }
            }
            spanEvals.add(spanEvaluator.evaluate(span, spanAnchors, spanComponentEvals,
                    inputByComponent, mainPartKeys));
        }

        // 10. 全桥级评定
        List<V2SingleControlMark> controlMarks = singleControlMarkMapper.selectByTaskId(taskId);
        BridgeEval bridgeEval = bridgeEvaluator.evaluate(spanEvals, controlMarks);

        // 11. 全量重算落库（先删后插）
        componentEvaluationMapper.deleteByTaskId(taskId);
        spanPartDetailMapper.deleteByTaskId(taskId);
        spanEvaluationMapper.deleteByTaskId(taskId);
        bridgeEvaluationMapper.deleteByTaskId(taskId);

        if (!allComponentEvals.isEmpty()) {
            componentEvaluationMapper.batchInsert(toComponentRows(taskId, allComponentEvals));
        }
        List<SpanPartDetail> detailRows = toDetailRows(taskId, unit);
        if (!detailRows.isEmpty()) {
            spanPartDetailMapper.batchInsert(detailRows);
        }
        for (int i = 0; i < unit.getSpans().size(); i++) {
            spanEvaluationMapper.insert(toSpanRow(taskId, unit.getBridgeType(),
                    unit.getSpans().get(i), spanEvals.get(i)));
        }
        bridgeEvaluationMapper.insert(toBridgeRow(taskId, rootId, unit.getBridgeType(), bridgeEval));
    }

    @Override
    public Map<String, Object> getResult(Long taskId) {
        load5230Task(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("bridge", bridgeEvaluationMapper.selectByTaskId(taskId));
        result.put("spans", spanEvaluationMapper.selectByTaskId(taskId));
        result.put("spanPartDetails", spanPartDetailMapper.selectByTaskId(taskId));
        result.put("componentEvaluations", componentEvaluationMapper.selectByTaskId(taskId));
        return result;
    }

    // ---------- 行映射 ----------

    private List<ComponentEvaluation> toComponentRows(Long taskId, List<ComponentEval> evals) {
        List<ComponentEvaluation> rows = new ArrayList<>();
        for (ComponentEval eval : evals) {
            ComponentEvaluation row = new ComponentEvaluation();
            row.setTaskId(taskId);
            row.setComponentId(eval.getComponentId());
            row.setPartKey(eval.getPartKey());
            row.setEsdi(eval.getEsdi());
            row.setEddi(eval.getEddi());
            row.setEdi(eval.getEdi());
            row.setEfi(eval.getEfi());
            row.setEai(eval.getEai());
            row.setEci(eval.getEci());
            row.setEcl(eval.getEcl());
            rows.add(row);
        }
        return rows;
    }

    private List<SpanPartDetail> toDetailRows(Long taskId, ResolvedUnit unit) {
        List<SpanPartDetail> rows = new ArrayList<>();
        for (ResolvedSpan span : unit.getSpans()) {
            for (ResolvedPart part : span.getParts()) {
                SpanPartDetail row = new SpanPartDetail();
                row.setTaskId(taskId);
                row.setSpanId(span.getSpanId());
                row.setPartId(part.getPartId());
                row.setLayerKey(part.getLayerKey());
                row.setPartKey(part.getPartKey());
                row.setCi(part.getCi());
                row.setOmegaOriginal(part.getOmega());
                row.setOmegaEffective(part.getOmegaEffective());
                row.setGamma(part.getGamma());
                row.setAbsentFlag(part.isAbsent() ? 1 : 0);
                rows.add(row);
            }
        }
        return rows;
    }

    private SpanEvaluation toSpanRow(Long taskId, String bridgeType,
                                     ResolvedSpan span, SpanEval eval) {
        SpanEvaluation row = new SpanEvaluation();
        row.setTaskId(taskId);
        row.setSpanId(span.getSpanId());
        row.setBridgeType(bridgeType);
        row.setBsci(eval.getBsci());
        row.setSpanLevel(eval.getBscl());
        row.setBsciRaw(eval.getBsciRaw());
        row.setRawLevel(eval.getRawLevel());
        row.setOverrideFlag(eval.isOverride() ? 1 : 0);
        row.setOverrideLevel(eval.getOverrideLevel());
        row.setOverrideTrigger(truncate(eval.getTrigger(), 200));
        row.setStdVersion(STD_VERSION);
        return row;
    }

    private BridgeEvaluation toBridgeRow(Long taskId, Long rootId, String bridgeType, BridgeEval eval) {
        BridgeEvaluation row = new BridgeEvaluation();
        row.setTaskId(taskId);
        row.setRootObjectId(rootId);
        row.setBridgeType(bridgeType);
        row.setBci(eval.getBci());
        row.setBcl(eval.getBcl());
        row.setCalcMode(eval.getCalcMode());
        row.setBsciMin(eval.getBsciMin());
        row.setBsciMax(eval.getBsciMax());
        row.setBsciMean(eval.getBsciMean());
        row.setBsciLim(eval.getBsciLim());
        row.setBclLayered(eval.getBclLayered());
        row.setBciLayered(eval.getBciLayered());
        row.setSingleControlHit(eval.isSingleControlHit() ? 1 : 0);
        row.setSingleControlNos(eval.getSingleControlNos());
        row.setWorstSpanId(eval.getWorstSpanId());
        row.setStdVersion(STD_VERSION);
        return row;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ---------- 任务/单元 ----------

    private Task load5230Task(Long taskId) {
        return taskContextResolver.require5230Task(taskId);
    }

    private Long unitRootId(Task task) {
        return taskContextResolver.unitRootId(task);
    }
}
