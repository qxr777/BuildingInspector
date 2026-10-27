package edu.whut.cs.bi.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessBridgeResult;
import edu.whut.cs.bi.biz.domain.AssessElementResult;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessUnitResult;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.SpanElementPart;
import edu.whut.cs.bi.biz.domain.SpanEvaluation;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.domain.vo.UploadItemResultVo;
import edu.whut.cs.bi.biz.mapper.AssessAbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.AssessBridgeResultMapper;
import edu.whut.cs.bi.biz.mapper.AssessElementResultMapper;
import edu.whut.cs.bi.biz.mapper.AssessManualInputMapper;
import edu.whut.cs.bi.biz.mapper.AssessUnitResultMapper;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.mapper.SpanEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import edu.whut.cs.bi.biz.service.std.evaluation.Assess5230CalculateService;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App 现场离线作业全链路 E2E（JTG/T 5230—2026）：
 * <ol>
 *   <li>云端先行（等价 Web 端建桥与任务下发，发生在下载离线包之前）：ROOT 根节点 +
 *       2 个评定单元(B01)、挂接新树根的桥梁、5230 评定任务、EDI 病害类型全部在云端建好；</li>
 *   <li>模拟 App 已下载的 user.db：bi_building / bi_task / ROOT+UNIT 的 bi_object
 *       均为服务端下发行（带 server_id、is_offline_data=0），现场不新增桥梁、不改 bi_building；</li>
 *   <li>现场离线作业只增补结构节点：在下发的 UNIT 下新增 2 个 SPAN（每单元，单元1第1跨
 *       再挂上部结构 LAYER 与板 PART；其余三跨为空跨），乱序写入本地 SQLite；</li>
 *   <li>第一批 sync/upload（JWT + MockMvc，走真实控制器/十二段管线，buildings 为空数组）；
 *       现场新增节点的父链指向云端既存 UNIT，由管线 UUID 反查云端回查兜底落库；</li>
 *   <li>App 在构件页继续录入：element + 锚定 + 病害(EDI 标度2) + 缺损详情，
 *       拍照走真实预上传（MinIO 原图+缩略图）后回填附件元数据，
 *       并录入人工判定（EFI=1）与应设未设标记（幻影支座，作用于单元1第1跨）；</li>
 *   <li>第二批 sync/upload；</li>
 *   <li>后端内部触发 calculate（App 不调用），断言桥跨/单元/全桥四级结果与重算幂等。</li>
 * </ol>
 *
 * <p>期望（手算）：单元1第1跨 = 100 − 3×100×0.40/5(slab，ω′重分配100)
 * − 3×25×0.40/5(幻影支座) = 70.0/3类；单元1第2跨空跨 100/1类 →
 * 式3.4.3 跨间插值 BCI=72.5/3类；单元2两空跨 100/1类；
 * 3.4.4 最差单元 → 全桥 WORST_UNIT 3类/72.5。</p>
 *
 * @Tag("E2E") 手工执行，需 docker MySQL 3307(bi_test，含第八·十/十一/十二部分 DDL)
 * + MinIO 9000、znjc_test_1/123456 账号。@Transactional 回滚 MySQL，
 * MinIO 原图/缩略图对象真实保留（与既有附件 E2E 一致）。
 */
