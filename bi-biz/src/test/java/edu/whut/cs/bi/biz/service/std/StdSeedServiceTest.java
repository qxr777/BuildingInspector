package edu.whut.cs.bi.biz.service.std;

import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.std.impl.StdSeedServiceImpl;
import edu.whut.cs.bi.biz.service.std.model.StdBridgeType;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseScale;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseType;
import edu.whut.cs.bi.biz.service.std.model.StdTemplateNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link StdSeedServiceImpl} 纯 Mockito 测试：不连库，目录用最小可控样本。
 * 覆盖幂等 upsert、dryRun 零写入、对账删除仅限同 std_version 命名空间、
 * code→id / 父链解析、scope 过滤。
 */
@ExtendWith(MockitoExtension.class)
class StdSeedServiceTest {

    private static final String VER = "5230-2026";
    private static final String TYPE_CODE = "PAV-A-01";

    @Mock
    private StandardCatalogLoader catalogLoader;
    @Mock
    private StdSchemaService schemaService;
    @Mock
    private DiseaseTypeMapper diseaseTypeMapper;
    @Mock
    private DiseaseScaleMapper diseaseScaleMapper;
    @Mock
    private BiTemplateObjectMapper templateObjectMapper;
    @Mock
    private TODiseaseTypeMapper todMapper;

    @InjectMocks
    private StdSeedServiceImpl seedService;

    // ---------- 目录 / domain 工厂 ----------

    private StdCatalog buildCatalog() {
        StdCatalog c = new StdCatalog();
        c.setStdVersion(VER);

        StdBridgeType b01 = new StdBridgeType();
        b01.setCode("B01");
        b01.setName("整体式板");
        b01.setStructureComplete(true);
        c.getBridgeTypes().add(b01);

        StdDiseaseType t = new StdDiseaseType();
        t.setCode(TYPE_CODE);
        t.setName("车辙");
        t.setMinScale(1);
        t.setMaxScale(2);
        t.getScales().add(stdScale(1, "深度小于10mm"));
        t.getScales().add(stdScale(2, "深度大于10mm"));
        c.getDiseaseTypes().add(t);

        StdTemplateNode root = node("B01", "梁桥", null, "ROOT", 0, null);
        StdTemplateNode deck = node("B01.deck", "桥面系", "B01", "LAYER", 0, null);
        StdTemplateNode paving = node("B01.deck.paving", "桥面铺装", "B01.deck", "PART", 0, 40);
        paving.getDiseaseTypeCodes().add(TYPE_CODE);
        c.getNodes().addAll(Arrays.asList(root, deck, paving));
        return c;
    }

    private StdDiseaseScale stdScale(int scale, String quantitative) {
        StdDiseaseScale s = new StdDiseaseScale();
        s.setScale(scale);
        s.setQuantitative(quantitative);
        return s;
    }

    private StdTemplateNode node(String code, String name, String parent, String role,
                                 int orderNum, Integer omega) {
        StdTemplateNode n = new StdTemplateNode();
        n.setNodeCode(code);
        n.setName(name);
        n.setParentCode(parent);
        n.setRole(role);
        n.setBridgeType("B01");
        n.setOrderNum(orderNum);
        n.setOmega(omega);
        n.setStatus("0");
        return n;
    }

    private DiseaseType persistedType(long id) {
        DiseaseType t = new DiseaseType();
        t.setId(id);
        t.setCode(TYPE_CODE);
        t.setName("车辙");
        t.setMinScale(1);
        t.setMaxScale(2);
        // 与真实库一致：insert 未给 threshold 时由列默认值落为 10（回归“每次重跑误判 update”缺陷）
        t.setThreshold(10);
        t.setStdVersion(VER);
        return t;
    }

    private DiseaseScale persistedScale(long id, int scale, String quantitative) {
        DiseaseScale s = new DiseaseScale();
        s.setId(id);
        s.setTypeCode(TYPE_CODE);
        s.setScale(scale);
        s.setQuantitativeDescription(quantitative);
        s.setStdVersion(VER);
        return s;
    }

    private BiTemplateObject persistedNode(long id, String nodeCode, String name,
                                           long parentId, String ancestors, Integer omega) {
        BiTemplateObject o = new BiTemplateObject();
        o.setId(id);
        o.setNodeCode(nodeCode);
        o.setName(name);
        o.setParentId(parentId);
        o.setAncestors(ancestors);
        o.setOrderNum(0);
        o.setStatus("0");
        o.setOmega(omega);
        o.setStdVersion(VER);
        o.setDelFlag("0");
        return o;
    }

