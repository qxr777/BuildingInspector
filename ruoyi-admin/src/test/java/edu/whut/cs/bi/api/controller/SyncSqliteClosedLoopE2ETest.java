package edu.whut.cs.bi.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQLite 闭环 E2E（外部真实接口）
 *
 * 测试路径：
 * 1) Java 登录 -> 获取用户 SQLite URL -> 下载 .db
 * 2) JDBC 修改 SQLite（模拟离线新增/更新/删除）
 * 3) 从 SQLite 提取 is_offline_data=1 的增量数据，组装 payload
 * 4) 调用 /api/v2/sync/upload
 * 5) 下载响应返回的新 SQLite
 * 6) JDBC 校验闭环结果
 */
@Tag("E2E")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SyncSqliteClosedLoopE2ETest {

    private static final String BASE_URL = System.getProperty("sync.baseUrl", "http://localhost:80");
    private static final String USERNAME = System.getProperty("sync.username", "znjc_test_1");
    private static final String PASSWORD = System.getProperty("sync.password", "123456");
    private static final Long USER_ID = Long.parseLong(System.getProperty("sync.userId", "95"));

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 顺序编排场景共享的离线 UUID：
     * step1 先新增，step2 更新同 UUID，step3 删除同 UUID。
     */
    private static String orderedBuildingUuid;
    private static String orderedDiseaseUuid;
    private static String orderedBuildingName;
    private static String orderedDiseaseDesc;
    private static String orderedComponentCode;
    private static String orderedAttachmentName;

    /**
     * 外部接口健康检查：避免服务未启动时产生大量 ConnectException 堆栈。
     */
    @BeforeEach
    void ensureServerReachable() {
        Assumptions.assumeTrue(isServerReachable(),
                () -> "外部接口不可达，请先启动服务或通过 -Dsync.baseUrl 指定可访问地址，当前=" + BASE_URL);
    }

    @Test
    @Order(1)
    @DisplayName("ClosedLoop-Ordered-01: 顺序编排-先新增")
    void testOrdered01_insert_then_prepare_for_update_delete() throws Exception {
        String token = loginAndGetToken();
        Path baseDb = downloadUserSqlite(token, USER_ID);

        orderedBuildingName = "Ordered更新桥梁-" + random5();
        String rootUuid = uuid("e2e-ordered-root-");
        String childUuid = uuid("e2e-ordered-child-");
        String compUuid = uuid("e2e-ordered-comp-");
        orderedDiseaseUuid = uuid("e2e-ordered-dis-");
        String detailUuid = uuid("e2e-ordered-det-");
        String attUuid = uuid("e2e-ordered-att-");
        orderedComponentCode = "ORDER-C-" + random5();
        orderedAttachmentName = "ordered_insert_" + random5() + ".jpg";

        try (Connection c = open(baseDb)) {
            orderedBuildingUuid = firstOfflineUuid(c, "bi_building");
            assertNotNull(orderedBuildingUuid, "测试前置失败：bi_building 无可用 offline_uuid");

            updateByOfflineUuid(c, "bi_building", orderedBuildingUuid, mapOf(
                    "name", orderedBuildingName,
                    "root_object_uuid", rootUuid,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", rootUuid,
                    "name", "Ordered根节点",
                    "parent_uuid", "",
                    "building_uuid", orderedBuildingUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", childUuid,
                    "name", "Ordered子节点",
                    "parent_uuid", rootUuid,
                    "building_uuid", orderedBuildingUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_component", mapOf(
                    "offline_uuid", compUuid,
                    "name", "Ordered构件-" + random5(),
                    "code", orderedComponentCode,
                    "object_uuid", childUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            orderedDiseaseDesc = "Ordered新增病害-" + random5();
            insertRow(c, "bi_disease", mapOf(
                    "offline_uuid", orderedDiseaseUuid,
                    "building_uuid", orderedBuildingUuid,
                    "object_uuid", childUuid,
                    "component_uuid", compUuid,
                    "description", orderedDiseaseDesc,
                    "type", "1",
                    "level", 1,
                    "quantity", 1,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_disease_detail", mapOf(
                    "offline_uuid", detailUuid,
                    "disease_uuid", orderedDiseaseUuid,
                    "reference1_location", "跨中",
                    "length1", 2.22,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_attachment", mapOf(
                    "offline_uuid", attUuid,
                    "offline_subject_uuid", orderedDiseaseUuid,
                    "name", orderedAttachmentName,
                    "type", 1,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        Map<String, Object> payload = buildPayloadFromSqlite(baseDb);
        debugPrintUploadPayload("Ordered-01", payload);
        preUploadAttachments(token, payload);
        debugPrintUploadPayload("Ordered-01-after-attachment", payload);
        JsonNode uploadRes = upload(token, payload);
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            assertNotNull(selectOneByField(c2, "bi_building", "name", orderedBuildingName));
            assertNotNull(selectOneByField(c2, "bi_component", "code", orderedComponentCode));
            assertNotNull(selectOneByField(c2, "bi_disease", "description", orderedDiseaseDesc));
            assertNotNull(selectOneByField(c2, "bi_disease_detail", "disease_uuid", orderedDiseaseUuid));
            assertNotNull(selectOneByField(c2, "bi_attachment", "name", orderedAttachmentName));
        }
    }

    @Test
    @Order(2)
    @DisplayName("ClosedLoop-Ordered-02: 顺序编排-再更新（同UUID）")
    void testOrdered02_update_after_insert() throws Exception {
        Assumptions.assumeTrue(orderedBuildingUuid != null && orderedDiseaseUuid != null,
                "顺序编排前置条件不满足：Ordered-01 未成功执行（通常是服务不可达或登录失败）");

        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);

        String updatedOrderedBuildingName = "Ordered更新后的桥梁名称-" + random5();
        String updatedOrderedDiseaseDesc = "Ordered更新后的病害描述-" + random5();

        try (Connection c = open(db)) {
            updateByOfflineUuid(c, "bi_building", orderedBuildingUuid, mapOf(
                    "name", updatedOrderedBuildingName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c, "bi_disease", orderedDiseaseUuid, mapOf(
                    "description", updatedOrderedDiseaseDesc,
                    "quantity", 7,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode uploadRes = upload(token, buildPayloadFromSqlite(db));
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            Map<String, Object> b = selectOneByField(c2, "bi_building", "name", updatedOrderedBuildingName);
            Map<String, Object> d = selectOneByField(c2, "bi_disease", "description", updatedOrderedDiseaseDesc);
            assertNotNull(b);
            assertNotNull(d);
            assertEquals(updatedOrderedBuildingName, stringVal(b.get("name")));
            assertEquals(updatedOrderedDiseaseDesc, stringVal(d.get("description")));
        }
    }

    @Test
    @Order(3)
    @DisplayName("ClosedLoop-Ordered-03: 顺序编排-最后删除（同UUID）")
    void testOrdered03_delete_after_update() throws Exception {
        Assumptions.assumeTrue(orderedDiseaseUuid != null,
                "顺序编排前置条件不满足：Ordered-01/02 未成功执行（通常是服务不可达或登录失败）");

        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);
        String deletedOrderedDiseaseDesc = orderedDiseaseDesc;

        try (Connection c = open(db)) {
            updateByOfflineUuid(c, "bi_disease", orderedDiseaseUuid, mapOf(
                    "is_offline_data", 1,
                    "offline_deleted", 1
            ));
        }

        JsonNode uploadRes = upload(token, buildPayloadFromSqlite(db));
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            assertNull(selectOneByField(c2, "bi_disease", "description", deletedOrderedDiseaseDesc),
                    "顺序编排删除失败：新库中仍存在该病害");
        }
    }

    @Test
    @DisplayName("ClosedLoop-01: 新增闭环（building/object/component/disease/detail/attachment）")
    void test01_insert_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path baseDb = downloadUserSqlite(token, USER_ID);

        String rootUuid = uuid("e2e-root-");
        String childUuid = uuid("e2e-child-");
        String compUuid = uuid("e2e-comp-");
        String disUuid = uuid("e2e-dis-");
        String detailUuid = uuid("e2e-det-");
        String attUuid = uuid("e2e-att-");

        String insertBuildingName = "E2E新增桥梁-" + random5();
        String insertRootName = "根节点-" + random5();
        String insertChildName = "子节点-" + random5();
        String insertCompName = "E2E构件-" + random5();
        String insertCompCode = "E2E-C-" + random5();
        String insertDiseaseDesc = "E2E新增病害-" + random5();
        String insertAttachmentName = "e2e_insert_" + random5() + ".jpg";

        String bUuid;
        try (Connection c = open(baseDb)) {
            bUuid = firstOfflineUuid(c, "bi_building");
            assertNotNull(bUuid, "测试前置失败：bi_building 无可用 offline_uuid");

            updateByOfflineUuid(c, "bi_building", bUuid, mapOf(
                    "name", insertBuildingName,
                    "root_object_uuid", rootUuid,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", rootUuid,
                    "name", insertRootName,
                    "parent_uuid", "",
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", childUuid,
                    "name", insertChildName,
                    "parent_uuid", rootUuid,
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_component", mapOf(
                    "offline_uuid", compUuid,
                    "name", insertCompName,
                    "code", insertCompCode,
                    "object_uuid", childUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_disease", mapOf(
                    "offline_uuid", disUuid,
                    "building_uuid", bUuid,
                    "object_uuid", childUuid,
                    "component_uuid", compUuid,
                    "description", insertDiseaseDesc,
                    "type", "1",
                    "level", 1,
                    "quantity", 1,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_disease_detail", mapOf(
                    "offline_uuid", detailUuid,
                    "disease_uuid", disUuid,
                    "reference1_location", "跨中",
                    "length1", 1.23,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_attachment", mapOf(
                    "offline_uuid", attUuid,
                    "offline_subject_uuid", disUuid,
                    "name", insertAttachmentName,
                    "type", 1,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        Map<String, Object> payload = buildPayloadFromSqlite(baseDb);
        preUploadAttachments(token, payload);
        JsonNode uploadRes = upload(token, payload);
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            assertNotNull(selectOneByField(c2, "bi_building", "name", insertBuildingName));
            assertNotNull(selectOneByField(c2, "bi_object", "name", insertChildName));
            assertNotNull(selectOneByField(c2, "bi_component", "code", insertCompCode));
            assertNotNull(selectOneByField(c2, "bi_disease", "description", insertDiseaseDesc));
            assertNotNull(selectOneByField(c2, "bi_disease_detail", "disease_uuid", disUuid));
            assertNotNull(selectOneByField(c2, "bi_attachment", "name", insertAttachmentName));
        }
    }

    @Test
    @DisplayName("ClosedLoop-02: 更新闭环（同 offlineUuid 更新名称/数量）")
    void test02_update_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);

        String buildingUuid;
        String diseaseUuid;
        String updatedBuildingName = "E2E更新后的桥梁名称-" + random5();
        String updatedDiseaseDesc = "E2E更新后的病害描述-" + random5();

        try (Connection c = open(db)) {
            buildingUuid = firstOfflineUuid(c, "bi_building");
            diseaseUuid = firstOfflineUuid(c, "bi_disease");
            assertNotNull(buildingUuid, "测试前置失败：bi_building 无可更新记录");
            assertNotNull(diseaseUuid, "测试前置失败：bi_disease 无可更新记录");

            updateByOfflineUuid(c, "bi_building", buildingUuid, mapOf(
                    "name", updatedBuildingName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            updateByOfflineUuid(c, "bi_disease", diseaseUuid, mapOf(
                    "description", updatedDiseaseDesc,
                    "quantity", 9,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode uploadRes = upload(token, buildPayloadFromSqlite(db));
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            Map<String, Object> b = selectOneByField(c2, "bi_building", "name", updatedBuildingName);
            Map<String, Object> d = selectOneByField(c2, "bi_disease", "description", updatedDiseaseDesc);
            assertNotNull(b);
            assertNotNull(d);
            assertEquals(updatedBuildingName, stringVal(b.get("name")));
            assertEquals(updatedDiseaseDesc, stringVal(d.get("description")));
        }
    }

    @Test
    @DisplayName("ClosedLoop-03: 删除闭环（offline_deleted=1，同步后新库不存在）")
    void test03_delete_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);

        String deleteUuid;
        String deletedDiseaseDesc;
        try (Connection c = open(db)) {
            deleteUuid = firstOfflineUuid(c, "bi_disease");
            assertNotNull(deleteUuid, "测试前置失败：bi_disease 无可删除记录");

            Map<String, Object> disease = selectByOfflineUuid(c, "bi_disease", deleteUuid);
            assertNotNull(disease, "测试前置失败：无法找到待删除病害详情");
            deletedDiseaseDesc = stringVal(disease.get("description"));
            assertNotNull(deletedDiseaseDesc, "测试前置失败：待删除病害 description 为空");

            updateByOfflineUuid(c, "bi_disease", deleteUuid, mapOf(
                    "is_offline_data", 1,
                    "offline_deleted", 1
            ));
        }

        JsonNode uploadRes = upload(token, buildPayloadFromSqlite(db));
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            assertNull(selectOneByField(c2, "bi_disease", "description", deletedDiseaseDesc), "删除闭环失败：新库中仍存在该病害");
        }
    }

    @Test
    @DisplayName("ClosedLoop-04: 幂等闭环（同 payload 连续提交 2 次，不重复）")
    void test04_idempotent_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);

        String idempotentBuildingName = "E2E幂等桥梁-" + random5();
        try (Connection c = open(db)) {
            String bUuid = firstOfflineUuid(c, "bi_building");
            assertNotNull(bUuid, "测试前置失败：bi_building 无可用 offline_uuid");
            updateByOfflineUuid(c, "bi_building", bUuid, mapOf(
                    "name", idempotentBuildingName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        Map<String, Object> payload = buildPayloadFromSqlite(db);
        JsonNode r1 = upload(token, payload);
        assertSuccess(r1);

        JsonNode r2 = upload(token, payload);
        assertSuccess(r2);

        Path newDb = downloadSqliteFromUploadResponse(r2);
        try (Connection c2 = open(newDb)) {
            assertNotNull(selectOneByField(c2, "bi_building", "name", idempotentBuildingName));
        }
    }

    @Test
    @DisplayName("ClosedLoop-05: 父子乱序闭环（子节点先于父节点）")
    void test05_parent_out_of_order_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);

        String rootUuid = uuid("e2e-order-root-");
        String childUuid = uuid("e2e-order-child-");
        String outOfOrderBuildingName = "E2E乱序桥梁-" + random5();
        String outOfOrderRootName = "乱序父节点-" + random5();
        String outOfOrderChildName = "乱序子节点-" + random5();

        try (Connection c = open(db)) {
            String bUuid = firstOfflineUuid(c, "bi_building");
            assertNotNull(bUuid, "测试前置失败：bi_building 无可用 offline_uuid");

            updateByOfflineUuid(c, "bi_building", bUuid, mapOf(
                    "name", outOfOrderBuildingName,
                    "root_object_uuid", rootUuid,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", childUuid,
                    "name", outOfOrderChildName,
                    "parent_uuid", rootUuid,
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", rootUuid,
                    "name", outOfOrderRootName,
                    "parent_uuid", "",
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode uploadRes = upload(token, buildPayloadFromSqlite(db));
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            Map<String, Object> child = selectOneByField(c2, "bi_object", "name", outOfOrderChildName);
            Map<String, Object> root = selectOneByField(c2, "bi_object", "name", outOfOrderRootName);
            assertNotNull(child);
            assertNotNull(root);
            assertEquals(stringVal(root.get("offline_uuid")), stringVal(child.get("parent_uuid")));
        }
    }

    @Test
    @DisplayName("ClosedLoop-06: 附件闭环（预上传成功并关联 disease）")
    void test06_attachment_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path db = downloadUserSqlite(token, USER_ID);

        String dUuid = uuid("e2e-att-d-");
        String attUuid = uuid("e2e-att-");
        String attachmentDiseaseDesc = "附件闭环病害-" + random5();
        String attachmentName = "e2e_attachment_only_" + random5() + ".jpg";

        try (Connection c = open(db)) {
            // 选取用户 SQLite 中已存在的一条 component（优先），确保 disease 绑定到用户可见 building
            String componentUuid = firstOfflineUuid(c, "bi_component");
            assertNotNull(componentUuid, "测试前置失败：bi_component 无可用记录，无法构造附件闭环关联");

            Map<String, Object> comp = selectByOfflineUuid(c, "bi_component", componentUuid);
            assertNotNull(comp, "测试前置失败：无法通过 offline_uuid 获取 component 详情");
            String objectUuid = stringVal(comp.get("object_uuid"));
            assertNotNull(objectUuid, "测试前置失败：component.object_uuid 为空");

            Map<String, Object> obj = selectByOfflineUuid(c, "bi_object", objectUuid);
            assertNotNull(obj, "测试前置失败：无法通过 object_uuid 获取 object 详情");
            String buildingUuid = stringVal(obj.get("building_uuid"));
            assertNotNull(buildingUuid, "测试前置失败：object.building_uuid 为空");

            // 确保关联链条实体进入本次离线增量，服务端可建立 uuidMap
            updateByOfflineUuid(c, "bi_building", buildingUuid, mapOf(
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c, "bi_object", objectUuid, mapOf(
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c, "bi_component", componentUuid, mapOf(
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_disease", mapOf(
                    "offline_uuid", dUuid,
                    "building_uuid", buildingUuid,
                    "object_uuid", objectUuid,
                    "component_uuid", componentUuid,
                    "description", attachmentDiseaseDesc,
                    "type", "1",
                    "level", 1,
                    "quantity", 1,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_attachment", mapOf(
                    "offline_uuid", attUuid,
                    "offline_subject_uuid", dUuid,
                    "name", attachmentName,
                    "type", 1,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        Map<String, Object> payload = buildPayloadFromSqlite(db);
        preUploadAttachments(token, payload);

        JsonNode uploadRes = upload(token, payload);
        assertSuccess(uploadRes);

        Path newDb = downloadSqliteFromUploadResponse(uploadRes);
        try (Connection c2 = open(newDb)) {
            Map<String, Object> att = selectOneByField(c2, "bi_attachment", "name", attachmentName);
            Map<String, Object> disease = selectOneByField(c2, "bi_disease", "description", attachmentDiseaseDesc);
            assertNotNull(att);
            assertNotNull(disease);
            assertEquals(stringVal(disease.get("offline_uuid")), stringVal(att.get("offline_subject_uuid")));
            if (att.containsKey("minio_id")) {
                assertNotNull(att.get("minio_id"));
            }
        }
    }

    @Test
    @DisplayName("ClosedLoop-07: Object叶子更新三次上传验证（仅叶子/叶子+父/叶子+全父链）")
    void test07_object_leaf_update_requires_parent_chain_closed_loop() throws Exception {
        String token = loginAndGetToken();
        Path baseDb = downloadUserSqlite(token, USER_ID);

        String rootUuid = uuid("e2e-chain-root-");
        String l2Uuid = uuid("e2e-chain-l2-");
        String leafUuid = uuid("e2e-chain-leaf-");

        String buildingName = "链路桥梁-" + random5();
        String rootName = "链路根节点-" + random5();
        String l2Name = "链路二级节点-" + random5();
        String leafName = "链路叶子节点-" + random5();

        // phase0: 先创建完整 building + 3级object 链路，建立服务端基线数据
        String bUuid;
        try (Connection c = open(baseDb)) {
            bUuid = firstOfflineUuid(c, "bi_building");
            assertNotNull(bUuid, "测试前置失败：bi_building 无可用 offline_uuid");

            updateByOfflineUuid(c, "bi_building", bUuid, mapOf(
                    "name", buildingName,
                    "root_object_uuid", rootUuid,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", rootUuid,
                    "name", rootName,
                    "parent_uuid", "",
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", l2Uuid,
                    "name", l2Name,
                    "parent_uuid", rootUuid,
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));

            insertRow(c, "bi_object", mapOf(
                    "offline_uuid", leafUuid,
                    "name", leafName,
                    "parent_uuid", l2Uuid,
                    "building_uuid", bUuid,
                    "status", "0",
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode initRes = upload(token, buildPayloadFromSqlite(baseDb));
        assertSuccess(initRes);

        Path db1 = downloadSqliteFromUploadResponse(initRes);

        // phase1: 仅更新叶子节点
        String leafOnlyName = "仅叶子更新-" + random5();
        try (Connection c1 = open(db1)) {
            updateByOfflineUuid(c1, "bi_object", leafUuid, mapOf(
                    "name", leafOnlyName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode res1 = upload(token, buildPayloadFromSqlite(db1));
        assertSuccess(res1);

        Path dbAfter1 = downloadSqliteFromUploadResponse(res1);
        try (Connection c1v = open(dbAfter1)) {
            assertNull(selectOneByField(c1v, "bi_object", "name", leafOnlyName),
                    "仅上传叶子节点时，按当前后端逻辑不应成功更新");
        }

        // phase2: 更新叶子 + 直接父节点（不带根节点和building）
        String parentAndLeafParentName = "叶子父更新-父-" + random5();
        String parentAndLeafLeafName = "叶子父更新-叶-" + random5();
        try (Connection c2 = open(dbAfter1)) {
            updateByOfflineUuid(c2, "bi_object", l2Uuid, mapOf(
                    "name", parentAndLeafParentName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c2, "bi_object", leafUuid, mapOf(
                    "name", parentAndLeafLeafName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode res2 = upload(token, buildPayloadFromSqlite(dbAfter1));
        assertSuccess(res2);

        Path dbAfter2 = downloadSqliteFromUploadResponse(res2);
        try (Connection c2v = open(dbAfter2)) {
            assertNull(selectOneByField(c2v, "bi_object", "name", parentAndLeafParentName),
                    "仅上传叶子+直接父节点时，按当前后端逻辑不应成功更新");
            assertNull(selectOneByField(c2v, "bi_object", "name", parentAndLeafLeafName),
                    "仅上传叶子+直接父节点时，叶子也不应成功更新");
        }

        // phase3: 更新叶子 + 全父链object（根节点、直接父节点、叶子），但不带building
        String objectChainOnlyRootName = "仅对象全链更新-根-" + random5();
        String objectChainOnlyL2Name = "仅对象全链更新-二级-" + random5();
        String objectChainOnlyLeafName = "仅对象全链更新-叶-" + random5();

        try (Connection c3 = open(dbAfter2)) {
            updateByOfflineUuid(c3, "bi_object", rootUuid, mapOf(
                    "name", objectChainOnlyRootName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c3, "bi_object", l2Uuid, mapOf(
                    "name", objectChainOnlyL2Name,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c3, "bi_object", leafUuid, mapOf(
                    "name", objectChainOnlyLeafName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode res3 = upload(token, buildPayloadFromSqlite(dbAfter2));
        assertSuccess(res3);

        Path dbAfter3 = downloadSqliteFromUploadResponse(res3);
        try (Connection c3v = open(dbAfter3)) {
            assertNotNull(selectOneByField(c3v, "bi_object", "name", objectChainOnlyRootName),
                    "仅上传object全链但不带building时，按当前后端逻辑应成功更新root");
            assertNotNull(selectOneByField(c3v, "bi_object", "name", objectChainOnlyL2Name),
                    "仅上传object全链但不带building时，按当前后端逻辑应成功更新二级");
            assertNotNull(selectOneByField(c3v, "bi_object", "name", objectChainOnlyLeafName),
                    "仅上传object全链但不带building时，按当前后端逻辑应成功更新叶子");
        }

        // phase4: 更新叶子 + 全父链（含building、根节点、直接父节点）
        String fullChainBuildingName = "全链更新-桥-" + random5();
        String fullChainRootName = "全链更新-根-" + random5();
        String fullChainL2Name = "全链更新-二级-" + random5();
        String fullChainLeafName = "全链更新-叶-" + random5();

        try (Connection c4 = open(dbAfter3)) {
            updateByOfflineUuid(c4, "bi_building", bUuid, mapOf(
                    "name", fullChainBuildingName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c4, "bi_object", rootUuid, mapOf(
                    "name", fullChainRootName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c4, "bi_object", l2Uuid, mapOf(
                    "name", fullChainL2Name,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
            updateByOfflineUuid(c4, "bi_object", leafUuid, mapOf(
                    "name", fullChainLeafName,
                    "is_offline_data", 1,
                    "offline_deleted", 0
            ));
        }

        JsonNode res4 = upload(token, buildPayloadFromSqlite(dbAfter3));
        assertSuccess(res4);

        Path dbAfter4 = downloadSqliteFromUploadResponse(res4);
        try (Connection c4v = open(dbAfter4)) {
            assertNotNull(selectOneByField(c4v, "bi_building", "name", fullChainBuildingName),
                    "全链路上传后 building 应更新成功");
            assertNotNull(selectOneByField(c4v, "bi_object", "name", fullChainRootName),
                    "全链路上传后 root object 应更新成功");
            assertNotNull(selectOneByField(c4v, "bi_object", "name", fullChainL2Name),
                    "全链路上传后二级 object 应更新成功");
            assertNotNull(selectOneByField(c4v, "bi_object", "name", fullChainLeafName),
                    "全链路上传后叶子 object 应更新成功");
        }
    }

    private boolean isServerReachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/jwt/login"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            // /jwt/login 预期是 POST，GET 返回 4xx 也代表服务可达
            return resp.statusCode() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String loginAndGetToken() throws Exception {
        String body = "username=" + USERNAME + "&password=" + PASSWORD;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/jwt/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "登录失败: " + resp.body());
        JsonNode json = MAPPER.readTree(resp.body());
        JsonNode token = json.get("token");
        assertNotNull(token, "登录返回缺少token: " + resp.body());
        return token.asText();
    }

    private Path downloadUserSqlite(String token, Long userId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v2/user/" + userId + "/sqlite"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", token)
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "获取用户SQLite地址失败: " + resp.body());

        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode data = root.get("data");
        assertNotNull(data, "响应缺少 data: " + resp.body());
        String dbUrl = data.path("url").asText();
        assertFalse(dbUrl.isBlank(), "SQLite下载URL为空: " + resp.body());

        return downloadFile(dbUrl, "seed-");
    }

    private JsonNode upload(String token, Map<String, Object> payload) throws Exception {
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("clientInfo", "SyncSqliteClosedLoopE2ETest/UploadOnly");

        String json = MAPPER.writeValueAsString(payload);
        System.out.println("[E2E upload json] " + json);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v2/sync/upload"))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("[E2E upload response] " + resp.body());
        assertEquals(200, resp.statusCode(), "upload http失败: " + resp.body());
        return MAPPER.readTree(resp.body());
    }

    private void preUploadAttachments(String token, Map<String, Object> payload) throws Exception {
        List<Map<String, Object>> atts = castListMap(payload.get("attachments"));
        for (Map<String, Object> att : atts) {
            Object minioId = att.get("minioId");
            if (minioId != null && !String.valueOf(minioId).isBlank()) {
                continue;
            }
            String fileName = Optional.ofNullable(att.get("name")).map(String::valueOf).orElse("e2e.jpg");
            Long serverId = uploadAttachment(token, fileName);
            att.put("minioId", serverId);
        }
    }

    private Long uploadAttachment(String token, String filename) throws Exception {
        String boundary = "----JavaE2EBoundary" + UUID.randomUUID();
        byte[] fileBytes = "dummy-image-content".getBytes();

        String part1 = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n";
        String part2 = "\r\n--" + boundary + "--\r\n";

        byte[] body = concat(part1.getBytes(), fileBytes, part2.getBytes());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v2/sync/attachment"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "附件预上传失败: " + resp.body());
        JsonNode json = MAPPER.readTree(resp.body());
        int code = json.path("code").asInt(-1);
        assertTrue(code == 0 || code == 200, "附件预上传业务失败: " + resp.body());
        return json.path("data").asLong();
    }

    private Path downloadSqliteFromUploadResponse(JsonNode uploadRes) throws Exception {
        String url = uploadRes.path("data").path("url").asText();
        assertFalse(url.isBlank(), "upload 返回缺少 sqlite url: " + uploadRes);
        return downloadFile(url, "closed-loop-");
    }

    private Path downloadFile(String url, String prefix) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, resp.statusCode(), "下载文件失败: " + url);

        Path file = Files.createTempFile(prefix, ".db");
        try (InputStream in = resp.body()) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    private void assertSuccess(JsonNode res) {
        assertNotNull(res);
        assertTrue(res.has("code"), "响应缺少code: " + res);
        int code = res.path("code").asInt(-1);
        String msg = res.path("msg").asText("");
        assertTrue(code == 0 || code == 200, "同步失败, code=" + code + ", msg=" + msg + ", res=" + res);
    }

    private Connection open(Path dbFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    private Map<String, Object> buildPayloadFromSqlite(Path dbFile) throws Exception {
        try (Connection c = open(dbFile)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("buildings", rowsByOffline(c, "bi_building"));
            payload.put("objects", rowsByOffline(c, "bi_object"));
            payload.put("components", rowsByOffline(c, "bi_component"));
            payload.put("diseases", rowsByOffline(c, "bi_disease"));
            payload.put("diseaseDetails", rowsByOffline(c, "bi_disease_detail"));
            payload.put("attachments", rowsByOffline(c, "bi_attachment"));
            payload.put("biObjectComponents", rowsByOffline(c, "bi_object_component"));
            return payload;
        }
    }

    private List<Map<String, Object>> rowsByOffline(Connection c, String table) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "is_offline_data")) {
            return new ArrayList<>();
        }
        String sql = "SELECT * FROM " + table + " WHERE is_offline_data = 1";
        List<Map<String, Object>> out = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String col = md.getColumnName(i);
                    Object val = rs.getObject(i);
                    item.put(snakeToCamel(col), val);
                }
                sanitizeForUpload(table, item);
                out.add(item);
            }
        }
        return out;
    }

    private void insertRow(Connection c, String table, Map<String, Object> row) throws SQLException {
        if (!tableExists(c, table)) return;

        Set<String> cols = columnsOf(c, table);
        LinkedHashMap<String, Object> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (cols.contains(e.getKey())) {
                effective.put(e.getKey(), e.getValue());
            }
        }
        if (effective.isEmpty()) return;

        String colSql = String.join(", ", effective.keySet());
        String qSql = String.join(", ", Collections.nCopies(effective.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + colSql + ") VALUES (" + qSql + ")";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            for (Object v : effective.values()) {
                ps.setObject(idx++, v);
            }
            ps.executeUpdate();
        }
    }

    private void updateByOfflineUuid(Connection c, String table, String offlineUuid, Map<String, Object> changes) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "offline_uuid")) return;
        Set<String> cols = columnsOf(c, table);

        List<String> setCols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            if (cols.contains(e.getKey())) {
                setCols.add(e.getKey() + " = ?");
                vals.add(e.getValue());
            }
        }
        if (setCols.isEmpty()) return;

        String sql = "UPDATE " + table + " SET " + String.join(", ", setCols) + " WHERE offline_uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : vals) ps.setObject(i++, v);
            ps.setObject(i, offlineUuid);
            ps.executeUpdate();
        }
    }

    private String firstOfflineUuid(Connection c, String table) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "offline_uuid")) return null;
        String sql = "SELECT offline_uuid FROM " + table + " WHERE offline_uuid IS NOT NULL AND offline_uuid <> '' LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private Map<String, Object> selectByOfflineUuid(Connection c, String table, String offlineUuid) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "offline_uuid")) return null;
        String sql = "SELECT * FROM " + table + " WHERE offline_uuid = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, offlineUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ResultSetMetaData md = rs.getMetaData();
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    map.put(md.getColumnName(i), rs.getObject(i));
                }
                return map;
            }
        }
    }

    private Map<String, Object> selectOneByField(Connection c, String table, String field, Object value) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, field)) return null;
        String sql = "SELECT * FROM " + table + " WHERE " + field + " = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ResultSetMetaData md = rs.getMetaData();
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    map.put(md.getColumnName(i), rs.getObject(i));
                }
                return map;
            }
        }
    }

    private int countByOfflineUuid(Connection c, String table, String offlineUuid) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "offline_uuid")) return 0;
        String sql = "SELECT COUNT(1) FROM " + table + " WHERE offline_uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, offlineUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private boolean tableExists(Connection c, String tableName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean columnExists(Connection c, String tableName, String col) throws SQLException {
        return columnsOf(c, tableName).contains(col);
    }

    private Set<String> columnsOf(Connection c, String tableName) throws SQLException {
        Set<String> cols = new HashSet<>();
        if (!tableExists(c, tableName)) return cols;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        return cols;
    }

    private void sanitizeForUpload(String table, Map<String, Object> item) {
        // 关键修复：避免把本地 SQLite 的主键 id 带到服务端新增分支，导致主键冲突
        item.remove("id");

        // 以下字段由服务端维护或不适合作为离线上传输入，统一剔除，避免干扰
        item.remove("createBy");
        item.remove("createTime");
        item.remove("updateBy");
        item.remove("updateTime");

        // 可选字段：若为空字符串，移除避免触发后端不必要分支
        Object offlineDeleted = item.get("offlineDeleted");
        if (offlineDeleted == null || "".equals(String.valueOf(offlineDeleted).trim())) {
            item.put("offlineDeleted", 0);
        }
    }

    private static String snakeToCamel(String name) {
        if (name == null || !name.contains("_")) return name;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : name.toCharArray()) {
            if (ch == '_') {
                up = true;
                continue;
            }
            sb.append(up ? Character.toUpperCase(ch) : ch);
            up = false;
        }
        return sb.toString();
    }

    private static String uuid(String prefix) {
        return prefix + UUID.randomUUID();
    }

    private static String random5() {
        int n = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        return String.format("%05d", n);
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] arr : arrays) len += arr.length;
        byte[] out = new byte[len];
        int p = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, out, p, arr.length);
            p += arr.length;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListMap(Object value) {
        if (value == null) return new ArrayList<>();
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private void debugPrintUploadPayload(String label, Map<String, Object> payload) throws Exception {
        System.out.println("[E2E payload " + label + "]");
        System.out.println("  buildings=" + castListMap(payload.get("buildings")).size());
        System.out.println("  objects=" + castListMap(payload.get("objects")).size());
        System.out.println("  components=" + castListMap(payload.get("components")).size());
        System.out.println("  diseases=" + castListMap(payload.get("diseases")).size());
        System.out.println("  diseaseDetails=" + castListMap(payload.get("diseaseDetails")).size());
        System.out.println("  attachments=" + castListMap(payload.get("attachments")).size());
        System.out.println("  biObjectComponents=" + castListMap(payload.get("biObjectComponents")).size());
        System.out.println("[E2E payload " + label + " detail] " + MAPPER.writeValueAsString(payload));
    }
}