@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000",
        "minio.endpoint=http://localhost:9000",
        "spring.datasource.druid.web-stat-filter.enabled=false",
        "spring.datasource.druid.webStatFilter.enabled=false",
        "spring.datasource.druid.stat-view-servlet.enabled=false",
        "spring.datasource.druid.statViewServlet.enabled=false"
})
@Tag("E2E")
@Transactional
public class AppOfflineWorkflowE2ETest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApiControllerV2 apiControllerV2;

    /** Spy：generateUserSqliteSync 打桩（离线包重打非本测试关注点），其余（含建表 DDL）走真实方法。 */
    @SpyBean
    private SqliteService sqliteService;

    @Autowired
    private Assess5230CalculateService calculateService;
    @Autowired
    private BuildingMapper buildingMapper;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private DiseaseTypeMapper diseaseTypeMapper;
    @Autowired
    private ElementMapper elementMapper;
    @Autowired
    private SpanElementPartMapper spanElementPartMapper;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private DiseaseDetailMapper diseaseDetailMapper;
    @Autowired
    private AttachmentMapper attachmentMapper;
    @Autowired
    private FileMapMapper fileMapMapper;
    @Autowired
    private AssessManualInputMapper manualInputMapper;
    @Autowired
    private AssessAbsentMarkMapper absentMarkMapper;
    @Autowired
    private AssessElementResultMapper elementResultMapper;
    @Autowired
    private SpanEvaluationMapper spanEvaluationMapper;
    @Autowired
    private AssessUnitResultMapper unitResultMapper;
    @Autowired
    private AssessBridgeResultMapper bridgeResultMapper;
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;

    private Path appDb;

    @BeforeEach
    void stubSqliteRegen() throws Exception {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://localhost/mock-app-offline-user.db");
        vo.setTimestamp(new Date());
        vo.setSize("1MB");
        doReturn(vo).when(sqliteService).generateUserSqliteSync(anyLong());

        appDb = Files.createTempFile("app-offline-", ".db");
        try (Connection conn = open(appDb)) {
            // 直接复用生产 DDL（13 张用户包表），保证测试库结构与 App 端 user.db 一致
            ReflectionTestUtils.invokeMethod(sqliteService, "createAllTables", conn);
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(appDb);
    }

    @Test
    @DisplayName("E2E-APP-001: 离线包已有ROOT/UNIT，现场增补Span/Part→构件页录入→拍照→同步→后端5230评定")
    void testFieldAddSpansParts_captureDisease_sync_thenBackendAssessment() throws Exception {
        String p = "app001-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        String bUuid = p + "building";
        String rootUuid = p + "root";
        String u1 = p + "unit1";
        String u2 = p + "unit2";
        String u1s1 = p + "u1span1";
        String u1s2 = p + "u1span2";
        String u2s1 = p + "u2span1";
        String u2s2 = p + "u2span2";
        String layerUuid = p + "layer";
        String slabUuid = p + "slab";

        // ---------- 阶段0：云端先行（等价 Web 建桥+任务下发，发生在离线包下载之前） ----------
        BiObject rootNode = new BiObject();
        rootNode.setName("全桥根节点");
        rootNode.setParentId(0L);
        rootNode.setNodeType("ROOT");
        rootNode.setOfflineUuid(rootUuid);
        rootNode.setStatus("0");
        biObjectMapper.insertBiObject(rootNode);

        BiObject u1Node = newBiUnit(u1, rootNode.getId(), "评定单元一");
        BiObject u2Node = newBiUnit(u2, rootNode.getId(), "评定单元二");

        Building building = new Building();
        building.setName("App现场检查桥");
        building.setIsLeaf("1");
        building.setOfflineUuid(bUuid);
        building.setRootObjectId(rootNode.getId());
        building.setNewRootObjectId(rootNode.getId()); // 新树根在云端建桥时已挂好
        buildingMapper.insertBuilding(building);

        DiseaseType ediType = new DiseaseType();
        ediType.setCode("APPE2E-EDI-" + p);
        ediType.setName("App全链路结构性缺损");
        ediType.setMaxScale(3);
        ediType.setStdVersion("5230-2026");
        ediType.setScaleType("EDI");
        diseaseTypeMapper.insertDiseaseType(ediType);

        Task task = new Task();
        task.setBuildingId(building.getId());
        task.setStdVersion("5230-2026");
        taskMapper.insertTask(task);
        Long taskId = task.getId();

        // ---------- 阶段1：模拟 App 已下载的 user.db（服务端下发行：带 server_id、is_offline_data=0） ----------
        try (Connection c = open(appDb)) {
            insert(c, "bi_building", mapOf(
                    "id", building.getId(), "offline_uuid", bUuid, "name", building.getName(),
                    "is_leaf", "1", "status", "0", "root_object_id", rootNode.getId(),
                    "new_root_object_id", rootNode.getId(), "offline_deleted", 0));
            insert(c, "bi_task", mapOf("id", taskId, "building_id", building.getId(),
                    "std_version", "5230-2026", "status", "0"));
            insert(c, "bi_object", downloadedObjectRow(rootUuid, "", rootNode.getId(),
                    "ROOT", "全桥根节点", null, null));
            insert(c, "bi_object", downloadedObjectRow(u1, rootUuid, u1Node.getId(),
                    "UNIT", "评定单元一", "B01", null));
            insert(c, "bi_object", downloadedObjectRow(u2, rootUuid, u2Node.getId(),
                    "UNIT", "评定单元二", "B01", null));
        }

        // ---------- 阶段2：现场离线作业——只在下发的 UNIT 下增补 SPAN/LAYER/PART，不碰 bi_building ----------
        try (Connection c = open(appDb)) {
            // 故意乱序插入（PART/SPAN 先于同批 LAYER 之外的节点），模拟 App 任意时刻建节点 + 服务端多轮父链解析
            insert(c, "bi_object", objectRow(slabUuid, layerUuid, "PART", "实心板", null, null,
                    "{\"part\":\"slab\"}", 75, null));
            insert(c, "bi_object", objectRow(u1s1, u1, "SPAN", "第1跨", "B01", 1, null, null, null));
            insert(c, "bi_object", objectRow(u1s2, u1, "SPAN", "第2跨", "B01", 2, null, null, null));
            insert(c, "bi_object", objectRow(u2s1, u2, "SPAN", "第1跨", "B01", 1, null, null, null));
            insert(c, "bi_object", objectRow(u2s2, u2, "SPAN", "第2跨", "B01", 2, null, null, null));
            insert(c, "bi_object", objectRow(layerUuid, u1s1, "LAYER", "上部结构", null, null,
                    "{\"layer\":\"superstructure\"}", null, new BigDecimal("0.40")));
        }

        // ---------- 阶段3：第一次同步（仅结构增补；buildings 恒为空数组，云端既存父节点走 UUID 回查） ----------
        JsonNode res1 = upload(token(), extractPayloadFromAppDb());
        assertEquals(0, res1.path("code").asInt(-1), "第一批同步失败: " + res1);

        BiObject layerNode = biObjectMapper.selectByOfflineUuid(layerUuid);
        BiObject slabNode = biObjectMapper.selectByOfflineUuid(slabUuid);
        BiObject u1s1Node = biObjectMapper.selectByOfflineUuid(u1s1);
        assertNotNull(slabNode);
        assertNotNull(u1s1Node);
        assertEquals(u1Node.getId(), u1s1Node.getParentId(), "现场新增跨应挂到下发的评定单元一");
        assertEquals(u1s1Node.getId(), layerNode.getParentId(), "现场新增结构层应挂到第1跨");
        assertEquals(layerNode.getId(), slabNode.getParentId(), "现场新增板部件应挂到上部结构层");

        // 离线不更新 bi_building：两批同步前后桥梁行（名称/旧根/新根）保持云端建桥时的状态
        Building untouched = buildingMapper.selectByOfflineUuid(bUuid);
        assertEquals(building.getName(), untouched.getName());
        assertEquals(rootNode.getId(), untouched.getRootObjectId());
        assertEquals(rootNode.getId(), untouched.getNewRootObjectId());

        // ---------- 阶段4：构件 Element 页录入 + 拍照（真实预上传 MinIO） ----------
        String elemUuid = p + "element";
        String anchorUuid = p + "anchor";
        String disUuid = p + "disease";
        String detUuid = p + "detail";
        String attUuid = p + "photo";
        String uploadUuid = UUID.randomUUID().toString().replace("-", "");

        MockMultipartFile photo = jpeg("crack-" + p + ".jpg");
        AjaxResult uploaded = apiControllerV2.uploadSyncAttachment(photo, uploadUuid);
        assertEquals(0, uploaded.get(AjaxResult.CODE_TAG),
                () -> "病害照片预上传失败: " + uploaded.get(AjaxResult.MSG_TAG));
        UploadItemResultVo photoItem = (UploadItemResultVo) uploaded.get(AjaxResult.DATA_TAG);
        assertNotNull(photoItem.getMinioId());
        assertNotNull(photoItem.getThumbMinioId(), "服务端应生成缩略图");

        try (Connection c = open(appDb)) {
            insert(c, "bi_element", mapOf(
                    "offline_uuid", elemUuid, "code", "EL-APP-1", "name", "1号板",
                    "object_uuid", slabUuid, "status", "0",
                    "is_offline_data", 1, "offline_deleted", 0));
            insert(c, "bi_span_element_part", mapOf(
                    "offline_uuid", anchorUuid, "element_uuid", elemUuid,
                    "span_uuid", u1s1, "part_uuid", slabUuid, "is_shared", 0,
                    "is_offline_data", 1, "offline_deleted", 0));
            insert(c, "bi_disease", mapOf(
                    "offline_uuid", disUuid, "task_id", taskId,
                    "object_uuid", slabUuid, "element_uuid", elemUuid,
                    "disease_type_id", ediType.getId(), "type", "结构性裂缝",
                    "description", "App现场记录：板底横向裂缝", "level", 2,
                    "quantity", "1", "participate_assess", "1", "attachment_count", 0,
                    "is_offline_data", 1, "offline_deleted", 0));
            insert(c, "bi_disease_detail", mapOf(
                    "offline_uuid", detUuid, "disease_uuid", disUuid,
                    "length1", 1.2, "width", 0.25,
                    "is_offline_data", 1, "offline_deleted", 0));
            insert(c, "bi_attachment", mapOf(
                    "offline_uuid", attUuid, "name", "crack-" + p + ".jpg", "type", 1,
                    "minio_id", photoItem.getMinioId(),
                    "thumb_minio_id", photoItem.getThumbMinioId(),
                    "offline_subject_uuid", disUuid,
                    "is_offline_data", 1, "offline_deleted", 0));
            // 构件级人工判定：EFI=1（缺损发展变化状况显著）
            insert(c, "bi_assess_manual_input", mapOf(
                    "offline_uuid", p + "manual", "task_id", taskId,
                    "element_uuid", elemUuid, "efi", 1,
                    "is_offline_data", 1, "offline_deleted", 0));
            // 应设未设：上部结构支座缺失（幻影件），作用范围单元1第1跨
            insert(c, "bi_assess_absent_mark", mapOf(
                    "offline_uuid", p + "absent", "task_id", taskId,
                    "unit_uuid", u1, "span_uuid", u1s1,
                    "part_key", "bearing", "layer_key", "superstructure",
                    "is_offline_data", 1, "offline_deleted", 0));
        }

        // ---------- 阶段5：第二次同步（构件/病害/详情/照片/人工判定） ----------
        JsonNode res2 = upload(token(), extractPayloadFromAppDb());
        assertEquals(0, res2.path("code").asInt(-1), "第二批同步失败: " + res2);

        // 两批同步均不携带 buildings：桥梁行保持云端建桥时状态，无任何回写
        Building stillUntouched = buildingMapper.selectByOfflineUuid(bUuid);
        assertEquals(building.getName(), stillUntouched.getName());
        assertEquals(rootNode.getId(), stillUntouched.getRootObjectId());
        assertEquals(rootNode.getId(), stillUntouched.getNewRootObjectId());

        Element savedElement = elementMapper.selectByOfflineUuid(elemUuid);
        assertNotNull(savedElement);
        SpanElementPart savedAnchor = spanElementPartMapper.selectByOfflineUuid(anchorUuid);
        assertNotNull(savedAnchor);
        assertEquals(savedElement.getId(), savedAnchor.getElementId());
        assertEquals(u1s1Node.getId(), savedAnchor.getSpanId());
        assertEquals(slabNode.getId(), savedAnchor.getPartId());

        Disease savedDisease = diseaseMapper.selectByOfflineUuid(disUuid);
        assertNotNull(savedDisease);
        assertEquals(taskId, savedDisease.getTaskId());
        assertEquals(elemUuid, savedDisease.getElementUuid());
        assertEquals("1", savedDisease.getParticipateAssess());
        DiseaseDetail savedDetail = diseaseDetailMapper.selectByOfflineUuid(detUuid);
        assertNotNull(savedDetail);
        assertEquals(savedDisease.getId(), savedDetail.getDiseaseId());
        assertEquals(0, new BigDecimal("0.25").compareTo(savedDetail.getWidth()));

        AssessManualInput savedManual = manualInputMapper.selectByOfflineUuid(p + "manual");
        assertNotNull(savedManual);
        assertEquals(taskId, savedManual.getTaskId());
        assertEquals(savedElement.getId(), savedManual.getElementId());
        assertEquals(1, savedManual.getEfi());
        AssessAbsentMark savedAbsent = absentMarkMapper.selectByOfflineUuid(p + "absent");
        assertNotNull(savedAbsent);
        assertEquals(u1s1Node.getId(), savedAbsent.getSpanId());
        assertEquals(u1Node.getId(), savedAbsent.getUnitId());
        assertEquals("bearing", savedAbsent.getPartKey());

        Attachment savedPhoto = attachmentMapper.selectByOfflineUuid(attUuid);
        assertNotNull(savedPhoto);
        assertEquals(savedDisease.getId(), savedPhoto.getSubjectId());
        assertEquals(photoItem.getMinioId(), savedPhoto.getMinioId());
        assertEquals(photoItem.getThumbMinioId(), savedPhoto.getThumbMinioId());
        assertEquals(1, diseaseMapper.selectByOfflineUuid(disUuid).getAttachmentCount(),
                "病害照片落库后 attachment_count 应重算为 1");
        assertMinioObjectExists(photoItem.getMinioId());
        assertMinioObjectExists(photoItem.getThumbMinioId());

        // ---------- 阶段6：后端内部触发 5230 评定（App 不调 calculate） ----------
        Map<String, Object> calc = calculateService.calculate(taskId, "backend-system");
        assertEquals(2, calc.get("unitCount"));
        assertEquals(4, calc.get("spanCount"));
        assertEquals(2, calc.get("elementRowCount"), "真实 slab 构件 + 幻影支座各一行");

        // 桥跨级：单元1第1跨 70.0/3类，其余三空跨 100.0/1类
        List<SpanEvaluation> spans = spanEvaluationMapper.selectByTaskId(taskId);
        assertEquals(4, spans.size());
        SpanEvaluation damagedSpan = spans.stream()
                .filter(s -> u1s1Node.getId().equals(s.getSpanId())).findFirst().orElseThrow();
        assertEquals("B01", damagedSpan.getBridgeType());
        assertEquals(0, new BigDecimal("70.0").compareTo(damagedSpan.getBsci()));
        assertEquals(3, damagedSpan.getSpanLevel());
        assertEquals(0, damagedSpan.getOverrideFlag());
        assertEquals(3, spans.stream().filter(s -> s.getSpanLevel() == 1
                && s.getBsci().compareTo(new BigDecimal("100.0")) == 0).count(),
                "其余三跨应为健康 100.0/1类");

        // 单元级：单元1 跨间插值 72.5/3类；单元2 等级一致 100/1类
        List<AssessUnitResult> units = unitResultMapper.selectByTaskId(taskId);
        assertEquals(2, units.size());
        AssessUnitResult unit1 = units.stream()
                .filter(u -> u1Node.getId().equals(u.getUnitId())).findFirst().orElseThrow();
        assertEquals("INTERP", unit1.getCombineMode());
        assertEquals(3, unit1.getBcl());
        assertEquals(0, new BigDecimal("72.5").compareTo(unit1.getBci()));
        assertEquals(2, unit1.getSpanCount());
        assertEquals(0, new BigDecimal("85.0").compareTo(unit1.getBsciAvg()));
        AssessUnitResult unit2 = units.stream()
                .filter(u -> !u1Node.getId().equals(u.getUnitId())).findFirst().orElseThrow();
        assertEquals("EQUAL", unit2.getCombineMode());
        assertEquals(1, unit2.getBcl());
        assertEquals(0, new BigDecimal("100.0").compareTo(unit2.getBci()));

        // 全桥：3.4.4 最差单元直通
        AssessBridgeResult bridge = bridgeResultMapper.selectByTaskId(taskId);
        assertNotNull(bridge);
        assertEquals("WORST_UNIT", bridge.getCombineMode());
        assertEquals(u1Node.getId(), bridge.getWorstUnitId());
        assertEquals(rootNode.getId(), bridge.getRootObjectId());
        assertEquals(3, bridge.getBcl());
        assertEquals(0, new BigDecimal("72.5").compareTo(bridge.getBci()));
        assertEquals(3, bridge.getLayeredBcl());
        assertEquals(0, new BigDecimal("72.5").compareTo(bridge.getLayeredBci()));
        assertNull(bridge.getSingleControlHits());

        // 构件级结果：slab ESDI=2(自动)/EDI=2/EFI=1/ECI=3/ECL3；幻影支座 ABSENT/EDI=3
        List<AssessElementResult> elementRows = elementResultMapper.selectByTaskId(taskId);
        assertEquals(2, elementRows.size());
        AssessElementResult slabRow = elementRows.stream()
                .filter(r -> slabNode.getId().equals(r.getPartId())).findFirst().orElseThrow();
        assertEquals("slab", slabRow.getPartKey());
        assertEquals("superstructure", slabRow.getLayerKey());
        assertEquals(elemUuid, slabRow.getElementUuid());
        assertEquals(2, slabRow.getEsdi());
        assertEquals(2, slabRow.getEdi());
        assertEquals(1, slabRow.getEfi());
        assertEquals(3, slabRow.getEci());
        assertEquals(3, slabRow.getEcl());
        assertEquals(1, slabRow.getIsMain());
        assertEquals(0, slabRow.getAbsentFlag());
        assertEquals("AUTO", slabRow.getEsdiSource());
        AssessElementResult phantom = elementRows.stream()
                .filter(r -> r.getAbsentFlag() == 1).findFirst().orElseThrow();
        assertEquals("bearing", phantom.getPartKey());
        assertNull(phantom.getPartId());
        assertEquals(3, phantom.getEdi());
        assertEquals(3, phantom.getEci());
        assertEquals(3, phantom.getEcl());
        assertEquals("ABSENT", phantom.getEsdiSource());

        // 结果/输入回查（App 展示评级只读调用的同一装配）
        Map<String, Object> resultView = calculateService.getResult(taskId);
        assertNotNull(resultView.get("bridge"));
        assertEquals(2, ((List<?>) resultView.get("units")).size());
        assertEquals(4, ((List<?>) resultView.get("spans")).size());
        assertEquals(2, ((List<?>) resultView.get("elements")).size());
        Map<String, Object> inputs = calculateService.getInputs(taskId);
        assertEquals(1, ((List<?>) inputs.get("manualInputs")).size());
        assertEquals(1, ((List<?>) inputs.get("absentMarks")).size());

        // 重算幂等：四表先删后插，行数不翻倍，结果不变
        Map<String, Object> recalc = calculateService.calculate(taskId, "backend-system");
        assertEquals(2, recalc.get("elementRowCount"));
        assertEquals(4, spanEvaluationMapper.selectByTaskId(taskId).size());
        assertEquals(2, unitResultMapper.selectByTaskId(taskId).size());
        assertNotNull(bridgeResultMapper.selectByTaskId(taskId));
        assertEquals(0, new BigDecimal("72.5")
                .compareTo(bridgeResultMapper.selectByTaskId(taskId).getBci()));
    }

    // ---------- App 端模拟辅助 ----------

    /** 云端建桥时创建的评定单元（ROOT 下挂 B01 单元）。 */
    private BiObject newBiUnit(String uuid, Long rootId, String name) {
        BiObject unit = new BiObject();
        unit.setName(name);
        unit.setParentId(rootId);
        unit.setNodeType("UNIT");
        unit.setBridgeType("B01");
        unit.setOfflineUuid(uuid);
        unit.setStatus("0");
        biObjectMapper.insertBiObject(unit);
        return unit;
    }

    /**
     * 离线包下发行形态（对应 SqliteService.insertBiObjects）：
     * 带 server_id 与父 uuid、is_offline_data=0，App 视为服务端数据不回传。
     */
    private Map<String, Object> downloadedObjectRow(String uuid, String parentUuid, Long serverId,
                                                    String nodeType, String name,
                                                    String bridgeType, Integer spanNo) {
        Map<String, Object> m = mapOf(
                "offline_uuid", uuid, "server_id", serverId, "parent_uuid", parentUuid,
                "name", name, "node_type", nodeType, "status", "0",
                "is_offline_data", 0, "offline_deleted", 0);
        if (bridgeType != null) m.put("bridge_type", bridgeType);
        if (spanNo != null) m.put("span_no", spanNo);
        return m;
    }

    private Map<String, Object> objectRow(String uuid, String parent, String nodeType, String name,
                                          String bridgeType, Integer spanNo, String propsJson,
                                          Integer omega, BigDecimal gamma) {
        Map<String, Object> m = mapOf(
                "offline_uuid", uuid, "parent_uuid", parent == null ? "" : parent,
                "name", name, "node_type", nodeType, "status", "0",
                "is_offline_data", 1, "offline_deleted", 0);
        if (bridgeType != null) m.put("bridge_type", bridgeType);
        if (spanNo != null) m.put("span_no", spanNo);
        if (propsJson != null) m.put("props_json", propsJson);
        if (omega != null) m.put("omega", omega);
        if (gamma != null) m.put("gamma", gamma);
        return m;
    }

    /** 从 App 本地 user.db 抽取 is_offline_data=1 增量行，按 sync/upload 十二段键名组装。 */
    private Map<String, Object> extractPayloadFromAppDb() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        try (Connection c = open(appDb)) {
            // 桥梁由云端建桥时创建并随包下发：现场不新增/修改桥梁，buildings 恒为空数组
            // （本地 bi_building 行是带 server_id 的下发行，App 不会回传）
            payload.put("buildings", new ArrayList<>());
            payload.put("objects", extract(c, "bi_object"));
            // components 为冻结旧链：v1.6 App 新功能不产生该键
            payload.put("elements", extract(c, "bi_element"));
            payload.put("spanElementParts", extract(c, "bi_span_element_part"));
            payload.put("diseases", extract(c, "bi_disease"));
            payload.put("diseaseDetails", extract(c, "bi_disease_detail"));
            payload.put("attachments", extract(c, "bi_attachment"));
            payload.put("assessManualInputs", extract(c, "bi_assess_manual_input"));
            payload.put("assessAbsentMarks", extract(c, "bi_assess_absent_mark"));
            payload.put("assessSingleControlMarks", extract(c, "bi_assess_single_control_mark"));
        }
        return payload;
    }

    private List<Map<String, Object>> extract(Connection c, String table) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!tableExists(c, table)) return rows;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM " + table + " WHERE is_offline_data = 1")) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    item.put(snakeToCamel(md.getColumnName(i)), rs.getObject(i));
                }
                // 本地主键/审计列不带入载荷（与 ClosedLoop 夹具同一教训：避免 id 误入新增分支）
                item.remove("id");
                item.remove("serverId");
                item.remove("createBy");
                item.remove("createTime");
                item.remove("updateBy");
                item.remove("updateTime");
                rows.add(item);
            }
        }
        return rows;
    }

    private String token() throws Exception {
        MvcResult login = mockMvc.perform(post("/jwt/login")
                        .param("username", "znjc_test_1")
                        .param("password", "123456"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(
                login.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode tokenNode = json.get("token");
        assertNotNull(tokenNode, "登录返回缺少token: " + json);
        return tokenNode.asText();
    }

    private JsonNode upload(String token, Map<String, Object> payload) throws Exception {
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("clientInfo", "AppOfflineWorkflowE2ETest/1.0");
        payload.put("taskId", null); // 键保留模拟 App，taskId 以各行业务列为准
        MvcResult result = mockMvc.perform(post("/api/v2/sync/upload")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void assertMinioObjectExists(Long fileMapId) throws Exception {
        FileMap fm = fileMapMapper.selectFileMapById(fileMapId);
        assertNotNull(fm, "bi_file_map 行应存在: " + fileMapId);
        String key = fm.getNewName().substring(0, 2) + "/" + fm.getNewName();
        minioClient.statObject(StatObjectArgs.builder()
                .bucket(minioConfig.getBucketName()).object(key).build());
    }

    private MockMultipartFile jpeg(String name) throws Exception {
        BufferedImage img = new BufferedImage(1280, 960, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("file", name, "image/jpeg", out.toByteArray());
    }

    private Connection open(Path db) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    private void insert(Connection c, String table, Map<String, Object> row) throws SQLException {
        Set<String> cols = columnsOf(c, table);
        Map<String, Object> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (cols.contains(e.getKey())) effective.put(e.getKey(), e.getValue());
        }
        if (effective.isEmpty()) return;
        String sql = "INSERT INTO " + table + " (" + String.join(", ", effective.keySet())
                + ") VALUES (" + String.join(", ", Collections.nCopies(effective.size(), "?")) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : effective.values()) ps.setObject(i++, v);
            ps.executeUpdate();
        }
    }

    private boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Set<String> columnsOf(Connection c, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) cols.add(rs.getString("name"));
        }
        return cols;
    }

    private static String snakeToCamel(String name) {
        if (name == null || !name.contains("_")) return name;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : name.toCharArray()) {
            if (ch == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return sb.toString();
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
