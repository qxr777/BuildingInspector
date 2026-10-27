package edu.whut.cs.bi.biz.service.std.evaluation;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import edu.whut.cs.bi.biz.service.std.model.StdBridgeTemplate;
import edu.whut.cs.bi.biz.service.std.model.StdLayerDef;
import edu.whut.cs.bi.biz.service.std.model.StdPartDef;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新链结构解析（JTG/T 5230—2026 三级评定第一步）。
 *
 * <p>把桥梁新树根（bi_building.new_root_object_id）下的实例树解析为一个或多个
 * {@link ResolvedUnit}：</p>
 * <ul>
 *   <li>根节点本身为 UNIT（演示链/单评定单元）→ 解析出 1 个单元；</li>
 *   <li>根节点为 ROOT，其下挂多个 UNIT（3.1.2 纵向不同结构形式/横向拼宽）→ 每个 UNIT 各解析一个；</li>
 *   <li>层级固定 UNIT→SPAN→LAYER→PART，全部在一次树查询后于内存组树。</li>
 * </ul>
 *
 * <p>部件 key、ω、γ 三级解析：实例节点 propsJson/props → 绑定模板对象 nodeCode/omega/gamma
 * → YAML 权威目录按名称兜底；均失败则硬错误，禁止评定。每次兜底在 {@link EvalWarnings} 留痕。
 * 应设未设幻影件（3.2.2-5）在新链中无 PART 节点，由 absent mark 的 partKey/layerKey
 * 直接从目录构造 {@link #phantomPart}。</p>
 */
@Component
public class UnitStructureResolver {

    /** 结构层固定顺序（用于无任何 key 来源时的最终兜底）。 */
    private static final String[] LAYER_ORDER = {"deck", "superstructure", "substructure", "ancillary"};

    public static final String NODE_ROOT = "ROOT";
    public static final String NODE_UNIT = "UNIT";
    public static final String NODE_SPAN = "SPAN";
    public static final String NODE_LAYER = "LAYER";
    public static final String NODE_PART = "PART";

    private final BiObjectMapper biObjectMapper;
    private final BiTemplateObjectMapper biTemplateObjectMapper;
    private final StandardCatalogLoader standardCatalogLoader;

    public UnitStructureResolver(BiObjectMapper biObjectMapper,
                                 BiTemplateObjectMapper biTemplateObjectMapper,
                                 StandardCatalogLoader standardCatalogLoader) {
        this.biObjectMapper = biObjectMapper;
        this.biTemplateObjectMapper = biTemplateObjectMapper;
        this.standardCatalogLoader = standardCatalogLoader;
    }

    /**
     * 解析桥梁根下的全部评定单元。
     *
     * @param rootObjectId bi_building.new_root_object_id（ROOT 或 UNIT 节点）
     * @param warnings 非阻断告警收集
     * @return 解析后的评定单元列表（单单元时长度 1）
     */
    public List<ResolvedUnit> resolveAll(Long rootObjectId, EvalWarnings warnings) {
        if (rootObjectId == null) {
            throw new ServiceException("桥梁缺少新标评定根节点(new_root_object_id)，无法评定");
        }
        // 含根节点自身、最深 5 层（ROOT→UNIT→SPAN→LAYER→PART）的扁平列表
        List<BiObject> nodes = biObjectMapper.selectChildrenByIdRemoveLeafNew(rootObjectId);
        if (nodes == null || nodes.isEmpty()) {
            throw new ServiceException("评定根节点不存在或无结构数据: " + rootObjectId);
        }

        BiObject root = null;
        for (BiObject node : nodes) {
            if (rootObjectId.equals(node.getId())) {
                root = node;
                break;
            }
        }
        if (root == null) {
            throw new ServiceException("评定根节点不存在: " + rootObjectId);
        }

        List<BiObject> unitNodes = new ArrayList<>();
        if (NODE_UNIT.equals(root.getNodeType())) {
            unitNodes.add(root);
        } else {
            for (BiObject node : nodes) {
                if (NODE_UNIT.equals(node.getNodeType()) && rootObjectId.equals(node.getParentId())) {
                    unitNodes.add(node);
                }
            }
            if (unitNodes.isEmpty()) {
                throw new ServiceException(
                        "评定根节点(id=" + rootObjectId + ")下无 UNIT 评定单元节点，无法进行新标评定");
            }
        }
        unitNodes.sort(Comparator.comparing(BiObject::getId));

        List<ResolvedUnit> units = new ArrayList<>();
        for (BiObject unitNode : unitNodes) {
            units.add(resolveUnit(unitNode, nodes, warnings));
        }
        return units;
    }

    /**
     * 按 partKey 从权威目录构造应设未设幻影部件（树中无 PART 节点）。
     *
     * @param partKey  absent mark 的部件键
     * @param layerKey absent mark 的结构层键（可空，空则按目录定位）
     * @param bridgeType 桥型代码
     */
    public ResolvedPart phantomPart(String partKey, String layerKey, String bridgeType) {
        if (partKey == null || partKey.isEmpty()) {
            throw new ServiceException("应设未设标记缺少 partKey，无法构造幻影部件");
        }
        CatalogContext context = loadTemplate(bridgeType);
        StdPartDef def = findPartDef(context, partKey);
        if (def == null) {
            throw new ServiceException("应设未设部件 " + partKey
                    + " 在桥型 " + bridgeType + " 模板中不存在，禁止评定");
        }
        String resolvedLayer = layerKey;
        if (resolvedLayer == null) {
            resolvedLayer = layerOfDef(context, def);
        }
        if (resolvedLayer == null) {
            throw new ServiceException("应设未设部件 " + partKey + " 无法定位结构层，请补 layerKey");
        }
        BigDecimal gamma = context.template.getGamma().get(resolvedLayer);
        if (gamma == null) {
            throw new ServiceException("结构层 " + resolvedLayer + " 在桥型 " + bridgeType
                    + " 模板中缺少影响系数 γ");
        }

        ResolvedPart part = new ResolvedPart();
        part.setPartId(null);
        part.setPartKey(def.getKey());
        part.setLayerKey(resolvedLayer);
        part.setOmega(def.getOmega());
        part.setOmegaEffective(BigDecimal.valueOf(def.getOmega()));
        part.setGamma(gamma);
        part.setAbsent(true);
        part.setCi(3);
        return part;
    }

    // ---------- 单元/跨/层/部件解析 ----------

    private ResolvedUnit resolveUnit(BiObject unitNode, List<BiObject> nodes, EvalWarnings warnings) {
        Long unitId = unitNode.getId();
        String bridgeType = unitNode.getBridgeType();
        if (bridgeType == null || bridgeType.isEmpty()) {
            throw new ServiceException("评定单元(id=" + unitId + ")缺少 bridge_type，无法按新标评定");
        }
        CatalogContext context = loadTemplate(bridgeType);

        ResolvedUnit unit = new ResolvedUnit();
        unit.setUnitId(unitId);
        unit.setBridgeType(bridgeType);

        List<BiObject> spanNodes = childrenOfType(nodes, unitId, NODE_SPAN);
        if (spanNodes.isEmpty()) {
            throw new ServiceException("评定单元(id=" + unitId + ")下无 SPAN 桥跨节点");
        }
        spanNodes.sort(Comparator.comparing((BiObject n) -> n.getSpanNo() == null ? Integer.MAX_VALUE : n.getSpanNo())
                .thenComparing(BiObject::getId));

        for (BiObject spanNode : spanNodes) {
            ResolvedSpan span = new ResolvedSpan();
            span.setUnitId(unitId);
            span.setSpanId(spanNode.getId());
            span.setSpanNo(spanNode.getSpanNo());

            List<BiObject> layerNodes = childrenOfType(nodes, spanNode.getId(), NODE_LAYER);
            layerNodes.sort(Comparator.comparing(n -> n.getOrderNum() == null ? Integer.MAX_VALUE : n.getOrderNum()));
            int layerIndex = 0;
            for (BiObject layerNode : layerNodes) {
                String layerKey = resolveLayerKey(layerNode, layerIndex, warnings);
                BigDecimal gamma = resolveGamma(layerNode, layerKey, context.template, warnings);
                if (gamma == null) {
                    throw new ServiceException("结构层节点(id=" + layerNode.getId()
                            + ")无法解析影响系数 γ，禁止评定");
                }

                List<BiObject> partNodes = childrenOfType(nodes, layerNode.getId(), NODE_PART);
                partNodes.sort(Comparator.comparing(n -> n.getOrderNum() == null ? Integer.MAX_VALUE : n.getOrderNum()));
                for (BiObject partNode : partNodes) {
                    span.getParts().add(resolvePart(partNode, layerKey, gamma, context, warnings));
                }
                layerIndex++;
            }
            unit.getSpans().add(span);
        }
        return unit;
    }

    private ResolvedPart resolvePart(BiObject partNode, String layerKey, BigDecimal gamma,
                                     CatalogContext context, EvalWarnings warnings) {
        StdPartDef def = matchPartDefByName(context, partNode.getName());

        String partKey = readProps(partNode).getString("part");
        if (partKey == null) {
            BiTemplateObject templateObject = templateObjectOf(partNode, warnings);
            if (templateObject != null && templateObject.getNodeCode() != null
                    && templateObject.getNodeCode().contains(".part.")) {
                partKey = templateObject.getNodeCode().substring(
                        templateObject.getNodeCode().lastIndexOf('.') + 1);
            }
        }
        if (partKey == null && def != null) {
            partKey = def.getKey();
            warnings.add("部件节点(id=" + partNode.getId() + ",名称=" + partNode.getName()
                    + ")的 part key 由权威目录名称匹配兜底");
        }
        if (partKey == null) {
            throw new ServiceException("部件节点(id=" + partNode.getId() + ",名称=" + partNode.getName()
                    + ")无法解析 part key（模板对象与目录匹配均失败）");
        }

        Integer omega = partNode.getOmega();
        if (omega == null) {
            BiTemplateObject templateObject = templateObjectOf(partNode, warnings);
            if (templateObject != null) {
                omega = templateObject.getOmega();
            }
        }
        if (omega == null && def != null) {
            omega = def.getOmega();
            warnings.add("部件节点(id=" + partNode.getId() + ",名称=" + partNode.getName()
                    + ")的权重 ω 由权威目录兜底");
        }
        if (omega == null) {
            throw new ServiceException("部件节点(id=" + partNode.getId() + ",名称=" + partNode.getName()
                    + ")无法解析权重 ω（模板对象与目录匹配均失败）");
        }

        ResolvedPart part = new ResolvedPart();
        part.setPartId(partNode.getId());
        part.setPartKey(partKey);
        part.setLayerKey(layerKey);
        part.setOmega(omega);
        part.setOmegaEffective(BigDecimal.valueOf(omega));
        part.setGamma(gamma);
        return part;
    }

    private String resolveLayerKey(BiObject layerNode, int layerIndex, EvalWarnings warnings) {
        String layerKey = readProps(layerNode).getString("layer");
        if (layerKey == null) {
            BiTemplateObject templateObject = templateObjectOf(layerNode, warnings);
            if (templateObject != null && templateObject.getNodeCode() != null
                    && templateObject.getNodeCode().contains(".layer.")) {
                layerKey = templateObject.getNodeCode().substring(
                        templateObject.getNodeCode().lastIndexOf('.') + 1);
            }
        }
        if (layerKey == null && layerIndex >= 0 && layerIndex < LAYER_ORDER.length) {
            layerKey = LAYER_ORDER[layerIndex];
            warnings.add("结构层节点(id=" + layerNode.getId() + ")的 layer key 按层顺序兜底: " + layerKey);
        }
        if (layerKey == null) {
            throw new ServiceException("结构层节点(id=" + layerNode.getId() + ")无法解析 layer key");
        }
        return layerKey;
    }

    private BigDecimal resolveGamma(BiObject layerNode, String layerKey,
                                    StdBridgeTemplate template, EvalWarnings warnings) {
        if (layerNode.getGamma() != null) {
            return layerNode.getGamma();
        }
        BiTemplateObject templateObject = templateObjectOf(layerNode, warnings);
        if (templateObject != null && templateObject.getGamma() != null) {
            return templateObject.getGamma();
        }
        BigDecimal gamma = template.getGamma().get(layerKey);
        if (gamma != null) {
            warnings.add("结构层节点(id=" + layerNode.getId() + ")的 γ 由权威目录兜底: " + gamma);
        }
        return gamma;
    }

    private BiTemplateObject templateObjectOf(BiObject node, EvalWarnings warnings) {
        if (node.getTemplateObjectId() == null) {
            return null;
        }
        BiTemplateObject templateObject =
                biTemplateObjectMapper.selectBiTemplateObjectById(node.getTemplateObjectId());
        if (templateObject == null) {
            warnings.add("节点(id=" + node.getId() + ")引用的模板对象不存在: "
                    + node.getTemplateObjectId());
        }
        return templateObject;
    }

    /** 合并读取节点的 propsJson / props 为 JSON（非法 JSON 返回空对象）。 */
    private JSONObject readProps(BiObject node) {
        JSONObject merged = new JSONObject(new LinkedHashMap<>());
        mergeJson(merged, node.getPropsJson());
        mergeJson(merged, node.getProps());
        return merged;
    }

    private void mergeJson(JSONObject target, String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            target.putAll(JSONObject.parseObject(json));
        } catch (Exception ignored) {
            // 非 JSON（如旧模板附件属性串），忽略
        }
    }

    // ---------- 目录 ----------

    private CatalogContext loadTemplate(String bridgeType) {
        StdBridgeTemplate template = standardCatalogLoader.getCatalog().getBridgeTemplates().get(bridgeType);
        if (template == null) {
            throw new ServiceException("桥型 " + bridgeType + " 暂不支持新标评定（模板未定义）");
        }
        boolean structureComplete = standardCatalogLoader.getCatalog().getBridgeTypes().stream()
                .filter(t -> bridgeType.equals(t.getCode()))
                .map(t -> t.isStructureComplete())
                .findFirst()
                .orElse(false);
        if (!structureComplete) {
            throw new ServiceException("桥型 " + bridgeType + " 结构不完整，本期不支持新标评定");
        }
        CatalogContext context = new CatalogContext();
        context.template = template;
        context.layerDefs = new LinkedHashMap<>();
        for (StdLayerDef layerDef : standardCatalogLoader.getCatalog().getSharedLayers()) {
            context.layerDefs.put(layerDef.getKey(), layerDef);
        }
        return context;
    }

    private StdPartDef findPartDef(CatalogContext context, String key) {
        if (key == null) {
            return null;
        }
        for (StdPartDef def : context.template.getSuperstructureParts()) {
            if (key.equals(def.getKey()) || key.equals(def.getName())) {
                return def;
            }
        }
        for (StdLayerDef layerDef : context.layerDefs.values()) {
            for (StdPartDef def : layerDef.getParts()) {
                if (key.equals(def.getKey()) || key.equals(def.getName())) {
                    return def;
                }
            }
        }
        return null;
    }

    private StdPartDef matchPartDefByName(CatalogContext context, String name) {
        return findPartDef(context, name);
    }

    private String layerOfDef(CatalogContext context, StdPartDef target) {
        for (StdPartDef def : context.template.getSuperstructureParts()) {
            if (def.getKey().equals(target.getKey())) {
                return "superstructure";
            }
        }
        for (Map.Entry<String, StdLayerDef> entry : context.layerDefs.entrySet()) {
            for (StdPartDef def : entry.getValue().getParts()) {
                if (def.getKey().equals(target.getKey())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    // ---------- 辅助 ----------

    private List<BiObject> childrenOfType(List<BiObject> nodes, Long parentId, String type) {
        List<BiObject> result = new ArrayList<>();
        for (BiObject node : nodes) {
            if (type.equals(node.getNodeType())
                    && parentId.equals(node.getParentId())
                    && !Integer.valueOf(1).equals(node.getOfflineDeleted())) {
                result.add(node);
            }
        }
        return result;
    }

    /** 单次解析内的模板上下文。 */
    private static class CatalogContext {
        private StdBridgeTemplate template;
        private Map<String, StdLayerDef> layerDefs;
    }
}
