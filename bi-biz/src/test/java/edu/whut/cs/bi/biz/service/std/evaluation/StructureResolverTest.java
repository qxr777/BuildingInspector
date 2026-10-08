package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructureResolverTest {

    @Mock
    private BiObjectMapper biObjectMapper;
    @Mock
    private BiTemplateObjectMapper biTemplateObjectMapper;
    @Mock
    private StandardCatalogLoader standardCatalogLoader;

    private StructureResolver resolver;
    private EvalWarnings warnings;

    @BeforeEach
    void setUp() {
        resolver = new StructureResolver(biObjectMapper, biTemplateObjectMapper, standardCatalogLoader);
        warnings = new EvalWarnings();
        // 部分异常用例在读取目录前即抛错，故采用 lenient
        org.mockito.Mockito.lenient().when(standardCatalogLoader.getCatalog()).thenReturn(buildCatalog());
    }

    // ---------- 目录/节点构造 ----------

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

        StdBridgeType bridgeType = new StdBridgeType();
        bridgeType.setCode("B03");
        bridgeType.setStructureComplete(true);
        catalog.getBridgeTypes().add(bridgeType);

        StdBridgeTemplate template = new StdBridgeTemplate();
        template.setCode("B03");
        template.getGamma().put("deck", new BigDecimal("0.15"));
        template.getGamma().put("superstructure", new BigDecimal("0.42"));
        template.getGamma().put("substructure", new BigDecimal("0.38"));
        template.getGamma().put("ancillary", new BigDecimal("0.05"));
        template.getSuperstructureParts().add(def("mainGirder", "主梁", 55));
        template.getSuperstructureParts().add(def("diaphragm", "横隔板", 20));
        template.getSuperstructureParts().add(def("bearing", "支座", 25));
        catalog.getBridgeTemplates().put("B03", template);

        catalog.getSharedLayers().add(layerDef("deck", "桥面系", List.of(
                def("paving", "铺装", 40), def("expansionJoint", "伸缩装置", 25),
                def("railing", "护栏", 20), def("drainage", "排水系统", 15))));
        catalog.getSharedLayers().add(layerDef("substructure", "下部结构", List.of(
                def("pierAbutment", "桥墩、台", 60), def("foundation", "基础", 40))));
        catalog.getSharedLayers().add(layerDef("ancillary", "附属设施", List.of(
                def("riverbed", "河床调治", 35), def("revetment", "护坡", 35),
                def("otherAncillary", "其他附属", 30))));
        return catalog;
    }

    private BiObject node(long id, Long parentId, String type) {
        BiObject n = new BiObject();
        n.setId(id);
        n.setParentId(parentId);
        n.setNodeType(type);
        return n;
    }

    private BiTemplateObject templateObject(long id, String nodeCode, Integer omega, BigDecimal gamma) {
        BiTemplateObject t = new BiTemplateObject();
        t.setId(id);
        t.setNodeCode(nodeCode);
        t.setOmega(omega);
        t.setGamma(gamma);
        return t;
    }

    // ---------- 用例 ----------

    @Test
    void resolveViaTemplateObjectPath() {
        BiObject unit = node(1, 0L, "UNIT");
        unit.setBridgeType("B03");
        BiObject span = node(2, 1L, "SPAN");
        span.setSpanNo(1);
        BiObject layer = node(3, 2L, "LAYER");
        layer.setTemplateObjectId(30L);
        BiObject part = node(4, 3L, "PART");
        part.setTemplateObjectId(40L);

        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(unit, span, layer, part));
        when(biTemplateObjectMapper.selectBiTemplateObjectById(30L))
                .thenReturn(templateObject(30L, "B03.layer.superstructure", null, new BigDecimal("0.42")));
        when(biTemplateObjectMapper.selectBiTemplateObjectById(40L))
                .thenReturn(templateObject(40L, "B03.part.mainGirder", 55, null));

        ResolvedUnit result = resolver.resolve(1L, warnings);
        ResolvedPart resolvedPart = result.getSpans().get(0).getParts().get(0);
        assertEquals("mainGirder", resolvedPart.getPartKey());
        assertEquals("superstructure", resolvedPart.getLayerKey());
        assertEquals(55, resolvedPart.getOmega());
        assertEquals(new BigDecimal("0.42"), resolvedPart.getGamma());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void resolveViaCatalogNameFallback() {
        BiObject unit = node(1, 0L, "UNIT");
        unit.setBridgeType("B03");
        BiObject span = node(2, 1L, "SPAN");
        span.setSpanNo(1);
        BiObject layer = node(3, 2L, "LAYER");
        BiObject part = node(4, 3L, "PART");
        part.setName("铺装");

        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(unit, span, layer, part));

        ResolvedUnit result = resolver.resolve(1L, warnings);
        ResolvedPart resolvedPart = result.getSpans().get(0).getParts().get(0);
        assertEquals("paving", resolvedPart.getPartKey());
        assertEquals("deck", resolvedPart.getLayerKey());
        assertEquals(40, resolvedPart.getOmega());
        assertEquals(new BigDecimal("0.15"), resolvedPart.getGamma());
        assertTrue(!warnings.isEmpty());
    }

    @Test
    void throwsWhenPartUnresolved() {
        BiObject unit = node(1, 0L, "UNIT");
        unit.setBridgeType("B03");
        BiObject span = node(2, 1L, "SPAN");
        span.setSpanNo(1);
        BiObject layer = node(3, 2L, "LAYER");
        BiObject part = node(4, 3L, "PART");
        part.setName("规范外的部件");

        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(unit, span, layer, part));
        assertThrows(ServiceException.class, () -> resolver.resolve(1L, warnings));
    }

    @Test
    void throwsWhenUnitMissingBridgeType() {
        BiObject unit = node(1, 0L, "UNIT");
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(unit));
        assertThrows(ServiceException.class, () -> resolver.resolve(1L, warnings));
    }

    @Test
    void throwsWhenNoSpanNode() {
        BiObject unit = node(1, 0L, "UNIT");
        unit.setBridgeType("B03");
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(unit));
        assertThrows(ServiceException.class, () -> resolver.resolve(1L, warnings));
    }

    @Test
    void phantomPartMarkedAbsentWithCi3() {
        BiObject partNode = node(4, 3L, "PART");
        partNode.setName("主梁");

        ResolvedPart phantom = resolver.phantomPart(partNode, "B03");
        assertTrue(phantom.isAbsent());
        assertEquals(3, phantom.getCi());
        assertEquals("mainGirder", phantom.getPartKey());
        assertEquals("superstructure", phantom.getLayerKey());
        assertEquals(55, phantom.getOmega());
        assertEquals(new BigDecimal("0.42"), phantom.getGamma());
    }

    @Test
    void phantomPartRejectsNonTemplatePart() {
        BiObject partNode = node(4, 3L, "PART");
        partNode.setName("规范外的部件");
        assertThrows(ServiceException.class, () -> resolver.phantomPart(partNode, "B03"));
    }

    @Test
    void isTemplatePartCheck() {
        BiObject partNode = node(4, 3L, "PART");
        partNode.setName("支座");
        assertTrue(resolver.isTemplatePart(partNode, "B03"));

        BiObject unknown = node(5, 3L, "PART");
        unknown.setName("规范外的部件");
        assertTrue(!resolver.isTemplatePart(unknown, "B03"));
    }
}
