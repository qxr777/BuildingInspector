package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ReportMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.ReportGenerateTools;
import io.minio.MinioClient;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Report1LevelSingleBridgeServiceImplTest {

    @org.mockito.InjectMocks
    private Report1LevelSingleBridgeServiceImpl service;

    @org.mockito.Mock
    private ReportMapper reportMapper;
    @org.mockito.Mock
    private IReportDataService reportDataService;
    @org.mockito.Mock
    private IReportTemplateService reportTemplateService;
    @org.mockito.Mock
    private ITemplateVariableService templateVariableService;
    @org.mockito.Mock
    private IFileMapService fileMapService;
    @org.mockito.Mock
    private IProjectService projectService;
    @org.mockito.Mock
    private IBuildingService buildingService;
    @org.mockito.Mock
    private BiObjectMapper biObjectMapper;
    @org.mockito.Mock
    private FileMapController fileMapController;
    @org.mockito.Mock
    private MinioClient minioClient;
    @org.mockito.Mock
    private MinioConfig minioConfig;
    @org.mockito.Mock
    private DiseaseController diseaseController;
    @org.mockito.Mock
    private IComponentService componentService;
    @org.mockito.Mock
    private DiseaseComparisonService diseaseComparisonService;
    @org.mockito.Mock
    private IDiseaseService diseaseService;
    @org.mockito.Mock
    private DiseaseMapper diseaseMapper;
    @org.mockito.Mock
    private DiseaseTypeMapper diseaseTypeMapper;
    @org.mockito.Mock
    private IBiEvaluationService biEvaluationService;
    @org.mockito.Mock
    private EvaluationTableService evaluationTableService;
    @org.mockito.Mock
    private ComparisonAnalysisService comparisonAnalysisService;
    @org.mockito.Mock
    private TestConclusionService testConclusionService;
    @org.mockito.Mock
    private IBridgeCardService bridgeCardService;
    @org.mockito.Mock
    private RegularInspectionService regularInspectionService;
    @org.mockito.Mock
    private IBiTemplateObjectService biTemplateObjectService;

    /**
     * 测试 getDiseaseSummary 正常场景：序列化成功并调用 AI 接口返回结果。
     */
    @Test
    void testGetDiseaseSummary_Success() throws Exception {
        ReflectionTestUtils.setField(service, "SpringAiUrl", "http://mock-ai");

        try (MockedConstruction<RestTemplate> restTemplateMockedConstruction =
                     mockConstruction(RestTemplate.class, (mock, context) ->
                             when(mock.postForObject(anyString(), any(), eq(String.class))).thenReturn("AI病害小结"))) {

            Disease disease = new Disease();
            disease.setId(1L);
            disease.setType("规范#裂缝");
            disease.setPosition("部位#跨中");

            String result = service.getDiseaseSummary(Collections.singletonList(disease));

            assertEquals("AI病害小结", result);
            RestTemplate constructed = restTemplateMockedConstruction.constructed().get(0);
            verify(constructed, times(1))
                    .postForObject(eq("http://mock-ai/api-ai/diseaseSummary"), any(), eq(String.class));
        }
    }

    /**
     * 测试 getDiseaseSummary 异常场景：JSON 序列化失败抛出异常。
     */
    @Test
    void testGetDiseaseSummary_JsonProcessingException() {
        ReflectionTestUtils.setField(service, "SpringAiUrl", "http://mock-ai");

        try (MockedConstruction<com.fasterxml.jackson.databind.ObjectMapper> objectMapperMockedConstruction =
                     mockConstruction(com.fasterxml.jackson.databind.ObjectMapper.class,
                             (mock, context) -> when(mock.writeValueAsString(any()))
                                     .thenThrow(new JsonProcessingException("serialize error") {
                                     }))) {

            Disease disease = new Disease();
            disease.setId(2L);
            disease.setType("规范#腐蚀");
            disease.setPosition("部位#桥面");

            assertThrows(JsonProcessingException.class,
                    () -> service.getDiseaseSummary(Collections.singletonList(disease)));
        }
    }

    /**
     * 测试 handleEvaluationResults 正常场景：评定结果存在并成功生成评定表。
     */
    @Test
    void testHandleEvaluationResults_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("${evaluationResults}");

        Building building = new Building();
        building.setName("测试桥");

        BiEvaluation evaluation = buildEvaluation(1001L);
        when(biEvaluationService.selectBiEvaluationByTaskId(1001L)).thenReturn(evaluation);
        doNothing().when(evaluationTableService).generateEvaluationTableAfterParagraph(
                any(XWPFDocument.class), any(XWPFParagraph.class), eq(building), eq(evaluation), eq("测试桥")
        );

        ReflectionTestUtils.invokeMethod(service, "handleEvaluationResults",
                document, "${evaluationResults}", building, 1001L);

        verify(biEvaluationService, times(1)).selectBiEvaluationByTaskId(1001L);
        verify(evaluationTableService, times(1)).generateEvaluationTableAfterParagraph(
                any(XWPFDocument.class), any(XWPFParagraph.class), eq(building), eq(evaluation), eq("测试桥")
        );
    }

    /**
     * 测试 handleEvaluationResults 异常场景：生成评定表时抛出运行时异常。
     */
    @Test
    void testHandleEvaluationResults_Exception() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("${evaluationResults}");

        Building building = new Building();
        building.setName("异常桥");

        BiEvaluation evaluation = buildEvaluation(2002L);
        when(biEvaluationService.selectBiEvaluationByTaskId(2002L)).thenReturn(evaluation);
        doThrow(new RuntimeException("评定表生成失败")).when(evaluationTableService)
                .generateEvaluationTableAfterParagraph(any(), any(), any(), any(), anyString());

        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "handleEvaluationResults", document, "${evaluationResults}", building, 2002L
        ));
    }

    /**
     * 测试 handleComparisonAnalysis 正常场景：找到占位符并成功调用比较分析服务。
     */
    @Test
    void testHandleComparisonAnalysis_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("${comparativeAnalysisOfEvaluationResults}");

        Task task = new Task();
        task.setId(3003L);

        doNothing().when(comparisonAnalysisService).generateComparisonAnalysisTable(
                any(XWPFDocument.class), any(XWPFParagraph.class), eq(task), eq("比较桥"), eq(false)
        );

        ReflectionTestUtils.invokeMethod(service, "handleComparisonAnalysis",
                document, "${comparativeAnalysisOfEvaluationResults}", task, "比较桥", false);

        verify(comparisonAnalysisService, times(1)).generateComparisonAnalysisTable(
                any(XWPFDocument.class), any(XWPFParagraph.class), eq(task), eq("比较桥"), eq(false)
        );
    }

    /**
     * 测试 handleComparisonAnalysis 异常场景：比较分析服务抛出运行时异常。
     */
    @Test
    void testHandleComparisonAnalysis_Exception() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("${comparativeAnalysisOfEvaluationResults}");

        Task task = new Task();
        task.setId(4004L);

        doThrow(new RuntimeException("比较分析失败")).when(comparisonAnalysisService)
                .generateComparisonAnalysisTable(any(), any(), any(), anyString(), anyBoolean());

        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "handleComparisonAnalysis",
                document, "${comparativeAnalysisOfEvaluationResults}", task, "比较桥", true
        ));
    }

    /**
     * 测试 handleTestConclusion 正常场景：找到占位符并成功调用检测结论服务。
     */
    @Test
    void testHandleTestConclusion_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Task task = new Task();
        task.setId(5005L);

        BiEvaluation evaluation = buildEvaluation(5005L);
        when(biEvaluationService.selectBiEvaluationByTaskId(5005L)).thenReturn(evaluation);
        doNothing().when(testConclusionService).handleTestConclusion(
                any(XWPFDocument.class), any(XWPFParagraph.class), anyList(), eq("结论桥"), anyMap(), eq(2)
        );

        try (MockedStatic<ReportGenerateTools> mockedStatic = mockStatic(ReportGenerateTools.class)) {
            mockedStatic.when(() -> ReportGenerateTools.findParagraphByPlaceholder(document, "${testConclusion}"))
                    .thenReturn(paragraph);

            ReflectionTestUtils.invokeMethod(service, "handleTestConclusion",
                    document, "${testConclusion}", task, "结论桥");
        }

        verify(testConclusionService, times(1)).handleTestConclusion(
                any(XWPFDocument.class), eq(paragraph), anyList(), eq("结论桥"), anyMap(), eq(2)
        );
    }

    /**
     * 测试 handleTestConclusion 异常场景：检测结论服务抛出运行时异常。
     */
    @Test
    void testHandleTestConclusion_Exception() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Task task = new Task();
        task.setId(6006L);

        BiEvaluation evaluation = buildEvaluation(6006L);
        when(biEvaluationService.selectBiEvaluationByTaskId(6006L)).thenReturn(evaluation);
        doThrow(new RuntimeException("检测结论失败")).when(testConclusionService)
                .handleTestConclusion(any(), any(), anyList(), anyString(), anyMap(), anyInt());

        try (MockedStatic<ReportGenerateTools> mockedStatic = mockStatic(ReportGenerateTools.class)) {
            mockedStatic.when(() -> ReportGenerateTools.findParagraphByPlaceholder(document, "${testConclusion}"))
                    .thenReturn(paragraph);

            assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                    service, "handleTestConclusion", document, "${testConclusion}", task, "结论桥"
            ));
        }
    }

    /**
     * 测试 handleTestConclusionBridge 正常场景：找到占位符并成功调用桥梁详情结论服务。
     */
    @Test
    void testHandleTestConclusionBridge_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Task task = new Task();
        task.setId(7007L);

        doNothing().when(testConclusionService).handleTestConclusionBridge(
                any(XWPFDocument.class), any(XWPFParagraph.class), anyList()
        );

        try (MockedStatic<ReportGenerateTools> mockedStatic = mockStatic(ReportGenerateTools.class)) {
            mockedStatic.when(() -> ReportGenerateTools.findParagraphByPlaceholder(document, "${testConclusionBridge}"))
                    .thenReturn(paragraph);

            ReflectionTestUtils.invokeMethod(service, "handleTestConclusionBridge",
                    document, "${testConclusionBridge}", task, "详情桥");
        }

        verify(testConclusionService, times(1)).handleTestConclusionBridge(
                any(XWPFDocument.class), eq(paragraph), anyList()
        );
    }

    /**
     * 测试 handleTestConclusionBridge 异常场景：桥梁详情结论服务抛出运行时异常。
     */
    @Test
    void testHandleTestConclusionBridge_Exception() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();

        Task task = new Task();
        task.setId(8008L);

        doThrow(new RuntimeException("桥梁详情结论失败")).when(testConclusionService)
                .handleTestConclusionBridge(any(), any(), anyList());

        try (MockedStatic<ReportGenerateTools> mockedStatic = mockStatic(ReportGenerateTools.class)) {
            mockedStatic.when(() -> ReportGenerateTools.findParagraphByPlaceholder(document, "${testConclusionBridge}"))
                    .thenReturn(paragraph);

            assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                    service, "handleTestConclusionBridge", document, "${testConclusionBridge}", task, "详情桥"
            ));
        }
    }

    private BiEvaluation buildEvaluation(Long taskId) {
        BiEvaluation evaluation = new BiEvaluation();
        evaluation.setTaskId(taskId);
        evaluation.setSuperstructureScore(new BigDecimal("85.2"));
        evaluation.setSubstructureScore(new BigDecimal("88.3"));
        evaluation.setDeckSystemScore(new BigDecimal("90.1"));
        evaluation.setSystemScore(new BigDecimal("86.4"));
        evaluation.setSuperstructureLevel(2);
        evaluation.setSubstructureLevel(2);
        evaluation.setDeckSystemLevel(1);
        evaluation.setSystemLevel(2);
        return evaluation;
    }
}