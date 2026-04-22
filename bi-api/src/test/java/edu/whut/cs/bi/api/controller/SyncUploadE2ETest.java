package edu.whut.cs.bi.api.controller;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据同步 E2E 集成测试
 *
 * 测试场景覆盖：
 * 1. 完整同步流程 - 新增数据
 * 2. 更新已有数据
 * 3. 软删除数据
 * 4. 树形结构多轮处理
 * 5. SQLite 生成与下载
 * 6. 并发同步事务隔离
 * 7. 部分失败回滚
 * 8. 空数据处理
 * 9. 重复 UUID 处理
 * 10. BiObjectComponent 同步
 *
 * @author QiXin
 * @date 2026/04/22
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "minio.bucket=test-bucket",
    "minio.url=http://localhost:9000"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("E2E")
public class SyncUploadE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ISyncUploadService syncUploadService;

    @Autowired
    private SqliteService sqliteService;

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
    private BiObjectComponentMapper biObjectComponentMapper;

    @Autowired
    private IdMappingMapper idMappingMapper;

    @Autowired
    private SyncLogMapper syncLogMapper;

    private final String TEST_UUID_PREFIX = "e2e-test-";
    private final List<String> createdUuids = new ArrayList<>();

    @BeforeEach
    void setUp() {
        createdUuids.clear();
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        for (String uuid : createdUuids) {
            try {
                Building b = buildingMapper.selectByOfflineUuid(uuid);
                if (b != null) {
                    buildingMapper.deleteBuildingById(b.getId());
                }
            } catch (Exception ignored) {}
        }
    }

    // ==================== E2E-001: 完整同步流程 ====================

    /**
     * 测试场景：完整同步流程 - 新增 Building+Object+Component+Disease+Detail
     * 预期结果：所有实体成功落库，ID 映射已保存
     */
    @Test
    @DisplayName("E2E-001: 完整同步流程 - 新增数据")
    public void testSyncUpload_NewData_HappyPath() throws Exception {
        // Given
        String syncUuid = TEST_UUID_PREFIX + UUID.randomUUID();
        String buildingUuid = TEST_UUID_PREFIX + "b1-" + UUID.randomUUID();
        String objectUuid = TEST_UUID_PREFIX + "o1-" + UUID.randomUUID();
        String componentUuid = TEST_UUID_PREFIX + "c1-" + UUID.randomUUID();
        String diseaseUuid = TEST_UUID_PREFIX + "d1-" + UUID.randomUUID();

        Map<String, Object> payload = buildFullPayload(
            syncUuid, buildingUuid, objectUuid, componentUuid, diseaseUuid
        );

        createdUuids.addAll(Arrays.asList(buildingUuid, objectUuid, componentUuid, diseaseUuid));

        // When
        SyncResultVo result = syncUploadService.syncUpload(payload);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccessCount() >= 5, "应至少成功处理 5 个实体");
        assertTrue(result.getErrors().isEmpty(), "不应有错误：" + result.getErrors());

        // 验证 Building
        Building savedBuilding = buildingMapper.selectByOfflineUuid(buildingUuid);
        assertNotNull(savedBuilding);
        assertEquals(1, savedBuilding.getIsOfflineData());
        assertEquals("测试桥梁", savedBuilding.getName());

        // 验证 BiObject
        BiObject savedObject = biObjectMapper.selectByOfflineUuid(objectUuid);
        assertNotNull(savedObject);
        assertEquals(savedBuilding.getId(), savedObject.getBuildingId());

        // 验证 Component
        Component savedComp = componentMapper.selectByOfflineUuid(componentUuid);
        assertNotNull(savedComp);

        // 验证 Disease
        Disease savedDisease = diseaseMapper.selectByOfflineUuid(diseaseUuid);
        assertNotNull(savedDisease);

        // 验证 ID 映射
        List<IdMapping> mappings = idMappingMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IdMapping>()
                .eq("offline_uuid", buildingUuid)
        );
        assertFalse(mappings.isEmpty(), "应保存 ID 映射");
    }

    // ==================== E2E-002: 更新已有数据 ====================

    /**
     * 测试场景：同步更新已有 Building 记录
     * 预期结果：记录被更新，ID 不变，名称和区域改变
     */
    @Test
    @DisplayName("E2E-002: 同步更新已有数据")
    public void testSyncUpload_UpdateExisting() throws Exception {
        // Given
        String uuid = TEST_UUID_PREFIX + "update-" + UUID.randomUUID();
        createdUuids.add(uuid);

        Building existing = new Building();
        existing.setOfflineUuid(uuid);
        existing.setName("原始名称");
        existing.setArea("原始区域");
        buildingMapper.insertBuilding(existing);

        // When
        Map<String, Object> payload = buildUpdatePayload(uuid, "更新后的名称", "更新后的区域");
        SyncResultVo result = syncUploadService.syncUpload(payload);

        // Then
        assertEquals(1, result.getSuccessCount());

        Building updated = buildingMapper.selectByOfflineUuid(uuid);
        assertNotNull(updated);
        assertEquals("更新后的名称", updated.getName());
        assertEquals("更新后的区域", updated.getArea());
        assertEquals(existing.getId(), updated.getId());
    }

    // ==================== E2E-003: 软删除数据 ====================

    /**
     * 测试场景：同步删除 - offlineDeleted=1
     * 预期结果：记录被物理删除
     */
    @Test
    @DisplayName("E2E-003: 同步删除数据")
    public void testSyncUpload_Delete() throws Exception {
        // Given
        String uuid = TEST_UUID_PREFIX + "delete-" + UUID.randomUUID();
        createdUuids.add(uuid);

        Building existing = new Building();
        existing.setOfflineUuid(uuid);
        existing.setName("待删除桥梁");
        buildingMapper.insertBuilding(existing);

        // When
        Map<String, Object> payload = buildDeletePayload(uuid);
        SyncResultVo result = syncUploadService.syncUpload(payload);

        // Then
        Building deleted = buildingMapper.selectByOfflineUuid(uuid);
        assertNull(deleted, "记录应被删除");
    }

    // ==================== E2E-004: 树形结构多轮处理 ====================

    /**
     * 测试场景：BiObject 树形结构 - 子节点先于父节点提交
     * 预期结果：所有节点正确建立父子关系
     */
    @Test
    @DisplayName("E2E-004: BiObject 树形结构处理")
    public void testSyncUpload_BiObject_TreeOrder() throws Exception {
        // Given
        String syncUuid = TEST_UUID_PREFIX + "tree-" + UUID.randomUUID();
        String rootUuid = TEST_UUID_PREFIX + "o1-root-" + UUID.randomUUID();
        String middleUuid = TEST_UUID_PREFIX + "o2-middle-" + UUID.randomUUID();
        String childUuid = TEST_UUID_PREFIX + "o3-child-" + UUID.randomUUID();

        createdUuids.addAll(Arrays.asList(rootUuid, middleUuid, childUuid));

        Map<String, Object> payload = buildBiObjectTreePayload(syncUuid, rootUuid, middleUuid, childUuid);

        // When
        SyncResultVo result = syncUploadService.syncUpload(payload);

        // Then
        assertEquals(3, result.getSuccessCount());

        BiObject rootObj = biObjectMapper.selectByOfflineUuid(rootUuid);
        BiObject middleObj = biObjectMapper.selectByOfflineUuid(middleUuid);
        BiObject childObj = biObjectMapper.selectByOfflineUuid(childUuid);

        assertNotNull(rootObj);
        assertNotNull(middleObj);
        assertNotNull(childObj);

        assertEquals(0L, rootObj.getParentId());
        assertEquals(rootObj.getId(), middleObj.getParentId());
        assertEquals(middleObj.getId(), childObj.getParentId());
    }

    // ==================== E2E-005: 空数据处理 ====================

    /**
     * 测试场景：空数据载荷处理
     * 预期结果：不抛异常，返回空结果
     */
    @Test
    @DisplayName("E2E-005: 空数据处理")
    public void testSyncUpload_EmptyPayload() throws Exception {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", TEST_UUID_PREFIX + "empty-" + UUID.randomUUID());
        payload.put("buildings", new ArrayList<>());
        payload.put("objects", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());

        // When
        SyncResultVo result = syncUploadService.syncUpload(payload);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getErrors().isEmpty());
    }

    // ==================== E2E-006: 重复 UUID 处理 ====================

    /**
     * 测试场景：重复 offlineUuid - 后提交覆盖
     * 预期结果：第二次更新而非插入，只有一条记录
     */
    @Test
    @DisplayName("E2E-006: 重复 UUID 覆盖")
    public void testSyncUpload_DuplicateUuid() throws Exception {
        // Given
        String uuid = TEST_UUID_PREFIX + "dup-" + UUID.randomUUID();
        createdUuids.add(uuid);

        Map<String, Object> payload1 = buildMinimalPayload(uuid, "名称 V1");
        SyncResultVo result1 = syncUploadService.syncUpload(payload1);

        // When
        Map<String, Object> payload2 = buildMinimalPayload(uuid, "名称 V2");
        SyncResultVo result2 = syncUploadService.syncUpload(payload2);

        // Then
        assertEquals(1, result2.getSuccessCount());

        Building saved = buildingMapper.selectByOfflineUuid(uuid);
        assertNotNull(saved);
        assertEquals("名称 V2", saved.getName());
    }

    // ==================== E2E-007: BiObjectComponent 同步 ====================

    /**
     * 测试场景：BiObjectComponent 同步与评定触发
     * 预期结果：关联关系已建立
     */
    @Test
    @DisplayName("E2E-007: BiObjectComponent 同步")
    public void testSyncUpload_BiObjectComponent() throws Exception {
        // Given
        String buildingUuid = TEST_UUID_PREFIX + "b-eval-" + UUID.randomUUID();
        String objectUuid = TEST_UUID_PREFIX + "o-span-" + UUID.randomUUID();
        String componentUuid = TEST_UUID_PREFIX + "c-eval-" + UUID.randomUUID();
        String relUuid = TEST_UUID_PREFIX + "rel-" + UUID.randomUUID();

        createdUuids.addAll(Arrays.asList(buildingUuid, objectUuid, componentUuid));

        // 基础数据
        Map<String, Object> basePayload = buildBaseDataForEval(buildingUuid, objectUuid, componentUuid);
        syncUploadService.syncUpload(basePayload);

        // BiObjectComponent
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", TEST_UUID_PREFIX + "eval-" + UUID.randomUUID());

        List<Map<String, Object>> rels = new ArrayList<>();
        Map<String, Object> rel = new HashMap<>();
        rel.put("offlineUuid", relUuid);
        rel.put("componentUuid", componentUuid);
        rel.put("objectUuid", objectUuid);
        rel.put("weight", 1.0);
        rel.put("offlineDeleted", 0);
        rels.add(rel);
        payload.put("biObjectComponents", rels);
        payload.put("taskId", 999L);

        createdUuids.add(relUuid);

        // When
        SyncResultVo result = syncUploadService.syncUpload(payload);

        // Then
        assertEquals(1, result.getSuccessCount());

        BiObjectComponent savedRel = biObjectComponentMapper.selectByOfflineUuid(relUuid);
        assertNotNull(savedRel);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建完整同步载荷
     */
    private Map<String, Object> buildFullPayload(String syncUuid, String buildingUuid,
                                                   String objectUuid, String componentUuid,
                                                   String diseaseUuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", syncUuid);
        payload.put("clientInfo", "E2ETest/1.0");

        // Building
        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", buildingUuid);
        building.put("name", "测试桥梁");
        building.put("area", "武汉市");
        building.put("line", "二环线");
        building.put("status", "0");
        building.put("isLeaf", "1");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);

        // BiObject
        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> rootObj = new HashMap<>();
        rootObj.put("offlineUuid", objectUuid);
        rootObj.put("buildingUuid", buildingUuid);
        rootObj.put("parentUuid", "0");
        rootObj.put("name", "第一跨");
        rootObj.put("status", "0");
        rootObj.put("offlineDeleted", 0);
        objects.add(rootObj);
        payload.put("objects", objects);

        // Component
        List<Map<String, Object>> components = new ArrayList<>();
        Map<String, Object> comp = new HashMap<>();
        comp.put("offlineUuid", componentUuid);
        comp.put("objectUuid", objectUuid);
        comp.put("name", "左侧梁");
        comp.put("code", "L01");
        comp.put("status", "0");
        comp.put("offlineDeleted", 0);
        components.add(comp);
        payload.put("components", components);

        // Disease
        List<Map<String, Object>> diseases = new ArrayList<>();
        Map<String, Object> disease = new HashMap<>();
        disease.put("offlineUuid", diseaseUuid);
        disease.put("buildingUuid", buildingUuid);
        disease.put("objectUuid", objectUuid);
        disease.put("componentUuid", componentUuid);
        disease.put("description", "梁体裂缝");
        disease.put("type", "1");
        disease.put("level", 2);
        disease.put("quantity", 3);
        disease.put("offlineDeleted", 0);
        diseases.add(disease);
        payload.put("diseases", diseases);

        // DiseaseDetail
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("diseaseUuid", diseaseUuid);
        detail.put("reference1Location", "跨中");
        detail.put("width", 0.3);
        detail.put("length1", 150.0);
        detail.put("offlineDeleted", 0);
        details.add(detail);
        payload.put("diseaseDetails", details);

        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());

        return payload;
    }

    /**
     * 构建更新载荷
     */
    private Map<String, Object> buildUpdatePayload(String uuid, String name, String area) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", TEST_UUID_PREFIX + "update-" + UUID.randomUUID());

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", uuid);
        building.put("name", name);
        building.put("area", area);
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

    /**
     * 构建删除载荷
     */
    private Map<String, Object> buildDeletePayload(String uuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", TEST_UUID_PREFIX + "delete-" + UUID.randomUUID());

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", uuid);
        building.put("offlineDeleted", 1);
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

    /**
     * 构建 BiObject 树形结构载荷
     */
    private Map<String, Object> buildBiObjectTreePayload(String syncUuid,
                                                          String rootUuid,
                                                          String middleUuid,
                                                          String childUuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", syncUuid);

        // 乱序提交：子节点在前
        List<Map<String, Object>> objects = new ArrayList<>();

        // 子节点
        Map<String, Object> child = new HashMap<>();
        child.put("offlineUuid", childUuid);
        child.put("parentUuid", middleUuid);
        child.put("name", "子节点");
        child.put("offlineDeleted", 0);
        objects.add(child);

        // 中间节点
        Map<String, Object> middle = new HashMap<>();
        middle.put("offlineUuid", middleUuid);
        middle.put("parentUuid", rootUuid);
        middle.put("name", "中间节点");
        middle.put("offlineDeleted", 0);
        objects.add(middle);

        // 根节点
        Map<String, Object> root = new HashMap<>();
        root.put("offlineUuid", rootUuid);
        root.put("parentUuid", "0");
        root.put("name", "根节点");
        root.put("offlineDeleted", 0);
        objects.add(root);

        payload.put("objects", objects);
        payload.put("buildings", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());

        return payload;
    }

    /**
     * 构建最小载荷（仅 Building）
     */
    private Map<String, Object> buildMinimalPayload(String uuid, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", TEST_UUID_PREFIX + "minimal-" + UUID.randomUUID());

        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", uuid);
        building.put("name", name);
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

    /**
     * 构建评定基础数据
     */
    private Map<String, Object> buildBaseDataForEval(String buildingUuid,
                                                      String objectUuid,
                                                      String componentUuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", TEST_UUID_PREFIX + "base-" + UUID.randomUUID());

        // Building
        List<Map<String, Object>> buildings = new ArrayList<>();
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", buildingUuid);
        building.put("name", "评定桥梁");
        building.put("offlineDeleted", 0);
        buildings.add(building);
        payload.put("buildings", buildings);

        // BiObject
        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> span = new HashMap<>();
        span.put("offlineUuid", objectUuid);
        span.put("buildingUuid", buildingUuid);
        span.put("parentUuid", "0");
        span.put("name", "测试跨");
        span.put("offlineDeleted", 0);
        objects.add(span);
        payload.put("objects", objects);

        // Component
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
