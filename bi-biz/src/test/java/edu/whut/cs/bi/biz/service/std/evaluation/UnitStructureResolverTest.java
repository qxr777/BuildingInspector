package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import edu.whut.cs.bi.biz.service.std.model.StdBridgeTemplate;
import edu.whut.cs.bi.biz.service.std.model.StdBridgeType;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.std.model.StdLayerDef;
import edu.whut.cs.bi.biz.service.std.model.StdPartDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link UnitStructureResolver} 新链结构解析单测：根即 UNIT、ROOT 多单元、
 * propsJson/模板/目录三级兜底、幻影件按 partKey 构造、structureComplete 拦截。
 */
@ExtendWith(MockitoExtension.class)
class UnitStructureResolverTest {

    @Mock
    private BiObjectMapper biObjectMapper;
    @Mock
    private BiTemplateObjectMapper biTemplateObjectMapper;
    @Mock
    private StandardCatalogLoader standardCatalogLoader;

    private UnitStructureResolver resolver;
    private EvalWarnings warnings;

    @BeforeEach
    void setUp() {
        resolver = new UnitStructureResolver(biObjectMapper, biTemplateObjectMapper, standardCatalogLoader);
        warnings = new EvalWarnings();
        lenient().when(standardCatalogLoader.getCatalog()).thenReturn(buildCatalog());
    }

