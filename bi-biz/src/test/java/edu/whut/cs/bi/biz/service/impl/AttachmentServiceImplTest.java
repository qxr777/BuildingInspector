package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @InjectMocks
    private AttachmentServiceImpl attachmentService;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private IFileMapService fileMapService;

    /**
     * 测试场景：删除单个附件时，先删除文件映射，再删除附件记录。
     */
    @Test
    void testDeleteAttachmentById_Success() {
        Attachment attachment = new Attachment();
        attachment.setId(10L);
        attachment.setMinioId(100L);

        when(attachmentMapper.selectById(10L)).thenReturn(attachment);
        when(fileMapService.deleteFileMapById(100L)).thenReturn(1);

        attachmentService.deleteAttachmentById(10L);

        verify(attachmentMapper, times(1)).selectById(10L);
        verify(fileMapService, times(1)).deleteFileMapById(100L);
        verify(attachmentMapper, times(1)).deleteById(10L);
    }

    /**
     * 测试场景：删除单个附件时，若附件不存在导致空指针，应抛出异常。
     */
    @Test
    void testDeleteAttachmentById_AttachmentNotFound() {
        when(attachmentMapper.selectById(99L)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> attachmentService.deleteAttachmentById(99L));
    }

    /**
     * 测试场景：新增附件时，正常补全创建人并执行入库。
     */
    @Test
    void testInsertAttachment_Success() {
        Attachment attachment = new Attachment();
        attachment.setName("测试附件");

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            attachmentService.insertAttachment(attachment);
        }

        assertEquals("tester", attachment.getCreateBy());
        assertNotNull(attachment.getCreateTime());
        verify(attachmentMapper, times(1)).insert(attachment);
    }

    /**
     * 测试场景：新增附件时，若持久化失败应抛出运行时异常。
     */
    @Test
    void testInsertAttachment_InsertFailed() {
        Attachment attachment = new Attachment();

        doThrow(new RuntimeException("insert error")).when(attachmentMapper).insert(any(Attachment.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            assertThrows(RuntimeException.class, () -> attachmentService.insertAttachment(attachment));
        }
    }

    /**
     * 测试场景：更新附件时，正常补全更新人并执行更新。
     */
    @Test
    void testUpdateAttachment_Success() {
        Attachment attachment = new Attachment();
        attachment.setId(1L);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("updater");

            attachmentService.updateAttachment(attachment);
        }

        assertEquals("updater", attachment.getUpdateBy());
        assertNotNull(attachment.getUpdateTime());
        verify(attachmentMapper, times(1)).update(attachment);
    }

    /**
     * 测试场景：更新附件时，若更新失败应抛出运行时异常。
     */
    @Test
    void testUpdateAttachment_UpdateFailed() {
        Attachment attachment = new Attachment();
        attachment.setId(1L);

        doThrow(new RuntimeException("update error")).when(attachmentMapper).update(any(Attachment.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("updater");

            assertThrows(RuntimeException.class, () -> attachmentService.updateAttachment(attachment));
        }
    }

    /**
     * 测试场景：批量删除附件时，正常删除原图和缩略图文件并删除附件记录。
     */
    @Test
    void testDeleteAttachmentByIds_Success() {
        String ids = "1,2";
        String[] minioIds = new String[]{"11", "12"};
        String[] thumbMinioIds = new String[]{"21", "22"};

        when(attachmentMapper.selectMinioIdsByIds(any(String[].class))).thenReturn(minioIds);
        when(attachmentMapper.selectThumbMinioIdsByIds(any(String[].class))).thenReturn(thumbMinioIds);
        when(fileMapService.deleteFileMapByIds(anyString())).thenReturn(1);
        when(attachmentMapper.deleteByIds(any(String[].class))).thenReturn(2);

        int rows = attachmentService.deleteAttachmentByIds(ids);

        assertEquals(2, rows);
        verify(fileMapService, times(1)).deleteFileMapByIds("11,12");
        verify(fileMapService, times(1)).deleteFileMapByIds("21,22");
        verify(attachmentMapper, times(1)).deleteByIds(any(String[].class));
    }

    /**
     * 测试场景：批量删除附件时，传入空ID字符串应直接拦截并返回0。
     */
    @Test
    void testDeleteAttachmentByIds_EmptyIds() {
        int rows = attachmentService.deleteAttachmentByIds("");

        assertEquals(0, rows);
        verify(attachmentMapper, times(0)).selectMinioIdsByIds(any(String[].class));
        verify(fileMapService, times(0)).deleteFileMapByIds(anyString());
    }
}
