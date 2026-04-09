package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Report;
import edu.whut.cs.bi.biz.domain.ReportData;
import edu.whut.cs.bi.biz.domain.ReportTemplate;
import edu.whut.cs.bi.biz.domain.TemplateVariable;
import edu.whut.cs.bi.biz.mapper.ReportMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IProjectService;
import edu.whut.cs.bi.biz.service.IReportDataService;
import edu.whut.cs.bi.biz.service.IReportTemplateService;
import edu.whut.cs.bi.biz.service.ITemplateVariableService;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.ISysDeptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Spy
    @InjectMocks
    private ReportServiceImpl reportService;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private IReportDataService reportDataService;

    @Mock
    private IReportTemplateService reportTemplateService;

    @Mock
    private ITemplateVariableService templateVariableService;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private ISysDeptService deptService;

    @Mock
    private IFileMapService fileMapService;

    @Mock
    private IProjectService projectService;

    /**
     * 测试场景：管理员查询报告列表时，应查询全部报告数据。
     */
    @Test
    void testSelectReportList_Success() {
        Report query = new Report();
        SysUser currentUser = new SysUser();
        currentUser.setUserId(100L);
        List<Report> expected = Collections.singletonList(new Report());

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class);
             MockedStatic<PageUtils> pageUtilsMock = org.mockito.Mockito.mockStatic(PageUtils.class)) {
            shiroMock.when(ShiroUtils::getUserId).thenReturn(100L);
            pageUtilsMock.when(PageUtils::startPage).thenAnswer(invocation -> null);

            when(sysUserMapper.selectUserRoleByUserId(100L)).thenReturn(Collections.singletonList("admin"));
            when(sysUserMapper.selectUserById(100L)).thenReturn(currentUser);
            when(reportMapper.selectReportList(query, null, null, null)).thenReturn(expected);

            List<Report> result = reportService.selectReportList(query);

            assertEquals(1, result.size());
            verify(reportMapper, times(1)).selectReportList(query, null, null, null);
            pageUtilsMock.verify(PageUtils::startPage, times(1));
        }
    }

    /**
     * 测试场景：普通用户查询报告时，底层查询异常应向上抛出。
     */
    @Test
    void testSelectReportList_Exception() {
        Report query = new Report();
        SysUser currentUser = new SysUser();
        currentUser.setUserId(200L);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class);
             MockedStatic<PageUtils> pageUtilsMock = org.mockito.Mockito.mockStatic(PageUtils.class)) {
            shiroMock.when(ShiroUtils::getUserId).thenReturn(200L);
            pageUtilsMock.when(PageUtils::startPage).thenAnswer(invocation -> null);

            when(sysUserMapper.selectUserRoleByUserId(200L)).thenReturn(Collections.singletonList("common_user"));
            when(sysUserMapper.selectUserById(200L)).thenReturn(currentUser);
            when(reportMapper.selectReportList(query, 200L, null, null)).thenThrow(new RuntimeException("db error"));

            assertThrows(RuntimeException.class, () -> reportService.selectReportList(query));
            pageUtilsMock.verify(PageUtils::startPage, times(1));
        }
    }

    /**
     * 测试场景：新增报告成功后应触发报告数据生成。
     */
    @Test
    void testInsertReport_Success() {
        Report report = new Report();
        report.setId(11L);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            when(reportMapper.insertReport(report)).thenReturn(1);
            doReturn(1).when(reportService).generateReportData(11L);

            int rows = reportService.insertReport(report);

            assertEquals(1, rows);
            assertEquals("tester", report.getCreateBy());
            assertEquals(0, report.getStatus());
            verify(reportMapper, times(1)).insertReport(report);
            verify(reportService, times(1)).generateReportData(11L);
        }
    }

    /**
     * 测试场景：新增报告后生成报告数据失败时应抛出异常。
     */
    @Test
    void testInsertReport_Exception() {
        Report report = new Report();
        report.setId(12L);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            when(reportMapper.insertReport(report)).thenReturn(1);
            doThrow(new RuntimeException("generate fail")).when(reportService).generateReportData(12L);

            assertThrows(RuntimeException.class, () -> reportService.insertReport(report));
        }
    }

    /**
     * 测试场景：修改报告模板ID发生变化时，应删除旧数据并重建报告数据。
     */
    @Test
    void testUpdateReport_Success() {
        Report input = new Report();
        input.setId(20L);
        input.setReportTemplateId(2L);

        Report oldReport = new Report();
        oldReport.setId(20L);
        oldReport.setReportTemplateId(1L);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("updater");

            when(reportMapper.selectReportById(20L)).thenReturn(oldReport);
            when(reportMapper.updateReport(input)).thenReturn(1);
            doReturn(2).when(reportService).generateReportData(20L);

            int rows = reportService.updateReport(input);

            assertEquals(1, rows);
            assertEquals("updater", input.getUpdateBy());
            verify(reportDataService, times(1)).deleteReportDataByReportId(20L);
            verify(reportService, times(1)).generateReportData(20L);
            verify(reportMapper, times(1)).updateReport(input);
        }
    }

    /**
     * 测试场景：修改报告时数据库更新异常，应抛出异常。
     */
    @Test
    void testUpdateReport_Exception() {
        Report input = new Report();
        input.setId(21L);
        input.setReportTemplateId(2L);

        Report oldReport = new Report();
        oldReport.setId(21L);
        oldReport.setReportTemplateId(1L);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("updater");

            when(reportMapper.selectReportById(21L)).thenReturn(oldReport);
            when(reportMapper.updateReport(input)).thenThrow(new RuntimeException("update fail"));

            assertThrows(RuntimeException.class, () -> reportService.updateReport(input));
        }
    }

    /**
     * 测试场景：生成报告数据时模板变量存在，应批量写入ReportData。
     */
    @Test
    void testGenerateReportData_Success() {
        Report report = new Report();
        report.setId(30L);
        report.setReportTemplateId(300L);

        ReportTemplate template = new ReportTemplate();
        template.setId(300L);

        TemplateVariable v1 = new TemplateVariable();
        v1.setName("project-name");
        v1.setType(0);

        TemplateVariable v2 = new TemplateVariable();
        v2.setName("image-1");
        v2.setType(1);

        SysUser sysUser = new SysUser();
        sysUser.setLoginName("creator");

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getSysUser).thenReturn(sysUser);

            when(reportMapper.selectReportById(30L)).thenReturn(report);
            when(reportTemplateService.selectReportTemplateById(300L)).thenReturn(template);
            when(templateVariableService.selectTemplateVariableList(any(TemplateVariable.class))).thenReturn(Arrays.asList(v1, v2));
            when(reportDataService.batchInsertReportData(any(List.class))).thenReturn(2);

            int rows = reportService.generateReportData(30L);

            assertEquals(2, rows);
            ArgumentCaptor<List<ReportData>> captor = ArgumentCaptor.forClass(List.class);
            verify(reportDataService, times(1)).batchInsertReportData(captor.capture());
            assertEquals(2, captor.getValue().size());
            assertEquals("project-name", captor.getValue().get(0).getKey());
            assertEquals("creator", captor.getValue().get(0).getCreateBy());
        }
    }

    /**
     * 测试场景：生成报告数据时报告不存在，应直接返回0并拦截后续流程。
     */
    @Test
    void testGenerateReportData_ReportNotFound() {
        when(reportMapper.selectReportById(31L)).thenReturn(null);

        int rows = reportService.generateReportData(31L);

        assertEquals(0, rows);
        verify(reportTemplateService, times(0)).selectReportTemplateById(any());
        verify(reportDataService, times(0)).batchInsertReportData(any(List.class));
    }

    /**
     * 测试场景：克隆报告成功时，应复制主表与明细，并克隆图片文件映射。
     */
    @Test
    void testCloneReport_Success() {
        Report source = new Report();
        source.setId(40L);
        source.setName("检测报告A");
        source.setProjectId(1L);
        source.setTaskIds("1,2");
        source.setReportTemplateId(99L);

        ReportData imageData = new ReportData();
        imageData.setType(1);
        imageData.setKey("img-key");
        imageData.setValue("101,102");

        ReportData textData = new ReportData();
        textData.setType(0);
        textData.setKey("txt-key");
        textData.setValue("text-value");

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("cloner");

            when(reportMapper.selectReportById(40L)).thenReturn(source);
            when(reportMapper.insertReport(any(Report.class))).thenAnswer(invocation -> {
                Report inserted = invocation.getArgument(0);
                inserted.setId(400L);
                return 1;
            });
            when(reportDataService.selectReportDataByReportId(40L)).thenReturn(Arrays.asList(imageData, textData));

            FileMap fm1 = new FileMap();
            fm1.setId(1001);
            FileMap fm2 = new FileMap();
            fm2.setId(1002);
            when(fileMapService.copyFile(101L)).thenReturn(fm1);
            when(fileMapService.copyFile(102L)).thenReturn(fm2);

            int rows = reportService.cloneReport(40L);

            assertEquals(1, rows);
            verify(reportMapper, times(1)).insertReport(any(Report.class));
            verify(fileMapService, times(1)).copyFile(101L);
            verify(fileMapService, times(1)).copyFile(102L);
            verify(reportDataService, times(1)).batchInsertReportData(any(List.class));
        }
    }

    /**
     * 测试场景：克隆报告时源报告不存在，应抛出业务异常。
     */
    @Test
    void testCloneReport_SourceNotFound() {
        when(reportMapper.selectReportById(41L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> reportService.cloneReport(41L));

        assertEquals("源报告不存在", ex.getMessage());
        verify(reportMapper, times(0)).insertReport(any(Report.class));
    }
}