    /** 空库：insert 时由 mapper 回填自增 id。 */
    private void stubFreshInserts() {
        AtomicLong typeSeq = new AtomicLong(100);
        when(diseaseTypeMapper.insertDiseaseType(any())).thenAnswer(inv -> {
            ((DiseaseType) inv.getArgument(0)).setId(typeSeq.get());
            return 1;
        });
        AtomicLong nodeSeq = new AtomicLong(1);
        when(templateObjectMapper.insertBiTemplateObject(any())).thenAnswer(inv -> {
            ((BiTemplateObject) inv.getArgument(0)).setId(nodeSeq.getAndIncrement());
            return 1;
        });
    }

    // ---------- 测试 ----------

    @Test
    void 首次执行_插入类型标度节点绑定_并解析父子链() {
        when(catalogLoader.getCatalog()).thenReturn(buildCatalog());
        when(schemaService.ensureSchema(anyBoolean())).thenReturn(Collections.emptyList());
        when(diseaseTypeMapper.selectByCodeAndStdVersion(TYPE_CODE, VER)).thenReturn(null);
        // 库里残留标度 3，目录只要 1/2，应被对账删除。
        when(diseaseScaleMapper.selectByTypeCodeAndStdVersion(TYPE_CODE, VER))
                .thenReturn(Collections.singletonList(persistedScale(99, 3, "旧标度")));
        when(templateObjectMapper.selectByStdVersion(VER)).thenReturn(Collections.emptyList());
        stubFreshInserts();

        StdSeedReport report = seedService.seed(StdSeedScope.ALL, false, null);

        assertEquals(1, report.getDiseaseTypes().getInserted());
        assertEquals(2, report.getDiseaseScales().getInserted());
        assertEquals(1, report.getDiseaseScales().getDeleted());
        assertEquals(3, report.getTemplateNodes().getInserted());
        assertEquals(1, report.getBindings().getInserted());
        assertTrue(report.getErrors().isEmpty());

        verify(diseaseScaleMapper).deleteDiseaseScaleById(99L);
        verify(todMapper).batchInsertBridgeTemplateDiseaseType(eq(3L),
                eq(Collections.singletonList(100L)));

        ArgumentCaptor<BiTemplateObject> captor = ArgumentCaptor.forClass(BiTemplateObject.class);
        verify(templateObjectMapper, times(3)).insertBiTemplateObject(captor.capture());
        List<BiTemplateObject> inserted = captor.getAllValues();
        assertEquals(0L, inserted.get(0).getParentId());
        assertEquals(1L, inserted.get(1).getParentId());
        assertEquals(2L, inserted.get(2).getParentId());
        assertEquals("0", inserted.get(0).getAncestors());
        assertEquals("0,1", inserted.get(1).getAncestors());
        assertEquals("0,1,2", inserted.get(2).getAncestors());
    }

    @Test
    void 再次执行_内容一致_全部不变_零写入() {
        when(catalogLoader.getCatalog()).thenReturn(buildCatalog());
        when(schemaService.ensureSchema(anyBoolean())).thenReturn(Collections.emptyList());
        when(diseaseTypeMapper.selectByCodeAndStdVersion(TYPE_CODE, VER)).thenReturn(persistedType(100));
        when(diseaseScaleMapper.selectByTypeCodeAndStdVersion(TYPE_CODE, VER)).thenReturn(Arrays.asList(
                persistedScale(10, 1, "深度小于10mm"),
                persistedScale(11, 2, "深度大于10mm")));
        when(templateObjectMapper.selectByStdVersion(VER)).thenReturn(Arrays.asList(
                persistedNode(1, "B01", "梁桥", 0, "0", null),
                persistedNode(2, "B01.deck", "桥面系", 1, "0,1", null),
                persistedNode(3, "B01.deck.paving", "桥面铺装", 2, "0,1,2", 40)));
        when(todMapper.selectDiseaseTypeIdsByTemplateObjectId(3L))
                .thenReturn(Collections.singletonList(100L));

        StdSeedReport report = seedService.seed(StdSeedScope.ALL, false, null);

        assertTrue(report.isClean(), "幂等再跑应无变更: " + report.summary());
        verify(diseaseTypeMapper, never()).insertDiseaseType(any());
        verify(diseaseTypeMapper, never()).updateDiseaseType(any());
        verify(diseaseScaleMapper, never()).insertDiseaseScale(any());
        verify(diseaseScaleMapper, never()).updateDiseaseScale(any());
        verify(diseaseScaleMapper, never()).deleteDiseaseScaleById(anyLong());
        verify(templateObjectMapper, never()).insertBiTemplateObject(any());
        verify(templateObjectMapper, never()).updateBiTemplateObject(any());
        verify(templateObjectMapper, never()).deleteBiTemplateObjectById(anyLong());
        verify(todMapper, never()).batchInsertBridgeTemplateDiseaseType(anyLong(), any());
        verify(todMapper, never()).batchDeleteData(anyLong(), any());
    }

