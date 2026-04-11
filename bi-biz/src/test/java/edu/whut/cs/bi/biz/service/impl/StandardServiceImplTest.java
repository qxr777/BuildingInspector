package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Standard;
import edu.whut.cs.bi.biz.mapper.StandardMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandardServiceImplTest {

    @InjectMocks
    private StandardServiceImpl standardService;

    @Mock
    private StandardMapper standardMapper;

    @Mock
    private AttachmentService attachmentService;

    /**
     * 测试场景：新增标准时正常补全创建信息并调用mapper入库。
     */
    @Test
    void testInsertStandard_Success() {
        Standard standard = new Standard();
        standard.setName("公路桥涵养护规范");

        Date fixedDate = new Date();
        when(standardMapper.insertStandard(standard)).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class);
             MockedStatic<DateUtils> dateMock = mockStatic(DateUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");
            dateMock.when(DateUtils::getNowDate).thenReturn(fixedDate);

            int rows = standardService.insertStandard(standard);

            assertEquals(1, rows);
            assertEquals("tester", standard.getCreateBy());
            assertEquals(fixedDate, standard.getCreateTime());
            verify(standardMapper, times(1)).insertStandard(standard);
        }
    }

    /**
     * 测试场景：新增标准时底层持久化失败，应抛出运行时异常。
     */
    @Test
    void testInsertStandard_InsertFailed() {
        Standard standard = new Standard();
        doThrow(new RuntimeException("insert error")).when(standardMapper).insertStandard(any(Standard.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class);
             MockedStatic<DateUtils> dateMock = mockStatic(DateUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");
            dateMock.when(DateUtils::getNowDate).thenReturn(new Date());

            assertThrows(RuntimeException.class, () -> standardService.insertStandard(standard));
        }
    }

    /**
     * 测试场景：修改标准时应同步更新附件名并更新标准信息。
     */
    @Test
    void testUpdateStandard_Success() {
        Standard standard = new Standard();
        standard.setName("桥梁技术状况评定标准");

        Date fixedDate = new Date();
        when(standardMapper.updateStandard(standard)).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class);
             MockedStatic<DateUtils> dateMock = mockStatic(DateUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("updater");
            dateMock.when(DateUtils::getNowDate).thenReturn(fixedDate);

            int rows = standardService.updateStandard(standard);

            assertEquals(1, rows);
            assertEquals("updater", standard.getUpdateBy());
            assertEquals(fixedDate, standard.getUpdateTime());

            ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
            verify(attachmentService, times(1)).updateAttachment(attachmentCaptor.capture());
            assertEquals(5, attachmentCaptor.getValue().getType());
            assertEquals("桥梁技术状况评定标准", attachmentCaptor.getValue().getName());
            verify(standardMapper, times(1)).updateStandard(standard);
        }
    }

    /**
     * 测试场景：修改标准时附件服务更新失败，应抛出运行时异常并中断流程。
     */
    @Test
    void testUpdateStandard_AttachmentUpdateFailed() {
        Standard standard = new Standard();
        standard.setName("标准A");

        doThrow(new RuntimeException("attachment error")).when(attachmentService).updateAttachment(any(Attachment.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class);
             MockedStatic<DateUtils> dateMock = mockStatic(DateUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("updater");
            dateMock.when(DateUtils::getNowDate).thenReturn(new Date());

            assertThrows(RuntimeException.class, () -> standardService.updateStandard(standard));
        }
    }

    /**
     * 测试场景：批量删除标准时，先删除关联附件，再删除标准记录。
     */
    @Test
    void testDeleteStandardByIds_Success() {
        when(standardMapper.selectAttachmentIds(any(String[].class))).thenReturn(new String[]{"10", "11"});
        when(attachmentService.deleteAttachmentByIds(anyString())).thenReturn(1);
        when(standardMapper.deleteStandardByIds(any(String[].class))).thenReturn(2);

        int rows = standardService.deleteStandardByIds("1,2");

        assertEquals(2, rows);
        verify(attachmentService, times(1)).deleteAttachmentByIds("10,11");
        verify(standardMapper, times(1)).deleteStandardByIds(any(String[].class));
    }

    /**
     * 测试场景：批量删除标准时附件删除失败，应抛出运行时异常。
     */
    @Test
    void testDeleteStandardByIds_DeleteAttachmentFailed() {
        when(standardMapper.selectAttachmentIds(any(String[].class))).thenReturn(new String[]{"20"});
        doThrow(new RuntimeException("delete attachment failed")).when(attachmentService).deleteAttachmentByIds("20");

        assertThrows(RuntimeException.class, () -> standardService.deleteStandardByIds("5"));
    }

    /**
     * 测试场景：删除单个标准时，先删附件再删标准，流程正常返回删除行数。
     */
    @Test
    void testDeleteStandardById_Success() {
        when(standardMapper.selectAttachmentIdById(9L)).thenReturn(99L);
        doNothing().when(attachmentService).deleteAttachmentById(99L);
        when(standardMapper.deleteStandardById(9L)).thenReturn(1);

        int rows = standardService.deleteStandardById(9L);

        assertEquals(1, rows);
        verify(attachmentService, times(1)).deleteAttachmentById(99L);
        verify(standardMapper, times(1)).deleteStandardById(9L);
    }

    /**
     * 测试场景：删除单个标准时附件ID为空导致附件删除异常，应抛出运行时异常。
     */
    @Test
    void testDeleteStandardById_AttachmentIdNull() {
        when(standardMapper.selectAttachmentIdById(8L)).thenReturn(null);
        doThrow(new RuntimeException("attachment id is null")).when(attachmentService).deleteAttachmentById(null);

        assertThrows(RuntimeException.class, () -> standardService.deleteStandardById(8L));
    }
}
