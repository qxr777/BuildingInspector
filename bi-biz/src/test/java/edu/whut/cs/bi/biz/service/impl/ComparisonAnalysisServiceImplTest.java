package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.mapper.SysUserMapper;
import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonAnalysisServiceImplTest {

    @InjectMocks
    private ComparisonAnalysisServiceImpl comparisonAnalysisService;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private ITaskService taskService;

    @Mock
    private IBiEvaluationService biEvaluationService;

    @Mock
    private IPropertyService propertyService;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private ProjectServiceImpl projectServiceImpl;

    @Mock
    private SysUserMapper sysUserMapper;

    /**
     * 测试 generateComparisonAnalysisTable：存在当前年和上一年评定数据时正常生成单桥表格。
     */
    @Test
    void testGenerateComparisonAnalysisTable_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Project currentProject = new Project();
        currentProject.setYear(2025);

        Project previousProject = new Project();
        previousProject.setYear(2024);

        Task currentTask = new Task();
        currentTask.setId(1L);
        currentTask.setBuildingId(10L);
        currentTask.setProject(currentProject);

        Task oldTask = new Task();
        oldTask.setId(2L);
        oldTask.setBuildingId(10L);
        oldTask.setProject(previousProject);

        BiEvaluation currentEval = new BiEvaluation();
        currentEval.setSystemLevel(2);
        currentEval.setSystemScore(BigDecimal.valueOf(88.2));

        BiEvaluation previousEval = new BiEvaluation();
        previousEval.setSystemLevel(3);
        previousEval.setSystemScore(BigDecimal.valueOf(80.1));

        when(taskMapper.selectTaskList(any(Task.class), eq(null))).thenReturn(Collections.singletonList(oldTask));
        when(biEvaluationService.selectBiEvaluationByTaskId(1L)).thenReturn(currentEval);
        when(biEvaluationService.selectBiEvaluationByTaskId(2L)).thenReturn(previousEval);

        try (MockedStatic<WordFieldUtils> wordMock = org.mockito.Mockito.mockStatic(WordFieldUtils.class)) {
            wordMock.when(() -> WordFieldUtils.createTableCaptionWithCounter(any(), any(), any(), eq(9), any()))
                    .thenReturn("bookmark_1");
            wordMock.when(() -> WordFieldUtils.createChapterTableReference(any(), eq("bookmark_1"), any(), any()))
                    .thenAnswer(invocation -> null);

            comparisonAnalysisService.generateComparisonAnalysisTable(document, paragraph, currentTask, "测试桥", true);

            verify(taskMapper, times(1)).selectTaskList(any(Task.class), eq(null));
            verify(biEvaluationService, times(1)).selectBiEvaluationByTaskId(1L);
            verify(biEvaluationService, times(1)).selectBiEvaluationByTaskId(2L);
        }
    }

    /**
     * 测试 generateComparisonAnalysisTable：目标段落为空时直接返回且不触发数据查询。
     */
    @Test
    void testGenerateComparisonAnalysisTable_NullParagraph() {
        XWPFDocument document = new XWPFDocument();

        Project project = new Project();
        project.setYear(2025);
        Task currentTask = new Task();
        currentTask.setId(1L);
        currentTask.setProject(project);

        comparisonAnalysisService.generateComparisonAnalysisTable(document, null, currentTask, "测试桥", false);

        verify(taskMapper, never()).selectTaskList(any(Task.class), eq(null));
        verify(biEvaluationService, never()).selectBiEvaluationByTaskId(anyLong());
    }

    /**
     * 测试 generateMultiBridgeComparisonAnalysisTable：多桥场景下正常生成并完成汇总等级计算。
     */
    @Test
    void testGenerateMultiBridgeComparisonAnalysisTable_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Project currentProject = new Project();
        currentProject.setYear(2025);

        Task bridgeTask1 = new Task();
        bridgeTask1.setId(11L);
        bridgeTask1.setBuildingId(101L);
        bridgeTask1.setProject(currentProject);
        Building building1 = new Building();
        building1.setName("1号桥");
        bridgeTask1.setBuilding(building1);

        Task bridgeTask2 = new Task();
        bridgeTask2.setId(12L);
        bridgeTask2.setBuildingId(102L);
        bridgeTask2.setProject(currentProject);
        Building building2 = new Building();
        building2.setName("2号桥");
        bridgeTask2.setBuilding(building2);

        Project previousProject = new Project();
        previousProject.setYear(2024);
        Task previousTask1 = new Task();
        previousTask1.setId(21L);
        previousTask1.setBuildingId(101L);
        previousTask1.setProject(previousProject);

        Task query1 = new Task();
        query1.setBuildingId(101L);
        Task query2 = new Task();
        query2.setBuildingId(102L);

        BiEvaluation currentEval1 = new BiEvaluation();
        currentEval1.setSystemLevel(2);
        currentEval1.setSystemScore(BigDecimal.valueOf(88.1));

        BiEvaluation currentEval2 = new BiEvaluation();
        currentEval2.setSystemLevel(3);
        currentEval2.setSystemScore(BigDecimal.valueOf(76.5));

        BiEvaluation previousEval1 = new BiEvaluation();
        previousEval1.setSystemLevel(2);
        previousEval1.setSystemScore(BigDecimal.valueOf(85.3));

        when(taskMapper.selectTaskList(any(Task.class), eq(null)))
                .thenReturn(Collections.singletonList(previousTask1))
                .thenReturn(Collections.emptyList());

        when(biEvaluationService.selectBiEvaluationByTaskId(11L)).thenReturn(currentEval1);
        when(biEvaluationService.selectBiEvaluationByTaskId(12L)).thenReturn(currentEval2);
        when(biEvaluationService.selectBiEvaluationByTaskId(21L)).thenReturn(previousEval1);

        Building b2 = new Building();
        b2.setRootPropertyId(1000L);
        when(buildingService.selectBuildingById(102L)).thenReturn(b2);

        Property rootProperty = new Property();
        when(propertyService.selectPropertyById(1000L)).thenReturn(rootProperty);

        Property p1 = new Property();
        p1.setName("最近评定日期");
        p1.setValue("2024-05-20");
        Property p2 = new Property();
        p2.setName("桥梁技术状况");
        p2.setValue("4类");
        when(propertyService.selectPropertyList(rootProperty)).thenReturn(Arrays.asList(p1, p2));

        try (MockedStatic<WordFieldUtils> wordMock = org.mockito.Mockito.mockStatic(WordFieldUtils.class)) {
            wordMock.when(() -> WordFieldUtils.createTableCaptionWithCounter(any(), any(), any(), eq(9), any()))
                    .thenReturn("bookmark_multi");
            wordMock.when(() -> WordFieldUtils.createChapterTableReference(any(), eq("bookmark_multi"), any(), any()))
                    .thenAnswer(invocation -> null);

            comparisonAnalysisService.generateMultiBridgeComparisonAnalysisTable(document, paragraph, Arrays.asList(bridgeTask1, bridgeTask2));

            verify(taskMapper, times(2)).selectTaskList(any(Task.class), eq(null));
            verify(biEvaluationService, times(1)).selectBiEvaluationByTaskId(11L);
            verify(biEvaluationService, times(1)).selectBiEvaluationByTaskId(12L);
            verify(buildingService, times(1)).selectBuildingById(102L);
        }
    }

    /**
     * 测试 generateMultiBridgeComparisonAnalysisTable：任务列表为空时抛出运行时异常。
     */
    @Test
    void testGenerateMultiBridgeComparisonAnalysisTable_EmptyTasks() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        assertThrows(RuntimeException.class,
                () -> comparisonAnalysisService.generateMultiBridgeComparisonAnalysisTable(document, paragraph, Collections.emptyList()));
    }

    /**
     * 测试 createComparisonTableWithData：私有方法在正常参数下可创建比较表格。
     */
    @Test
    void testCreateComparisonTableWithData_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        XmlCursor cursor = paragraph.getCTP().newCursor();

        BiEvaluation currentEval = new BiEvaluation();
        currentEval.setSuperstructureScore(BigDecimal.valueOf(90.2));
        currentEval.setSubstructureScore(BigDecimal.valueOf(89.1));
        currentEval.setDeckSystemScore(BigDecimal.valueOf(87.3));
        currentEval.setSystemScore(BigDecimal.valueOf(88.8));
        currentEval.setSystemLevel(2);

        BiEvaluation previousEval = new BiEvaluation();
        previousEval.setSystemLevel(3);
        previousEval.setSystemScore(BigDecimal.valueOf(80.5));

        invokePrivateMethod("createComparisonTableWithData",
                new Class[]{XWPFDocument.class, XmlCursor.class, String.class, Integer.class, BiEvaluation.class, Integer.class, BiEvaluation.class},
                document, cursor, "测试桥", 2025, currentEval, 2024, previousEval);

        assertEquals(1, document.getTables().size());
        XWPFTable table = document.getTables().get(0);
        assertEquals(3, table.getNumberOfRows());
    }

    /**
     * 测试 createComparisonTableWithData：私有方法入参非法时抛出运行时异常。
     */
    @Test
    void testCreateComparisonTableWithData_Exception() {
        XWPFParagraph paragraph = new XWPFDocument().createParagraph();
        XmlCursor cursor = paragraph.getCTP().newCursor();

        assertThrows(RuntimeException.class, () -> invokePrivateMethod("createComparisonTableWithData",
                new Class[]{XWPFDocument.class, XmlCursor.class, String.class, Integer.class, BiEvaluation.class, Integer.class, BiEvaluation.class},
                null, cursor, "测试桥", 2025, null, 2024, null));
    }

    /**
     * 测试 createMultiBridgeComparisonTable：私有方法在多桥场景下正常构建并生成表格。
     */
    @Test
    void testCreateMultiBridgeComparisonTable_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Object pair1 = newBridgePair("A桥", buildEvaluation(3), buildEvaluation(2));
        Object pair2 = newBridgePair("B桥", buildEvaluation(4), buildEvaluation(3));
        List<Object> bridgePairs = Arrays.asList(pair1, pair2);

        try (MockedStatic<WordFieldUtils> wordMock = org.mockito.Mockito.mockStatic(WordFieldUtils.class)) {
            wordMock.when(() -> WordFieldUtils.createTableCaptionWithCounter(any(), any(), any(), eq(9), any()))
                    .thenReturn("bookmark_private");
            wordMock.when(() -> WordFieldUtils.createChapterTableReference(any(), eq("bookmark_private"), any(), any()))
                    .thenAnswer(invocation -> null);

            invokePrivateMethod("createMultiBridgeComparisonTable",
                    new Class[]{XWPFDocument.class, XWPFParagraph.class, List.class, Integer.class, Integer.class, Integer.class, Integer.class},
                    document, paragraph, bridgePairs, 2024, 2025, 4, 3);

            assertEquals(1, document.getTables().size());
            assertEquals(5, document.getTables().get(0).getNumberOfRows());
        }
    }

    /**
     * 测试 createMultiBridgeComparisonTable：私有方法入参异常时抛出运行时异常。
     */
    @Test
    void testCreateMultiBridgeComparisonTable_Exception() {
        assertThrows(RuntimeException.class, () -> invokePrivateMethod("createMultiBridgeComparisonTable",
                new Class[]{XWPFDocument.class, XWPFParagraph.class, List.class, Integer.class, Integer.class, Integer.class, Integer.class},
                null, null, Collections.emptyList(), 2024, 2025, 4, 3));
    }

    /**
     * 测试 mergeCellsVertically：私有方法可正确执行垂直合并。
     */
    @Test
    void testMergeCellsVertically_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFTable table = document.createTable(3, 2);

        assertDoesNotThrow(() -> invokePrivateMethod("mergeCellsVertically",
                new Class[]{XWPFTable.class, int.class, int.class, int.class},
                table, 0, 0, 2));

        XWPFTableRow row0 = table.getRow(0);
        XWPFTableCell cell0 = row0.getCell(0);
        assertNotNull(cell0.getCTTc().getTcPr().getVMerge());
    }

    /**
     * 测试 mergeCellsVertically：私有方法传入空表格时内部捕获异常，不向外抛出。
     */
    @Test
    void testMergeCellsVertically_Exception() {
        assertDoesNotThrow(() -> invokePrivateMethod("mergeCellsVertically",
                new Class[]{XWPFTable.class, int.class, int.class, int.class},
                null, 0, 0, 1));
    }

    private BiEvaluation buildEvaluation(Integer level) {
        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setSystemLevel(level);
        evaluation.setSystemScore(BigDecimal.valueOf(80 + level));
        evaluation.setSuperstructureScore(BigDecimal.valueOf(81 + level));
        evaluation.setSubstructureScore(BigDecimal.valueOf(82 + level));
        evaluation.setDeckSystemScore(BigDecimal.valueOf(83 + level));
        return evaluation;
    }

    private Object newBridgePair(String bridgeName, BiEvaluation previous, BiEvaluation current) {
        try {
            Class<?> pairClass = Class.forName("edu.whut.cs.bi.biz.service.impl.ComparisonAnalysisServiceImpl$BridgeEvaluationPair");
            Constructor<?> constructor = pairClass.getDeclaredConstructor(String.class, BiEvaluation.class, BiEvaluation.class);
            constructor.setAccessible(true);
            return constructor.newInstance(bridgeName, previous, current);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object invokePrivateMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = ComparisonAnalysisServiceImpl.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(comparisonAnalysisService, args);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException) {
                throw (RuntimeException) target;
            }
            throw new RuntimeException(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
