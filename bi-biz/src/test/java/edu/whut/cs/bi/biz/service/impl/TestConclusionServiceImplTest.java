package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestConclusionServiceImplTest {

    @InjectMocks
    private TestConclusionServiceImpl testConclusionService;

    @Mock
    private IBiEvaluationService biEvaluationService;

    @Mock
    private BiObjectMapper biObjectMapper;

    @BeforeEach
    void setUp() {
        testConclusionService.clearDiseaseSummaryCache();
    }

    /**
     * 测试场景：检测结论正常生成，包含桥梁评定与全桥评定结果。
     */
    @Test
    void testHandleTestConclusion_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph targetParagraph = document.createParagraph();
        targetParagraph.createRun().setText("${testConclusion}");

        Building building = new Building();
        building.setName("一号桥");

        Task task = new Task();
        task.setId(1L);
        task.setBuilding(building);

        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setSystemLevel(2);

        Map<Long, BiEvaluation> evaluationMap = new HashMap<>();
        evaluationMap.put(1L, evaluation);

        testConclusionService.handleTestConclusion(
                document,
                targetParagraph,
                Collections.singletonList(task),
                "测试桥",
                evaluationMap,
                1
        );

        String text = targetParagraph.getText();
        assertTrue(text.contains("一号桥评定为2类"));
        assertTrue(text.contains("全桥技术状况评定为1类"));
    }

    /**
     * 测试场景：检测结论传入非法任务列表（null）时，应抛出空指针异常。
     */
    @Test
    void testHandleTestConclusion_NullTasks() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph targetParagraph = document.createParagraph();

        Map<Long, BiEvaluation> evaluationMap = new HashMap<>();
        assertThrows(NullPointerException.class, () -> testConclusionService.handleTestConclusion(
                document,
                targetParagraph,
                null,
                "测试桥",
                evaluationMap,
                1
        ));
    }

    /**
     * 测试场景：桥梁详情正常生成，写入桥名、结构层级和构件病害描述。
     */
    @Test
    void testHandleTestConclusionBridge_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph targetParagraph = document.createParagraph();
        targetParagraph.createRun().setText("${bridgeDetail}");

        Building building = new Building();
        building.setName("二号桥");
        building.setRootObjectId(100L);

        Task task = new Task();
        task.setId(10L);
        task.setBuilding(building);

        BiObject root = new BiObject();
        root.setId(100L);
        root.setName("Root");

        BiObject secondLevel = new BiObject();
        secondLevel.setId(200L);
        secondLevel.setParentId(100L);
        secondLevel.setName("上部结构");

        BiObject accessory = new BiObject();
        accessory.setId(201L);
        accessory.setParentId(100L);
        accessory.setName("附属设施");

        BiObject thirdLevel = new BiObject();
        thirdLevel.setId(300L);
        thirdLevel.setParentId(200L);
        thirdLevel.setName("主梁");

        BiObject otherNode = new BiObject();
        otherNode.setId(301L);
        otherNode.setParentId(200L);
        otherNode.setName("其他构件");

        List<BiObject> allNodes = Arrays.asList(secondLevel, accessory, thirdLevel, otherNode);

        when(biObjectMapper.selectBiObjectById(100L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(100L)).thenReturn(allNodes);

        testConclusionService.cacheDiseaseSummary(300L, "裂缝：长度2m");

        testConclusionService.handleTestConclusionBridge(document, targetParagraph, Collections.singletonList(task));

        verify(biObjectMapper, times(1)).selectBiObjectById(100L);
        verify(biObjectMapper, times(1)).selectChildrenById(100L);
        assertEquals(0, targetParagraph.getRuns().size());

        String allText = document.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(allText.contains("二号桥"));
        assertTrue(allText.contains("上部结构"));
        assertTrue(allText.contains("（1）主梁"));
        assertTrue(allText.contains("裂缝：长度2m"));
        assertFalse(allText.contains("附属设施"));
        assertFalse(allText.contains("其他构件"));
    }

    /**
     * 测试场景：桥梁详情生成时依赖查询子节点失败，应抛出运行时异常。
     */
    @Test
    void testHandleTestConclusionBridge_SelectChildrenFailed() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph targetParagraph = document.createParagraph();

        Building building = new Building();
        building.setName("三号桥");
        building.setRootObjectId(500L);

        Task task = new Task();
        task.setId(20L);
        task.setBuilding(building);

        BiObject root = new BiObject();
        root.setId(500L);
        root.setName("Root");

        when(biObjectMapper.selectBiObjectById(500L)).thenReturn(root);
        doThrow(new RuntimeException("db error")).when(biObjectMapper).selectChildrenById(500L);

        assertThrows(RuntimeException.class, () -> testConclusionService.handleTestConclusionBridge(
                document,
                targetParagraph,
                Collections.singletonList(task)
        ));
    }

    /**
     * 测试场景：缓存病害汇总后可通过缓存读取接口获取到对应内容。
     */
    @Test
    void testGetSummaryCache_Success() {
        testConclusionService.cacheDiseaseSummary(900L, "病害A");

        Map<Long, String> cache = testConclusionService.getSummaryCache();

        assertEquals("病害A", cache.get(900L));
    }

    /**
     * 测试场景：清空缓存后，再读取缓存应为空。
     */
    @Test
    void testClearDiseaseSummaryCache_EmptyAfterClear() {
        testConclusionService.cacheDiseaseSummary(901L, "病害B");

        testConclusionService.clearDiseaseSummaryCache();

        assertTrue(testConclusionService.getSummaryCache().isEmpty());
    }
}
