package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Score;
import edu.whut.cs.bi.biz.mapper.ConditionMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IScoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConditionServiceImplTest {

    @InjectMocks
    private ConditionServiceImpl conditionService;

    @Mock
    private ConditionMapper conditionMapper;

    @Mock
    private IScoreService scoreService;

    @Mock
    private ComponentServiceImpl componentService;

    @Mock
    private IBiObjectService biObjectService;

    /**
     * 测试 selectConditionByBiObjectId：存在记录时返回首条记录。
     */
    @Test
    void testSelectConditionByBiObjectId_Success() {
        Condition c1 = new Condition();
        c1.setId(1L);
        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(Collections.singletonList(c1));

        Condition result = conditionService.selectConditionByBiObjectId(10L, 20L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(conditionMapper, times(1)).selectConditionList(any(Condition.class));
    }

    /**
     * 测试 selectConditionByBiObjectId：无记录时返回null。
     */
    @Test
    void testSelectConditionByBiObjectId_NotFound() {
        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(Collections.emptyList());

        Condition result = conditionService.selectConditionByBiObjectId(10L, 20L);

        assertNull(result);
    }

    /**
     * 测试 calculateCondition：低分构件场景下按最低分计算并更新技术状况。
     */
    @Test
    void testCalculateCondition_Success() {
        BiObject biObject = new BiObject();
        biObject.setId(1L);
        biObject.setName("部件A");
        biObject.setWeight(new BigDecimal("0.5"));
        biObject.setCount(2);

        Condition existing = new Condition();
        existing.setId(11L);

        Component component = new Component();
        component.setId(101L);

        Score s1 = new Score();
        s1.setScore(new BigDecimal("39"));
        Score s2 = new Score();
        s2.setScore(new BigDecimal("90"));

        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(Collections.singletonList(existing));
        when(componentService.selectComponentsByBiObjectIdAndChildren(1L)).thenReturn(Collections.singletonList(component));
        when(scoreService.calculateScore(anyList(), eq(11L), eq(100L))).thenReturn(Arrays.asList(s1, s2));
        when(conditionMapper.updateCondition(any(Condition.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            Condition result = conditionService.calculateCondition(biObject, 10L, 100L);

            assertEquals(new BigDecimal("39"), result.getScore());
            assertEquals(5, result.getLevel());
            verify(conditionMapper, times(1)).updateCondition(existing);
        }
    }

    /**
     * 测试 calculateCondition：入参部件为空时抛出运行时异常。
     */
    @Test
    void testCalculateCondition_NullBiObject() {
        assertThrows(RuntimeException.class, () -> conditionService.calculateCondition(null, 10L, 100L));
    }

    /**
     * 测试 calculateCondition：无得分记录时按满分分支处理并更新。
     */
    @Test
    void testCalculateCondition_NoScoresFallbackSuccess() {
        BiObject biObject = new BiObject();
        biObject.setId(2L);
        biObject.setName("部件B");
        biObject.setWeight(new BigDecimal("0.8"));
        biObject.setCount(3);

        Condition existing = new Condition();
        existing.setId(12L);

        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(Collections.singletonList(existing));
        when(componentService.selectComponentsByBiObjectIdAndChildren(2L)).thenReturn(Collections.emptyList());
        when(conditionMapper.updateCondition(any(Condition.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            Condition result = conditionService.calculateCondition(biObject, 10L, 100L);

            assertEquals(new BigDecimal("100"), result.getScore());
            assertEquals(1, result.getLevel());
            verify(conditionMapper, times(1)).updateCondition(existing);
        }
    }

    /**
     * 测试 calculateCondition：构件服务返回null时抛出运行时异常。
     */
    @Test
    void testCalculateCondition_ComponentsNull() {
        BiObject biObject = new BiObject();
        biObject.setId(3L);
        biObject.setName("部件C");
        biObject.setWeight(new BigDecimal("0.6"));

        Condition existing = new Condition();
        existing.setId(13L);

        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(Collections.singletonList(existing));
        when(componentService.selectComponentsByBiObjectIdAndChildren(3L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> conditionService.calculateCondition(biObject, 10L, 100L));
    }
}
