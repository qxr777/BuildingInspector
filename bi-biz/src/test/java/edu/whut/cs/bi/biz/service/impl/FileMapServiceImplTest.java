package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import io.minio.*;
import io.minio.GetObjectResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileMapServiceImplTest {

    @InjectMocks
    private FileMapServiceImpl fileMapService;

    @Mock
    private FileMapMapper fileMapMapper;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private BiObjectMapper biObjectMapper;

    /**
     * 中文注释：测试批量删除文件成功，过滤无效ID后删除MinIO对象并删除数据库记录。
     */
    @Test
    void testDeleteFileMapByIds_Success() throws Exception {
        when(minioConfig.getBucketName()).thenReturn("bucket-a");

        FileMap f1 = new FileMap();
        f1.setId(1);
        f1.setNewName("ab123.png");
        FileMap f2 = new FileMap();
        f2.setId(2);
        f2.setNewName("cd456.png");

        when(fileMapMapper.selectFileMapById(1L)).thenReturn(f1);
        when(fileMapMapper.selectFileMapById(2L)).thenReturn(f2);
        when(fileMapMapper.deleteFileMapByIds(any())).thenReturn(2);

        int rows = fileMapService.deleteFileMapByIds("1,null, ,2");

        assertEquals(2, rows);
        verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
        verify(fileMapMapper, times(1)).deleteFileMapByIds(any());
    }

    /**
     * 中文注释：测试批量删除文件时，MinIO删除异常应抛出运行时异常。
     */
    @Test
    void testDeleteFileMapByIds_MinioException() throws Exception {
        when(minioConfig.getBucketName()).thenReturn("bucket-a");

        FileMap f1 = new FileMap();
        f1.setId(1);
        f1.setNewName("ab123.png");

        when(fileMapMapper.selectFileMapById(1L)).thenReturn(f1);
        doThrow(new RuntimeException("minio fail")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThrows(RuntimeException.class, () -> fileMapService.deleteFileMapByIds("1"));
    }

    /**
     * 中文注释：测试单文件上传成功，验证MinIO上传与数据库插入均被正确调用。
     */
    @Test
    void testHandleFileUpload_Success() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("demo.jpg");
        when(file.getSize()).thenReturn(5L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}));
        when(minioConfig.getBucketName()).thenReturn("bucket-a");
        when(fileMapMapper.insertFileMap(any(FileMap.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroUtils = Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtils.when(ShiroUtils::getLoginName).thenReturn("tester");

            FileMap result = fileMapService.handleFileUpload(file);

            assertNotNull(result);
            assertEquals("demo.jpg", result.getOldName());
            assertNotNull(result.getNewName());
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
            verify(fileMapMapper, times(1)).insertFileMap(any(FileMap.class));
        }
    }

    /**
     * 中文注释：测试单文件上传时，MinIO异常应包装为运行时异常抛出。
     */
    @Test
    void testHandleFileUpload_Exception() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("demo.jpg");
        when(file.getSize()).thenReturn(5L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(minioConfig.getBucketName()).thenReturn("bucket-a");
        doThrow(new RuntimeException("put fail")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThrows(RuntimeException.class, () -> fileMapService.handleFileUpload(file));
    }

    /**
     * 中文注释：测试复制文件成功，验证源文件读取、目标上传、新记录入库流程完整。
     */
    @Test
    void testCopyFile_Success() throws Exception {
        FileMap source = new FileMap();
        source.setId(10);
        source.setOldName("report.pdf");
        source.setNewName("ab_source.pdf");

        when(fileMapMapper.selectFileMapById(10L)).thenReturn(source);
        when(minioConfig.getBucketName()).thenReturn("bucket-a");
        GetObjectResponse objectResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse);
        when(fileMapMapper.insertFileMap(any(FileMap.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroUtils = Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtils.when(ShiroUtils::getLoginName).thenReturn("tester");

            FileMap copied = fileMapService.copyFile(10L);

            assertNotNull(copied);
            assertTrue(copied.getOldName().contains("副本"));
            assertNotNull(copied.getNewName());
            verify(minioClient, times(1)).getObject(any(GetObjectArgs.class));
            verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
            verify(fileMapMapper, times(1)).insertFileMap(any(FileMap.class));
        }
    }

    /**
     * 中文注释：测试复制文件时，源文件不存在应抛出运行时异常。
     */
    @Test
    void testCopyFile_SourceNotFound() {
        when(fileMapMapper.selectFileMapById(999L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> fileMapService.copyFile(999L));
    }

    /**
     * 中文注释：测试图片聚合查询成功，仅返回符合前缀且为图片类型且文件映射存在的数据。
     */
    @Test
    void testGetImageByIds_Success() {
        Attachment a1 = new Attachment();
        a1.setId(1L);
        a1.setName("biObject_img1.jpg");
        a1.setMinioId(11L);
        a1.setType(8);
        a1.setSubjectId(100L);
        a1.setRemark("备注1");

        Attachment a2 = new Attachment();
        a2.setId(2L);
        a2.setName("biObject_doc1.txt");
        a2.setMinioId(12L);
        a2.setType(8);

        when(attachmentService.getAttachmentBySubjectIds(Arrays.asList(100L)))
                .thenReturn(Arrays.asList(a1, a2));

        FileMap fm1 = new FileMap();
        fm1.setId(11);
        fm1.setNewName("ab_img1.jpg");
        fm1.setOldName("img1.jpg");

        FileMap fm2 = new FileMap();
        fm2.setId(12);
        fm2.setNewName("cd_doc1.txt");
        fm2.setOldName("doc1.txt");

        when(fileMapMapper.selectFileMapById(11L)).thenReturn(fm1);
        when(fileMapMapper.selectFileMapById(12L)).thenReturn(fm2);
        when(minioConfig.getUrl()).thenReturn("http://localhost:9000");
        when(minioConfig.getBucketName()).thenReturn("bucket-a");

        List<Map<String, Object>> result = fileMapService.getImage(Arrays.asList(100L), "biObject");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).get("id"));
        verify(attachmentService, times(1)).getAttachmentBySubjectIds(Arrays.asList(100L));
    }

    /**
     * 中文注释：测试图片聚合查询异常场景，附件名称不符合“前缀_文件名”格式时抛出异常。
     */
    @Test
    void testGetImageByIds_InvalidAttachmentName() {
        Attachment invalid = new Attachment();
        invalid.setId(3L);
        invalid.setName("biObjectInvalidName");
        invalid.setMinioId(13L);

        when(attachmentService.getAttachmentBySubjectIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(invalid));

        assertThrows(RuntimeException.class, () -> fileMapService.getImage(Collections.singletonList(100L), "biObject"));
    }
}