    @Test
    void resolveAll_rootIsUnit_singleUnitWithPropsKeys() {
        BiObject unit = node(1L, 0L, "UNIT", "演示单元", "B01", null, null, null);
        BiObject span = node(2L, 1L, "SPAN", "第1跨", null, 1, null, null);
        BiObject deckLayer = node(3L, 2L, "LAYER", "桥面系", null, null, 0, new BigDecimal("0.15"));
        BiObject slabLayer = node(4L, 2L, "LAYER", "上部结构", null, null, 1, null);
        BiObject paving = node(5L, 3L, "PART", "桥面铺装", null, null, 0, null);
        paving.setPropsJson("{\"part\":\"paving\"}");
        paving.setOmega(40);
        BiObject slab = node(6L, 4L, "PART", "实心板或空心板", null, null, 0, null);
        slab.setOmega(75);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L))
                .thenReturn(Arrays.asList(unit, span, deckLayer, slabLayer, paving, slab));

        List<ResolvedUnit> units = resolver.resolveAll(1L, warnings);

        assertEquals(1, units.size());
        ResolvedUnit resolved = units.get(0);
        assertEquals(1L, resolved.getUnitId());
        assertEquals("B01", resolved.getBridgeType());
        assertEquals(1, resolved.getSpans().size());
        assertEquals(2L, resolved.getSpans().get(0).getSpanId());
        assertEquals(2, resolved.getSpans().get(0).getParts().size());

        ResolvedPart pavingPart = resolved.getSpans().get(0).getParts().get(0);
        assertEquals("paving", pavingPart.getPartKey());
        assertEquals("deck", pavingPart.getLayerKey());
        assertEquals(40, pavingPart.getOmega());
        assertEquals(0, new BigDecimal("0.15").compareTo(pavingPart.getGamma()));

        ResolvedPart slabPart = resolved.getSpans().get(0).getParts().get(1);
        assertEquals("slab", slabPart.getPartKey());
        assertEquals("superstructure", slabPart.getLayerKey());
        assertEquals(75, slabPart.getOmega());
        // γ 节点缺失 → 目录兜底
        assertEquals(0, new BigDecimal("0.40").compareTo(slabPart.getGamma()));
        assertTrue(warnings.getWarnings().stream().anyMatch(w -> w.contains("γ 由权威目录兜底")));
    }

    @Test
    void resolveAll_rootWithTwoUnits_parsesBoth() {
        BiObject root = node(1L, 0L, "ROOT", "桥梁根", null, null, 0, null);
        BiObject unitA = node(2L, 1L, "UNIT", "主桥", "B01", null, 0, null);
        BiObject unitB = node(3L, 1L, "UNIT", "引桥", "B01", null, 1, null);
        BiObject spanA = node(4L, 2L, "SPAN", "A跨", null, 1, null, null);
        BiObject spanB = node(5L, 3L, "SPAN", "B跨", null, 1, null, null);
        BiObject layerA = node(6L, 4L, "LAYER", "上部", null, null, 0, new BigDecimal("0.40"));
        BiObject layerB = node(7L, 5L, "LAYER", "上部", null, null, 0, new BigDecimal("0.40"));
        BiObject slabA = node(8L, 6L, "PART", "实心板或空心板", null, null, 0, null);
        slabA.setOmega(75);
        BiObject slabB = node(9L, 7L, "PART", "实心板或空心板", null, null, 0, null);
        slabB.setOmega(75);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L)).thenReturn(Arrays.asList(
                root, unitA, unitB, spanA, spanB, layerA, layerB, slabA, slabB));

        List<ResolvedUnit> units = resolver.resolveAll(1L, warnings);

        assertEquals(2, units.size());
        assertEquals(2L, units.get(0).getUnitId());
        assertEquals(3L, units.get(1).getUnitId());
        assertEquals(1, units.get(0).getSpans().size());
        assertEquals("slab", units.get(1).getSpans().get(0).getParts().get(0).getPartKey());
    }

    @Test
    void resolveAll_partWithoutAnyKey_fallsBackToCatalogByName() {
        BiObject unit = node(1L, 0L, "UNIT", "单元", "B01", null, null, null);
        BiObject span = node(2L, 1L, "SPAN", "跨", null, 1, null, null);
        BiObject layer = node(3L, 2L, "LAYER", "桥面系", null, null, 0, new BigDecimal("0.15"));
        BiObject part = node(4L, 3L, "PART", "排水系统", null, null, 0, null);
        part.setOmega(15);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L))
                .thenReturn(Arrays.asList(unit, span, layer, part));

        ResolvedUnit resolved = resolver.resolveAll(1L, warnings).get(0);

        assertEquals("drainage", resolved.getSpans().get(0).getParts().get(0).getPartKey());
        assertTrue(warnings.getWarnings().stream().anyMatch(w -> w.contains("part key 由权威目录名称匹配兜底")));
    }

    @Test
    void resolveAll_unresolvablePartKey_throws() {
        BiObject unit = node(1L, 0L, "UNIT", "单元", "B01", null, null, null);
        BiObject span = node(2L, 1L, "SPAN", "跨", null, 1, null, null);
        BiObject layer = node(3L, 2L, "LAYER", "某层", null, null, 0, new BigDecimal("0.15"));
        BiObject part = node(4L, 3L, "PART", "火星部件", null, null, 0, null);
        part.setOmega(10);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L))
                .thenReturn(Arrays.asList(unit, span, layer, part));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolveAll(1L, warnings));
        assertTrue(ex.getMessage().contains("无法解析 part key"));
    }

    @Test
    void resolveAll_structureIncomplete_throws() {
        BiObject unit = node(1L, 0L, "UNIT", "单元", "B08", null, null, null);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L)).thenReturn(new ArrayList<>(List.of(unit)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolveAll(1L, warnings));
        assertTrue(ex.getMessage().contains("结构不完整"));
    }

    @Test
    void resolveAll_missingBridgeType_throws() {
        BiObject unit = node(1L, 0L, "UNIT", "单元", null, null, null, null);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L)).thenReturn(new ArrayList<>(List.of(unit)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolveAll(1L, warnings));
        assertTrue(ex.getMessage().contains("bridge_type"));
    }

    @Test
    void resolveAll_rootWithoutUnits_throws() {
        BiObject root = node(1L, 0L, "ROOT", "根", null, null, null, null);
        when(biObjectMapper.selectChildrenByIdRemoveLeafNew(1L)).thenReturn(new ArrayList<>(List.of(root)));

        assertThrows(ServiceException.class, () -> resolver.resolveAll(1L, warnings));
    }

    @Test
    void phantomPart_knownKey_buildsAbsentPartFromCatalog() {
        ResolvedPart phantom = resolver.phantomPart("bearing", "superstructure", "B01");

        assertNull(phantom.getPartId());
        assertEquals("bearing", phantom.getPartKey());
        assertEquals("superstructure", phantom.getLayerKey());
        assertEquals(25, phantom.getOmega());
        assertTrue(phantom.isAbsent());
        assertEquals(3, phantom.getCi());
        assertEquals(0, new BigDecimal("0.40").compareTo(phantom.getGamma()));
    }

    @Test
    void phantomPart_unknownKey_throws() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.phantomPart("marsPart", "deck", "B01"));
        assertTrue(ex.getMessage().contains("模板中不存在"));
    }

    // ---------- 夹具 ----------

    private BiObject node(Long id, Long parentId, String type, String name,
                          String bridgeType, Integer spanNo, Integer orderNum, BigDecimal gamma) {
        BiObject node = new BiObject();
        node.setId(id);
        node.setParentId(parentId);
        node.setNodeType(type);
        node.setName(name);
        node.setBridgeType(bridgeType);
        node.setSpanNo(spanNo);
        node.setOrderNum(orderNum);
        node.setGamma(gamma);
        return node;
    }

    private StdPartDef def(String key, String name, int omega) {
        StdPartDef d = new StdPartDef();
        d.setKey(key);
        d.setName(name);
        d.setOmega(omega);
        return d;
    }

    private StdLayerDef layerDef(String key, String name, List<StdPartDef> parts) {
        StdLayerDef layer = new StdLayerDef();
        layer.setKey(key);
        layer.setName(name);
        layer.getParts().addAll(parts);
        return layer;
    }

    private StdCatalog buildCatalog() {
        StdCatalog catalog = new StdCatalog();

        StdBridgeType b01 = new StdBridgeType();
        b01.setCode("B01");
        b01.setStructureComplete(true);
        catalog.getBridgeTypes().add(b01);
        StdBridgeType b08 = new StdBridgeType();
        b08.setCode("B08");
        b08.setStructureComplete(false);
        catalog.getBridgeTypes().add(b08);

        StdBridgeTemplate template = new StdBridgeTemplate();
        template.setCode("B01");
        template.getGamma().put("deck", new BigDecimal("0.15"));
        template.getGamma().put("superstructure", new BigDecimal("0.40"));
        template.getGamma().put("substructure", new BigDecimal("0.40"));
        template.getGamma().put("ancillary", new BigDecimal("0.05"));
        template.getSuperstructureParts().add(def("slab", "实心板或空心板", 75));
        template.getSuperstructureParts().add(def("bearing", "支座", 25));
        catalog.getBridgeTemplates().put("B01", template);

        // B08 模板桩已存在但 meta 标记 structureComplete=false（模拟 B08–B21 拦截路径）
        StdBridgeTemplate b08Template = new StdBridgeTemplate();
        b08Template.setCode("B08");
        catalog.getBridgeTemplates().put("B08", b08Template);

        catalog.getSharedLayers().add(layerDef("deck", "桥面系", List.of(
                def("paving", "桥面铺装", 40), def("expansionJoint", "伸缩装置", 25),
                def("railing", "护栏及人行道", 20), def("drainage", "排水系统", 15))));
        catalog.getSharedLayers().add(layerDef("substructure", "下部结构", List.of(
                def("pierAbutment", "桥墩或桥台", 60), def("foundation", "墩台基础", 40))));
        catalog.getSharedLayers().add(layerDef("ancillary", "附属设施", new ArrayList<>()));
        return catalog;
    }
}
