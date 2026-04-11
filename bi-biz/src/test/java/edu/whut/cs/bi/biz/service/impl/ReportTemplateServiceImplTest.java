package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.ReportTemplate;
import edu.whut.cs.bi.biz.mapper.ReportTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTemplateServiceImplTest {

    @InjectMocks
    private ReportTemplateServiceImpl reportTemplateService;

    @Mock
    private ReportTemplateMapper reportTemplateMapper;

    /**
     * 测试 insertReportTemplate：未传版本号时自动设置默认版本并成功新增。
     */
    @Test
    void testInsertReportTemplate_Success() {
        ReportTemplate template = new ReportTemplate();
        template.setName("模板A");
        template.setVersion(null);

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("admin");
            when(reportTemplateMapper.insertReportTemplate(template)).thenReturn(1);

            int result = reportTemplateService.insertReportTemplate(template);

            assertEquals(1, result);
            assertEquals("admin", template.getCreateBy());
            assertEquals(1, template.getVersion());
            verify(reportTemplateMapper, times(1)).insertReportTemplate(template);
        }
    }

    /**
     * 测试 insertReportTemplate：Mapper 新增异常时向上抛出运行时异常。
     */
    @Test
    void testInsertReportTemplate_Exception() {
        ReportTemplate template = new ReportTemplate();
        template.setName("模板A");

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("admin");
            when(reportTemplateMapper.insertReportTemplate(template)).thenThrow(new RuntimeException("DB异常"));

            assertThrows(RuntimeException.class, () -> reportTemplateService.insertReportTemplate(template));
        }
    }

    /**
     * 测试 updateReportTemplate：存在旧版本时自动+1并成功更新。
     */
    @Test
    void testUpdateReportTemplate_Success() {
        ReportTemplate template = new ReportTemplate();
        template.setId(100L);
        template.setName("模板B");

        ReportTemplate oldTemplate = new ReportTemplate();
        oldTemplate.setId(100L);
        oldTemplate.setVersion(3);

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("editor");
            when(reportTemplateMapper.selectReportTemplateById(100L)).thenReturn(oldTemplate);
            when(reportTemplateMapper.updateReportTemplate(template)).thenReturn(1);

            int result = reportTemplateService.updateReportTemplate(template);

            assertEquals(1, result);
            assertEquals("editor", template.getUpdateBy());
            assertEquals(4, template.getVersion());
            verify(reportTemplateMapper, times(1)).selectReportTemplateById(100L);
            verify(reportTemplateMapper, times(1)).updateReportTemplate(template);
        }
    }

    /**
     * 测试 updateReportTemplate：旧数据不存在时版本初始化为1后更新异常抛出。
     */
    @Test
    void testUpdateReportTemplate_Exception() {
        ReportTemplate template = new ReportTemplate();
        template.setId(200L);
        template.setName("模板C");

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("editor");
            when(reportTemplateMapper.selectReportTemplateById(200L)).thenReturn(null);
            when(reportTemplateMapper.updateReportTemplate(template)).thenThrow(new RuntimeException("更新失败"));

            assertThrows(RuntimeException.class, () -> reportTemplateService.updateReportTemplate(template));
            assertEquals(1, template.getVersion());
        }
    }

    /**
     * 测试 deleteReportTemplateByIds：批量删除参数正确拆分并成功执行。
     */
    @Test
    void testDeleteReportTemplateByIds_Success() {
        String ids = "1,2,3";
        when(reportTemplateMapper.deleteReportTemplateByIds(any(String[].class))).thenReturn(3);

        int result = reportTemplateService.deleteReportTemplateByIds(ids);

        assertEquals(3, result);
        verify(reportTemplateMapper, times(1)).deleteReportTemplateByIds(new String[]{"1", "2", "3"});
    }

    /**
     * 测试 deleteReportTemplateByIds：批量删除时 Mapper 异常抛出。
     */
    @Test
    void testDeleteReportTemplateByIds_Exception() {
        String ids = "1,2";
        when(reportTemplateMapper.deleteReportTemplateByIds(any(String[].class))).thenThrow(new RuntimeException("删除失败"));

        assertThrows(RuntimeException.class, () -> reportTemplateService.deleteReportTemplateByIds(ids));
    }
}
