package edu.whut.cs.bi.api.controller;

import com.ruoyi.RuoYiApplication;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.domain.IdMapping;
import edu.whut.cs.bi.biz.domain.LeafObject;
import edu.whut.cs.bi.biz.domain.LoDiseaseType;
import edu.whut.cs.bi.biz.domain.SpanElementPart;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.IdMappingMapper;
import edu.whut.cs.bi.biz.mapper.LeafObjectMapper;
import edu.whut.cs.bi.biz.mapper.LoDiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JTG/T 5230—2026 新链离线同步 E2E：
 * App 现场建模 UNIT→SPAN→LAYER→PART(bi_object) + bi_element + bi_span_element_part
 * + bi_disease(element_uuid/leaf_object_id) + bi_disease_detail 整链上传。
 * 需 5230 种子库（bi_leaf_object/bi_lo_disease_type 有数据），@Tag("E2E") 手工执行。
 */
@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000"
})
@Tag("E2E")
@Transactional
public class NewChainSyncUploadE2ETest {

    @Autowired
    private ISyncUploadService syncUploadService;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private ElementMapper elementMapper;
    @Autowired
    private SpanElementPartMapper spanElementPartMapper;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private DiseaseDetailMapper diseaseDetailMapper;
    @Autowired
    private LeafObjectMapper leafObjectMapper;
    @Autowired
    private LoDiseaseTypeMapper loDiseaseTypeMapper;
    @Autowired
    private IdMappingMapper idMappingMapper;

    /** 构造一棵 App 现场树：UNIT→SPAN→LAYER→PART，故意乱序提交，验证多轮父链解析。 */
    private List<Map<String, Object>> newChainObjects(String unit, String span, String layer, String part) {
        List<Map<String, Object>> objects = new ArrayList<>();
        objects.add(node(part, layer, "PART", "支座", null));
        objects.add(node(span, unit, "SPAN", "第1跨", 1));
        objects.add(node(unit, "0", "UNIT", "评定单元", null));
        objects.add(node(layer, span, "LAYER", "上部结构", null));
        return objects;
    }

    private Map<String, Object> node(String uuid, String parent, String nodeType, String name, Integer spanNo) {
        Map<String, Object> m = new HashMap<>();
        m.put("offlineUuid", uuid);
        m.put("parentUuid", parent);
        m.put("nodeType", nodeType);
        m.put("name", name);
        m.put("status", "0");
        m.put("offlineDeleted", 0);
        if (spanNo != null) {
            m.put("spanNo", spanNo);
            m.put("bridgeType", "B01");
        }
        return m;
    }

    /** 从种子字典里取一个有病害类型的叶子，返回 (leafId, diseaseTypeId)。 */
    private long[] anyLeafAndDiseaseType() {
        List<LeafObject> leaves = leafObjectMapper.selectLeafObjectList(new LeafObject());
        for (LeafObject leaf : leaves) {
            LoDiseaseType q = new LoDiseaseType();
            q.setLeafObjectId(leaf.getId());
            List<LoDiseaseType> binds = loDiseaseTypeMapper.selectLoDiseaseTypeList(q);
            if (!binds.isEmpty()) {
                return new long[] { leaf.getId(), binds.get(0).getDiseaseTypeId() };
            }
        }
        throw new IllegalStateException("种子库缺少叶子-病害绑定（需先加载 20261014_std5230_seed.sql）");
    }

    private Map<String, Object> elementItem(String elem, String part, String name, int deleted) {
        Map<String, Object> m = new HashMap<>();
        m.put("offlineUuid", elem);
        m.put("objectUuid", part);
        m.put("code", "EL-001");
        m.put("name", name);
        m.put("status", "0");
        m.put("offlineDeleted", deleted);
        return m;
    }

    private Map<String, Object> relationItem(String rel, String elem, String span, String part, int deleted) {
        Map<String, Object> m = new HashMap<>();
        m.put("offlineUuid", rel);
        m.put("elementUuid", elem);
        m.put("spanUuid", span);
        m.put("partUuid", part);
        m.put("isShared", 0);
        m.put("offlineDeleted", deleted);
        return m;
    }

