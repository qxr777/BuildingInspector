package edu.whut.cs.bi.biz.service.std;

import edu.whut.cs.bi.biz.service.std.model.StdBridgeType;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseScale;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseType;
import edu.whut.cs.bi.biz.service.std.model.StdPartDef;
import edu.whut.cs.bi.biz.service.std.model.StdTemplateNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 经 {@link StandardCatalogLoader} 读取 classpath 权威 YAML 的完整性校验。
 * 覆盖：唯一编码、绑定可解析、父子关系合法、ω 层内=100、γ 四层=1、标度与结构化区间形状。
 */
class StandardCatalogIntegrityTest {

    /** 正式稿中允许出现的全部部件逻辑键。 */
    private static final Set<String> KNOWN_PART_KEYS = new HashSet<>(Arrays.asList(
            // 桥面系
            "paving", "expansionJoint", "railing", "drainage",
            // 梁桥上部
            "slab", "hinge", "mainGirder", "diaphragm", "boxGirder",
            "steelMainGirder", "deckSlab", "lateralBracing", "bearing",
            // 下部 / 附属
            "pierAbutment", "foundation", "riverbed", "revetment", "otherAncillary"));

    private static StdCatalog catalog;
    private static List<StdTemplateNode> nodes;

    @BeforeAll
    static void load() {
        catalog = new StandardCatalogLoader().load();
        nodes = catalog.getNodes();
    }

    @Test
    @DisplayName("标准版本与桥型清单")
    void testMeta() {
        assertEquals("5230-2026", catalog.getStdVersion());
        assertNotNull(catalog.getCode());
        assertEquals(21, catalog.getBridgeTypes().size(), "正式稿表 B.1.1 共 21 个桥型");
    }

    @Test
    @DisplayName("桥型代码版本内唯一")
    void testBridgeCodesUnique() {
        List<String> codes = catalog.getBridgeTypes().stream()
                .map(StdBridgeType::getCode).collect(Collectors.toList());
        assertEquals(codes.size(), new HashSet<>(codes).size(), "桥型代码重复");
    }

    @Test
    @DisplayName("21 个根节点，B01–B07 可用、B08–B21 停用")
    void testRoots() {
        List<StdTemplateNode> roots = nodes.stream()
                .filter(n -> "ROOT".equals(n.getRole())).collect(Collectors.toList());
        assertEquals(21, roots.size());
        for (StdTemplateNode root : roots) {
            assertNull(root.getParentCode());
            boolean complete = catalog.getBridgeTypes().stream()
                    .filter(t -> t.getCode().equals(root.getBridgeType()))
                    .findFirst().map(StdBridgeType::isStructureComplete).orElse(false);
            assertEquals(complete ? "0" : "1", root.getStatus(),
                    "根 " + root.getNodeCode() + " 状态与 structureComplete 不一致");
        }
    }

    @Test
    @DisplayName("病害代码版本内唯一，部件键合法，标度自洽")
    void testDiseaseTypes() {
        Set<String> codes = new HashSet<>();
        for (StdDiseaseType type : catalog.getDiseaseTypes()) {
            assertNotNull(type.getCode());
            assertTrue(codes.add(type.getCode()), "病害代码重复: " + type.getCode());
            assertNotNull(type.getName());
            assertNotNull(type.getMinScale());
            assertNotNull(type.getMaxScale());
            assertTrue(type.getMinScale() >= 0 && type.getMinScale() <= 3,
                    type.getCode() + " minScale 越界");
            assertTrue(type.getMaxScale() >= type.getMinScale() && type.getMaxScale() <= 3,
                    type.getCode() + " maxScale 越界");
            assertTrue(!type.getParts().isEmpty(), type.getCode() + " 未绑定部件");
            for (String part : type.getParts()) {
                assertTrue(KNOWN_PART_KEYS.contains(part),
                        type.getCode() + " 引用未知部件键: " + part);
            }

            // 标度：编号唯一、落在 [min,max]、必有原文
            Set<Integer> scaleNums = new HashSet<>();
            for (StdDiseaseScale scale : type.getScales()) {
                assertNotNull(scale.getScale());
                assertTrue(scaleNums.add(scale.getScale()),
                        type.getCode() + " 标度重复 " + scale.getScale());
                assertTrue(scale.getScale() >= type.getMinScale()
                                && scale.getScale() <= type.getMaxScale(),
                        type.getCode() + " 标度 " + scale.getScale() + " 越界");
                assertNotNull(scale.getQuantitative());
            }
        }
    }

