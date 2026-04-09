package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BridgeCardServiceImplTest {

    @InjectMocks
    private BridgeCardServiceImpl bridgeCardService;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private IPropertyService propertyService;

    @Mock
    private IFileMapService fileMapService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private IBiEvaluationService biEvaluationService;

    /**
     * 测试场景：处理桥梁卡片数据时，依赖数据完整，能正常完成属性替换与图片处理流程。
     */
    @Test
    void testProcessBridgeCardData_Success() {
        XWPFDocument document = createDocumentWithPlaceholders("${桥梁名称}", "${桥梁技术状况}", "${是否公铁两用桥梁}", "${跨径组合}", "${桥梁正面照}", "${概况上部结构}");

        Building building = new Building();
        building.setId(10L);
        building.setName("测试桥");
        building.setRootPropertyId(100L);

        Property rootProperty = new Property();
        rootProperty.setId(100L);

        List<Property> properties = new ArrayList<>();
        properties.add(prop("主桥上部构造结构形式", "钢筋混凝土拱"));
        properties.add(prop("桥梁技术状况", "2类"));
        properties.add(prop("是否公铁两用桥梁", "否"));
        properties.add(prop("跨径组合", "3*20"));

        Attachment attachment = new Attachment();
        attachment.setMinioId(1000L);
        attachment.setName("0_newfront_demo.jpg");

        FileMap fileMap = new FileMap();
        fileMap.setNewName("minio-new-name");

        when(propertyService.selectPropertyById(100L)).thenReturn(rootProperty);
        when(propertyService.selectPropertyList(rootProperty)).thenReturn(properties);
        when(attachmentService.getAttachmentList(10L)).thenReturn(Collections.singletonList(attachment));
        when(fileMapService.selectFileMapById(1000L)).thenReturn(fileMap);
        when(fileMapService.handleFileDownloadByNewName("minio-new-name")).thenReturn(new byte[]{1, 2, 3});

        bridgeCardService.processBridgeCardData(document, building, ReportTemplateTypes.LEVEL_2_ARCH_BRIDGE, 2);

        verify(propertyService, times(1)).selectPropertyById(100L);
        verify(propertyService, times(1)).selectPropertyList(rootProperty);
        verify(attachmentService, times(1)).getAttachmentList(10L);
        verify(fileMapService, times(1)).selectFileMapById(1000L);
        verify(fileMapService, times(1)).handleFileDownloadByNewName("minio-new-name");
    }

    /**
     * 测试场景：处理桥梁卡片数据时，建筑对象为空，应该抛出业务异常。
     */
    @Test
    void testProcessBridgeCardData_BuildingNull() {
        XWPFDocument document = new XWPFDocument();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bridgeCardService.processBridgeCardData(document, null, ReportTemplateTypes.COMBINED_BRIDGE, null));

        assertTrue(ex.getMessage().contains("处理桥梁卡片数据失败"));
    }

    /**
     * 测试场景：调用私有方法处理特殊字段时，关键字段齐全，能正确追加并转换属性。
     */
    @Test
    void testProcessSpecialProp_Success() {
        Building building = new Building();
        building.setName("汉江大桥");

        List<Property> properties = new ArrayList<>();
        properties.add(prop("主桥上部构造结构形式", "连续梁"));
        properties.add(prop("桥梁技术状况", "3类"));
        properties.add(prop("是否公铁两用桥梁", "否"));
        properties.add(prop("跨径组合", "2*30"));

        invokePrivate("processSpecialProp",
                new Class[]{Building.class, List.class, ReportTemplateTypes.class, Integer.class},
                building, properties, ReportTemplateTypes.LEVEL_2_BEAM_BRIDGE, 2);

        assertTrue(properties.stream().anyMatch(p -> "桥梁名称".equals(p.getName()) && "汉江大桥".equals(p.getValue())));
        assertTrue(properties.stream().anyMatch(p -> "结构体系".equals(p.getName()) && "梁桥".equals(p.getValue())));
        assertTrue(properties.stream().anyMatch(p -> "梁桥主桥上部构造结构形式".equals(p.getName()) && "连续梁".equals(p.getValue())));
        assertTrue(properties.stream().anyMatch(p -> "当前评定结果".equals(p.getName()) && "2类".equals(p.getValue())));
        assertTrue(properties.stream().anyMatch(p -> "上一次处治对策".equals(p.getName()) && p.getValue() != null));
        assertTrue(properties.stream().anyMatch(p -> "是否公铁两用桥梁".equals(p.getName()) && "公路".equals(p.getValue())));
        assertTrue(properties.stream().anyMatch(p -> "跨径组合".equals(p.getName()) && "2×30".equals(p.getValue())));
    }

    /**
     * 测试场景：调用私有方法处理特殊字段时，缺少“桥梁技术状况”会触发异常分支。
     */
    @Test
    void testProcessSpecialProp_MissingLastSysLevel() {
        Building building = new Building();
        building.setName("异常桥");

        List<Property> properties = new ArrayList<>();
        properties.add(prop("主桥上部构造结构形式", "钢箱梁"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokePrivate("processSpecialProp",
                        new Class[]{Building.class, List.class, ReportTemplateTypes.class, Integer.class},
                        building, properties, ReportTemplateTypes.LEVEL_2_BEAM_BRIDGE, null));

        assertNotNull(ex);
    }

    /**
     * 测试场景：调用私有方法处理一级桥照片时，按命名规则分发并触发文件下载。
     */
    @Test
    void testProcessImageAttachments_Success() {
        XWPFDocument document = createDocumentWithPlaceholders("${桥梁正面照0}", "${桥梁立面照0}");

        Attachment front = new Attachment();
        front.setMinioId(1L);
        front.setName("0_newfront_demo.jpg");

        Attachment side = new Attachment();
        side.setMinioId(2L);
        side.setName("0_newside_demo.jpg");

        FileMap frontMap = new FileMap();
        frontMap.setNewName("front-new");

        FileMap sideMap = new FileMap();
        sideMap.setNewName("side-new");

        when(attachmentService.getAttachmentList(99L)).thenReturn(List.of(front, side));
        when(fileMapService.selectFileMapById(1L)).thenReturn(frontMap);
        when(fileMapService.selectFileMapById(2L)).thenReturn(sideMap);
        when(fileMapService.handleFileDownloadByNewName("front-new")).thenReturn(new byte[]{1});
        when(fileMapService.handleFileDownloadByNewName("side-new")).thenReturn(new byte[]{2});

        invokePrivate("processImageAttachments",
                new Class[]{XWPFDocument.class, Long.class, ReportTemplateTypes.class},
                document, 99L, ReportTemplateTypes.LEVEL_1_BEAM_BRIDGE);

        verify(attachmentService, times(1)).getAttachmentList(99L);
        verify(fileMapService, times(1)).selectFileMapById(1L);
        verify(fileMapService, times(1)).selectFileMapById(2L);
        verify(fileMapService, times(1)).handleFileDownloadByNewName("front-new");
        verify(fileMapService, times(1)).handleFileDownloadByNewName("side-new");
    }

    /**
     * 测试场景：调用私有方法处理照片时模板类型为空，应该抛出异常。
     */
    @Test
    void testProcessImageAttachments_TemplateTypeNull() {
        XWPFDocument document = new XWPFDocument();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokePrivate("processImageAttachments",
                        new Class[]{XWPFDocument.class, Long.class, ReportTemplateTypes.class},
                        document, 99L, null));

        assertNotNull(ex);
    }

    /**
     * 测试场景：调用私有方法替换段落占位符时，能将匹配占位符替换为目标文本。
     */
    @Test
    void testReplaceTextInParagraphs_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("桥名：${桥梁名称}");

        invokePrivate("replaceTextInParagraphs",
                new Class[]{List.class, String.class, String.class, String.class, double.class},
                Collections.singletonList(paragraph), "${桥梁名称}", "鹦鹉洲大桥", "正文", 12D);

        assertEquals("桥名：鹦鹉洲大桥", paragraph.getText());
    }

    /**
     * 测试场景：调用私有方法替换段落占位符时，段落集合为空应抛出异常。
     */
    @Test
    void testReplaceTextInParagraphs_NullParagraphs() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokePrivate("replaceTextInParagraphs",
                        new Class[]{List.class, String.class, String.class, String.class, double.class},
                        null, "${桥梁名称}", "测试桥", "正文", 12D));

        assertNotNull(ex);
    }

    /**
     * 测试场景：调用私有方法替换剩余占位符时，应将未替换占位符统一替换为“/”。
     */
    @Test
    void testReplaceRemainingPlaceholders_Success() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("结果：${未知字段}");

        invokePrivate("replaceRemainingPlaceholders",
                new Class[]{XWPFParagraph.class},
                paragraph);

        assertEquals("结果：/", paragraph.getText());
    }

    /**
     * 测试场景：调用私有方法替换剩余占位符时，段落对象为空应抛出异常。
     */
    @Test
    void testReplaceRemainingPlaceholders_ParagraphNull() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokePrivate("replaceRemainingPlaceholders",
                        new Class[]{XWPFParagraph.class},
                        new Object[]{null}));

        assertNotNull(ex);
    }

    private Property prop(String name, String value) {
        Property p = new Property();
        p.setName(name);
        p.setValue(value);
        return p;
    }

    private XWPFDocument createDocumentWithPlaceholders(String... placeholders) {
        XWPFDocument document = new XWPFDocument();
        XWPFTable table = document.createTable(1, 1);
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell cell = row.getCell(0);
        cell.removeParagraph(0);
        for (String placeholder : placeholders) {
            XWPFParagraph p = cell.addParagraph();
            p.createRun().setText(placeholder);
        }
        return document;
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = BridgeCardServiceImpl.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(bridgeCardService, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
