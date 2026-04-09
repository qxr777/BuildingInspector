package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ConditionMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegularInspectionServiceImplTest {

    @InjectMocks
    private RegularInspectionServiceImpl regularInspectionService;

    @Mock
    private BiObjectMapper biObjectMapper;

    @Mock
    private DiseaseMapper diseaseMapper;

    @Mock
    private ConditionMapper conditionMapper;

    @Mock
    private IBiEvaluationService biEvaluationService;

    @Mock
    private IPropertyService propertyService;

    /**
     * 测试场景：定期检查记录表生成成功，依赖数据完整并触发表格主体查询流程。
     */
    @Test
    void testGenerateRegularInspectionTable_Success() throws Exception {
        XWPFDocument document = new XWPFDocument();
        document.createParagraph().createRun().setText("前置内容");
        document.createParagraph().createRun().setText("${REGULAR_INSPECTION}");

        Building building = new Building();
        building.setId(10L);
        building.setName("测试桥梁");
        building.setRootObjectId(1L);

        Project project = new Project();
        project.setId(20L);

        Task task = new Task();
        task.setId(30L);
        task.setBuilding(building);
        task.setProject(project);

        BiObject root = new BiObject();
        root.setId(1L);

        BiObject level2 = new BiObject();
        level2.setId(2L);
        level2.setParentId(1L);
        level2.setName("上部结构");

        BiObject level3 = new BiObject();
        level3.setId(3L);
        level3.setParentId(2L);
        level3.setName("主梁");

        BiObject level4 = new BiObject();
        level4.setId(4L);
        level4.setParentId(3L);
        level4.setName("腹板");

        BiEvaluation biEvaluation = new BiEvaluation();
        biEvaluation.setId(100L);

        Condition condition = new Condition();
        condition.setBiObjectId(3L);
        condition.setScore(BigDecimal.valueOf(95));

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setName("裂缝");

        Disease disease = new Disease();
        disease.setBiObjectId(4L);
        disease.setDiseaseType(diseaseType);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(level2, level3, level4));
        when(biEvaluationService.selectBiEvaluationByTaskId(30L)).thenReturn(biEvaluation);
        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(List.of(condition));
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(List.of(disease));

        regularInspectionService.generateRegularInspectionTable(document, "${REGULAR_INSPECTION}", List.of(task));

        verify(biObjectMapper, times(1)).selectBiObjectById(1L);
        verify(biObjectMapper, times(1)).selectChildrenById(1L);
        verify(conditionMapper, times(1)).selectConditionList(any(Condition.class));
        verify(diseaseMapper, times(1)).selectDiseaseList(any(Disease.class));
    }

    /**
     * 测试场景：定期检查记录表生成时任务列表为空导致运行时异常。
     */
    @Test
    void testGenerateRegularInspectionTable_NullTasks() {
        XWPFDocument document = new XWPFDocument();
        document.createParagraph().createRun().setText("${REGULAR_INSPECTION}");

        assertThrows(NullPointerException.class,
                () -> regularInspectionService.generateRegularInspectionTable(document, "${REGULAR_INSPECTION}", null));
    }

    /**
     * 测试场景：单桥定期检查表填充成功，能够完成评分/病害计算并触发属性查询与替换。
     */
    @Test
    void testFillSingleBridgeRegularInspectionTable_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFTable table = document.createTable(1, 2);
        table.getRow(0).getCell(0).setText("${评分1}");
        table.getRow(0).getCell(1).setText("${类型1}");

        Building building = new Building();
        building.setId(10L);
        building.setRootObjectId(1L);
        building.setRootPropertyId(500L);

        Project project = new Project();
        project.setId(20L);

        Task task = new Task();
        task.setId(30L);

        BiObject root = new BiObject();
        root.setId(1L);

        BiObject level2 = new BiObject();
        level2.setId(2L);
        level2.setParentId(1L);
        level2.setName("上部结构");

        BiObject level3 = new BiObject();
        level3.setId(3L);
        level3.setParentId(2L);
        level3.setName("支座");

        BiObject level4 = new BiObject();
        level4.setId(4L);
        level4.setParentId(3L);
        level4.setName("垫石");

        BiObject conditionObject = new BiObject();
        conditionObject.setWeight(BigDecimal.ONE);

        BiEvaluation biEvaluation = new BiEvaluation();
        biEvaluation.setId(100L);

        Condition condition = new Condition();
        condition.setBiObjectId(3L);
        condition.setScore(BigDecimal.valueOf(88));
        condition.setBiObject(conditionObject);

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setName("剥落");

        Disease disease = new Disease();
        disease.setBiObjectId(4L);
        disease.setDiseaseType(diseaseType);

        Property rootProperty = new Property();
        rootProperty.setId(500L);

        Property spanProperty = new Property();
        spanProperty.setName("跨径组合");
        spanProperty.setValue("3*20+2*30.5");

        Property dateProperty = new Property();
        dateProperty.setName("最近评定日期");
        dateProperty.setValue("2024-01-01");

        List<Property> allProperties = new ArrayList<>();
        allProperties.add(spanProperty);
        allProperties.add(dateProperty);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(level2, level3, level4));
        when(biEvaluationService.selectBiEvaluationByTaskId(30L)).thenReturn(biEvaluation);
        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(List.of(condition));
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(List.of(disease));
        when(propertyService.selectPropertyById(500L)).thenReturn(rootProperty);
        when(propertyService.selectPropertyList(rootProperty)).thenReturn(allProperties);

        regularInspectionService.fillSingleBridgeRegularInspectionTable(
                document,
                building,
                task,
                project,
                ReportTemplateTypes.LEVEL_2_BEAM_BRIDGE
        );

        verify(biObjectMapper, times(1)).selectBiObjectById(1L);
        verify(conditionMapper, times(2)).selectConditionList(any(Condition.class));
        verify(diseaseMapper, times(1)).selectDiseaseList(any(Disease.class));
        verify(propertyService, times(1)).selectPropertyById(500L);
        verify(propertyService, times(1)).selectPropertyList(rootProperty);
    }

    /**
     * 测试场景：单桥定期检查表填充时属性服务异常，方法应抛出运行时异常。
     */
    @Test
    void testFillSingleBridgeRegularInspectionTable_PropertyServiceException() {
        XWPFDocument document = new XWPFDocument();
        document.createTable(1, 1).getRow(0).getCell(0).setText("${评分1}");

        Building building = new Building();
        building.setId(10L);
        building.setRootObjectId(1L);
        building.setRootPropertyId(500L);

        Project project = new Project();
        project.setId(20L);

        Task task = new Task();
        task.setId(30L);

        BiObject root = new BiObject();
        root.setId(1L);

        BiObject level2 = new BiObject();
        level2.setId(2L);
        level2.setParentId(1L);

        BiObject level3 = new BiObject();
        level3.setId(3L);
        level3.setParentId(2L);
        level3.setName("支座");

        Property rootProperty = new Property();
        rootProperty.setId(500L);

        BiEvaluation biEvaluation = new BiEvaluation();
        biEvaluation.setId(100L);

        Condition condition = new Condition();
        condition.setBiObjectId(3L);
        condition.setScore(BigDecimal.valueOf(80));
        BiObject conditionObject = new BiObject();
        conditionObject.setWeight(BigDecimal.ONE);
        condition.setBiObject(conditionObject);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(List.of(level2, level3));
        when(biEvaluationService.selectBiEvaluationByTaskId(30L)).thenReturn(biEvaluation);
        when(conditionMapper.selectConditionList(any(Condition.class))).thenReturn(List.of(condition));
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(Collections.emptyList());
        when(propertyService.selectPropertyById(500L)).thenReturn(rootProperty);
        doThrow(new RuntimeException("property service error")).when(propertyService).selectPropertyList(rootProperty);

        assertThrows(RuntimeException.class,
                () -> regularInspectionService.fillSingleBridgeRegularInspectionTable(
                        document,
                        building,
                        task,
                        project,
                        ReportTemplateTypes.LEVEL_2_BEAM_BRIDGE
                ));
    }
}
