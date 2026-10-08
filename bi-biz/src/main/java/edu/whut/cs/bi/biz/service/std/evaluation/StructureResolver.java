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
 * 结构解析（JTG/T 5230—2026 三级评定第一步）：
 * 把评定单元的 bi_object 实例树（UNIT/SPAN/LAYER/PART）解析为 {@link ResolvedUnit}。
 *
 * <p>部件 key、ω、γ 双路解析：优先实例节点及其模板对象（templateObjectId、
 * 节点 props JSON 中的 layer/part），缺失时按 bridgeType 从权威目录按名称兜底；
 * 均失败则报 UNRESOLVED 错误。每次兜底在 {@link EvalWarnings} 留痕。</p>
 */
@Component
public class StructureResolver {

    /** 结构层固定顺序（用于无任何 key 来源时的最终兜底）。 */
    private static final String[] LAYER_ORDER = {"deck", "superstructure", "substructure", "ancillary"};

    private final BiObjectMapper biObjectMapper;
    private final BiTemplateObjectMapper biTemplateObjectMapper;
    private final StandardCatalogLoader standardCatalogLoader;

    public StructureResolver(BiObjectMapper biObjectMapper,
                             BiTemplateObjectMapper biTemplateObjectMapper,
                             StandardCatalogLoader standardCatalogLoader) {
        this.biObjectMapper = biObjectMapper;
        this.biTemplateObjectMapper = biTemplateObjectMapper;
        this.standardCatalogLoader = standardCatalogLoader;
    }

    /**
     * 解析评定单元。
     *
     * @param unitObjectId UNIT 节点ID
     * @param warnings 非阻断告警收集
     * @return 解析后的评定单元
     */
    public ResolvedUnit resolve(Long unitObjectId, EvalWarnings warnings) {
        List<BiObject> nodes = biObjectMapper.selectChildrenById(unitObjectId);
        if (nodes == null || nodes.isEmpty()) {
            throw new ServiceException("评定单元节点不存在或无结构数据: " + unitObjectId);
        }

        BiObject unit = nodes.stream()
                .filter(n -> unitObjectId.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("评定单元节点不存在: " + unitObjectId));
        String bridgeType = unit.getBridgeType();
        if (bridgeType == null || bridgeType.isEmpty()) {
            throw new ServiceException("评定单元(id=" + unitObjectId + ")缺少 bridge_type，无法按新标评定");
        }

        StdCatalogContext context = loadTemplate(bridgeType);

        ResolvedUnit result = new ResolvedUnit();
        result.setRootObjectId(unitObjectId);
        result.setBridgeType(bridgeType);

        List<BiObject> spanNodes = filterType(nodes, "SPAN");
        if (spanNodes.isEmpty()) {
            throw new ServiceException("评定单元(id=" + unitObjectId + ")下无 SPAN 桥跨节点");
        }
        spanNodes.sort(Comparator.comparing((BiObject n) -> n.getSpanNo() == null ? Integer.MAX_VALUE : n.getSpanNo())
                .thenComparing(BiObject::getId));

        List<BiObject> layerNodes = filterType(nodes, "LAYER");
        List<BiObject> partNodes = filterType(nodes, "PART");

        for (BiObject spanNode : spanNodes) {
            ResolvedSpan span = new ResolvedSpan();
            span.setSpanId(spanNode.getId());
            span.setSpanNo(spanNode.getSpanNo());

            List<BiObject> myLayers = childrenOf(layerNodes, spanNode.getId());
            myLayers.sort(Comparator.comparing(n -> n.getOrderNum() == null ? Integer.MAX_VALUE : n.getOrderNum()));
            int layerIndex = 0;
            for (BiObject layerNode : myLayers) {
                String layerKey = resolveLayerKey(layerNode, layerIndex, warnings);
                BigDecimal gamma = resolveGamma(layerNode, layerKey, context.template, warnings);
                if (gamma == null) {
                    throw new ServiceException("结构层节点(id=" + layerNode.getId()
                            + ")无法解析影响系数 γ，禁止评定");
                }

                List<BiObject> myParts = childrenOf(partNodes, layerNode.getId());
                myParts.sort(Comparator.comparing(n -> n.getOrderNum() == null ? Integer.MAX_VALUE : n.getOrderNum()));
                for (BiObject partNode : myParts) {
                    span.getParts().add(resolvePart(partNode, layerKey, gamma, context, warnings));
                }
                layerIndex++;
            }
            result.getSpans().add(span);
        }
        return result;
    }


