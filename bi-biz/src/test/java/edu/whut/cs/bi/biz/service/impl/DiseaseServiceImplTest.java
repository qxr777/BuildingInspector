package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import com.ruoyi.system.service.ISysDictDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiseaseServiceImplTest {

    @InjectMocks
    private DiseaseServiceImpl diseaseService;

    @Mock
    private DiseaseMapper diseaseMapper;
    @Mock
    private DiseaseTypeMapper diseaseTypeMapper;
    @Mock
    private IComponentService componentService;
    @Mock
    private BiObjectMapper biObjectMapper;
    @Mock
    private IFileMapService fileMapService;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private DiseaseDetailMapper diseaseDetailMapper;
    @Mock
    private DiseaseController diseaseController;
    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private IDiseaseTypeService diseaseTypeService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ITaskService taskService;
    @Mock
    private IProjectService projectService;
    @Mock
    private ComponentMapper componentMapper;
    @Mock
    private FileMapController fileMapController;
    @Mock
    private IBuildingService buildingService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ISysDictDataService sysDictDataService;
    @Mock
    private IPropertyService propertyService;

    @Test
    void testSelectDiseaseById_Success() {
        // 中文注释：查询病害详情成功，校验级联对象和详情列表被正确组装
        Long diseaseId = 1L;
        Disease disease = new Disease();
        disease.setId(diseaseId);
        disease.setBiObjectId(10L);
        disease.setComponentId(20L);

        BiObject biObject = new BiObject();
        biObject.setId(10L);
        biObject.setParentId(100L);
        biObject.setName("主梁");

        BiObject parentBiObject = new BiObject();
        parentBiObject.setId(100L);
        parentBiObject.setName("上部结构");

        Component component = new Component();
        component.setId(20L);
        component.setName("C01#腹板");

        DiseaseDetail detail = new DiseaseDetail();
        detail.setDiseaseId(diseaseId);
        detail.setLength1(BigDecimal.ONE);

        when(diseaseMapper.selectDiseaseById(diseaseId)).thenReturn(disease);
        when(biObjectMapper.selectBiObjectById(10L)).thenReturn(biObject);
        when(biObjectMapper.selectBiObjectById(100L)).thenReturn(parentBiObject);
        when(componentService.selectComponentById(20L)).thenReturn(component);
        when(diseaseDetailMapper.selectDiseaseDetailList(any(DiseaseDetail.class))).thenReturn(Collections.singletonList(detail));

        Disease result = diseaseService.selectDiseaseById(diseaseId);

        assertNotNull(result);
        assertEquals("上部结构——主梁", result.getBindBiObjectName());
        assertEquals("腹板", result.getBiObjectName());
        assertNotNull(result.getDiseaseDetails());
        assertEquals(1, result.getDiseaseDetails().size());
        verify(diseaseMapper, times(1)).selectDiseaseById(diseaseId);
        verify(componentService, times(1)).selectComponentById(20L);
        verify(diseaseDetailMapper, times(1)).selectDiseaseDetailList(any(DiseaseDetail.class));
    }

    @Test
    void testSelectDiseaseById_BiObjectIdMissing() {
        // 中文注释：病害缺失 biObjectId 时应抛出业务异常
        Long diseaseId = 2L;
        Disease disease = new Disease();
        disease.setId(diseaseId);
        disease.setBiObjectId(null);
        disease.setComponentId(20L);

        when(diseaseMapper.selectDiseaseById(diseaseId)).thenReturn(disease);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> diseaseService.selectDiseaseById(diseaseId));
        assertTrue(ex.getMessage().contains("biObjectId不存在"));
    }

    @Test
    void testInsertDisease_Success() {
        // 中文注释：新增病害成功，校验构件插入、病害插入、病害详情插入流程
        Disease disease = new Disease();
        disease.setId(99L);
        disease.setBiObjectId(10L);
        disease.setDiseaseTypeId(30L);
        disease.setType(null);
        disease.setImgNoExp(null);

        Component newComponent = new Component();
        newComponent.setCode("C01");
        disease.setComponent(newComponent);

        DiseaseDetail detail = new DiseaseDetail();
        disease.setDiseaseDetails(Collections.singletonList(detail));

        BiObject biObject = new BiObject();
        biObject.setId(10L);
        biObject.setName("腹板");

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(30L);
        diseaseType.setCode("DT");
        diseaseType.setName("裂缝");

        when(biObjectMapper.selectBiObjectById(10L)).thenReturn(biObject);
        when(diseaseTypeMapper.selectDiseaseTypeById(30L)).thenReturn(diseaseType);
        when(componentService.selectComponent(any(Component.class))).thenReturn((Component) null);
        doAnswer(invocation -> {
            Component c = invocation.getArgument(0);
            c.setId(200L);
            return 1;
        }).when(componentService).insertComponent(any(Component.class));
        when(diseaseMapper.insertDisease(any(Disease.class))).thenReturn(1);
        doNothing().when(diseaseDetailMapper).insertDiseaseDetails(anyList());

        Integer result = diseaseService.insertDisease(disease);

        assertEquals(1, result);
        assertEquals("DT#裂缝", disease.getType());
        assertEquals(200L, disease.getComponentId());
        verify(componentService, times(1)).insertComponent(any(Component.class));
        verify(diseaseMapper, times(1)).insertDisease(disease);
        verify(diseaseDetailMapper, times(1)).insertDiseaseDetails(anyList());
    }

    @Test
    void testInsertDisease_InvalidImgNoExp() {
        // 中文注释：新增病害时图片编号格式转换失败应抛出异常
        Disease disease = new Disease();
        disease.setId(100L);
        disease.setBiObjectId(10L);
        disease.setDiseaseTypeId(30L);
        disease.setType("T#N");
        disease.setImgNoExp("A、B");
        disease.setDiseaseDetails(Collections.singletonList(new DiseaseDetail()));

        Component component = new Component();
        component.setCode("C02");
        disease.setComponent(component);

        BiObject biObject = new BiObject();
        biObject.setId(10L);
        biObject.setName("桥面");

        when(biObjectMapper.selectBiObjectById(10L)).thenReturn(biObject);
        when(componentService.selectComponent(any(Component.class))).thenReturn(new Component() {{
            setId(300L);
        }});

        try (MockedStatic<ReadFileServiceImpl> staticMock = mockStatic(ReadFileServiceImpl.class)) {
            staticMock.when(() -> ReadFileServiceImpl.convertToDbFormat(anyList()))
                    .thenThrow(new RuntimeException("格式错误"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> diseaseService.insertDisease(disease));
            assertTrue(ex.getMessage().contains("图片编号格式错误"));
        }
    }

    @Test
    void testUpdateDisease_Success() {
        // 中文注释：修改病害成功，校验详情先删后增并执行更新
        Disease updateParam = new Disease();
        updateParam.setId(1L);
        updateParam.setDiseaseTypeId(8L);
        updateParam.setImgNoExp(null);
        updateParam.setDiseaseDetails(Collections.singletonList(new DiseaseDetail()));

        Component inputComponent = new Component();
        inputComponent.setCode("OLD-CODE");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(1L);
        old.setDiseaseTypeId(8L);
        old.setComponentId(20L);
        old.setBiObjectName("腹板");

        DiseaseType type = new DiseaseType();
        type.setId(8L);
        type.setCode("D8");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(20L);
        oldComponent.setCode("OLD-CODE");
        oldComponent.setBiObjectId(100L);

        when(diseaseMapper.selectDiseaseById(1L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(8L)).thenReturn(type);
        when(componentService.selectComponentById(20L)).thenReturn(oldComponent);
        when(diseaseDetailMapper.deleteDiseaseDetailByDiseaseId(1L)).thenReturn(1);
        doNothing().when(diseaseDetailMapper).insertDiseaseDetails(anyList());
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        int result = diseaseService.updateDisease(updateParam);

        assertEquals(1, result);
        assertEquals("D8#裂缝", updateParam.getType());
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseId(1L);
        verify(diseaseDetailMapper, times(1)).insertDiseaseDetails(anyList());
        verify(diseaseMapper, times(1)).updateDisease(updateParam);
    }

    @Test
    void testUpdateDisease_InvalidImgNoExp() {
        // 中文注释：修改病害时图片编号格式错误应抛出异常
        Disease updateParam = new Disease();
        updateParam.setId(2L);
        updateParam.setDiseaseTypeId(9L);
        updateParam.setImgNoExp("X、Y");
        updateParam.setDiseaseDetails(Collections.singletonList(new DiseaseDetail()));

        Component inputComponent = new Component();
        inputComponent.setCode("C01");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(2L);
        old.setDiseaseTypeId(9L);
        old.setComponentId(21L);
        old.setBiObjectName("桥面");

        DiseaseType type = new DiseaseType();
        type.setId(9L);
        type.setCode("D9");
        type.setName("渗水");

        Component oldComponent = new Component();
        oldComponent.setId(21L);
        oldComponent.setCode("C01");
        oldComponent.setBiObjectId(101L);

        when(diseaseMapper.selectDiseaseById(2L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(9L)).thenReturn(type);
        when(componentService.selectComponentById(21L)).thenReturn(oldComponent);
        when(diseaseDetailMapper.deleteDiseaseDetailByDiseaseId(2L)).thenReturn(1);
        doNothing().when(diseaseDetailMapper).insertDiseaseDetails(anyList());

        try (MockedStatic<ReadFileServiceImpl> staticMock = mockStatic(ReadFileServiceImpl.class)) {
            staticMock.when(() -> ReadFileServiceImpl.convertToDbFormat(anyList()))
                    .thenThrow(new RuntimeException("非法"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> diseaseService.updateDisease(updateParam));
            assertTrue(ex.getMessage().contains("图片编号格式错误"));
        }
    }

    @Test
    void testDeleteDiseaseByIds_Success() {
        // 中文注释：批量删除病害成功，校验详情和附件被联动删除并执行批量删除
        String ids = "1,2";

        Disease d1 = new Disease();
        d1.setId(1L);
        Disease d2 = new Disease();
        d2.setId(2L);

        Attachment a1 = new Attachment();
        a1.setId(11L);
        a1.setName("disease_1.jpg");
        Attachment a2 = new Attachment();
        a2.setId(12L);
        a2.setName("other_2.jpg");
        Attachment a3 = new Attachment();
        a3.setId(13L);
        a3.setName("disease_3.jpg");

        when(diseaseMapper.selectDiseaseById(1L)).thenReturn(d1);
        when(diseaseMapper.selectDiseaseById(2L)).thenReturn(d2);
        when(attachmentService.getAttachmentList(1L)).thenReturn(Arrays.asList(a1, a2));
        when(attachmentService.getAttachmentList(2L)).thenReturn(Collections.singletonList(a3));
        when(diseaseMapper.deleteDiseaseByIds(any(String[].class))).thenReturn(2);

        int result = diseaseService.deleteDiseaseByIds(ids);

        assertEquals(2, result);
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseId(1L);
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseId(2L);
        verify(attachmentService, times(1)).deleteAttachmentById(11L);
        verify(attachmentService, times(1)).deleteAttachmentById(13L);
        verify(diseaseMapper, times(1)).deleteDiseaseByIds(any(String[].class));
    }

    @Test
    void testDeleteDiseaseByIds_DiseaseNotFound() {
        // 中文注释：批量删除时若病害不存在应在异步汇总阶段抛出异常
        when(diseaseMapper.selectDiseaseById(1L)).thenReturn(null);

        assertThrows(CompletionException.class, () -> diseaseService.deleteDiseaseByIds("1"));
        verify(diseaseMapper, never()).deleteDiseaseByIds(any(String[].class));
    }

    @Test
    void testComputeDeductPoints_Success() {
        // 中文注释：扣分计算正常场景，验证分值映射正确
        int result = diseaseService.computeDeductPoints(5, 4);
        assertEquals(60, result);
    }

    @Test
    void testComputeDeductPoints_InvalidMaxScale() {
        // 中文注释：扣分计算异常场景，非法 maxScale 应抛出参数异常
        assertThrows(IllegalArgumentException.class, () -> diseaseService.computeDeductPoints(6, 1));
    }
}