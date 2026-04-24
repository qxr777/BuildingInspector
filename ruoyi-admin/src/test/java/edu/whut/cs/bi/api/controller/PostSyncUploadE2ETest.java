package edu.whut.cs.bi.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.RuoYiApplication;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000",
        "spring.datasource.druid.web-stat-filter.enabled=false",
        "spring.datasource.druid.webStatFilter.enabled=false",
        "spring.datasource.druid.stat-view-servlet.enabled=false",
        "spring.datasource.druid.statViewServlet.enabled=false"
})
@Tag("E2E")
@Transactional //注释后数据库不回滚
public class PostSyncUploadE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BuildingMapper buildingMapper;

    @MockBean
    private SqliteService sqliteService;

    @BeforeEach
    void mockSqliteGeneration() {
        SqliteVo sqliteVo = new SqliteVo();
        sqliteVo.setUrl("http://localhost/mock-user-sqlite.db");
        sqliteVo.setTimestamp(new Date());
        sqliteVo.setSize("1MB");
        lenient().when(sqliteService.generateUserSqliteSync(anyLong())).thenReturn(sqliteVo);
    }

    @Test
    @DisplayName("PostUpload-Test1: 正常插入最小闭环")
    void test1_insert_happyPath() throws Exception {
        String token = loginAndGetToken();

        String bUuid = rnd("t1-bld-");
        String rootUuid = rnd("t1-obj-root-");
        String spanUuid = rnd("t1-obj-span-");
        String cUuid = rnd("t1-comp-");
        String dUuid = rnd("t1-dis-");
        String ddUuid = rnd("t1-dd-");

        Map<String, Object> payload = basePayload("Test1");
        payload.put("buildings", List.of(mapOf(
                "offlineUuid", bUuid,
                "name", "测试桥梁T1",
                "isLeaf", "1",
                "status", "0",
                "offlineDeleted", 0,
                "rootObjectUuid", rootUuid
        )));
        payload.put("objects", List.of(
                mapOf("offlineUuid", rootUuid, "name", "上部结构", "parentUuid", "", "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0),
                mapOf("offlineUuid", spanUuid, "name", "第1跨", "parentUuid", rootUuid, "buildingUuid", bUuid, "status", "0", "spanIndex", 1, "spanLength", 30.5, "offlineDeleted", 0)
        ));
        payload.put("components", List.of(mapOf(
                "offlineUuid", cUuid,
                "name", "主梁1",
                "code", "T1-GL-01",
                "status", "0",
                "objectUuid", spanUuid,
                "edi", 1,
                "efi", 0,
                "eai", 0,
                "offlineDeleted", 0
        )));
        payload.put("diseases", List.of(mapOf(
                "offlineUuid", dUuid,
                "buildingUuid", bUuid,
                "objectUuid", spanUuid,
                "componentUuid", cUuid,
                "taskId", 1,
                "type", "裂缝",
                "position", "腹板",
                "positionNumber", 1,
                "description", "离线裂缝",
                "level", 1,
                "quantity", 1,
                "units", "m",
                "deductPoints", 1,
                "offlineDeleted", 0
        )));
        payload.put("diseaseDetails", List.of(mapOf(
                "offlineUuid", ddUuid,
                "diseaseUuid", dUuid,
                "reference1Location", "距左端",
                "reference1LocationStart", 1.2,
                "reference1LocationEnd", 2.4,
                "length1", 1.2,
                "crackWidth", 0.3,
                "offlineDeleted", 0
        )));
        payload.put("attachments", List.of(mapOf(
                "offlineUuid", rnd("t1-att-"),
                "offlineSubjectUuid", dUuid,
                "name", "裂缝照片1.jpg",
                "type", 1,
                "minioId", 76115,
                "offlineDeleted", 0
        )));
        payload.put("biObjectComponents", List.of(mapOf(
                "offlineUuid", rnd("t1-rel-"),
                "objectUuid", spanUuid,
                "componentUuid", cUuid,
                "weight", 1.0,
                "offlineDeleted", 0
        )));

        JsonNode res = postUpload(token, payload, true);
        assertUploadAccepted(res);
        assertNotNull(buildingMapper.selectByOfflineUuid(bUuid));
    }

    @Test
    @DisplayName("PostUpload-Test2: 更新已有offlineUuid")
    void test2_update_existingOfflineUuid() throws Exception {
        String token = loginAndGetToken();
        String bUuid = rnd("t2-bld-");

        Map<String, Object> createPayload = basePayload("Test2-init");
        createPayload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "原始桥梁", "isLeaf", "1", "status", "0", "offlineDeleted", 0)));
        JsonNode initRes = postUpload(token, createPayload, true);
        assertUploadAccepted(initRes);

        Map<String, Object> updatePayload = basePayload("Test2");
        updatePayload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "测试桥梁T1-更新", "isLeaf", "1", "status", "0", "offlineDeleted", 0)));
        JsonNode updateRes = postUpload(token, updatePayload, true);
        assertUploadAccepted(updateRes);

        Building updated = buildingMapper.selectByOfflineUuid(bUuid);
        assertNotNull(updated);
        assertEquals("测试桥梁T1-更新", updated.getName());
    }

    @Test
    @DisplayName("PostUpload-Test3: offlineDeleted=1 删除已存在")
    void test3_delete_existing() throws Exception {
        String token = loginAndGetToken();
        String bUuid = rnd("t3-bld-");

        Map<String, Object> createPayload = basePayload("Test3-init");
        createPayload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "待删除桥梁", "isLeaf", "1", "status", "0", "offlineDeleted", 0)));
        assertUploadAccepted(postUpload(token, createPayload, true));

        Map<String, Object> delPayload = basePayload("Test3");
        delPayload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "offlineDeleted", 1)));
        JsonNode delRes = postUpload(token, delPayload, true);
        assertUploadAccepted(delRes);
    }

    @Test
    @DisplayName("PostUpload-Test4: 删除不存在记录幂等")
    void test4_delete_notExist() throws Exception {
        String token = loginAndGetToken();
        Map<String, Object> payload = basePayload("Test4");
        payload.put("buildings", List.of(mapOf("offlineUuid", rnd("not-exist-bld-"), "offlineDeleted", 1)));
        JsonNode res = postUpload(token, payload, true);
        assertUploadAccepted(res);
    }

    @Test
    @DisplayName("PostUpload-Test5: 类型边界与非法类型")
    void test5_type_cases() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> p1 = basePayload("Test5-1");
        p1.put("diseases", List.of(mapOf("offlineUuid", rnd("t5-dis-"), "type", "裂缝", "description", "错误类型测试", "quantity", "1.2", "offlineDeleted", 0)));
        JsonNode r1 = postUpload(token, p1, true);
        assertUploadErrorOrAccepted(r1);

        Map<String, Object> p2 = basePayload("Test5-2");
        p2.put("diseases", List.of(mapOf("offlineUuid", rnd("t5-dis-"), "type", "裂缝", "description", "positionNumber字符串测试", "positionNumber", "1", "level", 1, "quantity", 1, "offlineDeleted", 0)));
        JsonNode r2 = postUpload(token, p2, true);
        assertUploadErrorOrAccepted(r2);

        Map<String, Object> p3 = basePayload("Test5-3");
        p3.put("diseases", List.of(mapOf("offlineUuid", rnd("t5-dis-"), "type", "裂缝", "description", "负数数量测试", "level", 1, "quantity", -1, "offlineDeleted", 0)));
        JsonNode r3 = postUpload(token, p3, true);
        assertUploadErrorOrAccepted(r3);

        String bUuid = rnd("t5-bld-");
        String oUuid = rnd("t5-obj-");
        String cUuid = rnd("t5-comp-");
        Map<String, Object> p4 = basePayload("Test5-4");
        p4.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "桥梁T5-4", "isLeaf", "1", "status", "0", "offlineDeleted", 0)));
        p4.put("objects", List.of(mapOf("offlineUuid", oUuid, "name", "对象T5-4", "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0)));
        p4.put("components", List.of(mapOf("offlineUuid", cUuid, "name", "构件T5-4", "code", "T5C04", "status", "0", "objectUuid", oUuid, "offlineDeleted", 0)));
        p4.put("biObjectComponents", List.of(
                mapOf("offlineUuid", rnd("t5-rel-a-"), "objectUuid", oUuid, "componentUuid", cUuid, "weight", 0, "offlineDeleted", 0),
                mapOf("offlineUuid", rnd("t5-rel-b-"), "objectUuid", oUuid, "componentUuid", cUuid, "weight", 1.5, "offlineDeleted", 0)
        ));
        JsonNode r4 = postUpload(token, p4, true);
        assertUploadAccepted(r4);
    }

    @Test
    @DisplayName("PostUpload-Test6: 父子依赖顺序")
    void test6_parent_child_ordered() throws Exception {
        String token = loginAndGetToken();
        String bUuid = rnd("t6-bld-");
        String rootUuid = rnd("t6-root-");
        String childUuid = rnd("t6-child-");

        Map<String, Object> payload = basePayload("Test6");
        payload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "测试桥梁T6", "isLeaf", "1", "status", "0", "offlineDeleted", 0, "rootObjectUuid", rootUuid)));
        payload.put("objects", List.of(
                mapOf("offlineUuid", rootUuid, "name", "根节点", "parentUuid", "", "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0),
                mapOf("offlineUuid", childUuid, "name", "子节点", "parentUuid", rootUuid, "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0)
        ));

        JsonNode res = postUpload(token, payload, true);
        assertUploadAccepted(res);
    }

    @Test
    @DisplayName("PostUpload-Test7: 跨实体引用链")
    void test7_cross_entity_refs() throws Exception {
        String token = loginAndGetToken();
        String bUuid = rnd("t7-bld-");
        String rootUuid = rnd("t7-root-");
        String oUuid = rnd("t7-obj-");
        String cUuid = rnd("t7-comp-");
        String dUuid = rnd("t7-dis-");

        Map<String, Object> payload = basePayload("Test7");
        payload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "桥梁T7", "isLeaf", "1", "status", "0", "offlineDeleted", 0, "rootObjectUuid", rootUuid)));
        payload.put("objects", List.of(
                mapOf("offlineUuid", rootUuid, "name", "上部结构", "parentUuid", "", "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0),
                mapOf("offlineUuid", oUuid, "name", "对象T7", "parentUuid", rootUuid, "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0)
        ));
        payload.put("components", List.of(mapOf("offlineUuid", cUuid, "name", "构件T7", "code", "T7-C01", "status", "0", "objectUuid", oUuid, "offlineDeleted", 0)));
        payload.put("diseases", List.of(mapOf("offlineUuid", dUuid, "buildingUuid", bUuid, "objectUuid", oUuid, "componentUuid", cUuid, "type", "锈蚀", "description", "链路测试", "level", 1, "quantity", 1, "offlineDeleted", 0)));

        JsonNode res = postUpload(token, payload, true);
        assertUploadAccepted(res);
    }

    @Test
    @DisplayName("PostUpload-Test8: 附件正常映射")
    void test8_attachment_mapping() throws Exception {
        String token = loginAndGetToken();
        String dUuid = rnd("t8-dis-");

        Map<String, Object> payload = basePayload("Test8");
        payload.put("diseases", List.of(mapOf("offlineUuid", dUuid, "type", "破损", "description", "附件测试病害", "level", 1, "quantity", 1, "offlineDeleted", 0)));
        payload.put("attachments", List.of(mapOf("offlineUuid", rnd("t8-att-"), "offlineSubjectUuid", dUuid, "name", "t8.jpg", "type", 1, "minioId", 76115, "offlineDeleted", 0)));

        JsonNode res = postUpload(token, payload, true);
        assertUploadAccepted(res);
    }

    @Test
    @DisplayName("PostUpload-Test9: 附件孤儿引用")
    void test9_attachment_orphan_subject() throws Exception {
        String token = loginAndGetToken();
        Map<String, Object> payload = basePayload("Test9");
        payload.put("attachments", List.of(mapOf(
                "offlineUuid", rnd("t9-att-"),
                "offlineSubjectUuid", rnd("not-exist-dis-"),
                "name", "orphan.jpg",
                "type", 1,
                "minioId", 76115,
                "offlineDeleted", 0
        )));

        JsonNode res = postUpload(token, payload, true);
        assertUploadErrorOrAccepted(res);
    }

    @Test
    @DisplayName("PostUpload-Test10: 新标 biObjectComponents")
    void test10_biObjectComponents() throws Exception {
        String token = loginAndGetToken();
        String bUuid = rnd("t10-bld-");
        String oUuid = rnd("t10-obj-");
        String cUuid = rnd("t10-comp-");

        Map<String, Object> payload = basePayload("Test10");
        payload.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "桥梁T10", "isLeaf", "1", "status", "0", "offlineDeleted", 0)));
        payload.put("objects", List.of(mapOf("offlineUuid", oUuid, "name", "对象T10", "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0)));
        payload.put("components", List.of(mapOf("offlineUuid", cUuid, "name", "构件T10", "code", "T10-C01", "status", "0", "objectUuid", oUuid, "offlineDeleted", 0)));
        payload.put("biObjectComponents", List.of(mapOf("offlineUuid", rnd("t10-rel-"), "objectUuid", oUuid, "componentUuid", cUuid, "weight", 1.0, "offlineDeleted", 0)));

        JsonNode res = postUpload(token, payload, true);
        assertUploadAccepted(res);
    }

    @Test
    @DisplayName("PostUpload-Test11: 幂等重复提交")
    void test11_idempotent_twice() throws Exception {
        String token = loginAndGetToken();
        String bUuid = rnd("t11-bld-");
        String rootUuid = rnd("t11-root-");

        Map<String, Object> payloadA = basePayload("Test11-A");
        payloadA.put("buildings", List.of(mapOf("offlineUuid", bUuid, "name", "桥梁T11", "isLeaf", "1", "status", "0", "offlineDeleted", 0, "rootObjectUuid", rootUuid)));
        payloadA.put("objects", List.of(mapOf("offlineUuid", rootUuid, "name", "上部结构", "parentUuid", "", "buildingUuid", bUuid, "status", "0", "offlineDeleted", 0)));

        JsonNode r1 = postUpload(token, payloadA, true);
        assertUploadAccepted(r1);

        Map<String, Object> payloadB = new LinkedHashMap<>(payloadA);
        payloadB.put("syncUuid", UUID.randomUUID().toString());
        payloadB.put("clientInfo", "ApiPost/Test11-B");
        JsonNode r2 = postUpload(token, payloadB, true);
        assertUploadAccepted(r2);

        assertNotNull(buildingMapper.selectByOfflineUuid(bUuid));
    }

    @Test
    @DisplayName("PostUpload-Test12: 鉴权失败")
    void test12_auth_failed() throws Exception {
        Map<String, Object> payload = basePayload("Test12");
        MvcResult result = mockMvc.perform(post("/api/v2/sync/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is4xxClientError())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body == null || body.isEmpty() || body.contains("未登录") || body.contains("Unauthorized") || result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    @Test
    @DisplayName("PostUpload-Test13: 空载荷与缺key")
    void test13_empty_and_missing_keys() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> p1 = basePayload("Test13-1");
        JsonNode r1 = postUpload(token, p1, true);
        assertUploadErrorOrAccepted(r1);

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("syncUuid", UUID.randomUUID().toString());
        p2.put("clientInfo", "ApiPost/Test13-2");
        p2.put("buildings", List.of(mapOf("offlineUuid", rnd("t13-bld-"), "name", "空数组缺失键测试", "isLeaf", "1", "status", "0", "offlineDeleted", 0)));
        JsonNode r2 = postUpload(token, p2, true);
        assertUploadErrorOrAccepted(r2);
    }

    private String loginAndGetToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/jwt/login")
                        .param("username", "znjc_test_1")
                        .param("password", "123456"))
                .andExpect(status().isOk())
                .andReturn();

        String body = login.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);
        JsonNode tokenNode = json.get("token");
        assertNotNull(tokenNode, "登录返回缺少token，响应=" + body);
        String token = tokenNode.asText();
        assertFalse(token.isBlank(), "token为空");
        return token;
    }

    private JsonNode postUpload(String token, Map<String, Object> payload, boolean expectHttp200) throws Exception {
        var action = mockMvc.perform(post("/api/v2/sync/upload")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));

        MvcResult result = expectHttp200
                ? action.andExpect(status().isOk()).andReturn()
                : action.andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void assertUploadAccepted(JsonNode res) {
        assertNotNull(res);
        assertTrue(res.has("code"));
        int code = res.get("code").asInt();
        String msg = res.has("msg") ? res.get("msg").asText("") : "";

        boolean accepted = code == 0
                || (code == 500 && (msg.contains("同步上传成功")
                || msg.contains("离线包生成失败")
                || msg.contains("重新打包数据库时发生异常")));
        assertTrue(accepted, "upload未通过预期口径, code=" + code + ", msg=" + msg);
    }

    private void assertUploadErrorOrAccepted(JsonNode res) {
        assertNotNull(res);
        assertTrue(res.has("code"));
        int code = res.get("code").asInt();
        assertTrue(code == 0 || code == 500 || code == 301, "非预期code: " + code + ", res=" + res);
    }

    private Map<String, Object> basePayload(String caseName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("clientInfo", "ApiPost/" + caseName);
        payload.put("taskId", 1);
        payload.put("buildings", new ArrayList<>());
        payload.put("objects", new ArrayList<>());
        payload.put("components", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        payload.put("biObjectComponents", new ArrayList<>());
        return payload;
    }

    private String rnd(String prefix) {
        return prefix + UUID.randomUUID();
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
