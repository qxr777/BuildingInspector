package edu.whut.cs.bi.api.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.domain.BuildingPackage;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.dto.UploadQueryDto;
import edu.whut.cs.bi.biz.domain.vo.BatchUploadResultVo;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.domain.vo.UploadItemResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadStatusVo;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.ISyncAttachmentService;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiControllerV2Test {

    @InjectMocks
    private ApiControllerV2 apiControllerV2;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private SqliteService sqliteService;

    @Mock
    private IFileMapService fileMapService;

    @Mock
    private ISyncAttachmentService syncAttachmentService;

    @BeforeEach
    void setUp() {
        // @Value 字段在单测中无 Spring 注入，显式设置批量上限
        ReflectionTestUtils.setField(apiControllerV2, "maxBatchSize", 20);
    }

    @Test
    void testGetUserSqliteUrl_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://localhost:9000/bucket/bb/user.db");

        doReturn(vo).when(sqliteService).getUserSqliteUrl(11L);

        AjaxResult result = apiControllerV2.getUserSqliteUrl(11L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("获取成功", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testGenerateUserSqlite_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://test.url");
        doReturn(vo).when(sqliteService).generateUserSqliteSync(20L);

        AjaxResult result = apiControllerV2.generateUserSqlite(20L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("已成功生成离线包并获取地址", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
        verify(sqliteService, times(1)).generateUserSqliteSync(20L);
    }

    @Test
    void testGenerateBuildingPackage_BuildingIdNull() {
        AjaxResult result = apiControllerV2.generateBuildingPackage(null);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("参数错误，buildingId不能为空", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void testGenerateBuildingPackage_Success() {
        AjaxResult expected = AjaxResult.success("ok");
        doReturn(expected).when(buildingService).generateBuildingPackage(100L);

        AjaxResult result = apiControllerV2.generateBuildingPackage(100L);

        assertSame(expected, result);
    }

    @Test
    void testGetBuildingPackage_Success() {
        BuildingPackage item = new BuildingPackage();
        item.setBuildingId(8L);
        item.setFileName("pkg.zip");

        doReturn(item).when(buildingService).selectLatestBuildingPackage(8L);

        AjaxResult result = apiControllerV2.getBuildingPackage(8L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("查询成功", result.get(AjaxResult.MSG_TAG));
        assertNotNull(result.get(AjaxResult.DATA_TAG));
        BuildingPackage data = (BuildingPackage) result.get(AjaxResult.DATA_TAG);
        assertEquals("pkg.zip", data.getFileName());
    }

    @Test
    void testGetBuildingPackage_Empty() {
        doReturn(null).when(buildingService).selectLatestBuildingPackage(9L);

        AjaxResult result = apiControllerV2.getBuildingPackage(9L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("该建筑物暂未生成数据包", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void testGenerateCommonSqlite_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://localhost:9000/bucket/cc/common.db");
        vo.setTimestamp(new Date());
        vo.setSize("10MB");

        doReturn(vo).when(sqliteService).generateCommonBaseSqlite();

        AjaxResult result = apiControllerV2.generateCommonSqlite();

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("获取成功", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testGenerateCommonSqlite_Null() {
        doReturn(null).when(sqliteService).generateCommonBaseSqlite();

        AjaxResult result = apiControllerV2.generateCommonSqlite();

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("通用基础 SQLite 生成失败", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void testGenerateCommonSqlite_Exception() {
        doThrow(new RuntimeException("io error")).when(sqliteService).generateCommonBaseSqlite();

        AjaxResult result = apiControllerV2.generateCommonSqlite();

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("生成基础数据包失败"));
    }

    // ==================== 通道A 附件预上传 ====================

    private MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    /** 旧版兼容：不带 uploadUuid 时 data 必须仍是裸 minioId，且不走幂等服务 */
    @Test
    void testUploadAttachment_LegacyBareId() {
        MockMultipartFile file = jpeg("a.jpg");
        FileMap fileMap = new FileMap();
        fileMap.setId(910001);
        when(fileMapService.handleFileUpload(file)).thenReturn(fileMap);

        AjaxResult result = apiControllerV2.uploadSyncAttachment(file, null);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals(910001, result.get(AjaxResult.DATA_TAG));
        verify(syncAttachmentService, times(0)).preUpload(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 空字符串 uploadUuid 同样走旧路径 */
    @Test
    void testUploadAttachment_LegacyBlankUuid() {
        MockMultipartFile file = jpeg("a.jpg");
        FileMap fileMap = new FileMap();
        fileMap.setId(910002);
        when(fileMapService.handleFileUpload(file)).thenReturn(fileMap);

        AjaxResult result = apiControllerV2.uploadSyncAttachment(file, "  ");

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals(910002, result.get(AjaxResult.DATA_TAG));
    }

    /** 增强模式：成功返回 VO 对象 */
    @Test
    void testUploadAttachment_EnhancedSuccess() {
        MockMultipartFile file = jpeg("a.jpg");
        String uuid = "0123456789abcdef0123456789abcdef";
        UploadItemResultVo item = UploadItemResultVo.success(uuid, 1001L, 1002L, false);
        when(syncAttachmentService.preUpload(uuid, file)).thenReturn(item);

        AjaxResult result = apiControllerV2.uploadSyncAttachment(file, uuid);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertSame(item, result.get(AjaxResult.DATA_TAG));
        verify(fileMapService, times(0)).handleFileUpload(org.mockito.ArgumentMatchers.any());
    }

    /** 增强模式：服务返回 failed 时信封为 500 */
    @Test
    void testUploadAttachment_EnhancedFailed() {
        MockMultipartFile file = jpeg("a.jpg");
        String uuid = "0123456789abcdef0123456789abcdef";
        when(syncAttachmentService.preUpload(uuid, file))
                .thenReturn(UploadItemResultVo.failed(uuid, "文件为空"));

        AjaxResult result = apiControllerV2.uploadSyncAttachment(file, uuid);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("文件为空"));
    }

    @Test
    void testBatchUpload_EmptyFiles() {
        AjaxResult result = apiControllerV2.uploadSyncAttachments(new MockMultipartFile[0], new String[0]);
        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("files 不能为空"));
    }

    @Test
    void testBatchUpload_LengthMismatch() {
        MockMultipartFile[] files = {jpeg("a.jpg"), jpeg("b.jpg")};
        AjaxResult result = apiControllerV2.uploadSyncAttachments(files, new String[]{"u1"});
        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("数量一致"));
    }

    @Test
    void testBatchUpload_OverLimit() {
        ReflectionTestUtils.setField(apiControllerV2, "maxBatchSize", 1);
        MockMultipartFile[] files = {jpeg("a.jpg"), jpeg("b.jpg")};
        String[] uuids = {"0123456789abcdef0123456789abcdef", "0123456789abcdef0123456789abcde0"};

        AjaxResult result = apiControllerV2.uploadSyncAttachments(files, uuids);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("超过上限"));
    }

    @Test
    void testBatchUpload_Success() {
        MockMultipartFile[] files = {jpeg("a.jpg"), jpeg("b.jpg")};
        String[] uuids = {"0123456789abcdef0123456789abcdef", "0123456789abcdef0123456789abcde0"};
        BatchUploadResultVo vo = new BatchUploadResultVo();
        when(syncAttachmentService.preUploadBatch(uuids, files)).thenReturn(vo);

        AjaxResult result = apiControllerV2.uploadSyncAttachments(files, uuids);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testQueryAttachments_NullBody() {
        assertEquals(500, apiControllerV2.querySyncAttachments(null).get(AjaxResult.CODE_TAG));
    }

    @Test
    void testQueryAttachments_Empty() {
        UploadQueryDto dto = new UploadQueryDto();
        dto.setUploadUuids(Collections.emptyList());
        assertEquals(500, apiControllerV2.querySyncAttachments(dto).get(AjaxResult.CODE_TAG));
    }

    @Test
    void testQueryAttachments_TooMany() {
        UploadQueryDto dto = new UploadQueryDto();
        dto.setUploadUuids(Arrays.asList(new String[501]));
        assertEquals(500, apiControllerV2.querySyncAttachments(dto).get(AjaxResult.CODE_TAG));
    }

    @Test
    void testQueryAttachments_Success() {
        List<String> uuids = Collections.singletonList("0123456789abcdef0123456789abcdef");
        List<UploadStatusVo> statuses = Collections.singletonList(new UploadStatusVo());
        when(syncAttachmentService.queryUploaded(uuids)).thenReturn(statuses);

        UploadQueryDto dto = new UploadQueryDto();
        dto.setUploadUuids(uuids);
        AjaxResult result = apiControllerV2.querySyncAttachments(dto);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertSame(statuses, result.get(AjaxResult.DATA_TAG));
    }
}
