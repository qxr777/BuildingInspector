package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessBridgeResult;
import edu.whut.cs.bi.biz.domain.AssessElementResult;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.AssessUnitResult;
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
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.mapper.SpanEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.evaluation.model.BridgeAggregate;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentInput;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import edu.whut.cs.bi.biz.service.std.evaluation.model.SpanEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.UnitEval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JTG/T 5230—2026 正式稿三级评定装配服务（后端计算引擎，无输入页面）。
 *
 * <p>calculate 装配步骤：</p>
 * <ol>
 *   <li>任务校验（std_version='5230-2026'）+ 根节点解析（ROOT→多 UNIT 或根即 UNIT）；</li>
 *   <li>{@link UnitStructureResolver} 结构解析（partKey/ω/γ 三级解析，B08–B21 拦截）；</li>
 *   <li>bi_span_element_part 锚定 + bi_element 装载；</li>
 *   <li>应设未设标记 → 已有 PART 置 absent/CI=3 或按 partKey 注入幻影件（可按单元/跨/全桥广播）；</li>
 *   <li>病害按 element_uuid + participate_assess='1' + 病害类型(5230-2026/EDI) 装 structuralScales；</li>
 *   <li>人工判定三表装载；附录 B.1.3 权重重分配；</li>
 *   <li>构件级 → 桥跨级（3.3.3 覆盖）→ 单元级（3.4.1~3.4.3）→ 全桥（3.4.4/3.4.5）；</li>
 *   <li>四张结果表按 task_id 先删后插（幂等可重算）。</li>
 * </ol>
 */
@Service
public class Assess5230CalculateService {

    public static final String STD_VERSION = "5230-2026";

    /** 病害类型 scale_type：结构性缺损标度（ESDI 来源）。 */
    private static final String SCALE_TYPE_EDI = "EDI";

    private final TaskMapper taskMapper;
    private final BuildingMapper buildingMapper;
    private final UnitStructureResolver structureResolver;
    private final SpanElementPartMapper spanElementPartMapper;
    private final ElementMapper elementMapper;
    private final DiseaseMapper diseaseMapper;
    private final DiseaseTypeMapper diseaseTypeMapper;
    private final AssessManualInputMapper manualInputMapper;
    private final AssessAbsentMarkMapper absentMarkMapper;
    private final AssessSingleControlMarkMapper singleControlMarkMapper;
    private final AssessElementResultMapper elementResultMapper;
    private final AssessUnitResultMapper unitResultMapper;
    private final AssessBridgeResultMapper bridgeResultMapper;
    private final SpanEvaluationMapper spanEvaluationMapper;
    private final StandardCatalogLoader standardCatalogLoader;
    private final WeightRedistributor weightRedistributor;
    private final ComponentEvaluator componentEvaluator;
    private final SpanEvaluator spanEvaluator;
    private final UnitEvaluator unitEvaluator;
    private final BridgeAggregator bridgeAggregator;

    public Assess5230CalculateService(TaskMapper taskMapper,
                                      BuildingMapper buildingMapper,
                                      UnitStructureResolver structureResolver,
                                      SpanElementPartMapper spanElementPartMapper,
                                      ElementMapper elementMapper,
                                      DiseaseMapper diseaseMapper,
                                      DiseaseTypeMapper diseaseTypeMapper,
                                      AssessManualInputMapper manualInputMapper,
                                      AssessAbsentMarkMapper absentMarkMapper,
                                      AssessSingleControlMarkMapper singleControlMarkMapper,
                                      AssessElementResultMapper elementResultMapper,
                                      AssessUnitResultMapper unitResultMapper,
                                      AssessBridgeResultMapper bridgeResultMapper,
                                      SpanEvaluationMapper spanEvaluationMapper,
                                      StandardCatalogLoader standardCatalogLoader,
                                      WeightRedistributor weightRedistributor,
                                      ComponentEvaluator componentEvaluator,
                                      SpanEvaluator spanEvaluator,
                                      UnitEvaluator unitEvaluator,
                                      BridgeAggregator bridgeAggregator) {
        this.taskMapper = taskMapper;
        this.buildingMapper = buildingMapper;
        this.structureResolver = structureResolver;
        this.spanElementPartMapper = spanElementPartMapper;
        this.elementMapper = elementMapper;
        this.diseaseMapper = diseaseMapper;
        this.diseaseTypeMapper = diseaseTypeMapper;
        this.manualInputMapper = manualInputMapper;
        this.absentMarkMapper = absentMarkMapper;
        this.singleControlMarkMapper = singleControlMarkMapper;
        this.elementResultMapper = elementResultMapper;
        this.unitResultMapper = unitResultMapper;
        this.bridgeResultMapper = bridgeResultMapper;
        this.spanEvaluationMapper = spanEvaluationMapper;
        this.standardCatalogLoader = standardCatalogLoader;
        this.weightRedistributor = weightRedistributor;
        this.componentEvaluator = componentEvaluator;
        this.spanEvaluator = spanEvaluator;
        this.unitEvaluator = unitEvaluator;
        this.bridgeAggregator = bridgeAggregator;
    }

