package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void testSelectDiseaseById_ComponentIdMissing() {
        // 中文注释：病害缺失 componentId 时应抛出业务异常
        Long diseaseId = 3L;
        Disease disease = new Disease();
        disease.setId(diseaseId);
        disease.setBiObjectId(10L);
        disease.setComponentId(null);

        BiObject biObject = new BiObject();
        biObject.setId(10L);
        biObject.setParentId(100L);

        when(diseaseMapper.selectDiseaseById(diseaseId)).thenReturn(disease);
        when(biObjectMapper.selectBiObjectById(10L)).thenReturn(biObject);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> diseaseService.selectDiseaseById(diseaseId));
        assertTrue(ex.getMessage().contains("componentId不存在"));
    }

    @Test
    void testInsertDisease_UseExistingComponentAndSetBiObjectName() {
        // 中文注释：已有构件时复用构件ID，并回填 biObjectName
        Disease disease = new Disease();
        disease.setId(101L);
        disease.setBiObjectId(11L);
        disease.setDiseaseTypeId(31L);
        disease.setType("已存在类型");
        disease.setImgNoExp(null);

        Component inputComponent = new Component();
        inputComponent.setCode("C03");
        disease.setComponent(inputComponent);

        DiseaseDetail detail = new DiseaseDetail();
        disease.setDiseaseDetails(Collections.singletonList(detail));

        BiObject biObject = new BiObject();
        biObject.setId(11L);
        biObject.setName("横梁");

        Component oldComponent = new Component();
        oldComponent.setId(301L);

        when(biObjectMapper.selectBiObjectById(11L)).thenReturn(biObject);
        when(componentService.selectComponent(any(Component.class))).thenReturn(oldComponent);
        when(diseaseMapper.insertDisease(any(Disease.class))).thenReturn(1);

        Integer result = diseaseService.insertDisease(disease);

        assertEquals(1, result);
        assertEquals(301L, disease.getComponentId());
        assertEquals("横梁", disease.getBiObjectName());
        verify(componentService, never()).insertComponent(any(Component.class));
        verify(diseaseDetailMapper, times(1)).insertDiseaseDetails(anyList());
    }

    @Test
    void testUpdateDisease_TypeOtherAndNoDetailInsert() {
        // 中文注释：病害类型为“其他”时不重写 type，且空详情不执行插入
        Disease updateParam = new Disease();
        updateParam.setId(4L);
        updateParam.setDiseaseTypeId(10L);
        updateParam.setType("保持原值");
        updateParam.setImgNoExp(null);
        updateParam.setDiseaseDetails(Collections.emptyList());

        Component inputComponent = new Component();
        inputComponent.setCode("C09");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(4L);
        old.setDiseaseTypeId(10L);
        old.setComponentId(40L);
        old.setBiObjectName("桥面");

        DiseaseType type = new DiseaseType();
        type.setId(10L);
        type.setCode("D10");
        type.setName("其他");

        Component oldComponent = new Component();
        oldComponent.setId(40L);
        oldComponent.setCode("C09");
        oldComponent.setBiObjectId(201L);

        when(diseaseMapper.selectDiseaseById(4L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(10L)).thenReturn(type);
        when(componentService.selectComponentById(40L)).thenReturn(oldComponent);
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        int result = diseaseService.updateDisease(updateParam);

        assertEquals(1, result);
        assertEquals("保持原值", updateParam.getType());
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseId(4L);
        verify(diseaseDetailMapper, never()).insertDiseaseDetails(anyList());
        verify(componentService, never()).updateComponent(any(Component.class));
    }

    @Test
    void testUpdateDisease_ChangeComponentUseExistingSelectedComponent() {
        // 中文注释：修改构件编码且数据库已存在目标构件时，直接换绑 componentId
        Disease updateParam = new Disease();
        updateParam.setId(5L);
        updateParam.setDiseaseTypeId(11L);
        updateParam.setType("原类型");
        updateParam.setImgNoExp(null);
        updateParam.setDiseaseDetails(Collections.emptyList());

        Component inputComponent = new Component();
        inputComponent.setCode("NEW-CODE");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(5L);
        old.setDiseaseTypeId(11L);
        old.setComponentId(50L);
        old.setBiObjectName("盖梁");

        DiseaseType type = new DiseaseType();
        type.setId(11L);
        type.setCode("D11");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(50L);
        oldComponent.setCode("OLD-CODE");
        oldComponent.setBiObjectId(202L);

        Component selectedComponent = new Component();
        selectedComponent.setId(888L);

        when(diseaseMapper.selectDiseaseById(5L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(11L)).thenReturn(type);
        when(componentService.selectComponentById(50L)).thenReturn(oldComponent);
        when(componentMapper.selectComponent(any(Component.class))).thenReturn(selectedComponent);
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        int result = diseaseService.updateDisease(updateParam);

        assertEquals(1, result);
        assertEquals(888L, updateParam.getComponentId());
        verify(componentService, never()).updateComponent(any(Component.class));
    }

    @Test
    void testDeleteDiseaseByDiseaseIds_EmptyInput() {
        // 中文注释：批量删除病害ID为空时直接返回0
        int result1 = diseaseService.deleteDiseaseByDiseaseIds(null);
        int result2 = diseaseService.deleteDiseaseByDiseaseIds("");

        assertEquals(0, result1);
        assertEquals(0, result2);
        verify(diseaseMapper, never()).deleteDiseaseByIds(any(String[].class));
    }

    @Test
    void testDeleteDiseaseByDiseaseIds_Success() {
        // 中文注释：批量删除病害ID正常流程
        when(diseaseMapper.deleteDiseaseByIds(any(String[].class))).thenReturn(2);

        int result = diseaseService.deleteDiseaseByDiseaseIds("6,7");

        assertEquals(2, result);
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseIds(any(Long[].class));
        verify(diseaseMapper, times(1)).deleteDiseaseByIds(any(String[].class));
    }

    @Test
    void testComputeDeductPoints_AllValidAndInvalidScale() {
        // 中文注释：覆盖所有 maxScale 的有效映射和非法 scale 分支
        assertEquals(0, diseaseService.computeDeductPoints(3, 1));
        assertEquals(20, diseaseService.computeDeductPoints(3, 2));
        assertEquals(35, diseaseService.computeDeductPoints(3, 3));

        assertEquals(0, diseaseService.computeDeductPoints(4, 1));
        assertEquals(25, diseaseService.computeDeductPoints(4, 2));
        assertEquals(40, diseaseService.computeDeductPoints(4, 3));
        assertEquals(50, diseaseService.computeDeductPoints(4, 4));

        assertEquals(0, diseaseService.computeDeductPoints(5, 1));
        assertEquals(35, diseaseService.computeDeductPoints(5, 2));
        assertEquals(45, diseaseService.computeDeductPoints(5, 3));
        assertEquals(60, diseaseService.computeDeductPoints(5, 4));
        assertEquals(100, diseaseService.computeDeductPoints(5, 5));

        assertThrows(IllegalArgumentException.class, () -> diseaseService.computeDeductPoints(3, 4));
        assertThrows(IllegalArgumentException.class, () -> diseaseService.computeDeductPoints(4, 5));
        assertThrows(IllegalArgumentException.class, () -> diseaseService.computeDeductPoints(5, 6));
    }

    @Test
    void testDeleteDiseaseById_Success() {
        // 中文注释：按ID删除病害时，先删详情后删主表
        Disease disease = new Disease();
        disease.setId(9L);

        when(diseaseMapper.selectDiseaseById(9L)).thenReturn(disease);
        when(diseaseMapper.deleteDiseaseById(9L)).thenReturn(1);

        int result = diseaseService.deleteDiseaseById(9L);

        assertEquals(1, result);
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseId(9L);
        verify(diseaseMapper, times(1)).deleteDiseaseById(9L);
    }

    @Test
    void testSelectDiseaseList_WithBiObjectFilter_ImageSplitAndImgNoExpParsed() {
        // 中文注释：按biObjectId过滤查询，覆盖子节点查询、图片分流、imgNoExp解析成功
        Disease query = new Disease();
        query.setBiObjectId(1000L);
        query.setProjectId(2000L);

        BiObject child = new BiObject();
        child.setId(1001L);

        Disease ds = new Disease();
        ds.setId(1L);
        ds.setComponentId(20L);
        ds.setBiObjectId(30L);
        ds.setDiseaseTypeId(40L);
        ds.setImgNoExp("[\"A\",\"B\"]");

        Component component = new Component();
        component.setId(20L);
        component.setName("C01#腹板");
        component.setBiObjectId(300L);

        BiObject parent = new BiObject();
        parent.setId(301L);
        parent.setName("上部结构");
        parent.setParentId(302L);

        BiObject grand = new BiObject();
        grand.setId(302L);
        grand.setName("桥梁");

        BiObject biObject = new BiObject();
        biObject.setId(30L);
        biObject.setName("主梁");

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(40L);
        diseaseType.setName("裂缝");

        DiseaseDetail detail = new DiseaseDetail();
        detail.setDiseaseId(1L);

        Map<String, Object> normalImg = new HashMap<>();
        normalImg.put("type", 1);
        normalImg.put("url", "u1");

        Map<String, Object> adImg = new HashMap<>();
        adImg.put("type", 7);
        adImg.put("url", "u7");

        when(biObjectMapper.selectChildrenById(1000L)).thenReturn(Collections.singletonList(child));
        when(diseaseMapper.selectDiseaseListByBiObjectIds(anyList(), eq(2000L))).thenReturn(Collections.singletonList(ds));
        when(componentService.selectComponentById(20L)).thenReturn(component);
        when(biObjectMapper.selectDirectParentById(300L)).thenReturn(parent);
        when(biObjectMapper.selectBiObjectById(302L)).thenReturn(grand);
        when(biObjectMapper.selectBiObjectById(30L)).thenReturn(biObject);
        when(diseaseTypeMapper.selectDiseaseTypeById(40L)).thenReturn(diseaseType);
        when(diseaseDetailMapper.selectDiseaseDetailList(any(DiseaseDetail.class))).thenReturn(Collections.singletonList(detail));
        when(diseaseController.getDiseaseImage(1L)).thenReturn(Arrays.asList(normalImg, adImg));

        try (MockedStatic<PageUtils> pageUtilsMock = mockStatic(PageUtils.class)) {
            pageUtilsMock.when(PageUtils::startPage).thenAnswer(inv -> null);
            List<Disease> result = diseaseService.selectDiseaseList(query);

            assertEquals(1, result.size());
            Disease out = result.get(0);
            assertNotNull(out.getComponent());
            assertEquals("主梁", out.getBindBiObjectName());
            assertEquals("裂缝", out.getBindType());
            assertEquals(1, out.getDiseaseDetails().size());
            assertEquals(Collections.singletonList("u1"), out.getImages());
            assertEquals(Collections.singletonList("u7"), out.getADImgs());
            assertEquals("A、B", out.getImgNoExp());
        }
    }

    @Test
    void testSelectDiseaseList_WithoutBiObjectFilter_ImgNoExpParseFail() {
        // 中文注释：无biObjectId查询时走普通列表，imgNoExp解析失败不抛异常
        Disease query = new Disease();
        query.setBiObjectId(null);

        Disease ds = new Disease();
        ds.setId(2L);
        ds.setComponentId(null);
        ds.setBiObjectId(31L);
        ds.setDiseaseTypeId(41L);
        ds.setImgNoExp("not-json");

        BiObject biObject = new BiObject();
        biObject.setId(31L);
        biObject.setName("桥面");

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(41L);
        diseaseType.setName("渗水");

        when(diseaseMapper.selectDiseaseList(query)).thenReturn(Collections.singletonList(ds));
        when(biObjectMapper.selectBiObjectById(31L)).thenReturn(biObject);
        when(diseaseTypeMapper.selectDiseaseTypeById(41L)).thenReturn(diseaseType);
        when(diseaseDetailMapper.selectDiseaseDetailList(any(DiseaseDetail.class))).thenReturn(Collections.emptyList());
        when(diseaseController.getDiseaseImage(2L)).thenReturn(Collections.emptyList());

        try (MockedStatic<PageUtils> pageUtilsMock = mockStatic(PageUtils.class)) {
            pageUtilsMock.when(PageUtils::startPage).thenAnswer(inv -> null);
            List<Disease> result = diseaseService.selectDiseaseList(query);

            assertEquals(1, result.size());
            Disease out = result.get(0);
            assertEquals("桥面", out.getBindBiObjectName());
            assertEquals("渗水", out.getBindType());
            assertEquals("not-json", out.getImgNoExp());
        }
    }

    @Test
    void testSelectDiseaseListForTask_SuccessWithImageSplit() {
        // 中文注释：任务查询列表覆盖构件层级组装、详情装配和图片分流
        Disease query = new Disease();

        Disease ds = new Disease();
        ds.setId(3L);
        ds.setComponentId(22L);

        Component component = new Component();
        component.setId(22L);
        component.setBiObjectId(333L);

        BiObject parent = new BiObject();
        parent.setId(334L);
        parent.setName("下部结构");
        parent.setParentId(335L);

        BiObject grand = new BiObject();
        grand.setId(335L);
        grand.setName("桥梁");

        Map<String, Object> normalImg = new HashMap<>();
        normalImg.put("type", 2);
        normalImg.put("url", "t1");

        Map<String, Object> adImg = new HashMap<>();
        adImg.put("type", 7);
        adImg.put("url", "t7");

        when(diseaseMapper.selectDiseaseList(query)).thenReturn(Collections.singletonList(ds));
        when(componentService.selectComponentById(22L)).thenReturn(component);
        when(biObjectMapper.selectDirectParentById(333L)).thenReturn(parent);
        when(biObjectMapper.selectBiObjectById(335L)).thenReturn(grand);
        when(diseaseDetailMapper.selectDiseaseDetailList(any(DiseaseDetail.class))).thenReturn(new ArrayList<>());
        when(diseaseController.getDiseaseImage(3L)).thenReturn(Arrays.asList(normalImg, adImg));

        List<Disease> result = diseaseService.selectDiseaseListForTask(query);

        assertEquals(1, result.size());
        Disease out = result.get(0);
        assertNotNull(out.getComponent());
        assertEquals(Collections.singletonList("t1"), out.getImages());
        assertEquals(Collections.singletonList("t7"), out.getADImgs());
        assertNotNull(out.getDiseaseDetails());
    }

    @Test
    void testSelectDiseaseListForApi_SuccessWithImageSplit() {
        // 中文注释：API查询列表覆盖构件层级组装、详情装配和图片分流
        Disease query = new Disease();

        Disease ds = new Disease();
        ds.setId(13L);
        ds.setComponentId(122L);

        Component component = new Component();
        component.setId(122L);
        component.setBiObjectId(433L);

        BiObject parent = new BiObject();
        parent.setId(434L);
        parent.setName("上部结构");
        parent.setParentId(435L);

        BiObject grand = new BiObject();
        grand.setId(435L);
        grand.setName("桥梁");

        Map<String, Object> normalImg = new HashMap<>();
        normalImg.put("type", 1);
        normalImg.put("url", "api1");

        Map<String, Object> adImg = new HashMap<>();
        adImg.put("type", 7);
        adImg.put("url", "api7");

        when(diseaseMapper.selectDiseaseList(query)).thenReturn(Collections.singletonList(ds));
        when(componentService.selectComponentById(122L)).thenReturn(component);
        when(biObjectMapper.selectDirectParentById(433L)).thenReturn(parent);
        when(biObjectMapper.selectBiObjectById(435L)).thenReturn(grand);
        when(diseaseDetailMapper.selectDiseaseDetailList(any(DiseaseDetail.class))).thenReturn(Collections.emptyList());
        when(diseaseController.getDiseaseImage(13L)).thenReturn(Arrays.asList(normalImg, adImg));

        List<Disease> result = diseaseService.selectDiseaseListForApi(query);

        assertEquals(1, result.size());
        Disease out = result.get(0);
        assertNotNull(out.getComponent());
        assertEquals(Collections.singletonList("api1"), out.getImages());
        assertEquals(Collections.singletonList("api7"), out.getADImgs());
        assertNotNull(out.getDiseaseDetails());
    }

    @Test
    void testSelectDiseaseListForZip_EmptyListFastReturn() {
        // 中文注释：ZIP导出查询为空时应快速返回，不执行后续批量查询
        Disease query = new Disease();
        when(diseaseMapper.selectDiseaseList(query)).thenReturn(Collections.emptyList());

        List<Disease> result = diseaseService.selectDiseaseListForZip(query);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(diseaseDetailMapper, never()).selectDiseaseDetailsByDiseaseIds(anyList());
        verify(componentService, never()).selectComponentsByIds(anyList());
        verify(attachmentService, never()).getAttachmentBySubjectIds(anyList());
    }

    @Test
    void testSelectDiseaseListForZip_SuccessAttachmentPathAndTypeSplit() {
        // 中文注释：ZIP导出查询覆盖附件路径拼接、type=7分流和disease前缀过滤
        Disease query = new Disease();

        Disease ds = new Disease();
        ds.setId(1L);
        ds.setComponentId(20L);
        ds.setBiObjectId(501L);
        ds.setBuildingId(100L);

        DiseaseDetail detail = new DiseaseDetail();
        detail.setDiseaseId(1L);

        Component component = new Component();
        component.setId(20L);
        component.setBiObjectId(500L);

        BiObject direct1 = new BiObject();
        direct1.setId(501L);
        direct1.setParentId(600L);

        BiObject direct2 = new BiObject();
        direct2.setId(500L);
        direct2.setParentId(600L);

        BiObject parent = new BiObject();
        parent.setId(600L);
        parent.setName("上部结构");
        parent.setParentId(700L);

        BiObject grand = new BiObject();
        grand.setId(700L);
        grand.setName("桥梁");

        Attachment a1 = new Attachment();
        a1.setId(55L);
        a1.setSubjectId(1L);
        a1.setName("disease_abc.jpg");
        a1.setType(1);

        Attachment a2 = new Attachment();
        a2.setId(56L);
        a2.setSubjectId(1L);
        a2.setName("disease_ad.jpg");
        a2.setType(7);

        Attachment a3 = new Attachment();
        a3.setId(57L);
        a3.setSubjectId(1L);
        a3.setName("other.jpg");
        a3.setType(1);

        when(diseaseMapper.selectDiseaseList(query)).thenReturn(Collections.singletonList(ds));
        when(diseaseDetailMapper.selectDiseaseDetailsByDiseaseIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(detail));
        when(componentService.selectComponentsByIds(Collections.singletonList(20L))).thenReturn(Collections.singletonList(component));
        when(biObjectMapper.selectBiObjectsByIds(anyList()))
                .thenReturn(Arrays.asList(direct1, direct2))
                .thenReturn(Collections.singletonList(parent))
                .thenReturn(Collections.singletonList(grand));
        when(attachmentService.getAttachmentBySubjectIds(Collections.singletonList(1L))).thenReturn(Arrays.asList(a1, a2, a3));

        List<Disease> result = diseaseService.selectDiseaseListForZip(query);

        assertEquals(1, result.size());
        Disease out = result.get(0);
        assertNotNull(out.getComponent());
        assertEquals("上部结构", out.getComponent().getParentObjectName());
        assertEquals("桥梁", out.getComponent().getGrandObjectName());
        assertEquals(1, out.getDiseaseDetails().size());
        assertEquals(Collections.singletonList("100/disease/images/55_abc.jpg"), out.getImages());
        assertEquals(Collections.singletonList("100/disease/images/56_ad.jpg"), out.getADImgs());
    }

    @Test
    void testNewUpdateDisease_NoRebindAndEmptyDetails() {
        // 中文注释：不换绑且详情为空时，不更新构件、不插入详情
        Disease updateParam = new Disease();
        updateParam.setId(21L);
        updateParam.setDiseaseTypeId(31L);
        updateParam.setBiObjectId(501L);
        updateParam.setType("原类型");
        updateParam.setImgNoExp(null);
        updateParam.setDiseaseDetails(Collections.emptyList());

        Component inputComponent = new Component();
        inputComponent.setCode("C01");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(21L);
        old.setDiseaseTypeId(31L);
        old.setComponentId(601L);
        old.setBiObjectId(501L);
        old.setBiObjectName("腹板");

        DiseaseType type = new DiseaseType();
        type.setId(31L);
        type.setCode("D31");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(601L);
        oldComponent.setCode("C01");
        oldComponent.setBiObjectId(501L);

        when(diseaseMapper.selectDiseaseById(21L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(31L)).thenReturn(type);
        when(componentService.selectComponentById(601L)).thenReturn(oldComponent);
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        int result = diseaseService.newUpdateDisease(updateParam);

        assertEquals(1, result);
        assertEquals("D31#裂缝", updateParam.getType());
        verify(componentMapper, never()).selectComponent(any(Component.class));
        verify(componentService, never()).updateComponent(any(Component.class));
        verify(diseaseDetailMapper, times(1)).deleteDiseaseDetailByDiseaseId(21L);
        verify(diseaseDetailMapper, never()).insertDiseaseDetails(anyList());
    }

    @Test
    void testNewUpdateDisease_RebindWithExistingComponent() {
        // 中文注释：换绑且目标构件已存在时，直接切换componentId
        Disease updateParam = new Disease();
        updateParam.setId(22L);
        updateParam.setDiseaseTypeId(32L);
        updateParam.setBiObjectId(777L);
        updateParam.setImgNoExp(null);
        updateParam.setDiseaseDetails(Collections.emptyList());

        Component inputComponent = new Component();
        inputComponent.setCode("NEW-CODE");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(22L);
        old.setDiseaseTypeId(32L);
        old.setComponentId(602L);
        old.setBiObjectId(502L);
        old.setBiObjectName("盖梁");

        DiseaseType type = new DiseaseType();
        type.setId(32L);
        type.setCode("D32");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(602L);
        oldComponent.setCode("OLD-CODE");
        oldComponent.setBiObjectId(502L);

        Component selectedComponent = new Component();
        selectedComponent.setId(999L);

        when(diseaseMapper.selectDiseaseById(22L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(32L)).thenReturn(type);
        when(componentService.selectComponentById(602L)).thenReturn(oldComponent);
        when(componentMapper.selectComponent(any(Component.class))).thenReturn(selectedComponent);
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        int result = diseaseService.newUpdateDisease(updateParam);

        assertEquals(1, result);
        assertEquals(999L, updateParam.getComponentId());
        verify(componentService, never()).updateComponent(any(Component.class));
    }

    @Test
    void testNewUpdateDisease_RebindWithoutExistingComponent_UpdateOldComponent() {
        // 中文注释：换绑且目标构件不存在时，更新旧构件
        Disease updateParam = new Disease();
        updateParam.setId(23L);
        updateParam.setDiseaseTypeId(33L);
        updateParam.setBiObjectId(778L);
        updateParam.setImgNoExp(null);
        updateParam.setDiseaseDetails(Collections.singletonList(new DiseaseDetail()));

        Component inputComponent = new Component();
        inputComponent.setCode("X1");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(23L);
        old.setDiseaseTypeId(33L);
        old.setComponentId(603L);
        old.setBiObjectId(503L);
        old.setBiObjectName("桥面");

        DiseaseType type = new DiseaseType();
        type.setId(33L);
        type.setCode("D33");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(603L);
        oldComponent.setCode("OLD");
        oldComponent.setBiObjectId(503L);

        when(diseaseMapper.selectDiseaseById(23L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(33L)).thenReturn(type);
        when(componentService.selectComponentById(603L)).thenReturn(oldComponent);
        when(componentMapper.selectComponent(any(Component.class))).thenReturn(null);
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = diseaseService.newUpdateDisease(updateParam);

            assertEquals(1, result);
            verify(componentService, times(1)).updateComponent(any(Component.class));
            verify(diseaseDetailMapper, times(1)).insertDiseaseDetails(anyList());
        }
    }

    @Test
    void testNewUpdateDisease_ImgNoExpConvertSuccess() {
        // 中文注释：图片编号转换成功后更新病害
        Disease updateParam = new Disease();
        updateParam.setId(24L);
        updateParam.setDiseaseTypeId(34L);
        updateParam.setBiObjectId(504L);
        updateParam.setImgNoExp("A、B");
        updateParam.setDiseaseDetails(Collections.emptyList());

        Component inputComponent = new Component();
        inputComponent.setCode("C11");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(24L);
        old.setDiseaseTypeId(34L);
        old.setComponentId(604L);
        old.setBiObjectId(504L);

        DiseaseType type = new DiseaseType();
        type.setId(34L);
        type.setCode("D34");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(604L);
        oldComponent.setCode("C11");
        oldComponent.setBiObjectId(504L);

        when(diseaseMapper.selectDiseaseById(24L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(34L)).thenReturn(type);
        when(componentService.selectComponentById(604L)).thenReturn(oldComponent);
        when(diseaseMapper.updateDisease(updateParam)).thenReturn(1);

        try (MockedStatic<ReadFileServiceImpl> staticMock = mockStatic(ReadFileServiceImpl.class)) {
            staticMock.when(() -> ReadFileServiceImpl.convertToDbFormat(anyList())).thenReturn("[\"A\",\"B\"]");

            int result = diseaseService.newUpdateDisease(updateParam);

            assertEquals(1, result);
            assertEquals("[\"A\",\"B\"]", updateParam.getImgNoExp());
        }
    }

    @Test
    void testNewUpdateDisease_ImgNoExpConvertFail() {
        // 中文注释：图片编号转换失败应抛出异常
        Disease updateParam = new Disease();
        updateParam.setId(25L);
        updateParam.setDiseaseTypeId(35L);
        updateParam.setBiObjectId(505L);
        updateParam.setImgNoExp("X、Y");
        updateParam.setDiseaseDetails(Collections.emptyList());

        Component inputComponent = new Component();
        inputComponent.setCode("C12");
        updateParam.setComponent(inputComponent);

        Disease old = new Disease();
        old.setId(25L);
        old.setDiseaseTypeId(35L);
        old.setComponentId(605L);
        old.setBiObjectId(505L);

        DiseaseType type = new DiseaseType();
        type.setId(35L);
        type.setCode("D35");
        type.setName("裂缝");

        Component oldComponent = new Component();
        oldComponent.setId(605L);
        oldComponent.setCode("C12");
        oldComponent.setBiObjectId(505L);

        when(diseaseMapper.selectDiseaseById(25L)).thenReturn(old);
        when(diseaseTypeMapper.selectDiseaseTypeById(35L)).thenReturn(type);
        when(componentService.selectComponentById(605L)).thenReturn(oldComponent);

        try (MockedStatic<ReadFileServiceImpl> staticMock = mockStatic(ReadFileServiceImpl.class)) {
            staticMock.when(() -> ReadFileServiceImpl.convertToDbFormat(anyList()))
                    .thenThrow(new RuntimeException("非法"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> diseaseService.newUpdateDisease(updateParam));
            assertTrue(ex.getMessage().contains("图片编号格式错误"));
        }
    }
}