package edu.whut.cs.bi.api.controller;

import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.FileUploadRegistry;
import edu.whut.cs.bi.biz.domain.vo.BatchUploadResultVo;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadItemResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadStatusVo;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.FileUploadRegistryMapper;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通道A 批量幂等预上传 E2E（ATT-006~011）：
 * 真实 MinIO putObject/statObject + 真实 MySQL bi_file_upload_registry/bi_file_map。
 *
 * <p>覆盖：批量成功且原图+缩略图双对象落库、同 uploadUuid 重放 exists 不增行、
 * query 已传/未传回查、批内逐项容错、预上传结果闭环 sync/upload 回填 thumb_minio_id、
 * 旧版接口（无 uploadUuid）仍返回裸 Integer 且不写登记表。</p>
 *
 * @Tag("E2E") 手工执行，需 docker MySQL 3307(bi_test，已跑含第八·十部分的 all_in_one) + MinIO 9000。
 * 注意：@Transactional 只回滚 DB，MinIO 对象会真实保留（与既有 E2E 一致）。
 */
@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000",
        "minio.endpoint=http://localhost:9000"
})
@Tag("E2E")
@Transactional
public class BatchAttachmentUploadE2ETest {

    @Autowired
    private ApiControllerV2 controller;
    @Autowired
    private FileUploadRegistryMapper registryMapper;
    @Autowired
    private FileMapMapper fileMapMapper;
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;
    @Autowired
    private ISyncUploadService syncUploadService;
    @Autowired
    private AttachmentMapper attachmentMapper;

    private static String freshUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private MockMultipartFile jpeg(String name) throws Exception {
        BufferedImage img = new BufferedImage(1280, 960, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("files", name, "image/jpeg", out.toByteArray());
    }

    /** MinIO 对象必须真实存在（key 规则：newName 前两位分目录） */
    private void assertObjectExists(Long fileMapId) throws Exception {
        FileMap fm = fileMapMapper.selectFileMapById(fileMapId);
        assertNotNull(fm, "bi_file_map 行应存在: " + fileMapId);
        String key = fm.getNewName().substring(0, 2) + "/" + fm.getNewName();
        minioClient.statObject(StatObjectArgs.builder()
                .bucket(minioConfig.getBucketName()).object(key).build());
    }

    /** 旧版接口内部直接调 ShiroUtils.getLoginName()，测试线程绑定一个最小 Subject */
    private void bindShiroSubject() {
        SysUser user = new SysUser();
        user.setLoginName("e2e");
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(user);
        ThreadContext.bind(subject);
    }

    @AfterEach
    void clearSubject() {
        ThreadContext.unbindSubject();
    }

    @Test
    @DisplayName("E2E-ATT-006: 批量3张全部成功，双对象(原图+缩略图)与登记落库")
    public void testBatchUpload_AllSuccess() throws Exception {
        String[] uuids = {freshUuid(), freshUuid(), freshUuid()};
        MockMultipartFile[] files = {jpeg("a.jpg"), jpeg("b.jpg"), jpeg("c.jpg")};

        AjaxResult result = controller.uploadSyncAttachments(files, uuids);

        assertEquals(0, result.get(AjaxResult.CODE_TAG), () -> String.valueOf(result.get(AjaxResult.MSG_TAG)));
        @SuppressWarnings("unchecked")
        BatchUploadResultVo data = (BatchUploadResultVo) result.get(AjaxResult.DATA_TAG);
        assertEquals(3, data.getTotal());
        assertEquals(3, data.getSuccessCount());
        assertEquals(0, data.getFailureCount());

        for (int i = 0; i < 3; i++) {
            UploadItemResultVo item = data.getItems().get(i);
            assertEquals(uuids[i], item.getUploadUuid());
            assertEquals(UploadItemResultVo.STATUS_SUCCESS, item.getStatus());
            assertNotNull(item.getMinioId());
            assertNotNull(item.getThumbMinioId(), "服务端应自动生成缩略图");

            FileUploadRegistry saved = registryMapper.selectByUploadUuid(uuids[i]);
            assertNotNull(saved);
            assertEquals(item.getMinioId(), saved.getFileMapId());
            assertEquals(item.getThumbMinioId(), saved.getThumbFileMapId());
            assertObjectExists(item.getMinioId());
            assertObjectExists(item.getThumbMinioId());
        }
    }

    @Test
    @DisplayName("E2E-ATT-007: 同uploadUuid重放返回exists，minioId不变、不新增登记行")
    public void testBatchUpload_ReplayIdempotent() throws Exception {
        String uuid = freshUuid();
        MockMultipartFile[] files = {jpeg("r.jpg")};
        String[] uuids = {uuid};

        BatchUploadResultVo first = (BatchUploadResultVo) controller
                .uploadSyncAttachments(files, uuids).get(AjaxResult.DATA_TAG);
        Long firstMinioId = first.getItems().get(0).getMinioId();
        Long firstThumbId = first.getItems().get(0).getThumbMinioId();
        Long firstRegistryId = registryMapper.selectByUploadUuid(uuid).getId();

        BatchUploadResultVo replay = (BatchUploadResultVo) controller
                .uploadSyncAttachments(files, uuids).get(AjaxResult.DATA_TAG);

        UploadItemResultVo item = replay.getItems().get(0);
        assertEquals(UploadItemResultVo.STATUS_EXISTS, item.getStatus());
        assertEquals(firstMinioId, item.getMinioId());
        assertEquals(firstThumbId, item.getThumbMinioId());
        assertEquals(firstRegistryId, registryMapper.selectByUploadUuid(uuid).getId(),
                "重放不得新增登记行");
    }

    @Test
    @DisplayName("E2E-ATT-008: query 混合已传/未传，标记与id正确")
    public void testQuery_Mixed() throws Exception {
        String uploaded = freshUuid();
        String missing = freshUuid();
        controller.uploadSyncAttachments(
                new MockMultipartFile[]{jpeg("q.jpg")}, new String[]{uploaded});

        List<UploadStatusVo> statuses = invokeQuery(List.of(uploaded, missing));

        assertEquals(2, statuses.size());
        assertEquals(uploaded, statuses.get(0).getUploadUuid());
        assertTrue(statuses.get(0).getUploaded());
        assertNotNull(statuses.get(0).getMinioId());
        assertNotNull(statuses.get(0).getThumbMinioId());
        assertEquals(missing, statuses.get(1).getUploadUuid());
        assertFalse(statuses.get(1).getUploaded());
        assertNull(statuses.get(1).getMinioId());
    }

    private List<UploadStatusVo> invokeQuery(List<String> uuids) {
        edu.whut.cs.bi.biz.domain.dto.UploadQueryDto query =
                new edu.whut.cs.bi.biz.domain.dto.UploadQueryDto();
        query.setUploadUuids(uuids);
        AjaxResult result = controller.querySyncAttachments(query);
        assertEquals(0, result.get(AjaxResult.CODE_TAG), () -> String.valueOf(result.get(AjaxResult.MSG_TAG)));
        @SuppressWarnings("unchecked")
        List<UploadStatusVo> data = (List<UploadStatusVo>) result.get(AjaxResult.DATA_TAG);
        return data;
    }

    @Test
    @DisplayName("E2E-ATT-009: 批内混空文件与非法uuid，逐项失败不影响合法项")
    public void testBatchUpload_PartialFailures() throws Exception {
        String good = freshUuid();
        String empty = freshUuid();
        MockMultipartFile[] files = {
                jpeg("ok.jpg"),
                new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]),
                jpeg("bad.jpg")
        };
        String[] uuids = {good, empty, "not-a-uuid"};

