package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Score;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.ScoreMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreServiceImplTest {

    @InjectMocks
    private ScoreServiceImpl scoreService;

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private DiseaseMapper diseaseMapper;

    /**
     * 测试 calculateScore：有病害数据时可正常计算并批量保存得分。
     */
    @Test
    void testCalculateScore_Success() {
        Component component = new Component();
        component.setId(1L);
        component.setBiObjectId(101L);

        DiseaseType typeA = new DiseaseType();
        typeA.setCode("A.1.1.1");
        typeA.setMaxScale(5);

        DiseaseType typeB = new DiseaseType();
        typeB.setCode("B.1.1.1");
        typeB.setMaxScale(4);

        Disease disease1 = new Disease();
        disease1.setDiseaseType(typeA);
        disease1.setLevel(3);

        Disease disease2 = new Disease();
        disease2.setDiseaseType(typeB);
        disease2.setLevel(2);

        when(scoreMapper.deleteScoreByConditionId(10L)).thenReturn(1);
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(Arrays.asList(disease1, disease2));
        when(scoreMapper.selectScoreBycomponentId(1L)).thenReturn(null);
        when(scoreMapper.insertScore(any(Score.class))).thenReturn(1);
        when(scoreMapper.batchInsertScores(anyList())).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("tester");

            List<Score> result = scoreService.calculateScore(Collections.singletonList(component), 10L, 100L);

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getComponentId());
            assertEquals(10L, result.get(0).getConditionId());
            verify(scoreMapper, times(1)).deleteScoreByConditionId(10L);
            verify(scoreMapper, times(1)).insertScore(any(Score.class));
            verify(scoreMapper, times(1)).batchInsertScores(anyList());
        }
    }

    /**
     * 测试 calculateScore：病害类型为空导致计算流程抛出空指针异常。
     */
    @Test
    void testCalculateScore_DiseaseTypeNull() {
        Component component = new Component();
        component.setId(1L);
        component.setBiObjectId(101L);

        Disease disease = new Disease();
        disease.setDiseaseType(null);
        disease.setLevel(3);

        when(scoreMapper.deleteScoreByConditionId(10L)).thenReturn(1);
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(Collections.singletonList(disease));

        assertThrows(NullPointerException.class,
                () -> scoreService.calculateScore(Collections.singletonList(component), 10L, 100L));

        verify(scoreMapper, never()).batchInsertScores(anyList());
    }

    /**
     * 测试 deleteScoreByConditionId：条件ID有效时执行删除。
     */
    @Test
    void testDeleteScoreByConditionId_Success() {
        when(scoreMapper.deleteScoreByConditionId(20L)).thenReturn(3);

        int result = scoreService.deleteScoreByConditionId(20L);

        assertEquals(3, result);
        verify(scoreMapper, times(1)).deleteScoreByConditionId(20L);
    }

    /**
     * 测试 deleteScoreByConditionId：条件ID为空时直接返回0。
     */
    @Test
    void testDeleteScoreByConditionId_NullConditionId() {
        int result = scoreService.deleteScoreByConditionId(null);

        assertEquals(0, result);
        verify(scoreMapper, never()).deleteScoreByConditionId(any());
    }

    /**
     * 测试 batchInsertScores：批量数据有效时补齐创建更新字段并入库。
     */
    @Test
    void testBatchInsertScores_Success() {
        Score score = new Score();
        score.setScore(new BigDecimal("88.50"));

        when(scoreMapper.batchInsertScores(anyList())).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = scoreService.batchInsertScores(Collections.singletonList(score));

            assertEquals(1, result);
            assertEquals("tester", score.getCreateBy());
            assertEquals("tester", score.getUpdateBy());
            verify(scoreMapper, times(1)).batchInsertScores(anyList());
        }
    }

    /**
     * 测试 batchInsertScores：空列表时不执行入库并返回0。
     */
    @Test
    void testBatchInsertScores_EmptyList() {
        int result = scoreService.batchInsertScores(Collections.emptyList());

        assertEquals(0, result);
        verify(scoreMapper, never()).batchInsertScores(anyList());
    }
}
