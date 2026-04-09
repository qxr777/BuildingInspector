package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.IConditionService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationTableServiceImplTest {

    @InjectMocks
    private EvaluationTableServiceImpl evaluationTableService;

    @Mock
    private BiObjectMapper biObjectMapper;

    @Mock
    private IConditionService conditionService;

    /**
     * 中文注释：测试生成评定表主流程成功，核心依赖均正常返回并完成级联调用。
     */
    @Test
    void testGenerateEvaluationTableAfterParagraph_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph afterParagraph = document.createParagraph();

        Building building = new Building();
        building.setRootObjectId(1L);

        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setId(100L);
        evaluation.setSuperstructureScore(new BigDecimal("90.0"));
        evaluation.setSubstructureScore(new BigDecimal("88.0"));
        evaluation.setDeckSystemScore(new BigDecimal("86.0"));
        evaluation.setSuperstructureLevel(1);
        evaluation.setSubstructureLevel(2);
        evaluation.setDeckSystemLevel(2);
        evaluation.setSystemScore(new BigDecimal("89.0"));
        evaluation.setSystemLevel(1);

        BiObject secondUpper = buildObject(10L, "上部结构", null, null);
        BiObject secondLower = buildObject(11L, "下部结构", null, null);
        BiObject secondDeck = buildObject(12L, "桥面系", null, null);

        BiObject upperComp = buildObject(101L, "主梁", new BigDecimal("0.20"), new BigDecimal("0.30"));
        BiObject lowerComp = buildObject(102L, "桥墩", new BigDecimal("0.30"), new BigDecimal("0.40"));
        BiObject deckComp = buildObject(103L, "铺装层", new BigDecimal("0.10"), new BigDecimal("0.30"));

        when(biObjectMapper.selectChildrenByParentId(1L)).thenReturn(Arrays.asList(secondUpper, secondLower, secondDeck));
        when(biObjectMapper.selectChildrenByParentId(10L)).thenReturn(Collections.singletonList(upperComp));
        when(biObjectMapper.selectChildrenByParentId(11L)).thenReturn(Collections.singletonList(lowerComp));
        when(biObjectMapper.selectChildrenByParentId(12L)).thenReturn(Collections.singletonList(deckComp));

        Condition c1 = new Condition();
        c1.setBiObjectId(101L);
        c1.setScore(new BigDecimal("95.0"));
        c1.setLevel(1);

        Condition c2 = new Condition();
        c2.setBiObjectId(102L);
        c2.setScore(new BigDecimal("85.0"));
        c2.setLevel(2);

        Condition c3 = new Condition();
        c3.setBiObjectId(103L);
        c3.setScore(new BigDecimal("80.0"));
        c3.setLevel(2);

        when(conditionService.selectConditionsByBiEvaluationId(100L)).thenReturn(Arrays.asList(c1, c2, c3));

        evaluationTableService.generateEvaluationTableAfterParagraph(document, afterParagraph, building, evaluation, "测试");

        verify(biObjectMapper, times(1)).selectChildrenByParentId(1L);
        verify(biObjectMapper, times(1)).selectChildrenByParentId(10L);
        verify(biObjectMapper, times(1)).selectChildrenByParentId(11L);
        verify(biObjectMapper, times(1)).selectChildrenByParentId(12L);
        verify(conditionService, times(1)).selectConditionsByBiEvaluationId(100L);
    }

    /**
     * 中文注释：测试生成评定表时，底层Mapper异常应向上抛出运行时异常。
     */
    @Test
    void testGenerateEvaluationTableAfterParagraph_MapperException() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph afterParagraph = document.createParagraph();

        Building building = new Building();
        building.setRootObjectId(1L);

        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setId(100L);

        when(biObjectMapper.selectChildrenByParentId(1L)).thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class, () ->
                evaluationTableService.generateEvaluationTableAfterParagraph(document, afterParagraph, building, evaluation, "测试"));
    }

    /**
     * 中文注释：测试核心结构数据收集逻辑，验证结构分类与“其他”过滤行为。
     */
    @Test
    void testCollectStructureData_Success() throws Exception {
        BiObject secondUpper = buildObject(10L, "上部结构", null, null);
        BiObject secondLower = buildObject(11L, "下部结构", null, null);
        BiObject secondDeck = buildObject(12L, "桥面系", null, null);

        BiObject upperComp1 = buildObject(101L, "主梁", null, null);
        BiObject upperComp2 = buildObject(102L, "其他构件", null, null);
        BiObject lowerComp = buildObject(103L, "桥墩", null, null);
        BiObject deckComp = buildObject(104L, "伸缩缝", null, null);

        when(biObjectMapper.selectChildrenByParentId(1L)).thenReturn(Arrays.asList(secondUpper, secondLower, secondDeck));
        when(biObjectMapper.selectChildrenByParentId(10L)).thenReturn(Arrays.asList(upperComp1, upperComp2));
        when(biObjectMapper.selectChildrenByParentId(11L)).thenReturn(Collections.singletonList(lowerComp));
        when(biObjectMapper.selectChildrenByParentId(12L)).thenReturn(Collections.singletonList(deckComp));

        Method method = EvaluationTableServiceImpl.class.getDeclaredMethod("collectStructureData", Long.class, Long.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<BiObject>> result = (Map<String, List<BiObject>>) method.invoke(evaluationTableService, 1L, 100L);

        assertEquals(1, result.get("上部结构").size());
        assertEquals("主梁", result.get("上部结构").get(0).getName());
        assertEquals(1, result.get("下部结构").size());
        assertEquals(1, result.get("桥面系").size());
    }

    /**
     * 中文注释：测试结构数据收集时，Mapper异常场景触发并抛出异常。
     */
    @Test
    void testCollectStructureData_Exception() throws Exception {
        when(biObjectMapper.selectChildrenByParentId(1L)).thenThrow(new RuntimeException("query failed"));

        Method method = EvaluationTableServiceImpl.class.getDeclaredMethod("collectStructureData", Long.class, Long.class);
        method.setAccessible(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try {
                method.invoke(evaluationTableService, 1L, 100L);
            } catch (Exception e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertEquals("query failed", ex.getMessage());
    }

    private BiObject buildObject(Long id, String name, BigDecimal standardWeight, BigDecimal weight) {
        BiObject obj = new BiObject();
        obj.setId(id);
        obj.setName(name);
        obj.setStandardWeight(standardWeight);
        obj.setWeight(weight);
        return obj;
    }
}