    @Test
    @DisplayName("结构化区间：有 metricKey 时上下界顺序合法，单位成对")
    void testStructuredIntervals() {
        for (StdDiseaseType type : catalog.getDiseaseTypes()) {
            for (StdDiseaseScale scale : type.getScales()) {
                BigDecimal lo = scale.getValueLower();
                BigDecimal hi = scale.getValueUpper();
                boolean hasMetric = scale.getMetricKey() != null;
                if (lo != null || hi != null) {
                    assertTrue(hasMetric, type.getCode() + " 有数值却无 metricKey");
                    assertNotNull(scale.getUnit(), type.getCode() + " 有数值却无 unit");
                }
                if (lo != null && hi != null) {
                    assertTrue(lo.compareTo(hi) <= 0,
                            type.getCode() + " 区间下界大于上界");
                }
            }
        }
    }

    @Test
    @DisplayName("节点编码唯一、父节点可解析、角色合法")
    void testNodeShape() {
        Map<String, StdTemplateNode> byCode = new HashMap<>();
        for (StdTemplateNode node : nodes) {
            assertTrue(byCode.put(node.getNodeCode(), node) == null,
                    "节点编码重复: " + node.getNodeCode());
            assertTrue(Arrays.asList("ROOT", "LAYER", "PART").contains(node.getRole()));
            assertNotNull(node.getBridgeType());
            if (!"ROOT".equals(node.getRole())) {
                assertNotNull(node.getParentCode(), node.getNodeCode() + " 缺父节点");
            }
        }
        for (StdTemplateNode node : nodes) {
            if (node.getParentCode() != null) {
                assertTrue(byCode.containsKey(node.getParentCode()),
                        node.getNodeCode() + " 的父节点不存在: " + node.getParentCode());
            }
        }
    }

    @Test
    @DisplayName("PART 节点绑定的病害代码全部存在，且每个病害至少被一个 PART 节点使用")
    void testBindingsResolve() {
        Set<String> diseaseCodes = catalog.getDiseaseTypes().stream()
                .map(StdDiseaseType::getCode).collect(Collectors.toSet());
        Set<String> used = new HashSet<>();
        for (StdTemplateNode node : nodes) {
            if ("PART".equals(node.getRole())) {
                for (String code : node.getDiseaseTypeCodes()) {
                    assertTrue(diseaseCodes.contains(code),
                            node.getNodeCode() + " 绑定未知病害: " + code);
                    used.add(code);
                }
            }
        }
        for (String code : diseaseCodes) {
            assertTrue(used.contains(code), "病害未被任何部件节点使用: " + code);
        }
    }

    @Test
    @DisplayName("PART 可绑多叶子：leaves 键合法，B07 两个 PART 双绑（表 6.2.15/6.2.16）")
    void testPartLeaves() {
        List<StdPartDef> allParts = new ArrayList<>();
        catalog.getSharedLayers().forEach(layer -> allParts.addAll(layer.getParts()));
        catalog.getBridgeTemplates().values()
                .forEach(tpl -> allParts.addAll(tpl.getSuperstructureParts()));
        for (StdPartDef part : allParts) {
            for (String leaf : part.getLeaves()) {
                assertTrue(KNOWN_PART_KEYS.contains(leaf),
                        "部件 " + part.getKey() + " 的 leaves 引用未知叶子: " + leaf);
            }
            assertEquals(!part.getLeaves().isEmpty() ? part.getLeaves()
                            : Collections.singletonList(part.getKey()),
                    part.effectiveLeafCodes(), part.getKey() + " 生效叶子解析错误");
        }
        StdPartDef steelGirder = catalog.getBridgeTemplates().get("B07")
                .getSuperstructureParts().stream()
                .filter(p -> "steelMainGirder".equals(p.getKey())).findFirst()
                .orElseThrow(() -> new AssertionError("B07 缺 steelMainGirder"));
        assertEquals(Arrays.asList("steelMainGirder", "mainGirder"), steelGirder.getLeaves());
        StdPartDef deckSlab = catalog.getBridgeTemplates().get("B07")
                .getSuperstructureParts().stream()
                .filter(p -> "deckSlab".equals(p.getKey())).findFirst()
                .orElseThrow(() -> new AssertionError("B07 缺 deckSlab"));
        assertEquals(Arrays.asList("deckSlab", "slab"), deckSlab.getLeaves());
    }