        BatchUploadResultVo data = (BatchUploadResultVo) controller
                .uploadSyncAttachments(files, uuids).get(AjaxResult.DATA_TAG);

        assertEquals(3, data.getTotal());
        assertEquals(1, data.getSuccessCount());
        assertEquals(2, data.getFailureCount());
        assertEquals(UploadItemResultVo.STATUS_SUCCESS, data.getItems().get(0).getStatus());
        assertEquals(UploadItemResultVo.STATUS_FAILED, data.getItems().get(1).getStatus());
        assertTrue(data.getItems().get(1).getError().contains("文件为空"));
        assertEquals(UploadItemResultVo.STATUS_FAILED, data.getItems().get(2).getStatus());
        assertTrue(data.getItems().get(2).getError().contains("uploadUuid"));
        assertNotNull(registryMapper.selectByUploadUuid(good));
        assertNull(registryMapper.selectByUploadUuid(empty));
    }

    @Test
    @DisplayName("E2E-ATT-010: 预上传双id闭环 sync/upload，bi_attachment.thumb_minio_id 回填")
    public void testPreUpload_ClosedLoopToAttachment() throws Exception {
        String uploadUuid = freshUuid();
        String diseaseUuid = "att010-dis-" + UUID.randomUUID();
        String attachmentUuid = "att010-att-" + UUID.randomUUID();

        AjaxResult uploaded = controller.uploadSyncAttachment(jpeg("disease.jpg"), uploadUuid);
        assertEquals(0, uploaded.get(AjaxResult.CODE_TAG),
                () -> String.valueOf(uploaded.get(AjaxResult.MSG_TAG)));
        UploadItemResultVo item = (UploadItemResultVo) uploaded.get(AjaxResult.DATA_TAG);
        assertNotNull(item.getThumbMinioId());

        Map<String, Object> disease = new HashMap<>();
        disease.put("offlineUuid", diseaseUuid);
        disease.put("type", "裂缝");
        disease.put("description", "批量预上传闭环测试病害");
        disease.put("level", 1);
        disease.put("quantity", 1);
        disease.put("offlineDeleted", 0);

        Map<String, Object> att = new HashMap<>();
        att.put("offlineUuid", attachmentUuid);
        att.put("offlineSubjectUuid", diseaseUuid);
        att.put("name", attachmentUuid + ".jpg");
        att.put("type", 1);
        att.put("minioId", item.getMinioId());
        att.put("thumbMinioId", item.getThumbMinioId());
        att.put("offlineDeleted", 0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("diseases", List.of(disease));
        payload.put("attachments", List.of(att));

        SyncResultVo syncResult = syncUploadService.syncUpload(payload);
        assertTrue(syncResult.getErrors().isEmpty(), () -> syncResult.getErrors().toString());

        Attachment saved = attachmentMapper.selectByOfflineUuid(attachmentUuid);
        assertNotNull(saved);
        assertEquals(item.getMinioId(), saved.getMinioId());
        assertEquals(item.getThumbMinioId(), saved.getThumbMinioId());
    }

    @Test
    @DisplayName("E2E-ATT-011: 旧版接口(无uploadUuid)仍返回裸Integer，且不写登记表")
    public void testLegacyEndpoint_BareIntegerResponse() throws Exception {
        bindShiroSubject();
        AjaxResult result = controller.uploadSyncAttachment(jpeg("legacy.jpg"), null);

        assertEquals(0, result.get(AjaxResult.CODE_TAG), () -> String.valueOf(result.get(AjaxResult.MSG_TAG)));
        Object data = result.get(AjaxResult.DATA_TAG);
        assertTrue(data instanceof Integer, "旧契约 data 必须是裸 minioId，实际: " + data);
        assertNotNull(fileMapMapper.selectFileMapById(((Integer) data).longValue()));
    }
}
