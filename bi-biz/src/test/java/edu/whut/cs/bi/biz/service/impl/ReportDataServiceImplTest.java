package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.ReportDataMapper;
import edu.whut.cs.bi.biz.service.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportDataServiceImplTest {

    @InjectMocks
    private ReportDataServiceImpl reportDataService;

    @Mock
    private ReportDataMapper reportDataMapper;
    @Mock
    private IPropertyService propertyService;
    @Mock
    private IBuildingService buildingService;
    @Mock
    private IFileMapService iFileMapService;
    @Mock
    private DiseaseController diseaseController;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private ITaskService taskService;
    @Mock
    private IDiseaseService diseaseService;
    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private BiObjectMapper biObjectMapper;
    @Mock
    private IProjectService projectService;
    @Mock
    private DiseaseMapper diseaseMapper;
    @Mock
    private IBridgeCardService bridgeCardService;

    /**
     * 测试 exportPropertyWord：建筑和属性存在时成功导出并上传文档。
     */
    @Test
    void testExportPropertyWord_Success() {
        Long bid = 1L;
        Building building = new Building();
        building.setId(bid);
        building.setRootPropertyId(10L);

        Property property = new Property();
        property.setId(10L);
        property.setName("测试桥梁");

        XWPFDocument document = new XWPFDocument();
        HttpServletResponse response = new MockHttpServletResponse();

        when(buildingService.selectBuildingById(bid)).thenReturn(building);
        when(propertyService.selectPropertyById(10L)).thenReturn(property);
        when(bridgeCardService.generateBridgeCardDocument(bid, ReportTemplateTypes.COMBINED_BRIDGE)).thenReturn(document);
        when(iFileMapService.handleFileUpload(any())).thenReturn(new FileMap());

        reportDataService.exportPropertyWord(bid, response);

        verify(buildingService, times(1)).selectBuildingById(bid);
        verify(propertyService, times(1)).selectPropertyById(10L);
        verify(bridgeCardService, times(1)).generateBridgeCardDocument(bid, ReportTemplateTypes.COMBINED_BRIDGE);
        verify(iFileMapService, times(1)).handleFileUpload(any());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", response.getContentType());
    }

    /**
     * 测试 exportPropertyWord：文档生成异常时抛出运行时异常。
     */
    @Test
    void testExportPropertyWord_Exception() {
        Long bid = 1L;
        Building building = new Building();
        building.setId(bid);
        building.setRootPropertyId(10L);

        Property property = new Property();
        property.setId(10L);
        property.setName("测试桥梁");

        HttpServletResponse response = new MockHttpServletResponse();

        when(buildingService.selectBuildingById(bid)).thenReturn(building);
        when(propertyService.selectPropertyById(10L)).thenReturn(property);
        when(bridgeCardService.generateBridgeCardDocument(bid, ReportTemplateTypes.COMBINED_BRIDGE))
                .thenThrow(new RuntimeException("生成失败"));

        assertThrows(RuntimeException.class, () -> reportDataService.exportPropertyWord(bid, response));
    }

    /**
     * 测试 saveReportDataBatch：同时包含新增、更新及图片差异删除时执行成功。
     */
    @Test
    void testSaveReportDataBatch_Success() {
        Long reportId = 100L;

        ReportData existing = new ReportData();
        existing.setId(1L);
        existing.setKey("imgKey");
        existing.setType(1);
        existing.setValue("1,2");

        ReportData updateData = new ReportData();
        updateData.setKey("imgKey");
        updateData.setType(1);
        updateData.setValue("2,3");

        ReportData insertData = new ReportData();
        insertData.setKey("textKey");
        insertData.setType(0);
        insertData.setValue("newValue");

        when(reportDataMapper.selectReportDataByReportId(reportId)).thenReturn(Collections.singletonList(existing));
        when(reportDataMapper.batchInsertReportData(anyList())).thenReturn(1);
        when(reportDataMapper.updateReportData(any(ReportData.class))).thenReturn(1);
        when(iFileMapService.deleteFileMapById(1L)).thenReturn(1);

        int result = reportDataService.saveReportDataBatch(reportId, Arrays.asList(updateData, insertData));

        assertEquals(2, result);
        verify(reportDataMapper, times(1)).batchInsertReportData(anyList());
        verify(reportDataMapper, times(1)).updateReportData(any(ReportData.class));
        verify(iFileMapService, times(1)).deleteFileMapById(1L);
    }

    /**
     * 测试 saveReportDataBatch：更新数据库异常时向上抛出运行时异常。
     */
    @Test
    void testSaveReportDataBatch_Exception() {
        Long reportId = 100L;

        ReportData existing = new ReportData();
        existing.setId(1L);
        existing.setKey("k1");

        ReportData updateData = new ReportData();
        updateData.setKey("k1");
        updateData.setValue("v2");

        when(reportDataMapper.selectReportDataByReportId(reportId)).thenReturn(Collections.singletonList(existing));
        when(reportDataMapper.updateReportData(any(ReportData.class))).thenThrow(new RuntimeException("DB错误"));

        assertThrows(RuntimeException.class,
                () -> reportDataService.saveReportDataBatch(reportId, Collections.singletonList(updateData)));
    }

    /**
     * 测试 getDiseaseComponentData：多层级对象与病害数据正常时返回任务病害结果。
     */
    @Test
    void testGetDiseaseComponentData_Success() {
        Report report = new Report();
        report.setId(1L);
        report.setTaskIds("11");

        Building building = new Building();
        building.setId(10L);
        building.setName("桥梁A");
        building.setRootObjectId(100L);

        Project project = new Project();
        project.setYear(2024);

        Task task = new Task();
        task.setId(11L);
        task.setBuilding(building);
        task.setProject(project);

        BiObject subBridge = new BiObject();
        subBridge.setId(100L);
        subBridge.setName("根节点");

        BiObject level2 = new BiObject();
        level2.setId(200L);
        level2.setParentId(100L);

        BiObject level3 = new BiObject();
        level3.setId(300L);
        level3.setParentId(200L);

        BiObject level4 = new BiObject();
        level4.setId(400L);
        level4.setParentId(300L);
        level4.setName("构件1");

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setName("裂缝");

        Disease disease = new Disease();
        disease.setBiObjectId(400L);
        disease.setDiseaseTypeId(500L);
        disease.setDiseaseType(diseaseType);

        when(taskService.selectTaskById(11L)).thenReturn(task);
        when(biObjectMapper.selectBiObjectById(100L)).thenReturn(subBridge);
        when(biObjectMapper.selectChildrenById(100L)).thenReturn(new ArrayList<>(Arrays.asList(level2, level3, level4)));
        when(diseaseMapper.selectDiseaseComponentData(anyList(), eq(10L), eq(2024)))
                .thenReturn(Collections.singletonList(disease));

        Map<Long, Map<String, Object>> result = reportDataService.getDiseaseComponentData(report);

        assertEquals(1, result.size());
        assertTrue(result.containsKey(11L));
        List<Map<String, Object>> diseases = (List<Map<String, Object>>) result.get(11L).get("diseases");
        assertEquals(1, diseases.size());
        verify(taskService, times(1)).selectTaskById(11L);
        verify(diseaseMapper, times(1)).selectDiseaseComponentData(anyList(), eq(10L), eq(2024));
    }

    /**
     * 测试 getDiseaseComponentData：传入空报告对象时抛出空指针异常。
     */
    @Test
    void testGetDiseaseComponentData_Exception() {
        assertThrows(NullPointerException.class, () -> reportDataService.getDiseaseComponentData(null));
    }
}
