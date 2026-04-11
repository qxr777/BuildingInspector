package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.TemplateVariable;
import edu.whut.cs.bi.biz.mapper.TemplateVariableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateVariableServiceImplTest {

    @InjectMocks
    private TemplateVariableServiceImpl templateVariableService;

    @Mock
    private TemplateVariableMapper templateVariableMapper;

    /**
     * 测试场景：根据ID查询模板变量时，Mapper正常返回数据。
     */
    @Test
    void testSelectTemplateVariableById_Success() {
        TemplateVariable expected = new TemplateVariable();
        expected.setId(1L);
        when(templateVariableMapper.selectTemplateVariableById(1L)).thenReturn(expected);

        TemplateVariable result = templateVariableService.selectTemplateVariableById(1L);

        assertEquals(1L, result.getId());
        verify(templateVariableMapper, times(1)).selectTemplateVariableById(1L);
    }

    /**
     * 测试场景：根据ID查询模板变量时，底层异常应向上抛出。
     */
    @Test
    void testSelectTemplateVariableById_Exception() {
        when(templateVariableMapper.selectTemplateVariableById(anyLong())).thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class, () -> templateVariableService.selectTemplateVariableById(1L));
        verify(templateVariableMapper, times(1)).selectTemplateVariableById(1L);
    }

    /**
     * 测试场景：查询模板变量列表时，正常返回列表。
     */
    @Test
    void testSelectTemplateVariableList_Success() {
        TemplateVariable query = new TemplateVariable();
        List<TemplateVariable> expected = Arrays.asList(new TemplateVariable(), new TemplateVariable());
        when(templateVariableMapper.selectTemplateVariableList(query)).thenReturn(expected);

        List<TemplateVariable> result = templateVariableService.selectTemplateVariableList(query);

        assertEquals(2, result.size());
        verify(templateVariableMapper, times(1)).selectTemplateVariableList(query);
    }

    /**
     * 测试场景：查询模板变量列表时，传入空查询对象导致底层异常。
     */
    @Test
    void testSelectTemplateVariableList_Exception() {
        when(templateVariableMapper.selectTemplateVariableList(any(TemplateVariable.class))).thenThrow(new RuntimeException("invalid query"));

        assertThrows(RuntimeException.class, () -> templateVariableService.selectTemplateVariableList(new TemplateVariable()));
        verify(templateVariableMapper, times(1)).selectTemplateVariableList(any(TemplateVariable.class));
    }

    /**
     * 测试场景：新增模板变量时，正常插入并返回影响行数。
     */
    @Test
    void testInsertTemplateVariable_Success() {
        TemplateVariable input = new TemplateVariable();
        input.setId(10L);
        when(templateVariableMapper.insertTemplateVariable(input)).thenReturn(1);

        int rows = templateVariableService.insertTemplateVariable(input);

        assertEquals(1, rows);
        verify(templateVariableMapper, times(1)).insertTemplateVariable(input);
    }

    /**
     * 测试场景：新增模板变量时，底层异常应向上抛出。
     */
    @Test
    void testInsertTemplateVariable_Exception() {
        TemplateVariable input = new TemplateVariable();
        when(templateVariableMapper.insertTemplateVariable(any(TemplateVariable.class))).thenThrow(new RuntimeException("insert failed"));

        assertThrows(RuntimeException.class, () -> templateVariableService.insertTemplateVariable(input));
        verify(templateVariableMapper, times(1)).insertTemplateVariable(input);
    }

    /**
     * 测试场景：修改模板变量时，正常更新并返回影响行数。
     */
    @Test
    void testUpdateTemplateVariable_Success() {
        TemplateVariable input = new TemplateVariable();
        input.setId(20L);
        when(templateVariableMapper.updateTemplateVariable(input)).thenReturn(1);

        int rows = templateVariableService.updateTemplateVariable(input);

        assertEquals(1, rows);
        verify(templateVariableMapper, times(1)).updateTemplateVariable(input);
    }

    /**
     * 测试场景：修改模板变量时，底层异常应向上抛出。
     */
    @Test
    void testUpdateTemplateVariable_Exception() {
        TemplateVariable input = new TemplateVariable();
        when(templateVariableMapper.updateTemplateVariable(any(TemplateVariable.class))).thenThrow(new RuntimeException("update failed"));

        assertThrows(RuntimeException.class, () -> templateVariableService.updateTemplateVariable(input));
        verify(templateVariableMapper, times(1)).updateTemplateVariable(input);
    }

    /**
     * 测试场景：按ID字符串批量删除时，应正确拆分并调用Mapper。
     */
    @Test
    void testDeleteTemplateVariableByIds_Success() {
        when(templateVariableMapper.deleteTemplateVariableByIds(any(String[].class))).thenReturn(2);

        int rows = templateVariableService.deleteTemplateVariableByIds("1,2");

        assertEquals(2, rows);
        verify(templateVariableMapper, times(1)).deleteTemplateVariableByIds(aryEq(new String[]{"1", "2"}));
    }

    /**
     * 测试场景：按ID字符串批量删除时，底层异常应向上抛出。
     */
    @Test
    void testDeleteTemplateVariableByIds_Exception() {
        when(templateVariableMapper.deleteTemplateVariableByIds(any(String[].class))).thenThrow(new RuntimeException("delete failed"));

        assertThrows(RuntimeException.class, () -> templateVariableService.deleteTemplateVariableByIds("1,2"));
        verify(templateVariableMapper, times(1)).deleteTemplateVariableByIds(aryEq(new String[]{"1", "2"}));
    }

    /**
     * 测试场景：按模板ID查询变量列表时，正常返回结果。
     */
    @Test
    void testSelectTemplateVariablesByTemplateId_Success() {
        List<TemplateVariable> expected = Collections.singletonList(new TemplateVariable());
        when(templateVariableMapper.selectTemplateVariablesByTemplateId(88L)).thenReturn(expected);

        List<TemplateVariable> result = templateVariableService.selectTemplateVariablesByTemplateId(88L);

        assertEquals(1, result.size());
        verify(templateVariableMapper, times(1)).selectTemplateVariablesByTemplateId(88L);
    }

    /**
     * 测试场景：按模板ID查询变量列表时，底层异常应向上抛出。
     */
    @Test
    void testSelectTemplateVariablesByTemplateId_Exception() {
        when(templateVariableMapper.selectTemplateVariablesByTemplateId(anyLong())).thenThrow(new RuntimeException("query failed"));

        assertThrows(RuntimeException.class, () -> templateVariableService.selectTemplateVariablesByTemplateId(88L));
        verify(templateVariableMapper, times(1)).selectTemplateVariablesByTemplateId(88L);
    }
}