    /**
     * 判断部件节点是否属于某桥型模板（用于应设未设标记保存校验）。
     */
    public boolean isTemplatePart(BiObject partNode, String bridgeType) {
        StdCatalogContext context = loadTemplate(bridgeType);
        return matchPartDefByName(context, partNode.getName()) != null;
    }

    /**
     * 为应设未设部件构建幻影部件（树中无 PART 节点时使用）。
     *
     * @param partNode 应设未设的部件节点
     * @param bridgeType 桥型代码
     */
    public ResolvedPart phantomPart(BiObject partNode, String bridgeType) {
        StdCatalogContext context = loadTemplate(bridgeType);
        StdPartDef def = matchPartDefByName(context, partNode.getName());
        if (def == null) {
            throw new ServiceException("应设未设部件(id=" + partNode.getId()
                    + ")在桥型 " + bridgeType + " 模板中不存在: " + partNode.getName());
        }
        ResolvedPart part = new ResolvedPart();
        part.setPartId(partNode.getId());
        part.setPartKey(def.getKey());
        part.setLayerKey(layerOfDef(context, def));
        part.setOmega(def.getOmega());
        part.setOmegaEffective(BigDecimal.valueOf(def.getOmega()));
        part.setGamma(context.template.getGamma().get(part.getLayerKey()));
        part.setAbsent(true);
        part.setCi(3);
        return part;
    }

    // ---------- 部件/层解析 ----------

    private ResolvedPart resolvePart(BiObject partNode, String layerKey, BigDecimal gamma,
                                     StdCatalogContext context, EvalWarnings warnings) {
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

    // ---------- 目录匹配 ----------

    private StdCatalogContext loadTemplate(String bridgeType) {
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
        StdCatalogContext context = new StdCatalogContext();
        context.template = template;
        context.layerDefs = new LinkedHashMap<>();
        for (StdLayerDef layerDef : standardCatalogLoader.getCatalog().getSharedLayers()) {
            context.layerDefs.put(layerDef.getKey(), layerDef);
        }
        return context;
    }

    private StdPartDef matchPartDefByName(StdCatalogContext context, String name) {
        if (name == null) {
            return null;
        }
        for (StdPartDef def : context.template.getSuperstructureParts()) {
            if (name.equals(def.getName()) || name.equals(def.getKey())) {
                return def;
            }
        }
        for (StdLayerDef layerDef : context.layerDefs.values()) {
            for (StdPartDef def : layerDef.getParts()) {
                if (name.equals(def.getName()) || name.equals(def.getKey())) {
                    return def;
                }
            }
        }
        return null;
    }

    private String layerOfDef(StdCatalogContext context, StdPartDef target) {
        for (StdPartDef def : context.template.getSuperstructureParts()) {
            if (def == target) {
                return "superstructure";
            }
        }
        for (Map.Entry<String, StdLayerDef> entry : context.layerDefs.entrySet()) {
            for (StdPartDef def : entry.getValue().getParts()) {
                if (def == target) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    // ---------- 辅助 ----------

    private List<BiObject> filterType(List<BiObject> nodes, String type) {
        List<BiObject> result = new ArrayList<>();
        for (BiObject node : nodes) {
            if (type.equals(node.getNodeType()) && !Integer.valueOf(1).equals(node.getOfflineDeleted())) {
                result.add(node);
            }
        }
        return result;
    }

    private List<BiObject> childrenOf(List<BiObject> nodes, Long parentId) {
        List<BiObject> result = new ArrayList<>();
        for (BiObject node : nodes) {
            if (parentId.equals(node.getParentId())) {
                result.add(node);
            }
        }
        return result;
    }

    /** 单次解析内的模板上下文。 */
    private static class StdCatalogContext {
        private StdBridgeTemplate template;
        private Map<String, StdLayerDef> layerDefs;
    }
}
