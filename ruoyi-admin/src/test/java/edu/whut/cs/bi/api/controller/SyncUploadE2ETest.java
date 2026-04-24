package edu.whut.cs.bi.api.controller;

import com.ruoyi.RuoYiApplication;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000"
})
@Tag("E2E")
@Transactional //注释后数据库不回滚
public class SyncUploadE2ETest {

    @Autowired
    private ISyncUploadService syncUploadService;
    @Autowired
    private BuildingMapper buildingMapper;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private ComponentMapper componentMapper;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private DiseaseDetailMapper diseaseDetailMapper;
    @Autowired
    private IdMappingMapper idMappingMapper;
    @Autowired
    private BiObjectComponentMapper biObjectComponentMapper;

    private final Set<String> createdUuids = new LinkedHashSet<>();

    @AfterEach
    void tearDown() {
        for (String uuid : createdUuids) {
            try {
                DiseaseDetail dd = diseaseDetailMapper.selectByOfflineUuid(uuid);
                if (dd != null && dd.getId() != null) diseaseDetailMapper.deleteDiseaseDetailById(dd.getId());
            } catch (Exception ignored) {}
            try {
                Disease d = diseaseMapper.selectByOfflineUuid(uuid);
                if (d != null && d.getId() != null) diseaseMapper.deleteDiseaseById(d.getId());
            } catch (Exception ignored) {}
            try {
                Component c = componentMapper.selectByOfflineUuid(uuid);
                if (c != null && c.getId() != null) {
                    biObjectComponentMapper.deleteBiObjectComponentByComponentId(c.getId());
                    componentMapper.deleteComponentById(c.getId());
                }
            } catch (Exception ignored) {}
            try {
                BiObject o = biObjectMapper.selectByOfflineUuid(uuid);
                if (o != null && o.getId() != null) biObjectMapper.deleteBiObjectById(o.getId());
            } catch (Exception ignored) {}
            try {
                Building b = buildingMapper.selectByOfflineUuid(uuid);
                if (b != null && b.getId() != null) buildingMapper.deleteBuildingById(b.getId());
            } catch (Exception ignored) {}
        }
        createdUuids.clear();
    }

    @Test
    @DisplayName("E2E2-001: 完整同步流程 - 新增 Building+Object+Component+Disease")
    public void testSyncUpload_NewData_HappyPath() {
        String bUuid = "b1-uuid-test2-" + UUID.randomUUID();
        String oUuid = "o1-uuid-test2-" + UUID.randomUUID();
        String cUuid = "c1-uuid-test2-" + UUID.randomUUID();
        String dUuid = "d1-uuid-test2-" + UUID.randomUUID();
        String ddUuid = "dd1-uuid-test2-" + UUID.randomUUID();
        registerCreatedUuids(bUuid, oUuid, cUuid, dUuid, ddUuid);

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("clientInfo", "TestClient/1.0");

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", bUuid);
        building.put("name", "测试桥梁");
        building.put("area", "武汉市");
        building.put("line", "二环线");
        building.put("status", "0");
        building.put("isLeaf", "1");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);

        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> rootObj = new HashMap<>();
        rootObj.put("offlineUuid", oUuid);
        rootObj.put("buildingUuid", bUuid);
        rootObj.put("parentUuid", "0");
        rootObj.put("name", "第一跨");
        rootObj.put("status", "0");
        rootObj.put("offlineDeleted", 0);
        objects.add(rootObj);
        payload.put("objects", objects);

        List<Map<String, Object>> components = new ArrayList<>();
        Map<String, Object> comp = new HashMap<>();
        comp.put("offlineUuid", cUuid);
        comp.put("objectUuid", oUuid);
        comp.put("name", "左侧梁");
        comp.put("code", "L01");
        comp.put("status", "0");
        comp.put("offlineDeleted", 0);
        components.add(comp);
        payload.put("components", components);

        List<Map<String, Object>> diseases = new ArrayList<>();
        Map<String, Object> disease = new HashMap<>();
        disease.put("offlineUuid", dUuid);
        disease.put("buildingUuid", bUuid);
        disease.put("objectUuid", oUuid);
        disease.put("componentUuid", cUuid);
        disease.put("description", "梁体裂缝");
        disease.put("type", "1");
        disease.put("level", 2);
        disease.put("quantity", 3);
        disease.put("offlineDeleted", 0);
        diseases.add(disease);
        payload.put("diseases", diseases);

        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("offlineUuid", ddUuid);
        detail.put("diseaseUuid", dUuid);
        detail.put("reference1Location", "跨中");
        detail.put("width", 0.3);
        detail.put("length1", 150.0);
        detail.put("offlineDeleted", 0);
        details.add(detail);
        payload.put("diseaseDetails", details);

        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertNotNull(result);
        assertTrue(result.getSuccessCount() >= 5);
        assertTrue(result.getErrors().isEmpty());