    @Test
    void 试运行_统计变更但不产生任何写操作() {
        when(catalogLoader.getCatalog()).thenReturn(buildCatalog());
        when(schemaService.ensureSchema(anyBoolean())).thenReturn(Collections.emptyList());
        when(diseaseTypeMapper.selectByCodeAndStdVersion(TYPE_CODE, VER)).thenReturn(null);
        when(diseaseScaleMapper.selectByTypeCodeAndStdVersion(TYPE_CODE, VER))
                .thenReturn(Collections.singletonList(persistedScale(99, 3, "旧标度")));
        when(templateObjectMapper.selectByStdVersion(VER)).thenReturn(Collections.emptyList());

        StdSeedReport report = seedService.seed(StdSeedScope.ALL, true, null);

        assertEquals(1, report.getDiseaseTypes().getInserted());
        assertEquals(2, report.getDiseaseScales().getInserted());
        assertEquals(1, report.getDiseaseScales().getDeleted());
        assertEquals(3, report.getTemplateNodes().getInserted());

        verify(diseaseTypeMapper, never()).insertDiseaseType(any());
        verify(diseaseTypeMapper, never()).updateDiseaseType(any());
        verify(diseaseScaleMapper, never()).insertDiseaseScale(any());
        verify(diseaseScaleMapper, never()).updateDiseaseScale(any());
        verify(diseaseScaleMapper, never()).deleteDiseaseScaleById(anyLong());
        verify(templateObjectMapper, never()).insertBiTemplateObject(any());
        verify(templateObjectMapper, never()).updateBiTemplateObject(any());
        verify(templateObjectMapper, never()).deleteBiTemplateObjectById(anyLong());
        verify(todMapper, never()).batchInsertBridgeTemplateDiseaseType(anyLong(), any());
        verify(todMapper, never()).batchDeleteData(anyLong(), any());
    }

    @Test
    void 对账删除_仅限所选桥型命名空间_不碰其它版本或桥型() {
        when(catalogLoader.getCatalog()).thenReturn(buildCatalog());
        when(schemaService.ensureSchema(anyBoolean())).thenReturn(Collections.emptyList());
        when(diseaseTypeMapper.selectByCodeAndStdVersion(TYPE_CODE, VER)).thenReturn(null);
        when(diseaseScaleMapper.selectByTypeCodeAndStdVersion(TYPE_CODE, VER))
                .thenReturn(Collections.emptyList());

        // B01 命名空间下已不在目录的节点应软删；B99（未选桥型）命名空间节点受保护。
        BiTemplateObject gone = persistedNode(50, "B01.gone.old", "废弃", 1, "0,1", null);
        BiTemplateObject foreign = persistedNode(60, "B99.foo", "他型", 1, "0,1", null);
        when(templateObjectMapper.selectByStdVersion(VER)).thenReturn(Arrays.asList(gone, foreign));
        stubFreshInserts();

        StdSeedReport report = seedService.seed(StdSeedScope.ALL, false, null);

        assertEquals(1, report.getTemplateNodes().getDeleted());
        verify(templateObjectMapper).deleteBiTemplateObjectById(50L);
        verify(templateObjectMapper, never()).deleteBiTemplateObjectById(60L);
    }

    @Test
    void scope_仅病害类型_不触碰结构与绑定() {
        when(catalogLoader.getCatalog()).thenReturn(buildCatalog());
        when(diseaseTypeMapper.selectByCodeAndStdVersion(TYPE_CODE, VER)).thenReturn(null);
        when(diseaseScaleMapper.selectByTypeCodeAndStdVersion(TYPE_CODE, VER))
                .thenReturn(Collections.emptyList());
        when(diseaseTypeMapper.insertDiseaseType(any())).thenAnswer(inv -> {
            ((DiseaseType) inv.getArgument(0)).setId(100L);
            return 1;
        });

        seedService.seed(StdSeedScope.TYPES, false, null);

        verify(diseaseTypeMapper).insertDiseaseType(any());
        verifyNoInteractions(schemaService);
        verify(templateObjectMapper, never()).insertBiTemplateObject(any());
        verifyNoInteractions(todMapper);
    }
}
