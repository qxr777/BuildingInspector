package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.BiEvaluationMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IConditionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BiEvaluationServiceImplTest {

    @InjectMocks
    private BiEvaluationServiceImpl biEvaluationService;

    @Mock
    private BiEvaluationMapper biEvaluationMapper;

    @Mock
    private IBiObjectService biObjectService;

    @Mock
    private IConditionService conditionService;

    @Mock
    private TaskServiceImpl taskService;

    /**
     * 测试场景：按任务ID查询评定记录时，存在数据则返回第一条记录。
     */
    @Test
    void testSelectBiEvaluationByTaskId_Success() {
        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setId(100L);
        when(biEvaluationMapper.selectBiEvaluationList(any(BiEvaluation.class))).thenReturn(Collections.singletonList(evaluation));

        BiEvaluation result = biEvaluationService.selectBiEvaluationByTaskId(1L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        verify(biEvaluationMapper, times(1)).selectBiEvaluationList(any(BiEvaluation.class));
    }

    /**
     * 测试场景：按任务ID查询评定记录时，底层返回null列表导致运行时异常。
     */
    @Test
    void testSelectBiEvaluationByTaskId_NullListException() {
        when(biEvaluationMapper.selectBiEvaluationList(any(BiEvaluation.class))).thenReturn(null);

        assertThrows(RuntimeException.class, () -> biEvaluationService.selectBiEvaluationByTaskId(1L));
    }

    /**
     * 测试场景：按ID串批量删除评定记录时，能够正确拆分并调用Mapper删除。
     */
    @Test
    void testDeleteBiEvaluationByIds_Success() {
        when(biEvaluationMapper.deleteBiEvaluationByIds(new String[]{"1", "2", "3"})).thenReturn(3);

        int result = biEvaluationService.deleteBiEvaluationByIds("1,2,3");

        assertEquals(3, result);
        verify(biEvaluationMapper, times(1)).deleteBiEvaluationByIds(new String[]{"1", "2", "3"});
    }

    /**
     * 测试场景：按ID串批量删除评定记录时，入参为null触发运行时异常。
     */
    @Test
    void testDeleteBiEvaluationByIds_NullIdsException() {
        assertThrows(RuntimeException.class, () -> biEvaluationService.deleteBiEvaluationByIds(null));
    }

    /**
     * 测试场景：计算桥梁评定时，三大结构均有有效数据，完整流程成功并更新记录。
     */
    @Test
    void testCalculateBiEvaluation_Success() {
        Long taskId = 10L;
        Long rootObjectId = 100L;

        Task task = new Task();
        task.setId(taskId);
        task.setProjectId(200L);
        when(taskService.selectTaskById(taskId)).thenReturn(task);

        BiEvaluation existed = new BiEvaluation();
        existed.setId(999L);
        when(biEvaluationMapper.selectBiEvaluationList(any(BiEvaluation.class))).thenReturn(Collections.singletonList(existed));

        BiObject superPart = new BiObject();
        superPart.setId(1L);
        superPart.setName("上部结构");
        superPart.setWeight(new BigDecimal("0.4"));

        BiObject subPart = new BiObject();
        subPart.setId(2L);
        subPart.setName("下部结构");
        subPart.setWeight(new BigDecimal("0.4"));

        BiObject deckPart = new BiObject();
        deckPart.setId(3L);
        deckPart.setName("桥面系");
        deckPart.setWeight(new BigDecimal("0.2"));

        when(biObjectService.selectDirectChildrenByParentId(rootObjectId)).thenReturn(Arrays.asList(superPart, subPart, deckPart));

        BiObject leaf1 = new BiObject();
        leaf1.setId(11L);
        leaf1.setName("上部构件");
        leaf1.setWeight(BigDecimal.ONE);

        BiObject leaf2 = new BiObject();
        leaf2.setId(12L);
        leaf2.setName("下部构件");
        leaf2.setWeight(BigDecimal.ONE);

        BiObject leaf3 = new BiObject();
        leaf3.setId(13L);
        leaf3.setName("桥面构件");
        leaf3.setWeight(BigDecimal.ONE);

        when(biObjectService.selectDirectChildrenByParentId(1L)).thenReturn(Collections.singletonList(leaf1));
        when(biObjectService.selectDirectChildrenByParentId(2L)).thenReturn(Collections.singletonList(leaf2));
        when(biObjectService.selectDirectChildrenByParentId(3L)).thenReturn(Collections.singletonList(leaf3));

        Condition c1 = new Condition();
        c1.setScore(new BigDecimal("90"));
        c1.setLevel(2);
        Condition c2 = new Condition();
        c2.setScore(new BigDecimal("80"));
        c2.setLevel(3);
        Condition c3 = new Condition();
        c3.setScore(new BigDecimal("50"));
        c3.setLevel(4);

        when(conditionService.calculateCondition(leaf1, 999L, 200L)).thenReturn(c1);
        when(conditionService.calculateCondition(leaf2, 999L, 200L)).thenReturn(c2);
        when(conditionService.calculateCondition(leaf3, 999L, 200L)).thenReturn(c3);

        when(biEvaluationMapper.updateBiEvaluation(any(BiEvaluation.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            BiEvaluation result = biEvaluationService.calculateBiEvaluation(taskId, rootObjectId);

            assertNotNull(result);
            assertEquals(0, new BigDecimal("78.0").compareTo(result.getSystemScore()));
            assertEquals(4, result.getWorstPartLevel());
            assertEquals(3, result.getSystemLevel());
            verify(biEvaluationMapper, times(1)).updateBiEvaluation(any(BiEvaluation.class));
        }
    }

    /**
     * 测试场景：计算桥梁评定时，任务不存在，抛出运行时异常。
     */
    @Test
    void testCalculateBiEvaluation_TaskNotFound() {
        when(taskService.selectTaskById(anyLong())).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> biEvaluationService.calculateBiEvaluation(10L, 100L));

        assertEquals("计算失败：未找到任务信息", ex.getMessage());
    }

    /**
     * 测试场景：计算单个结构部位得分时，存在有效子节点和评定数据，能够写入对应结构得分。
     */
    @Test
    void testCalculatePartScore_Success() throws Exception {
        BiObject part = new BiObject();
        part.setId(1L);
        part.setName("上部结构");
        part.setWeight(new BigDecimal("0.4"));

        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setId(1000L);

        BiObject leaf = new BiObject();
        leaf.setId(10L);
        leaf.setName("构件A");
        leaf.setWeight(BigDecimal.ONE);

        when(biObjectService.selectDirectChildrenByParentId(1L)).thenReturn(Collections.singletonList(leaf));

        Condition condition = new Condition();
        condition.setScore(new BigDecimal("88"));
        condition.setLevel(2);
        when(conditionService.calculateCondition(leaf, 1000L, 300L)).thenReturn(condition);

        Method method = BiEvaluationServiceImpl.class.getDeclaredMethod("calculatePartScore", BiObject.class, BiEvaluation.class, Long.class);
        method.setAccessible(true);
        method.invoke(biEvaluationService, part, evaluation, 300L);

        assertEquals(new BigDecimal("88.00"), evaluation.getSuperstructureScore());
        assertEquals(2, evaluation.getSuperstructureLevel());
    }

    /**
     * 测试场景：计算单个结构部位得分时，评定记录ID为空，抛出运行时异常。
     */
    @Test
    void testCalculatePartScore_EvaluationIdNullException() throws Exception {
        BiObject part = new BiObject();
        part.setId(1L);
        part.setName("上部结构");
        part.setWeight(new BigDecimal("0.4"));

        BiEvaluation evaluation = new BiEvaluation();

        Method method = BiEvaluationServiceImpl.class.getDeclaredMethod("calculatePartScore", BiObject.class, BiEvaluation.class, Long.class);
        method.setAccessible(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try {
                method.invoke(biEvaluationService, part, evaluation, 300L);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getTargetException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("计算失败：评定记录为空或ID为空", ex.getMessage());
    }

    /**
     * 测试场景：计算总体得分时，三大结构得分与权重完整，能够计算系统得分与等级。
     */
    @Test
    void testCalculateOverallScore_Success() throws Exception {
        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setSuperstructureScore(new BigDecimal("80"));
        evaluation.setSubstructureScore(new BigDecimal("80"));
        evaluation.setDeckSystemScore(new BigDecimal("50"));
        evaluation.setSuperstructureLevel(3);
        evaluation.setSubstructureLevel(3);
        evaluation.setDeckSystemLevel(4);

        setStaticWeight("superWeight", new BigDecimal("0.4"));
        setStaticWeight("subWeight", new BigDecimal("0.4"));
        setStaticWeight("deckWeight", new BigDecimal("0.2"));

        Method method = BiEvaluationServiceImpl.class.getDeclaredMethod("calculateOverallScore", BiEvaluation.class);
        method.setAccessible(true);
        method.invoke(biEvaluationService, evaluation);

        assertEquals(new BigDecimal("74.0"), evaluation.getSystemScore());
        assertEquals(4, evaluation.getWorstPartLevel());
        assertEquals(3, evaluation.getSystemLevel());
    }

    /**
     * 测试场景：计算总体得分时，权重之和不为1，抛出运行时异常。
     */
    @Test
    void testCalculateOverallScore_WeightSumInvalidException() throws Exception {
        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setSuperstructureScore(new BigDecimal("80"));
        evaluation.setSubstructureScore(new BigDecimal("80"));
        evaluation.setDeckSystemScore(new BigDecimal("50"));
        evaluation.setSuperstructureLevel(3);
        evaluation.setSubstructureLevel(3);
        evaluation.setDeckSystemLevel(4);

        setStaticWeight("superWeight", new BigDecimal("0.3"));
        setStaticWeight("subWeight", new BigDecimal("0.4"));
        setStaticWeight("deckWeight", new BigDecimal("0.2"));

        Method method = BiEvaluationServiceImpl.class.getDeclaredMethod("calculateOverallScore", BiEvaluation.class);
        method.setAccessible(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try {
                method.invoke(biEvaluationService, evaluation);
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getTargetException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("计算失败：权重之和不等于1，当前总和为0.9", ex.getMessage());
    }

    private void setStaticWeight(String fieldName, BigDecimal value) throws Exception {
        Field field = BiEvaluationServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
