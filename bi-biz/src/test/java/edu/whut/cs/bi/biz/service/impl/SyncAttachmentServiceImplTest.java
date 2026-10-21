package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.FileUploadRegistry;
import edu.whut.cs.bi.biz.domain.vo.BatchUploadResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadItemResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadStatusVo;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.FileUploadRegistryMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 通道A 幂等预上传服务单测（不依赖 MySQL/MinIO）。
 */
@ExtendWith(MockitoExtension.class)
class SyncAttachmentServiceImplTest {

    private static final String UUID_A = "0123456789abcdef0123456789abcdef";

    @InjectMocks
    private SyncAttachmentServiceImpl service;

    @Mock
    private FileUploadRegistryMapper registryMapper;
    @Mock
    private FileMapMapper fileMapMapper;
    @Mock
    private IFileMapService fileMapService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxFileBytes", 20L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "thumbnailEnabled", true);
    }

    private MockMultipartFile jpeg(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/jpeg", content);
    }

    private byte[] realJpegBytes() {
        try {
            BufferedImage img = new BufferedImage(128, 96, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FileMap fileMap(int id) {
        FileMap fm = new FileMap();
        fm.setId(id);
        fm.setNewName("ab" + id + ".jpg");
        return fm;
    }

    /** 幂等命中：不触达存储，返回 exists 与既有 id */
    @Test
    void testPreUpload_HitReturnsExistsZeroTraffic() {
        FileUploadRegistry hit = new FileUploadRegistry();
        hit.setUploadUuid(UUID_A);
        hit.setFileMapId(1001L);
        hit.setThumbFileMapId(1002L);
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(hit);
        when(fileMapMapper.selectFileMapById(1001L)).thenReturn(fileMap(1001));

        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", realJpegBytes()));

        assertEquals(UploadItemResultVo.STATUS_EXISTS, result.getStatus());
        assertEquals(1001L, result.getMinioId());
        assertEquals(1002L, result.getThumbMinioId());
        verify(fileMapService, never()).storeFromFile(any(), any(), any());
        verify(registryMapper, never()).insertFileUploadRegistry(any());
    }

    /** 登记存在但原图记录缺失：失败告警，不自动重传 */
    @Test
    void testPreUpload_HitButFileMapMissing_Failed() {
        FileUploadRegistry hit = new FileUploadRegistry();
        hit.setUploadUuid(UUID_A);
        hit.setFileMapId(1001L);
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(hit);
        when(fileMapMapper.selectFileMapById(1001L)).thenReturn(null);

        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", realJpegBytes()));

        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getStatus());
        assertTrue(result.getError().contains("登记状态不一致"));
        verify(fileMapService, never()).storeFromFile(any(), any(), any());
    }

    /** 支持带横线大写 UUID，归一化后查表 */
    @Test
    void testPreUpload_DashedUppercaseUuid_Normalized() {
        when(registryMapper.selectByUploadUuid(UUID_A))
                .thenReturn(null)
                .thenThrow(new AssertionError("不应第二次查询"));
        // 其余下游未打桩即抛异常 → failed，但足以证明首次查询用的是归一化键
        UploadItemResultVo result = service.preUpload(
                "01234567-89AB-CDEF-0123-456789ABCDEF", jpeg("a.jpg", new byte[]{1}));
        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getStatus());
        verify(registryMapper).selectByUploadUuid(UUID_A);
    }

    @Test
    void testPreUpload_InvalidUuid_FailedNoSideEffect() {
        UploadItemResultVo result = service.preUpload("not-a-uuid", jpeg("a.jpg", new byte[]{1}));
        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getStatus());
        assertTrue(result.getError().contains("uploadUuid"));
        verifyNoInteractions(registryMapper, fileMapService);
    }

    @Test
    void testPreUpload_EmptyFile_Failed() {
        UploadItemResultVo result = service.preUpload(UUID_A,
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]));
        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getStatus());
        assertTrue(result.getError().contains("文件为空"));
        verifyNoInteractions(fileMapService);
    }

    @Test
    void testPreUpload_OverSize_Failed() {
        ReflectionTestUtils.setField(service, "maxFileBytes", 10L);
        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", new byte[11]));
        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getStatus());
        assertTrue(result.getError().contains("大小限制"));
        verifyNoInteractions(fileMapService);
    }

    /** 全新上传真实 JPEG：原图+缩略图各存一次，登记一次，返回双 id */
    @Test
    void testPreUpload_FreshSuccess_WithThumbnail() {
        byte[] bytes = realJpegBytes();
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(null);
        when(fileMapService.isImageFile("a.jpg")).thenReturn(true);
        when(fileMapService.storeFromFile(any(File.class), eq("a.jpg"), any()))
                .thenReturn(fileMap(1001))   // 第一次：原图
                .thenReturn(fileMap(1002));  // 第二次：缩略图

        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", bytes));

        assertEquals(UploadItemResultVo.STATUS_SUCCESS, result.getStatus());
        assertEquals(1001L, result.getMinioId());
        assertEquals(1002L, result.getThumbMinioId());
        verify(fileMapService, times(2)).storeFromFile(any(File.class), eq("a.jpg"), any());

        ArgumentCaptor<FileUploadRegistry> captor = ArgumentCaptor.forClass(FileUploadRegistry.class);
        verify(registryMapper).insertFileUploadRegistry(captor.capture());
        FileUploadRegistry saved = captor.getValue();
        assertEquals(UUID_A, saved.getUploadUuid());
        assertEquals(1001L, saved.getFileMapId());
        assertEquals(1002L, saved.getThumbFileMapId());
        assertEquals((long) bytes.length, saved.getFileSize());
        assertNotNull(saved.getCreateTime());
    }

    /** 非图片：不生成缩略图，thumbMinioId=null，原图仍成功 */
    @Test
    void testPreUpload_NonImage_NoThumbnail() {
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(null);
        when(fileMapService.isImageFile("notes.txt")).thenReturn(false);
        when(fileMapService.storeFromFile(any(File.class), eq("notes.txt"), any()))
                .thenReturn(fileMap(1003));

        UploadItemResultVo result = service.preUpload(UUID_A,
                new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes()));

        assertEquals(UploadItemResultVo.STATUS_SUCCESS, result.getStatus());
        assertEquals(1003L, result.getMinioId());
        assertNull(result.getThumbMinioId());
        verify(fileMapService, times(1)).storeFromFile(any(File.class), eq("notes.txt"), any());
    }

    /** 图片内容损坏导致缩略图失败：只告警，原图成功、thumbMinioId=null */
    @Test
    void testPreUpload_ThumbnailFailure_DoesNotBlockOriginal() {
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(null);
        when(fileMapService.isImageFile("a.jpg")).thenReturn(true);
        when(fileMapService.storeFromFile(any(File.class), eq("a.jpg"), any()))
                .thenReturn(fileMap(1004));

        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", new byte[]{1, 2, 3}));

        assertEquals(UploadItemResultVo.STATUS_SUCCESS, result.getStatus());
        assertEquals(1004L, result.getMinioId());
        assertNull(result.getThumbMinioId());
        // 只有原图一次存储（缩略图在存储前就解码失败）
        verify(fileMapService, times(1)).storeFromFile(any(File.class), eq("a.jpg"), any());
    }

    /** 登记表唯一键冲突（并发双发负方）：补偿删除自己写入的行/对象，返回胜方结果 */
    @Test
    void testPreUpload_DuplicateKey_CompensateAndReturnWinner() {
        FileUploadRegistry winner = new FileUploadRegistry();
        winner.setUploadUuid(UUID_A);
        winner.setFileMapId(2001L);
        winner.setThumbFileMapId(2002L);
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(null).thenReturn(winner);
        when(fileMapMapper.selectFileMapById(2001L)).thenReturn(fileMap(2001));
        when(fileMapService.isImageFile("a.jpg")).thenReturn(true);
        when(fileMapService.storeFromFile(any(File.class), eq("a.jpg"), any()))
                .thenReturn(fileMap(3001))
                .thenReturn(fileMap(3002));
        doThrow(new DuplicateKeyException("duplicate upload_uuid"))
                .when(registryMapper).insertFileUploadRegistry(any());

        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", realJpegBytes()));

        assertEquals(UploadItemResultVo.STATUS_EXISTS, result.getStatus());
        assertEquals(2001L, result.getMinioId());
        assertEquals(2002L, result.getThumbMinioId());
        verify(fileMapService).deleteFileMapById(3001L);
        verify(fileMapService).deleteFileMapById(3002L);
    }

    /** 原图存储抛异常：整体 failed，且临时文件被清理 */
    @Test
    void testPreUpload_StoreThrows_FailedAndTempCleaned() throws Exception {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File[] before = tmpDir.listFiles((d, n) -> n.startsWith("syncatt_"));
        int beforeCount = before == null ? 0 : before.length;

        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(null);
        when(fileMapService.storeFromFile(any(File.class), any(), any()))
                .thenThrow(new RuntimeException("MinIO down"));

        UploadItemResultVo result = service.preUpload(UUID_A, jpeg("a.jpg", realJpegBytes()));

        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getStatus());
        assertTrue(result.getError().contains("MinIO down"));
        File[] after = tmpDir.listFiles((d, n) -> n.startsWith("syncatt_"));
        int afterCount = after == null ? 0 : after.length;
        assertEquals(beforeCount, afterCount, "临时文件应被 finally 清理");
    }

    /** 批量：批内重复 uuid 第二项确定性失败，合法项正常成功 */
    @Test
    void testBatch_DuplicateUuidWithinBatch() {
        byte[] bytes = realJpegBytes();
        when(registryMapper.selectByUploadUuid(UUID_A)).thenReturn(null);
        when(fileMapService.isImageFile("a.jpg")).thenReturn(true);
        when(fileMapService.storeFromFile(any(File.class), eq("a.jpg"), any()))
                .thenReturn(fileMap(1001))
                .thenReturn(fileMap(1002));

        MockMultipartFile[] files = {jpeg("a.jpg", bytes), jpeg("a.jpg", bytes)};
        String[] uuids = {UUID_A, UUID_A};

        BatchUploadResultVo result = service.preUploadBatch(uuids, files);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(UploadItemResultVo.STATUS_SUCCESS, result.getItems().get(0).getStatus());
        assertEquals(UploadItemResultVo.STATUS_FAILED, result.getItems().get(1).getStatus());
        assertTrue(result.getItems().get(1).getError().contains("批内"));
    }

    /** 批量：非法 uuid 不上传、不影响其他项 */
    @Test
    void testBatch_InvalidUuidSkipped() {
        BatchUploadResultVo result = service.preUploadBatch(
                new String[]{"bad"}, new MockMultipartFile[]{jpeg("a.jpg", new byte[]{1})});
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getFailureCount());
        verifyNoInteractions(fileMapService);
    }

    /** 回查：已传/未传/非法 uuid 混合，按请求顺序输出 */
    @Test
    void testQueryUploaded_Mixed() {
        FileUploadRegistry record = new FileUploadRegistry();
        record.setUploadUuid(UUID_A);
        record.setFileMapId(1001L);
        record.setThumbFileMapId(1002L);
        when(registryMapper.selectByUploadUuids(anyList())).thenReturn(Collections.singletonList(record));
        when(fileMapMapper.selectFileMapById(1001L)).thenReturn(fileMap(1001));

        String missing = "fedcba9876543210fedcba9876543210";
        List<UploadStatusVo> result = service.queryUploaded(Arrays.asList(UUID_A, missing, "非法"));

        assertEquals(3, result.size());
        assertEquals(UUID_A, result.get(0).getUploadUuid());
        assertTrue(result.get(0).getUploaded());
        assertEquals(1001L, result.get(0).getMinioId());
        assertEquals(1002L, result.get(0).getThumbMinioId());
        assertEquals(missing, result.get(1).getUploadUuid());
        assertFalse(result.get(1).getUploaded());
        assertNull(result.get(1).getMinioId());
        assertFalse(result.get(2).getUploaded());
        assertEquals("非法", result.get(2).getUploadUuid());
    }
}