    // ---------- 查询类端点 ----------

    /** 预览结构解析（不落数据）。 */
    public Map<String, Object> previewTree(Long taskId) {
        TaskContext ctx = loadContext(taskId);
        EvalWarnings warnings = new EvalWarnings();
        List<ResolvedUnit> units = structureResolver.resolveAll(ctx.rootObjectId, warnings);
        Map<String, Object> result = new HashMap<>();
        result.put("rootObjectId", ctx.rootObjectId);
        result.put("units", units);
        result.put("warnings", warnings.getWarnings());
        return result;
    }

    /** 查询当前人工判定三表输入。 */
    public Map<String, Object> getInputs(Long taskId) {
        require5230Task(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("manualInputs", manualInputMapper.selectByTaskId(taskId));
        result.put("absentMarks", absentMarkMapper.selectByTaskId(taskId));
        result.put("singleControlMarks", singleControlMarkMapper.selectByTaskId(taskId));
        return result;
    }

    /** 查询四张结果表的最近一次评定结果。 */
    public Map<String, Object> getResult(Long taskId) {
        require5230Task(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("bridge", bridgeResultMapper.selectByTaskId(taskId));
        result.put("units", unitResultMapper.selectByTaskId(taskId));
        result.put("spans", spanEvaluationMapper.selectByTaskId(taskId));
        result.put("elements", elementResultMapper.selectByTaskId(taskId));
        return result;
    }

    // ---------- 计算 ----------

    /**
     * 执行全桥三级评定并落库（按 task_id 先删后插，幂等可重算）。
     *
     * @return 全桥结果 + 非阻断告警
     */
    @Transactional
    public Map<String, Object> calculate(Long taskId, String loginName) {
        TaskContext ctx = loadContext(taskId);
        EvalWarnings warnings = new EvalWarnings();

        // 1. 结构解析
        List<ResolvedUnit> units = structureResolver.resolveAll(ctx.rootObjectId, warnings);

        // 2. 锚定记录与构件装载
        List<Long> spanIds = new ArrayList<>();
        Map<Long, ResolvedPart> partByNodeId = new HashMap<>();
        for (ResolvedUnit unit : units) {
            for (ResolvedSpan span : unit.getSpans()) {
                spanIds.add(span.getSpanId());
                for (ResolvedPart part : span.getParts()) {
                    if (part.getPartId() != null) {
                        partByNodeId.put(part.getPartId(), part);
                    }
                }
            }
        }
        List<SpanElementPart> anchors = spanElementPartMapper.selectBySpanIds(spanIds);
        if (anchors == null) {
            anchors = new ArrayList<>();
        }
        Set<Long> elementIds = new HashSet<>();
        Set<Long> anchorSpanIds = new HashSet<>();
        for (SpanElementPart anchor : anchors) {
            if (anchor.getElementId() == null || anchor.getSpanId() == null || anchor.getPartId() == null) {
                throw new ServiceException("锚定记录(id=" + anchor.getId()
                        + ")缺少 element_id/span_id/part_id，禁止评定");
            }
            if (!spanIds.contains(anchor.getSpanId())) {
                throw new ServiceException("锚定记录(id=" + anchor.getId() + ")引用的桥跨不在评定结构内: spanId="
                        + anchor.getSpanId());
            }
            if (!partByNodeId.containsKey(anchor.getPartId())) {
                throw new ServiceException("锚定记录(id=" + anchor.getId()
                        + ")引用的部件节点不在评定结构内: partId=" + anchor.getPartId());
            }
            elementIds.add(anchor.getElementId());
            anchorSpanIds.add(anchor.getSpanId());
        }
        Map<Long, Element> elementById = new HashMap<>();
        if (!elementIds.isEmpty()) {
            for (Element element : elementMapper.selectByIds(new ArrayList<>(elementIds))) {
                elementById.put(element.getId(), element);
            }
        }

        // 3. 应设未设标记 → 已有 PART 置 absent 或注入幻影件（须在 B.1.3 前完成）
        List<AssessAbsentMark> absentMarks = absentMarkMapper.selectByTaskId(taskId);
        if (absentMarks == null) {
            absentMarks = new ArrayList<>();
        }
        for (AssessAbsentMark mark : absentMarks) {
            applyAbsentMark(mark, units, warnings);
        }

        // 4. 实际设置集合（锚定到未置 absent 的真实部件为准）
        Set<String> presentSpanPartKeys = new HashSet<>();
        for (SpanElementPart anchor : anchors) {
            ResolvedPart part = partByNodeId.get(anchor.getPartId());
            if (part.isAbsent()) {
                warnings.add("部件(id=" + part.getPartId() + ",key=" + part.getPartKey()
                        + ")已标记应设未设，其下 " + anchor.getElementId() + " 条锚定构件不参与评定");
                continue;
            }
            presentSpanPartKeys.add(WeightRedistributor.key(anchor.getSpanId(), part.getPartKey()));
        }

        // 5. 病害：element_uuid 锚定 + 参与评定 + 类型(5230-2026/EDI)
        Map<String, List<Integer>> scalesByElementUuid = loadStructuralScales(taskId, elementById, warnings);

        // 6. 人工判定
        Map<String, AssessManualInput> manualByUuid = new HashMap<>();
        for (AssessManualInput input : manualInputMapper.selectByTaskId(taskId)) {
            if (input.getElementUuid() != null) {
                manualByUuid.put(input.getElementUuid(), input);
            }
        }
        List<Integer> hitIndicatorNos = new ArrayList<>();
        for (AssessSingleControlMark mark : singleControlMarkMapper.selectByTaskId(taskId)) {
            if (Integer.valueOf(1).equals(mark.getHit())) {
                hitIndicatorNos.add(mark.getIndicatorNo());
            }
        }

        // 7. 构件级评定（每条锚定一行；共用构件在所属每跨各评定一次）
        List<ComponentEval> allComponentEvals = new ArrayList<>();
        for (ResolvedUnit unit : units) {
            Set<String> mainPartKeys = mainPartKeys(unit.getBridgeType());
            for (ResolvedSpan span : unit.getSpans()) {
                for (SpanElementPart anchor : anchors) {
                    if (!span.getSpanId().equals(anchor.getSpanId())) {
                        continue;
                    }
                    ResolvedPart part = partByNodeId.get(anchor.getPartId());
                    if (part.isAbsent()) {
                        continue;
                    }
                    Element element = elementById.get(anchor.getElementId());
                    if (element == null) {
                        throw new ServiceException("锚定构件不存在或已删除: elementId=" + anchor.getElementId());
                    }
                    AssessManualInput manual = element.getOfflineUuid() == null
                            ? null : manualByUuid.get(element.getOfflineUuid());
                    ComponentInput input = ComponentInput.builder()
                            .unitId(unit.getUnitId())
                            .spanId(span.getSpanId())
                            .partId(part.getPartId())
                            .partKey(part.getPartKey())
                            .layerKey(part.getLayerKey())
                            .elementId(element.getId())
                            .elementUuid(element.getOfflineUuid())
                            .elementName(element.getName())
                            .mainPart(mainPartKeys.contains(part.getPartKey()))
                            .shared(Integer.valueOf(1).equals(anchor.getIsShared()))
                            .absent(false)
                            .structuralScales(element.getOfflineUuid() == null
                                    ? Collections.emptyList()
                                    : scalesByElementUuid.getOrDefault(element.getOfflineUuid(), Collections.emptyList()))
                            .eddiManual(manual == null ? null : manual.getEddi())
                            .esdiOverride(manual == null ? null : manual.getEsdiOverride())
                            .efiManual(manual == null ? null : manual.getEfi())
                            .eaiManual(manual == null ? null : manual.getEai())
                            .safetyAffected(manual == null ? null
                                    : Integer.valueOf(1).equals(manual.getSafetyAffected()))
                            .build();
                    allComponentEvals.add(componentEvaluator.evaluate(input, warnings));
                }
            }
        }

        // 8. B.1.3 权重重分配（逐单元）→ 9. 桥跨级 → 10. 单元级
        List<SpanEval> allSpanEvals = new ArrayList<>();
        List<UnitEval> unitEvals = new ArrayList<>();
        for (ResolvedUnit unit : units) {
            weightRedistributor.applyB13(unit, presentSpanPartKeys, warnings);

            List<SpanEval> spanEvals = new ArrayList<>();
            for (ResolvedSpan span : unit.getSpans()) {
                List<ComponentEval> spanComponentEvals = new ArrayList<>();
                for (ComponentEval eval : allComponentEvals) {
                    if (span.getSpanId().equals(eval.getSpanId())) {
                        spanComponentEvals.add(eval);
                    }
                }
                spanEvals.add(spanEvaluator.evaluate(span, spanComponentEvals));
            }
            allSpanEvals.addAll(spanEvals);
            unitEvals.add(unitEvaluator.evaluate(unit.getUnitId(), unit.getBridgeType(), spanEvals));
        }

        // 11. 全桥聚合（3.4.4 最差单元 + 3.4.5 单项控制）
        BridgeAggregate bridge = bridgeAggregator.aggregate(unitEvals, hitIndicatorNos);

        // 12. 落库（先删后插，幂等）
        String operator = loginName == null ? "system" : loginName;
        elementResultMapper.deleteByTaskId(taskId);
        spanEvaluationMapper.deleteByTaskId(taskId);
        unitResultMapper.deleteByTaskId(taskId);
        bridgeResultMapper.deleteByTaskId(taskId);

        List<AssessElementResult> elementRows = new ArrayList<>();
        for (ComponentEval eval : allComponentEvals) {
            elementRows.add(toElementRow(taskId, operator, eval));
        }
        for (ResolvedUnit unit : units) {
            for (ResolvedSpan span : unit.getSpans()) {
                for (ResolvedPart part : span.getParts()) {
                    if (part.isAbsent()) {
                        elementRows.add(toAbsentElementRow(taskId, operator, unit, span, part));
                    }
                }
            }
        }
        if (!elementRows.isEmpty()) {
            elementResultMapper.batchInsert(elementRows);
        }
        for (ResolvedUnit unit : units) {
            for (SpanEval spanEval : allSpanEvals) {
                if (unit.getUnitId().equals(spanEval.getUnitId())) {
                    spanEvaluationMapper.insertSpanEvaluation(
                            toSpanRow(taskId, operator, unit.getBridgeType(), spanEval));
                }
            }
        }
        for (UnitEval unitEval : unitEvals) {
            unitResultMapper.insertAssessUnitResult(toUnitRow(taskId, operator, unitEval));
        }
        bridgeResultMapper.insertAssessBridgeResult(
                toBridgeRow(taskId, ctx.rootObjectId, operator, bridge));

        Map<String, Object> result = new HashMap<>();
        result.put("bridge", bridgeResultMapper.selectByTaskId(taskId));
        result.put("warnings", warnings.getWarnings());
        result.put("unitCount", units.size());
        result.put("spanCount", allSpanEvals.size());
        result.put("elementRowCount", elementRows.size());
        return result;
    }

    // ---------- 装配辅助 ----------

    private void applyAbsentMark(AssessAbsentMark mark, List<ResolvedUnit> units, EvalWarnings warnings) {
        if (mark.getPartKey() == null || mark.getPartKey().isEmpty()) {
            throw new ServiceException("应设未设标记(id=" + mark.getId() + ")缺少 partKey");
        }
        List<ResolvedSpan> targets = new ArrayList<>();
        for (ResolvedUnit unit : units) {
            if (mark.getUnitId() != null && !mark.getUnitId().equals(unit.getUnitId())) {
                continue;
            }
            for (ResolvedSpan span : unit.getSpans()) {
                if (mark.getSpanId() != null && !mark.getSpanId().equals(span.getSpanId())) {
                    continue;
                }
                targets.add(span);
            }
        }
        if (targets.isEmpty()) {
            throw new ServiceException("应设未设标记(id=" + mark.getId() + ",partKey=" + mark.getPartKey()
                    + ")的作用范围(unitId=" + mark.getUnitId() + ",spanId=" + mark.getSpanId()
                    + ")未命中评定结构内任何桥跨");
        }
        for (ResolvedSpan span : targets) {
            ResolvedPart existing = null;
            for (ResolvedPart part : span.getParts()) {
                if (mark.getPartKey().equals(part.getPartKey())) {
                    existing = part;
                    break;
                }
            }
            if (existing != null) {
                if (!existing.isAbsent()) {
                    existing.setAbsent(true);
                    existing.setCi(3);
                }
            } else {
                // 幻影件按所属单元桥型从目录构造（partId 恒为 null）
                ResolvedPart phantom = structureResolver.phantomPart(
                        mark.getPartKey(), mark.getLayerKey(), bridgeTypeOf(units, span));
                span.getParts().add(phantom);
                warnings.add("桥跨(id=" + span.getSpanId() + ")按应设未设标记注入幻影部件: "
                        + mark.getPartKey());
            }
        }
    }

    private String bridgeTypeOf(List<ResolvedUnit> units, ResolvedSpan span) {
        for (ResolvedUnit unit : units) {
            if (unit.getUnitId().equals(span.getUnitId())) {
                return unit.getBridgeType();
            }
        }
        throw new ServiceException("内部错误：桥跨(id=" + span.getSpanId() + ")找不到所属评定单元");
    }

    private Map<String, List<Integer>> loadStructuralScales(Long taskId,
                                                            Map<Long, Element> elementById,
                                                            EvalWarnings warnings) {
        List<Disease> diseases = diseaseMapper.selectDiseaseListByTaskIds(
                Collections.singletonList(taskId));
        if (diseases == null || diseases.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> typeIds = new HashSet<>();
        for (Disease disease : diseases) {
            if (disease.getDiseaseTypeId() != null) {
                typeIds.add(disease.getDiseaseTypeId());
            }
        }
        Map<Long, DiseaseType> typeById = new HashMap<>();
        if (!typeIds.isEmpty()) {
            for (DiseaseType type : diseaseTypeMapper.selectByIds(new ArrayList<>(typeIds))) {
                typeById.put(type.getId(), type);
            }
        }

        Set<String> anchoredUuids = new HashSet<>();
        for (Element element : elementById.values()) {
            if (element.getOfflineUuid() != null) {
                anchoredUuids.add(element.getOfflineUuid());
            }
        }

        Map<String, List<Integer>> scalesByUuid = new LinkedHashMap<>();
        int dangling = 0;
        int ignored = 0;
        for (Disease disease : diseases) {
            if (!"1".equals(disease.getParticipateAssess())) {
                continue;
            }
            String uuid = disease.getElementUuid();
            if (uuid == null || !anchoredUuids.contains(uuid)) {
                dangling++;
                continue;
            }
            DiseaseType type = disease.getDiseaseTypeId() == null ? null : typeById.get(disease.getDiseaseTypeId());
            if (type == null || !STD_VERSION.equals(type.getStdVersion())
                    || !SCALE_TYPE_EDI.equals(type.getScaleType())) {
                ignored++;
                continue;
            }
            scalesByUuid.computeIfAbsent(uuid, k -> new ArrayList<>()).add(disease.getLevel());
        }
        if (dangling > 0) {
            warnings.add("有 " + dangling + " 条参与评定病害无法对应到锚定构件，未计入 ESDI");
        }
        if (ignored > 0) {
            warnings.add("有 " + ignored + " 条参与评定病害的类型非 5230-2026/EDI，未计入 ESDI");
        }
        return scalesByUuid;
    }

    private Set<String> mainPartKeys(String bridgeType) {
        List<String> mainParts = standardCatalogLoader.getCatalog().getMainParts().get(bridgeType);
        return mainParts == null ? new HashSet<>() : new HashSet<>(mainParts);
    }

    // ---------- 行映射 ----------

    private AssessElementResult toElementRow(Long taskId, String operator, ComponentEval eval) {
        AssessElementResult row = new AssessElementResult();
        row.setTaskId(taskId);
        row.setUnitId(eval.getUnitId());
        row.setSpanId(eval.getSpanId());
        row.setPartId(eval.getPartId());
        row.setPartKey(eval.getPartKey());
        row.setLayerKey(eval.getLayerKey());
        row.setElementUuid(eval.getElementUuid());
        row.setElementName(eval.getElementName());
        row.setIsMain(eval.isMainPart() ? 1 : 0);
        row.setIsShared(eval.isShared() ? 1 : 0);
        row.setAbsentFlag(0);
        row.setEddi(eval.getEddi());
        row.setEsdi(eval.getEsdi());
        row.setEdi(eval.getEdi());
        row.setEfi(eval.getEfi());
        row.setEai(eval.getEai());
        row.setEci(eval.getEci());
        row.setEcl(eval.getEcl());
        row.setEddiSource(eval.getEddiSource());
        row.setEsdiSource(eval.getEsdiSource());
        row.setCreateBy(operator);
        return row;
    }

    private AssessElementResult toAbsentElementRow(Long taskId, String operator,
                                                   ResolvedUnit unit, ResolvedSpan span,
                                                   ResolvedPart part) {
        AssessElementResult row = new AssessElementResult();
        row.setTaskId(taskId);
        row.setUnitId(unit.getUnitId());
        row.setSpanId(span.getSpanId());
        row.setPartId(part.getPartId());
        row.setPartKey(part.getPartKey());
        row.setLayerKey(part.getLayerKey());
        row.setElementUuid(null);
        row.setElementName(null);
        row.setIsMain(mainPartKeys(unit.getBridgeType()).contains(part.getPartKey()) ? 1 : 0);
        row.setIsShared(0);
        row.setAbsentFlag(1);
        row.setEddi(0);
        row.setEsdi(0);
        row.setEdi(3);
        row.setEfi(0);
        row.setEai(0);
        row.setEci(3);
        row.setEcl(3);
        row.setEddiSource(ComponentEval.SOURCE_ABSENT);
        row.setEsdiSource(ComponentEval.SOURCE_ABSENT);
        row.setCreateBy(operator);
        return row;
    }

    private SpanEvaluation toSpanRow(Long taskId, String operator,
                                     String bridgeType, SpanEval eval) {
        SpanEvaluation row = new SpanEvaluation();
        row.setTaskId(taskId);
        row.setUnitId(eval.getUnitId());
        row.setSpanId(eval.getSpanId());
        row.setBridgeType(bridgeType);
        row.setBsci(eval.getBsci());
        row.setSpanLevel(eval.getBscl());
        row.setBsciRaw(eval.getBsciRaw());
        row.setRawLevel(eval.getRawLevel());
        row.setOverrideFlag(eval.isOverride() ? 1 : 0);
        row.setOverrideLevel(eval.getOverrideLevel());
        row.setOverrideTrigger(truncate(eval.getTrigger(), 200));
        row.setStdVersion(STD_VERSION);
        row.setCreateBy(operator);
        return row;
    }

    private AssessUnitResult toUnitRow(Long taskId, String operator, UnitEval eval) {
        AssessUnitResult row = new AssessUnitResult();
        row.setTaskId(taskId);
        row.setUnitId(eval.getUnitId());
        row.setBridgeType(eval.getBridgeType());
        row.setBci(eval.getBci());
        row.setBcl(eval.getBcl());
        row.setCombineMode(eval.getCalcMode());
        row.setBsciMin(eval.getBsciMin());
        row.setBsciMax(eval.getBsciMax());
        row.setBsciAvg(eval.getBsciMean());
        row.setBsciLim(eval.getBsciLim());
        row.setSpanCount(eval.getSpanCount());
        row.setCreateBy(operator);
        return row;
    }

    private AssessBridgeResult toBridgeRow(Long taskId, Long rootObjectId,
                                           String operator, BridgeAggregate eval) {
        AssessBridgeResult row = new AssessBridgeResult();
        row.setTaskId(taskId);
        row.setRootObjectId(rootObjectId);
        row.setBci(eval.getBci());
        row.setBcl(eval.getBcl());
        row.setCombineMode(eval.getCombineMode());
        row.setWorstUnitId(eval.getWorstUnitId());
        row.setLayeredBci(eval.getLayeredBci());
        row.setLayeredBcl(eval.getLayeredBcl());
        row.setSingleControlHits(eval.getSingleControlNos());
        row.setStdVersion(STD_VERSION);
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        return row;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ---------- 任务上下文 ----------

    private static class TaskContext {
        private Long rootObjectId;
    }

    private TaskContext loadContext(Long taskId) {
        Task task = require5230Task(taskId);
        if (task.getBuildingId() == null) {
            throw new ServiceException("任务(id=" + taskId + ")未关联建筑物");
        }
        Building building = buildingMapper.selectBuildingById(task.getBuildingId());
        if (building == null || building.getNewRootObjectId() == null) {
            throw new ServiceException("任务(id=" + taskId
                    + ")关联建筑物缺少新标评定根节点(new_root_object_id)");
        }
        TaskContext context = new TaskContext();
        context.rootObjectId = building.getNewRootObjectId();
        return context;
    }

    private Task require5230Task(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        Task task = taskMapper.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在: " + taskId);
        }
        if (!STD_VERSION.equals(task.getStdVersion())) {
            throw new ServiceException("任务(id=" + taskId + ")非 JTG/T 5230—2026 任务，禁止使用新标评定接口");
        }
        return task;
    }
}