    @Test
    @DisplayName("多叶子 PART 的节点病害集合=各叶子病害并集（B07 可达牛腿/连接部/板梁类型）")
    void testMultiLeafDiseaseReach() {
        Map<String, StdTemplateNode> byCode = nodes.stream()
                .collect(Collectors.toMap(StdTemplateNode::getNodeCode, n -> n, (a, b) -> a));
        List<String> steelGirderDiseases = byCode.get("B07.part.steelMainGirder").getDiseaseTypeCodes();
        assertTrue(steelGirderDiseases.contains("STL-COR-01"), "牛腿病害应经 mainGirder 叶子可达");
        assertTrue(steelGirderDiseases.contains("RIB-RC-01"), "主梁叶子既有病害应可达");
        assertTrue(steelGirderDiseases.contains("STL-CONN-01"), "组合梁连接部病害应可达");
        List<String> deckSlabDiseases = byCode.get("B07.part.deckSlab").getDiseaseTypeCodes();
        assertTrue(deckSlabDiseases.contains("STL-COR-01"), "牛腿病害应经 slab 叶子可达");
        assertTrue(deckSlabDiseases.contains("SLB-I-01"), "板叶子既有病害应可达");
        assertTrue(deckSlabDiseases.contains("STL-CONN-01"), "组合梁连接部病害应可达");
    }

    @Nested
    @DisplayName("B01–B07 权重与系数")
    class Weights {

        private List<StdBridgeType> completeTypes() {
            return catalog.getBridgeTypes().stream()
                    .filter(StdBridgeType::isStructureComplete)
                    .collect(Collectors.toList());
        }

        @Test
        @DisplayName("每桥四个结构层 γ 之和 = 1.00")
        void testGammaSums() {
            for (StdBridgeType bridge : completeTypes()) {
                List<StdTemplateNode> layers = nodes.stream()
                        .filter(n -> "LAYER".equals(n.getRole())
                                && bridge.getCode().equals(n.getBridgeType()))
                        .collect(Collectors.toList());
                assertEquals(4, layers.size(), bridge.getCode() + " 应有 4 个结构层");
                BigDecimal sum = BigDecimal.ZERO;
                for (StdTemplateNode layer : layers) {
                    assertNotNull(layer.getGamma(),
                            bridge.getCode() + " 层 " + layer.getNodeCode() + " 缺 γ");
                    sum = sum.add(layer.getGamma());
                }
                assertEquals(0, new BigDecimal("1.00").compareTo(sum),
                        bridge.getCode() + " γ 之和 = " + sum + " ≠ 1.00");
            }
        }

        @Test
        @DisplayName("每个（桥型 × 结构层）内部件 ω 之和 = 100")
        void testOmegaSums() {
            for (StdBridgeType bridge : completeTypes()) {
                // 按父层分组 PART
                Map<String, List<StdTemplateNode>> byLayer = new HashMap<>();
                for (StdTemplateNode node : nodes) {
                    if ("PART".equals(node.getRole())
                            && bridge.getCode().equals(node.getBridgeType())) {
                        byLayer.computeIfAbsent(node.getParentCode(), k -> new ArrayList<>()).add(node);
                    }
                }
                assertEquals(4, byLayer.size(), bridge.getCode() + " 部件应分布在 4 层");
                for (Map.Entry<String, List<StdTemplateNode>> e : byLayer.entrySet()) {
                    int sum = 0;
                    for (StdTemplateNode part : e.getValue()) {
                        assertNotNull(part.getOmega(),
                                bridge.getCode() + " 部件 " + part.getNodeCode() + " 缺 ω");
                        sum += part.getOmega();
                    }
                    assertEquals(100, sum,
                            bridge.getCode() + " 层 " + e.getKey() + " ω 之和 = " + sum);
                }
            }
        }
    }
}