        Building savedBuilding = buildingMapper.selectByOfflineUuid(bUuid);
        assertNotNull(savedBuilding);
        assertEquals(1, savedBuilding.getIsOfflineData());

        BiObject savedObject = biObjectMapper.selectByOfflineUuid(oUuid);
        assertNotNull(savedObject);
        assertEquals(bUuid, savedObject.getBuildingUuid());

        Component savedComp = componentMapper.selectByOfflineUuid(cUuid);
        assertNotNull(savedComp);

        Disease savedDisease = diseaseMapper.selectByOfflineUuid(dUuid);
        assertNotNull(savedDisease);

        List<IdMapping> mappings = idMappingMapper.selectBySyncUuid((String) payload.get("syncUuid"));
        assertFalse(mappings.isEmpty());
    }

    @Test
    @DisplayName("E2E2-002: 同步更新已有 Building 记录")
    public void testSyncUpload_UpdateExisting() {
        String uuid = "b-update-uuid2-" + UUID.randomUUID();
        registerCreatedUuids(uuid);

        Building existing = new Building();
        existing.setOfflineUuid(uuid);
        existing.setName("原始名称");
        existing.setArea("原始区域");
        buildingMapper.insertBuilding(existing);

        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", uuid);
        building.put("name", "更新后的名称");
        building.put("area", "更新后的区域");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);
        fillEmptyLists(payload);

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertEquals(1, result.getSuccessCount());
        Building updated = buildingMapper.selectByOfflineUuid(uuid);
        assertNotNull(updated);
        assertEquals("更新后的名称", updated.getName());
        assertEquals("更新后的区域", updated.getArea());
        assertEquals(existing.getId(), updated.getId());
    }

    @Test
    @DisplayName("E2E2-003: 同步删除 - offlineDeleted=1")
    public void testSyncUpload_Delete() {
        String uuid = "b-delete-uuid2-" + UUID.randomUUID();
        registerCreatedUuids(uuid);

        Building existing = new Building();
        existing.setOfflineUuid(uuid);
        existing.setName("待删除桥梁");
        buildingMapper.insertBuilding(existing);

        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", uuid);
        building.put("offlineDeleted", 1);
        buildings.add(building);
        payload.put("buildings", buildings);
        fillEmptyLists(payload);

        SyncResultVo result = syncUploadService.syncUpload(payload);
        assertNotNull(result);

        Building deleted = buildingMapper.selectByOfflineUuid(uuid);
        assertNull(deleted);
    }

    @Test
    @DisplayName("E2E2-004: BiObject 树形结构 - 子节点先于父节点提交")
    public void testSyncUpload_BiObject_TreeOrder() {
        String bUuid = "b-tree-uuid2-" + UUID.randomUUID();
        String rootUuid = "o1-uuid2-" + UUID.randomUUID();
        String middleUuid = "o2-uuid2-" + UUID.randomUUID();
        String childUuid = "o-child-uuid2-" + UUID.randomUUID();
        registerCreatedUuids(bUuid, rootUuid, middleUuid, childUuid);

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", bUuid);
        building.put("name", "树测试桥梁");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);

        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> child = new HashMap<>();
        child.put("offlineUuid", childUuid);
        child.put("parentUuid", middleUuid);
        child.put("buildingUuid", bUuid);
        child.put("name", "子节点");
        child.put("offlineDeleted", 0);
        objects.add(child);

        Map<String, Object> middle = new HashMap<>();
        middle.put("offlineUuid", middleUuid);
        middle.put("parentUuid", rootUuid);
        middle.put("buildingUuid", bUuid);
        middle.put("name", "中间节点");
        middle.put("offlineDeleted", 0);
        objects.add(middle);

        Map<String, Object> root = new HashMap<>();
        root.put("offlineUuid", rootUuid);
        root.put("parentUuid", "0");
        root.put("buildingUuid", bUuid);
        root.put("name", "根节点");
        root.put("offlineDeleted", 0);
        objects.add(root);

        payload.put("objects", objects);
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertEquals(4, result.getSuccessCount());
        BiObject childObj = biObjectMapper.selectByOfflineUuid(childUuid);
        BiObject middleObj = biObjectMapper.selectByOfflineUuid(middleUuid);
        BiObject rootObj = biObjectMapper.selectByOfflineUuid(rootUuid);

        assertNotNull(childObj);
        assertNotNull(middleObj);
        assertNotNull(rootObj);

        assertEquals(middleObj.getId(), childObj.getParentId());
        assertEquals(rootObj.getId(), middleObj.getParentId());
        assertEquals(0L, rootObj.getParentId());
    }

    @Test
    @DisplayName("E2E2-006: 并发同步 - 事务隔离测试")
    public void testSyncUpload_Concurrent_Isolation() throws Exception {
        String uuid1 = "thread-1-uuid2-" + UUID.randomUUID();
        String uuid2 = "thread-2-uuid2-" + UUID.randomUUID();
        registerCreatedUuids(uuid1, uuid2);

        CountDownLatch latch = new CountDownLatch(2);
        List<SyncResultVo> results = Collections.synchronizedList(new ArrayList<>());

        Runnable task1 = () -> {
            try {
                results.add(syncUploadService.syncUpload(buildPayload(uuid1, "并发桥梁1")));
            } finally {
                latch.countDown();
            }
        };
        Runnable task2 = () -> {
            try {
                results.add(syncUploadService.syncUpload(buildPayload(uuid2, "并发桥梁2")));
            } finally {
                latch.countDown();
            }
        };

        new Thread(task1).start();
        new Thread(task2).start();
        assertTrue(latch.await(30, TimeUnit.SECONDS));

        assertEquals(2, results.size());
        for (SyncResultVo result : results) assertTrue(result.getSuccessCount() > 0);

        Building b1 = buildingMapper.selectByOfflineUuid(uuid1);
        Building b2 = buildingMapper.selectByOfflineUuid(uuid2);
        assertNotNull(b1);
        assertNotNull(b2);
        assertNotEquals(b1.getId(), b2.getId());
    }

    @Test
    @DisplayName("E2E2-008: 空数据载荷处理")
    public void testSyncUpload_EmptyPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("buildings", new ArrayList<>());
        payload.put("objects", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertNotNull(result);
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("E2E2-009: 重复 offlineUuid - 后提交覆盖")
    public void testSyncUpload_DuplicateUuid() {
        String uuid = "dup-uuid2-" + UUID.randomUUID();
        registerCreatedUuids(uuid);

        syncUploadService.syncUpload(buildPayload(uuid, "名称 V1"));
        SyncResultVo result2 = syncUploadService.syncUpload(buildPayload(uuid, "名称 V2"));

        assertEquals(1, result2.getSuccessCount());

        Building saved = buildingMapper.selectByOfflineUuid(uuid);
        assertNotNull(saved);
        assertEquals("名称 V2", saved.getName());

        List<Building> list = buildingMapper.selectBuildingsByIds(Collections.singletonList(saved.getId()));
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("E2E2-010: BiObjectComponent 同步与评定触发")
    public void testSyncUpload_BiObjectComponent_Eval() {
        String bUuid = "b-eval-uuid2-" + UUID.randomUUID();
        String oUuid = "o-span-uuid2-" + UUID.randomUUID();
        String cUuid = "c-eval-uuid2-" + UUID.randomUUID();
        String relUuid = "rel-uuid2-" + UUID.randomUUID();
        registerCreatedUuids(bUuid, oUuid, cUuid, relUuid);

        syncUploadService.syncUpload(buildBasePayload(bUuid, oUuid, cUuid));

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());

        List<Map<String, Object>> rels = new ArrayList<>();
        Map<String, Object> rel = new HashMap<>();
        rel.put("offlineUuid", relUuid);
        rel.put("componentUuid", cUuid);
        rel.put("objectUuid", oUuid);
        rel.put("weight", 1.0);
        rel.put("offlineDeleted", 0);
        rels.add(rel);
        payload.put("biObjectComponents", rels);
        payload.put("taskId", 999L);
        payload.put("buildings", new ArrayList<>());
        payload.put("objects", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertEquals(1, result.getSuccessCount());

        BiObjectComponent query = new BiObjectComponent();
        query.setObjectUuid(oUuid);
        query.setComponentUuid(cUuid);
        List<BiObjectComponent> savedList = biObjectComponentMapper.selectBiObjectComponentList(query);
        assertFalse(savedList.isEmpty());
    }

    private void registerCreatedUuids(String... uuids) { createdUuids.addAll(Arrays.asList(uuids)); }

    private void fillEmptyLists(Map<String, Object> payload) {
        payload.putIfAbsent("syncUuid", UUID.randomUUID().toString());
        payload.put("objects", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());
    }

    private Map<String, Object> buildPayload(String uuid, String buildingName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", uuid);
        building.put("name", buildingName);
        building.put("area", "测试区域");
        building.put("status", "0");
        building.put("isLeaf", "1");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);

        payload.put("objects", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());
        return payload;
    }

    private Map<String, Object> buildBasePayload(String buildingUuid, String objectUuid, String componentUuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", buildingUuid);
        building.put("name", "评定桥梁");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);

        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> span = new HashMap<>();
        span.put("offlineUuid", objectUuid);
        span.put("buildingUuid", buildingUuid);
        span.put("parentUuid", "0");
        span.put("name", "测试跨");
        span.put("offlineDeleted", 0);
        objects.add(span);
        payload.put("objects", objects);

        List<Map<String, Object>> components = new ArrayList<>();
        Map<String, Object> comp = new HashMap<>();
        comp.put("offlineUuid", componentUuid);
        comp.put("objectUuid", objectUuid);
        comp.put("name", "测试构件");
        comp.put("code", "L01");
        comp.put("offlineDeleted", 0);
        components.add(comp);
        payload.put("components", components);

        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());
        return payload;
    }
}
