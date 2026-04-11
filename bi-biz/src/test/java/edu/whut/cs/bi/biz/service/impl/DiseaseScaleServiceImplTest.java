package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiseaseScaleServiceImplTest {

    @InjectMocks
    private DiseaseScaleServiceImpl diseaseScaleService;

    @Mock
    private DiseaseScaleMapper diseaseScaleMapper;

    /**
     * 测试 selectDiseaseScaleList：层级typeCode会被截断后再查询。
     */
    @Test
    void testSelectDiseaseScaleList_Success() {
        DiseaseScale query = new DiseaseScale();
        query.setTypeCode("A-B-C-D");

        when(diseaseScaleMapper.selectDiseaseScaleList(any(DiseaseScale.class)))
                .thenReturn(Collections.singletonList(new DiseaseScale()));

        List<DiseaseScale> result = diseaseScaleService.selectDiseaseScaleList(query);

        assertEquals(1, result.size());
        assertEquals("A-B-C", query.getTypeCode());
        verify(diseaseScaleMapper, times(1)).selectDiseaseScaleList(query);
    }

    /**
     * 测试 selectDiseaseScaleList：入参对象为空时抛出运行时异常。
     */
    @Test
    void testSelectDiseaseScaleList_NullInput() {
        assertThrows(RuntimeException.class, () -> diseaseScaleService.selectDiseaseScaleList(null));
    }

    /**
     * 测试 deleteDiseaseScaleByIds：逗号分隔ID可正常转换并删除。
     */
    @Test
    void testDeleteDiseaseScaleByIds_Success() {
        when(diseaseScaleMapper.deleteDiseaseScaleByIds(any(Long[].class))).thenReturn(1);

        diseaseScaleService.deleteDiseaseScaleByIds("1,2,3");

        ArgumentCaptor<Long[]> idsCaptor = ArgumentCaptor.forClass(Long[].class);
        verify(diseaseScaleMapper, times(1)).deleteDiseaseScaleByIds(idsCaptor.capture());
        assertArrayEquals(new Long[]{1L, 2L, 3L}, idsCaptor.getValue());
    }

    /**
     * 测试 deleteDiseaseScaleByIds：非法ID字符串会按 Convert 规则转换为 null 并继续调用 mapper。
     */
    @Test
    void testDeleteDiseaseScaleByIds_InvalidIds() {
        when(diseaseScaleMapper.deleteDiseaseScaleByIds(any(Long[].class))).thenReturn(1);

        diseaseScaleService.deleteDiseaseScaleByIds("1,a,3");

        ArgumentCaptor<Long[]> idsCaptor = ArgumentCaptor.forClass(Long[].class);
        verify(diseaseScaleMapper, times(1)).deleteDiseaseScaleByIds(idsCaptor.capture());
        assertArrayEquals(new Long[]{1L, null, 3L}, idsCaptor.getValue());
    }
}
