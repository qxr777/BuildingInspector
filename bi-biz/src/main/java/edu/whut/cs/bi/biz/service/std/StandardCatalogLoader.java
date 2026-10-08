package edu.whut.cs.bi.biz.service.std;

import cn.hutool.json.JSONUtil;
import edu.whut.cs.bi.biz.service.std.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath {@code std/5230-2026/**}{@code .yml} 装配 {@link StdCatalog}。
 * 种子写库与 App 模板包生成共用本出口，确保权威数据单一来源。
 */
@Component
public class StandardCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(StandardCatalogLoader.class);

    private static final String RESOURCE_PATTERN = "classpath*:std/5230-2026/**/*.yml";

    /** 标准结构层在模板中的固定顺序。 */
    private static final String[] LAYER_ORDER = {"deck", "superstructure", "substructure", "ancillary"};

    private volatile StdCatalog cached;

    /** 重新读取并装配目录（刷新缓存）。 */
    public StdCatalog load() {
        StdCatalog catalog = new StdCatalog();
        Map<String, StdLayerDef> sharedByKey = new LinkedHashMap<>();

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(RESOURCE_PATTERN);
        } catch (Exception e) {
            throw new IllegalStateException("扫描标准种子资源失败: " + RESOURCE_PATTERN, e);
        }

        Yaml yaml = new Yaml();
        for (Resource resource : resources) {
            Map<String, Object> root = readYaml(yaml, resource);
            if (root == null || root.isEmpty()) {
                continue;
            }
            if (root.containsKey("bridgeTypes")) {
                parseMeta(catalog, root);
            } else if (root.containsKey("diseaseTypes")) {
                parseDiseaseTypes(catalog, root);
            } else if (root.containsKey("layers")) {
                parseSharedLayers(sharedByKey, root);
            } else if (root.containsKey("mainParts")) {
                parseMainParts(catalog, root);
            } else if (root.containsKey("gamma") && root.containsKey("code")) {
                parseBridgeTemplate(catalog, root);
            } else {
                log.warn("标准种子文件未识别（缺少已知顶层键）: {}", resource.getDescription());
            }
        }

        catalog.setSharedLayers(new ArrayList<>(sharedByKey.values()));
        materializeNodes(catalog, sharedByKey);
        validateShape(catalog);

        cached = catalog;
        return catalog;
    }

    /** 取目录，首次访问时装配。 */
    public StdCatalog getCatalog() {
        StdCatalog local = cached;
        if (local == null) {
            synchronized (this) {
                local = cached;
                if (local == null) {
                    local = load();
                }
            }
        }
        return local;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Yaml yaml, Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Object data = yaml.load(in);
            return data instanceof Map ? (Map<String, Object>) data : null;
        } catch (Exception e) {
            throw new IllegalStateException("解析标准种子文件失败: " + resource.getDescription(), e);
        }
    }

    private void parseMeta(StdCatalog catalog, Map<String, Object> root) {
        catalog.setStdVersion(str(root.get("stdVersion")));
        catalog.setName(str(root.get("name")));
        catalog.setCode(str(root.get("code")));
        catalog.setPublishedDate(str(root.get("publishedDate")));
        catalog.setEffectiveDate(str(root.get("effectiveDate")));
        catalog.setReplaces(str(root.get("replaces")));
        List<Map<String, Object>> rows = listOfMaps(root.get("bridgeTypes"));
        for (Map<String, Object> row : rows) {
            StdBridgeType type = new StdBridgeType();
            type.setCode(str(row.get("code")));
            type.setName(str(row.get("name")));
            type.setCategory(str(row.get("category")));
            type.setStructureComplete(Boolean.TRUE.equals(row.get("structureComplete")));
            catalog.getBridgeTypes().add(type);
        }
    }

    private void parseDiseaseTypes(StdCatalog catalog, Map<String, Object> root) {
        for (Map<String, Object> row : listOfMaps(root.get("diseaseTypes"))) {
            StdDiseaseType type = new StdDiseaseType();
            type.setCode(str(row.get("code")));
            type.setName(str(row.get("name")));
            type.setParts(stringList(row.get("parts")));
            type.setMinScale(intVal(row.get("minScale")));
            type.setMaxScale(intVal(row.get("maxScale")));
            type.setThreshold(intVal(row.get("threshold")));
            type.setSelectColumn(intVal(row.get("selectColumn")));
            for (Map<String, Object> scaleRow : listOfMaps(row.get("scales"))) {
                StdDiseaseScale scale = new StdDiseaseScale();
                scale.setScale(intVal(scaleRow.get("scale")));
                scale.setQualitative(str(scaleRow.get("qualitative")));
                scale.setQuantitative(str(scaleRow.get("quantitative")));
                scale.setMetricKey(str(scaleRow.get("metricKey")));
                scale.setValueLower(decimal(scaleRow.get("valueLower")));
                scale.setValueUpper(decimal(scaleRow.get("valueUpper")));
                scale.setUnit(str(scaleRow.get("unit")));
                type.getScales().add(scale);
            }
            catalog.getDiseaseTypes().add(type);
        }
    }

    private void parseSharedLayers(Map<String, StdLayerDef> sharedByKey, Map<String, Object> root) {
        for (Map<String, Object> layerRow : listOfMaps(root.get("layers"))) {
            StdLayerDef layer = new StdLayerDef();
            layer.setKey(str(layerRow.get("key")));
            layer.setName(str(layerRow.get("name")));
            for (Map<String, Object> partRow : listOfMaps(layerRow.get("parts"))) {
                layer.getParts().add(parsePartDef(partRow));
            }
            sharedByKey.put(layer.getKey(), layer);
        }
    }

    private StdPartDef parsePartDef(Map<String, Object> partRow) {
        StdPartDef part = new StdPartDef();
        part.setKey(str(partRow.get("key")));
        part.setName(str(partRow.get("name")));
        part.setOmega(intVal(partRow.get("omega")));
        return part;
    }

    private void parseMainParts(StdCatalog catalog, Map<String, Object> root) {
        Object raw = root.get("mainParts");
        if (!(raw instanceof Map)) {
            throw new IllegalStateException("mainParts 必须为桥型代码到部件key列表的映射");
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            catalog.getMainParts().put(str(entry.getKey()), stringList(entry.getValue()));
        }
    }

    private void parseBridgeTemplate(StdCatalog catalog, Map<String, Object> root) {
        StdBridgeTemplate template = new StdBridgeTemplate();
        template.setCode(str(root.get("code")));
        template.setSuperstructureName(str(root.get("superstructureName")));
        Object gammaRaw = root.get("gamma");
        if (gammaRaw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) gammaRaw).entrySet()) {
                template.getGamma().put(str(entry.getKey()), decimal(entry.getValue()));
            }
        }
        for (Map<String, Object> partRow : listOfMaps(root.get("superstructureParts"))) {
            template.getSuperstructureParts().add(parsePartDef(partRow));
        }
        template.setRules(stringList(root.get("rules")));
        catalog.getBridgeTemplates().put(template.getCode(), template);
    }

    /**
     * 把共享层定义 + 桥型模板展开为可写节点。
     * B01–B07 完整四结构层；其余桥型仅根桩（停用）。
     */
    private void materializeNodes(StdCatalog catalog, Map<String, StdLayerDef> sharedByKey) {
        int rootOrder = 0;
        for (StdBridgeType bridge : catalog.getBridgeTypes()) {
            String b = bridge.getCode();
            StdBridgeTemplate template = catalog.getBridgeTemplates().get(b);
            boolean complete = bridge.isStructureComplete() && template != null;

            Map<String, Object> rootProps = new LinkedHashMap<>();
            rootProps.put("stdVersion", catalog.getStdVersion());
            rootProps.put("bridgeType", b);
            rootProps.put("structureComplete", complete);
            if (complete && template.getRules() != null && !template.getRules().isEmpty()) {
                rootProps.put("rules", template.getRules());
            }

            StdTemplateNode root = new StdTemplateNode();
            root.setNodeCode(b + ".root");
            root.setName(bridge.getName());
            root.setOrderNum(rootOrder++);
            root.setRole("ROOT");
            root.setBridgeType(b);
            root.setStatus(complete ? "0" : "1");
            root.setProps(JSONUtil.toJsonStr(rootProps));
            catalog.getNodes().add(root);

            if (!complete) {
                continue;
            }

            int layerIndex = 0;
            for (String layerKey : LAYER_ORDER) {
                String layerName;
                List<StdPartDef> parts;
                if ("superstructure".equals(layerKey)) {
                    layerName = template.getSuperstructureName() != null ? template.getSuperstructureName() : "上部结构";
                    parts = template.getSuperstructureParts();
                } else {
                    StdLayerDef shared = sharedByKey.get(layerKey);
                    if (shared == null) {
                        throw new IllegalStateException(
                                "桥型 " + b + " 缺少共享结构层定义: " + layerKey);
                    }
                    layerName = shared.getName();
                    parts = shared.getParts();
                }
                BigDecimal gamma = template.getGamma().get(layerKey);

                String layerNodeCode = b + ".layer." + layerKey;
                StdTemplateNode layerNode = new StdTemplateNode();
                layerNode.setNodeCode(layerNodeCode);
                layerNode.setName(layerName);
                layerNode.setParentCode(root.getNodeCode());
                layerNode.setOrderNum(layerIndex + 1);
                layerNode.setRole("LAYER");
                layerNode.setBridgeType(b);
                layerNode.setGamma(gamma);
                layerNode.setStatus("0");
                layerNode.setProps(layerProps(catalog.getStdVersion(), b, layerKey, null));
                catalog.getNodes().add(layerNode);

                int partOrder = 0;
                for (StdPartDef partDef : parts) {
                    StdTemplateNode partNode = new StdTemplateNode();
                    partNode.setNodeCode(b + ".part." + partDef.getKey());
                    partNode.setName(partDef.getName());
                    partNode.setParentCode(layerNodeCode);
                    partNode.setOrderNum(partOrder + 1);
                    partNode.setRole("PART");
                    partNode.setBridgeType(b);
                    partNode.setOmega(partDef.getOmega());
                    partNode.setStatus("0");
                    partNode.setProps(layerProps(catalog.getStdVersion(), b, layerKey, partDef.getKey()));
                    partNode.setDiseaseTypeCodes(resolveDiseaseCodes(catalog, partDef.getKey()));
                    catalog.getNodes().add(partNode);
                    partOrder++;
                }
                layerIndex++;
            }
        }
    }

    private List<String> resolveDiseaseCodes(StdCatalog catalog, String partKey) {
        List<String> codes = new ArrayList<>();
        for (StdDiseaseType type : catalog.getDiseaseTypes()) {
            if (type.getParts() != null && type.getParts().contains(partKey)) {
                codes.add(type.getCode());
            }
        }
        return codes;
    }

    private String layerProps(String stdVersion, String bridge, String layer, String part) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stdVersion", stdVersion);
        props.put("bridgeType", bridge);
        props.put("layer", layer);
        if (part != null) {
            props.put("part", part);
        }
        return JSONUtil.toJsonStr(props);
    }

    /** 装配阶段的基本形状校验（细项引用完整性由独立的完整性测试覆盖）。 */
    private void validateShape(StdCatalog catalog) {
        if (catalog.getStdVersion() == null || catalog.getStdVersion().isEmpty()) {
            throw new IllegalStateException("标准种子缺少 stdVersion");
        }
        if (catalog.getBridgeTypes().isEmpty()) {
            throw new IllegalStateException("标准种子未定义任何桥型");
        }
        for (StdBridgeType bridge : catalog.getBridgeTypes()) {
            if (bridge.isStructureComplete() && !catalog.getBridgeTemplates().containsKey(bridge.getCode())) {
                throw new IllegalStateException("桥型 " + bridge.getCode() + " 标记完成但缺少模板定义");
            }
        }
    }

    // ---------- 标量/集合安全转换 ----------

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer intVal(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : null;
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Number) {
            return BigDecimal.valueOf(((Number) o).doubleValue());
        }
        return new BigDecimal(o.toString().trim());
    }

    private static List<String> stringList(Object o) {
        List<String> result = new ArrayList<>();
        if (o == null) {
            return result;
        }
        if (o instanceof List) {
            for (Object item : (List<?>) o) {
                result.add(str(item));
            }
        } else {
            result.add(str(o));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object o) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(o instanceof List)) {
            return result;
        }
        for (Object item : (List<Object>) o) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }
}
