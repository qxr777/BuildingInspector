package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import com.ruoyi.common.utils.ShiroUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiseaseTypeServiceImplTest {

    private MockedStatic<ShiroUtils> shiroUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        shiroUtilsMockedStatic = org.mockito.Mockito.mockStatic(ShiroUtils.class);
        shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("testUser");
    }

    @AfterEach
    void tearDown() {
        if (shiroUtilsMockedStatic != null) {
            shiroUtilsMockedStatic.close();
        }
    }

    @InjectMocks
    private DiseaseTypeServiceImpl diseaseTypeService;

    @Mock
    private DiseaseTypeMapper diseaseTypeMapper;

    @Mock
    private DiseaseScaleMapper diseaseScaleMapper;

    @Mock
    private TODiseaseTypeMapper toDiseaseTypeMapper;

    /**
     * 测试场景：病害类型编码包含三级层级时，应截断后查询病害标度。
     */
    @Test
    void testSelectDiseaseScaleByCode_Success() {
        DiseaseScale scale = new DiseaseScale();
        scale.setScale(3);
        when(diseaseScaleMapper.selectDiseaseScaleByTypeCode("A-B")).thenReturn(List.of(scale));

        List<DiseaseScale> result = diseaseTypeService.selectDiseaseScaleByCode("A-B-C");

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getScale());
        verify(diseaseScaleMapper, times(1)).selectDiseaseScaleByTypeCode("A-B");
    }

    /**
     * 测试场景：病害类型编码为空时，应按原值透传查询病害标度。
     */
    @Test
    void testSelectDiseaseScaleByCode_EmptyCode() {
        when(diseaseScaleMapper.selectDiseaseScaleByTypeCode("")).thenReturn(List.of());

        List<DiseaseScale> result = diseaseTypeService.selectDiseaseScaleByCode("");

        assertTrue(result.isEmpty());
        verify(diseaseScaleMapper, times(1)).selectDiseaseScaleByTypeCode("");
    }

    /**
     * 测试场景：批量删除病害类型时，若均未分配标度，应逐条删除成功。
     */
    @Test
    void testDeleteDiseaseTypeByIds_Success() {
        DiseaseType d1 = new DiseaseType();
        d1.setId(1L);
        d1.setName("裂缝");
        d1.setCode("A-B-C");

        DiseaseType d2 = new DiseaseType();
        d2.setId(2L);
        d2.setName("剥落");
        d2.setCode("D-E");

        when(diseaseTypeMapper.selectDiseaseTypeById(1L)).thenReturn(d1);
        when(diseaseTypeMapper.selectDiseaseTypeById(2L)).thenReturn(d2);
        when(diseaseScaleMapper.countDiseaseScaleByTypeCode("A-B")).thenReturn(0);
        when(diseaseScaleMapper.countDiseaseScaleByTypeCode("D-E")).thenReturn(0);

        diseaseTypeService.deleteDiseaseTypeByIds("1,2");

        verify(diseaseTypeMapper, times(1)).deleteDiseaseTypeById(1L);
        verify(diseaseTypeMapper, times(1)).deleteDiseaseTypeById(2L);
    }

    /**
     * 测试场景：批量删除病害类型时，若已分配病害标度，应抛出业务异常并拦截删除。
     */
    @Test
    void testDeleteDiseaseTypeByIds_AssignedScale() {
        DiseaseType d1 = new DiseaseType();
        d1.setId(1L);
        d1.setName("裂缝");
        d1.setCode("A-B-C");

        when(diseaseTypeMapper.selectDiseaseTypeById(1L)).thenReturn(d1);
        when(diseaseScaleMapper.countDiseaseScaleByTypeCode("A-B")).thenReturn(2);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> diseaseTypeService.deleteDiseaseTypeByIds("1"));

        assertTrue(ex.getMessage().contains("已分配,不能删除"));
        verify(diseaseTypeMapper, times(0)).deleteDiseaseTypeById(1L);
    }

    /**
     * 测试场景：导入JSON文件时，病害类型已存在，能够插入标度并更新最大标度。
     */
    @Test
    void testReadJsonFile_Success() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        String json = "[{\"DiseaseType\":{\"名称\":\"裂缝\",\"对应表序号\":\"A-1\"},\"DiseaseScale\":[{\"Scale\":1,\"QualitativeDescription\":\"轻微\",\"QuantitativeDescription\":\"<1mm\"},{\"Scale\":4,\"QualitativeDescription\":\"严重\",\"QuantitativeDescription\":\">3mm\"}]}]";

        DiseaseType param = new DiseaseType();
        DiseaseType old = new DiseaseType();
        old.setId(100L);
        old.setCode("A-1");

        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(diseaseTypeMapper.selectDiseaseTypeByCode("A-1")).thenReturn(old);
        when(diseaseScaleMapper.insertDiseaseScale(any(DiseaseScale.class))).thenReturn(1);
        when(diseaseTypeMapper.updateMaxScale(100L, 4)).thenReturn(1);

        Boolean result = diseaseTypeService.readJsonFile(file, param);

        assertTrue(result);
        verify(diseaseTypeMapper, times(0)).insertDiseaseType(any(DiseaseType.class));
        verify(diseaseScaleMapper, times(2)).insertDiseaseScale(any(DiseaseScale.class));
        verify(diseaseTypeMapper, times(1)).updateMaxScale(100L, 4);
    }

    /**
     * 测试场景：导入JSON文件时，上传文件为空，应直接抛出业务异常。
     */
    @Test
    void testReadJsonFile_EmptyFile() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        DiseaseType param = new DiseaseType();

        when(file.isEmpty()).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> diseaseTypeService.readJsonFile(file, param));

        assertEquals("文件不能为空", ex.getMessage());
    }

    /**
     * 测试场景：按模板对象查询病害类型时，存在映射关系应返回对应病害列表。
     */
    @Test
    void testSelectDiseaseTypeListByTemplateObjectId_Success() {
        DiseaseType d1 = new DiseaseType();
        d1.setId(11L);
        d1.setName("裂缝");

        when(toDiseaseTypeMapper.selectByTemplateObjectId(9L)).thenReturn(List.of(11L));
        when(diseaseTypeMapper.selectDiseaseTypeListByIds(List.of(11L))).thenReturn(List.of(d1));

        List<DiseaseType> result = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(9L);

        assertEquals(1, result.size());
        assertEquals("裂缝", result.get(0).getName());
        verify(diseaseTypeMapper, times(1)).selectDiseaseTypeListByIds(List.of(11L));
    }

    /**
     * 测试场景：按模板对象查询病害类型时，无映射关系应返回空列表。
     */
    @Test
    void testSelectDiseaseTypeListByTemplateObjectId_EmptyMapping() {
        when(toDiseaseTypeMapper.selectByTemplateObjectId(9L)).thenReturn(new ArrayList<>());

        List<DiseaseType> result = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(9L);

        assertTrue(result.isEmpty());
        verify(diseaseTypeMapper, times(0)).selectDiseaseTypeListByIds(any());
    }

    /**
     * 测试场景：批量按模板对象查询病害类型时，应正确组装模板与病害类型映射结果。
     */
    @Test
    void testBatchSelectDiseaseTypeListByTemplateObjectIds_Success() {
        List<Long> templateIds = List.of(1L, 2L);

        Map<String, Object> m1 = new HashMap<>();
        m1.put("template_object_id", 1L);
        m1.put("disease_type_id", 10L);

        Map<String, Object> m2 = new HashMap<>();
        m2.put("template_object_id", 1L);
        m2.put("disease_type_id", 11L);

        Map<String, Object> m3 = new HashMap<>();
        m3.put("template_object_id", 2L);
        m3.put("disease_type_id", 11L);

        DiseaseType dt10 = new DiseaseType();
        dt10.setId(10L);
        dt10.setName("裂缝");

        DiseaseType dt11 = new DiseaseType();
        dt11.setId(11L);
        dt11.setName("剥落");

        when(toDiseaseTypeMapper.selectTemplateObjectDiseaseTypeMappings(templateIds)).thenReturn(List.of(m1, m2, m3));
        when(diseaseTypeMapper.selectDiseaseTypeListByIds(any())).thenReturn(List.of(dt10, dt11));

        Map<Long, List<DiseaseType>> result = diseaseTypeService.batchSelectDiseaseTypeListByTemplateObjectIds(templateIds);

        assertEquals(2, result.size());
        assertEquals(2, result.get(1L).size());
        assertEquals(1, result.get(2L).size());
        verify(toDiseaseTypeMapper, times(1)).selectTemplateObjectDiseaseTypeMappings(templateIds);
        verify(diseaseTypeMapper, times(1)).selectDiseaseTypeListByIds(any());
    }

    /**
     * 测试场景：批量按模板对象查询病害类型时，入参为空应直接返回空映射。
     */
    @Test
    void testBatchSelectDiseaseTypeListByTemplateObjectIds_EmptyInput() {
        Map<Long, List<DiseaseType>> result = diseaseTypeService.batchSelectDiseaseTypeListByTemplateObjectIds(List.of());

        assertTrue(result.isEmpty());
        assertFalse(result.containsKey(1L));
        verify(toDiseaseTypeMapper, times(0)).selectTemplateObjectDiseaseTypeMappings(any());
    }
}