    private void putEmptyLegacyLists(Map<String, Object> payload) {
        payload.put("buildings", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("spanComponentParts", new ArrayList<>());
    }

    @Test
    @DisplayName("E2E-NEW-001: 新链整链上传 - object树+element+spanElementPart+disease(elementUuid/leafObjectId)+detail")
    public void testNewChainUpload_HappyPath() {
        String unit = "new-unit-" + UUID.randomUUID();
        String span = "new-span-" + UUID.randomUUID();
        String layer = "new-layer-" + UUID.randomUUID();
        String part = "new-part-" + UUID.randomUUID();
        String elem = "new-elem-" + UUID.randomUUID();
        String rel = "new-rel-" + UUID.randomUUID();
        String dis = "new-dis-" + UUID.randomUUID();
        String det = "new-det-" + UUID.randomUUID();

        long[] leafAndType = anyLeafAndDiseaseType();
        long leafId = leafAndType[0];
        long diseaseTypeId = leafAndType[1];

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("objects", newChainObjects(unit, span, layer, part));
        payload.put("elements", List.of(elementItem(elem, part, "1号支座构件", 0)));
        payload.put("spanElementParts", List.of(relationItem(rel, elem, span, part, 0)));

        Map<String, Object> disease = new HashMap<>();
        disease.put("offlineUuid", dis);
        disease.put("objectUuid", part);
        disease.put("elementUuid", elem);
        disease.put("leafObjectId", leafId);
        disease.put("diseaseTypeId", diseaseTypeId);
        disease.put("description", "新链支座病害");
        disease.put("level", 2);
        disease.put("offlineDeleted", 0);
        payload.put("diseases", List.of(disease));

        Map<String, Object> detail = new HashMap<>();
        detail.put("offlineUuid", det);
        detail.put("diseaseUuid", dis);
        detail.put("width", 0.25);
        detail.put("offlineDeleted", 0);
        payload.put("diseaseDetails", List.of(detail));
        putEmptyLegacyLists(payload);

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertTrue(result.getErrors().isEmpty(), "不应有错误: " + result.getErrors());

        Long partId = biObjectMapper.selectByOfflineUuid(part).getId();
        Long spanId = biObjectMapper.selectByOfflineUuid(span).getId();

        Element savedElement = elementMapper.selectByOfflineUuid(elem);
        assertNotNull(savedElement);
        assertEquals(partId, savedElement.getBiObjectId(), "element 应锚到 PART 节点");
        assertEquals(part, savedElement.getObjectUuid());

        SpanElementPart savedRel = spanElementPartMapper.selectByOfflineUuid(rel);
        assertNotNull(savedRel);
        assertEquals(savedElement.getId(), savedRel.getElementId());
        assertEquals(spanId, savedRel.getSpanId());
        assertEquals(partId, savedRel.getPartId());

        Disease savedDisease = diseaseMapper.selectByOfflineUuid(dis);
        assertNotNull(savedDisease);
        assertEquals(elem, savedDisease.getElementUuid(), "病害应记录构件 UUID");
        assertEquals(leafId, savedDisease.getLeafObjectId(), "病害应显式归属叶子");
        assertEquals(partId, savedDisease.getBiObjectId());

        DiseaseDetail savedDetail = diseaseDetailMapper.selectByOfflineUuid(det);
        assertNotNull(savedDetail);
        assertEquals(savedDisease.getId(), savedDetail.getDiseaseId());

        List<IdMapping> mappings = idMappingMapper.selectBySyncUuid((String) payload.get("syncUuid"));
        assertTrue(mappings.stream().anyMatch(m -> "Element".equals(m.getEntityType())
                && elem.equals(m.getOfflineUuid()) && m.getServerId().equals(savedElement.getId())));
        assertTrue(mappings.stream().anyMatch(m -> "SpanElementPart".equals(m.getEntityType())
                && rel.equals(m.getOfflineUuid()) && m.getServerId().equals(savedRel.getId())));
    }

    @Test
    @DisplayName("E2E-NEW-002: 重复上传走更新分支，锚定关系不重复插入")
    public void testNewChainUpload_UpdateIsIdempotent() {
        String part = "up-part-" + UUID.randomUUID();
        String elem = "up-elem-" + UUID.randomUUID();
        String rel = "up-rel-" + UUID.randomUUID();

        SyncResultVo first = syncUploadService.syncUpload(basePayload(part, elem, rel, "构件B"));
        assertTrue(first.getErrors().isEmpty(), () -> "首次: " + first.getErrors());

        Element before = elementMapper.selectByOfflineUuid(elem);
        SpanElementPart beforeRel = spanElementPartMapper.selectByOfflineUuid(rel);

        SyncResultVo second = syncUploadService.syncUpload(basePayload(part, elem, rel, "改名后的构件"));
        assertTrue(second.getErrors().isEmpty(), () -> "二次: " + second.getErrors());

        Element after = elementMapper.selectByOfflineUuid(elem);
        assertEquals(before.getId(), after.getId());
        assertEquals("改名后的构件", after.getName());
        SpanElementPart afterRel = spanElementPartMapper.selectByOfflineUuid(rel);
        assertEquals(beforeRel.getId(), afterRel.getId(), "锚定关系应更新而非新增");
    }

    @Test
    @DisplayName("E2E-NEW-003: 锚定三要素反查失败不落库，计入错误")
    public void testNewChainUpload_MissingAnchor_Rejected() {
        String part = "miss-part-" + UUID.randomUUID();
        String elem = "miss-elem-" + UUID.randomUUID();
        String rel = "miss-rel-" + UUID.randomUUID();

        // element/span 正常，但关系里 partUuid 给一个从未同步的 UUID
        Map<String, Object> payload = basePayload(part, elem, rel, "构件C");
        @SuppressWarnings("unchecked")
        Map<String, Object> badRel =
                ((List<Map<String, Object>>) payload.get("spanElementParts")).get(0);
        badRel.put("partUuid", "ghost-part-" + UUID.randomUUID());

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertEquals(1, result.getFailureCount());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "SpanElementPart".equals(e.getEntityType()) && rel.equals(e.getOfflineUuid())));
        assertNull(spanElementPartMapper.selectByOfflineUuid(rel), "反查失败的锚定关系不应落库");
        assertNotNull(elementMapper.selectByOfflineUuid(elem), "构件本身仍应正常入库");
    }

    @Test
    @DisplayName("E2E-NEW-004: offlineDeleted=1 - 构件逻辑删除、锚定关系物理删除")
    public void testNewChainUpload_Delete() {
        String part = "del-part-" + UUID.randomUUID();
        String elem = "del-elem-" + UUID.randomUUID();
        String rel = "del-rel-" + UUID.randomUUID();

        syncUploadService.syncUpload(basePayload(part, elem, rel, "构件D"));
        assertNotNull(elementMapper.selectByOfflineUuid(elem));
        assertNotNull(spanElementPartMapper.selectByOfflineUuid(rel));

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("objects", new ArrayList<>());
        payload.put("elements", List.of(deletedItem(elem)));
        payload.put("spanElementParts", List.of(deletedItem(rel)));
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        putEmptyLegacyLists(payload);

        SyncResultVo result = syncUploadService.syncUpload(payload);
        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());

        assertNull(elementMapper.selectByOfflineUuid(elem), "逻辑删除后 del_flag='2' 查不到");
        assertNull(spanElementPartMapper.selectByOfflineUuid(rel), "锚定关系应物理删除");
    }

    private Map<String, Object> deletedItem(String uuid) {
        Map<String, Object> m = new HashMap<>();
        m.put("offlineUuid", uuid);
        m.put("offlineDeleted", 1);
        return m;
    }

    private Map<String, Object> basePayload(String part, String elem, String rel, String elementName) {
        String unit = "b-unit-" + UUID.randomUUID();
        String span = "b-span-" + UUID.randomUUID();
        String layer = "b-layer-" + UUID.randomUUID();

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("objects", newChainObjects(unit, span, layer, part));
        payload.put("elements", List.of(elementItem(elem, part, elementName, 0)));
        payload.put("spanElementParts", List.of(relationItem(rel, elem, span, part, 0)));
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        putEmptyLegacyLists(payload);
        return payload;
    }
}
