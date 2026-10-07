package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.dto.CodeSegment;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComponentServiceImplTest {

    @InjectMocks
    private ComponentServiceImpl componentService;

    @Mock
    private ComponentMapper componentMapper;

    @Mock
    private BiObjectMapper biObjectMapper;

    /**
     * 测试场景：批量生成构件时，部件存在且编号片段合法，能够正常生成并入库。
     */
    @Test
    void testGenerateComponents_Success() {
        Long biObjectId = 10L;

        BiObject biObject = new BiObject();
        biObject.setId(biObjectId);
        biObject.setName("主梁");
        when(biObjectMapper.selectBiObjectById(biObjectId)).thenReturn(biObject);
        when(componentMapper.insertComponent(any(Component.class))).thenReturn(1);

        CodeSegment fixed = new CodeSegment();
        fixed.setType(1);
        fixed.setValue("A");

        CodeSegment sequence = new CodeSegment();
        sequence.setType(2);
        sequence.setMinValue(1);
        sequence.setMaxValue(2);

        List<CodeSegment> segments = Arrays.asList(fixed, sequence);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = componentService.generateComponents(biObjectId, segments, "-后缀");

            assertEquals(2, result);
            verify(componentMapper, times(2)).insertComponent(any(Component.class));
            verify(biObjectMapper, times(1)).updateBiObject(any(BiObject.class));

            ArgumentCaptor<BiObject> biObjectCaptor = ArgumentCaptor.forClass(BiObject.class);
            verify(biObjectMapper).updateBiObject(biObjectCaptor.capture());
            assertEquals(2, biObjectCaptor.getValue().getCount());
        }
    }

    /**
     * 测试场景：批量生成构件时，部件不存在，抛出运行时异常进行业务拦截。
     */
    @Test
    void testGenerateComponents_BiObjectNotExists() {
        when(biObjectMapper.selectBiObjectById(anyLong())).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> componentService.generateComponents(99L, new ArrayList<>(), "-后缀"));

        assertEquals("部件不存在", ex.getMessage());
        verify(componentMapper, never()).insertComponent(any(Component.class));
    }

    /**
     * 测试场景：批量插入构件时，首个构件包含部件ID，正常更新部件计数并执行批量插入。
     */
    @Test
    void testBatchInsertComponents_Success() {
        Long biObjectId = 20L;

        Component c1 = new Component();
        c1.setBiObjectId(biObjectId);
        Component c2 = new Component();
        c2.setBiObjectId(biObjectId);
        List<Component> components = Arrays.asList(c1, c2);

        BiObject biObject = new BiObject();
        biObject.setId(biObjectId);
        biObject.setCount(5);

        when(biObjectMapper.selectBiObjectById(biObjectId)).thenReturn(biObject);
        when(componentMapper.batchInsertComponents(components)).thenReturn(2);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = componentService.batchInsertComponents(components);

            assertEquals(2, result);
            verify(biObjectMapper, times(1)).updateBiObject(any(BiObject.class));
            verify(componentMapper, times(1)).batchInsertComponents(components);

            ArgumentCaptor<BiObject> biObjectCaptor = ArgumentCaptor.forClass(BiObject.class);
            verify(biObjectMapper).updateBiObject(biObjectCaptor.capture());
            assertEquals(7, biObjectCaptor.getValue().getCount());
        }
    }

    /**
     * 测试场景：批量插入构件时，部件计数为空导致计数累加异常，抛出运行时异常。
     */
    @Test
    void testBatchInsertComponents_CountNullException() {
        Long biObjectId = 21L;

        Component component = new Component();
        component.setBiObjectId(biObjectId);
        List<Component> components = List.of(component);

        BiObject biObject = new BiObject();
        biObject.setId(biObjectId);
        biObject.setCount(null);

        when(biObjectMapper.selectBiObjectById(biObjectId)).thenReturn(biObject);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            assertThrows(RuntimeException.class, () -> componentService.batchInsertComponents(components));
        }

        verify(componentMapper, never()).batchInsertComponents(any());
    }

    /**
     * 测试场景：按部件ID批量删除构件时，构件存在，能够重置计数并批量删除成功。
     */
    @Test
    void testDeleteComponentsByBiObjectId_Success() {
        Long biObjectId = 30L;

        Component c1 = new Component();
        c1.setId(101L);
        Component c2 = new Component();
        c2.setId(102L);
        when(componentMapper.selectComponentsByBiObjectId(biObjectId)).thenReturn(Arrays.asList(c1, c2));

        BiObject biObject = new BiObject();
        biObject.setId(biObjectId);
        biObject.setCount(2);
        when(biObjectMapper.selectBiObjectById(biObjectId)).thenReturn(biObject);

        when(componentMapper.deleteComponentByIds(any(String[].class))).thenReturn(2);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = componentService.deleteComponentsByBiObjectId(biObjectId);

            assertEquals(2, result);
            verify(biObjectMapper, times(1)).updateBiObject(any(BiObject.class));
            verify(componentMapper, times(1)).deleteComponentByIds(any(String[].class));
            verify(componentMapper, times(1)).selectComponentsByBiObjectId(biObjectId);
        }
    }

    /**
     * 测试场景：按部件ID批量删除构件时，查询结果为null导致空指针，抛出运行时异常。
     */
    @Test
    void testDeleteComponentsByBiObjectId_NullComponentsException() {
        Long biObjectId = 31L;
        when(componentMapper.selectComponentsByBiObjectId(anyLong())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> componentService.deleteComponentsByBiObjectId(biObjectId));

        verify(componentMapper, never()).deleteComponentByIds(any(String[].class));
        verify(biObjectMapper, never()).updateBiObject(any(BiObject.class));
    }
}
