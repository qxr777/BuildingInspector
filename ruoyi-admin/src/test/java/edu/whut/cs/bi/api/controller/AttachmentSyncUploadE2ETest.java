package edu.whut.cs.bi.api.controller;

import com.ruoyi.RuoYiApplication;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
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
 * 通道A 病害/桥梁/部件照片同步 E2E：
 * App 先调 /api/v2/sync/attachment 预上传拿 minioId，再在 sync/upload 的 attachments[] 提交。
 * 覆盖：类型路由 + 云端回查、offline_uuid 幂等 upsert、offlineDeleted 物理删除、
 * minioId/主体缺失逐行报错、bi_disease.attachment_count 重算。
 *
 * @Tag("E2E") 手工执行，需已跑 test_env_all_in_one.sql（bi_attachment.uk_attachment_offline_uuid）。
 */
@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000"
})
@Tag("E2E")
@Transactional
public class AttachmentSyncUploadE2ETest {

    @Autowired
    private ISyncUploadService syncUploadService;
    @Autowired
    private AttachmentMapper attachmentMapper;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private BuildingMapper buildingMapper;

    private static Map<String, Object> diseaseItem(String uuid) {
        Map<String, Object> m = new HashMap<>();
        m.put("offlineUuid", uuid);
        m.put("type", "裂缝");
        m.put("description", "附件通道测试病害");
        m.put("level", 1);
        m.put("quantity", 1);
        m.put("offlineDeleted", 0);
        return m;
    }

    private static Map<String, Object> attachmentItem(String uuid, String subjectUuid, Integer type,
            Long minioId, int deleted) {
        Map<String, Object> m = new HashMap<>();
        m.put("offlineUuid", uuid);
        m.put("offlineSubjectUuid", subjectUuid);
        m.put("name", uuid + ".jpg");
        m.put("type", type);
        m.put("minioId", minioId);
        m.put("offlineDeleted", deleted);
        return m;
    }

    private Map<String, Object> payloadWithDisease(String diseaseUuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("diseases", List.of(diseaseItem(diseaseUuid)));
        payload.put("attachments", new ArrayList<>());
        return payload;
    }

    /** 单 UNIT + 单个 PART 子节点（PART 当前照片挂载用）。 */
    private List<Map<String, Object>> unitWithPart(String unit, String part) {
        Map<String, Object> u = new HashMap<>();
        u.put("offlineUuid", unit);
        u.put("parentUuid", "0");
        u.put("nodeType", "UNIT");
        u.put("name", "附件测试评定单元");
        u.put("status", "0");
        u.put("offlineDeleted", 0);
        Map<String, Object> p = new HashMap<>();
        p.put("offlineUuid", part);
        p.put("parentUuid", unit);
        p.put("nodeType", "PART");
        p.put("name", "钢主梁");
        p.put("status", "0");
        p.put("offlineDeleted", 0);
        return List.of(p, u); // 乱序提交，验证多轮父链解析
    }

    @Test
    @DisplayName("E2E-ATT-001: 病害照片落库+count重算，同UUID重传走更新不重复")
    public void testDiseasePhoto_UpsertAndRecount() {
        String dis = "att1-dis-" + UUID.randomUUID();
        String att = "att1-att-" + UUID.randomUUID();

        Map<String, Object> payload = payloadWithDisease(dis);
        payload.put("attachments", List.of(attachmentItem(att, dis, 1, 910001L, 0)));
        SyncResultVo first = syncUploadService.syncUpload(payload);
        assertTrue(first.getErrors().isEmpty(), () -> "首次: " + first.getErrors());

        Disease saved = diseaseMapper.selectByOfflineUuid(dis);
        assertNotNull(saved);
        Attachment photo = attachmentMapper.selectByOfflineUuid(att);
        assertNotNull(photo);
        assertEquals(saved.getId(), photo.getSubjectId());
        assertEquals(1, attachmentMapper.selectBySubjectId(saved.getId()).size());
        assertNotNull(saved.getAttachmentCount());
        assertEquals(1, saved.getAttachmentCount(), "attachment_count 应按实际行数重算为1");

        // 同 UUID 重传（换文件/改名）→ 更新分支，仍只有一行，count 不翻倍
        Map<String, Object> again = payloadWithDisease(dis);
        again.put("attachments", List.of(attachmentItem(att, dis, 1, 910002L, 0)));
        SyncResultVo second = syncUploadService.syncUpload(again);
        assertTrue(second.getErrors().isEmpty(), () -> "重传: " + second.getErrors());

        Attachment updated = attachmentMapper.selectByOfflineUuid(att);
        assertEquals(910002L, updated.getMinioId());
        assertEquals(1, attachmentMapper.selectBySubjectId(saved.getId()).size());
        assertEquals(1, diseaseMapper.selectByOfflineUuid(dis).getAttachmentCount());
    }

    @Test
    @DisplayName("E2E-ATT-002: offlineDeleted=1 物理删除既有照片，count 归零；删除不存在记录幂等无错")
    public void testDelete_PhysicalAndIdempotent() {
        String dis = "att2-dis-" + UUID.randomUUID();
        String att = "att2-att-" + UUID.randomUUID();

        Map<String, Object> payload = payloadWithDisease(dis);
        payload.put("attachments", List.of(attachmentItem(att, dis, 1, 920001L, 0)));
        assertTrue(syncUploadService.syncUpload(payload).getErrors().isEmpty());
        Long diseaseId = diseaseMapper.selectByOfflineUuid(dis).getId();
        assertEquals(1, attachmentMapper.selectBySubjectId(diseaseId).size());

        Map<String, Object> del = new HashMap<>();
        del.put("syncUuid", UUID.randomUUID().toString());
        del.put("attachments", List.of(
                attachmentItem(att, dis, 1, null, 1),
                attachmentItem("att2-ghost-" + UUID.randomUUID(), dis, 1, null, 1)));
        SyncResultVo result = syncUploadService.syncUpload(del);
        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());

        assertNull(attachmentMapper.selectByOfflineUuid(att), "附件应为物理删除");
        assertEquals(0, attachmentMapper.selectBySubjectId(diseaseId).size());
        assertEquals(0, diseaseMapper.selectByOfflineUuid(dis).getAttachmentCount());
    }

    @Test
    @DisplayName("E2E-ATT-003: 缺minioId/主体不存在逐行报错且不落库，同批合法照片不受影响")
    public void testInvalidRows_ReportedAndSkipped() {
        String dis = "att3-dis-" + UUID.randomUUID();
        String good = "att3-good-" + UUID.randomUUID();
        String noMinio = "att3-nominio-" + UUID.randomUUID();
        String orphan = "att3-orphan-" + UUID.randomUUID();

        Map<String, Object> payload = payloadWithDisease(dis);
        List<Map<String, Object>> atts = new ArrayList<>();
        atts.add(attachmentItem(good, dis, 1, 930001L, 0));
        atts.add(attachmentItem(noMinio, dis, 1, null, 0));
        atts.add(attachmentItem(orphan, "att3-ghost-subject-" + UUID.randomUUID(), 1, 930003L, 0));
        payload.put("attachments", atts);

        SyncResultVo result = syncUploadService.syncUpload(payload);
        assertEquals(2, result.getFailureCount(), "应有2行错误: " + result.getErrors());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "Attachment".equals(e.getEntityType()) && noMinio.equals(e.getOfflineUuid())));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "Attachment".equals(e.getEntityType()) && orphan.equals(e.getOfflineUuid())));

        assertNotNull(attachmentMapper.selectByOfflineUuid(good), "合法照片应正常落库");
        assertNull(attachmentMapper.selectByOfflineUuid(noMinio));
        assertNull(attachmentMapper.selectByOfflineUuid(orphan));
        assertEquals(1, diseaseMapper.selectByOfflineUuid(dis).getAttachmentCount());
    }

    @Test
    @DisplayName("E2E-ATT-004: type=6挂桥梁/type=8挂PART，按类型路由且不写病害count")
    public void testTypeRouting_BuildingAndPart() {
        String bld = "att4-bld-" + UUID.randomUUID();
        String unit = "att4-unit-" + UUID.randomUUID();
        String part = "att4-part-" + UUID.randomUUID();
        String front = "att4-front-" + UUID.randomUUID();
        String partPhoto = "att4-partphoto-" + UUID.randomUUID();

        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", bld);
        building.put("name", "附件测试桥");
        building.put("isLeaf", "1");
        building.put("status", "0");
        building.put("offlineDeleted", 0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("buildings", List.of(building));
        payload.put("objects", unitWithPart(unit, part));
        payload.put("attachments", List.of(
                attachmentItem(front, bld, 6, 940001L, 0),
                attachmentItem(partPhoto, part, 8, 940002L, 0)));

        SyncResultVo result = syncUploadService.syncUpload(payload);
        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());

        Long buildingId = buildingMapper.selectByOfflineUuid(bld).getId();
        Long partId = biObjectMapper.selectByOfflineUuid(part).getId();

        Attachment facade = attachmentMapper.selectByOfflineUuid(front);
        assertEquals(buildingId, facade.getSubjectId(), "type=6 应挂到桥梁");
        Attachment current = attachmentMapper.selectByOfflineUuid(partPhoto);
        assertEquals(partId, current.getSubjectId(), "type=8 应挂到PART节点");
    }

    @Test
    @DisplayName("E2E-ATT-005: 主体不在本批（此前同步的病害），附件靠云端回查挂接")
    public void testCloudFallback_SubjectFromPreviousSync() {
        String dis = "att5-dis-" + UUID.randomUUID();
        assertTrue(syncUploadService.syncUpload(payloadWithDisease(dis)).getErrors().isEmpty());

        // 第二次同步只有附件，uuidMap 是每批新建的，必须回查云端
        String att = "att5-att-" + UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("attachments", List.of(attachmentItem(att, dis, 1, 950001L, 0)));
        SyncResultVo result = syncUploadService.syncUpload(payload);
        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());

        Long diseaseId = diseaseMapper.selectByOfflineUuid(dis).getId();
        Attachment photo = attachmentMapper.selectByOfflineUuid(att);
        assertNotNull(photo);
        assertEquals(diseaseId, photo.getSubjectId());
        assertEquals(1, diseaseMapper.selectByOfflineUuid(dis).getAttachmentCount());
    }
}
