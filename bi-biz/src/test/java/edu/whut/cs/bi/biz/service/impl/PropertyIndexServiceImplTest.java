package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.service.ISysDictDataService;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyIndexServiceImplTest {

    @InjectMocks
    private PropertyIndexServiceImpl propertyIndexService;

    @Mock
    private PropertyMapper propertyMapper;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private IPropertyService propertyService;

    @Mock
    private BuildingMapper buildingMapper;

    @Mock
    private ISysDictDataService dictDataService;

    @Mock
    private Executor executorService;

    /**
     * 测试场景：按名称查询属性树时，正常返回根节点+子节点。
     */
    @Test
    void testSelectPropertyTree_Success() {
        Property root = new Property();
        root.setId(1L);
        root.setParentId(0L);
        root.setName("根属性");

        Property child = new Property();
        child.setId(2L);
        child.setParentId(1L);
        child.setName("子属性");

        when(propertyMapper.selectPropertyByName(any(Property.class))).thenReturn(Collections.singletonList(root));
        when(propertyMapper.selectChildrenObjectById(1L)).thenReturn(Collections.singletonList(child));

        List<Ztree> result = propertyIndexService.selectPropertyTree("桥梁");

        assertEquals(2, result.size());
        verify(propertyMapper, times(1)).selectPropertyByName(any(Property.class));
        verify(propertyMapper, times(1)).selectChildrenObjectById(1L);
    }

    /**
     * 测试场景：按名称查询属性树时，子节点返回null触发异常。
     */
    @Test
    void testSelectPropertyTree_ChildrenNull() {
        Property root = new Property();
        root.setId(1L);
        root.setName("根属性");

        when(propertyMapper.selectPropertyByName(any(Property.class))).thenReturn(Collections.singletonList(root));
        when(propertyMapper.selectChildrenObjectById(1L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> propertyIndexService.selectPropertyTree("桥梁"));
    }

    /**
     * 测试场景：导入单桥属性Excel成功，完成旧树删除与新树构建。
     */
    @Test
    void testReadExcelPropertyData_Success() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "property.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildExcelBytes(150));

        Building building = new Building();
        building.setId(1L);
        building.setRootPropertyId(88L);
        Property oldRoot = new Property();
        oldRoot.setId(88L);

        when(buildingService.selectBuildingById(1L)).thenReturn(building);
        when(propertyService.selectPropertyById(88L)).thenReturn(oldRoot);
        when(propertyMapper.insertProperty(any(Property.class))).thenReturn(1);
        when(buildingMapper.updateBuilding(any(Building.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            Boolean result = propertyIndexService.readExcelPropertyData(file, 1L);

            assertTrue(result);
            verify(propertyService, times(1)).deletePropertyById(88L);
            verify(propertyMapper, times(146)).insertProperty(any(Property.class));
            verify(buildingMapper, times(1)).updateBuilding(any(Building.class));
        }
    }

    /**
     * 测试场景：导入单桥属性Excel时文件读取失败，抛出业务异常。
     */
    @Test
    void testReadExcelPropertyData_FileReadError() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("io error"));

        assertThrows(ServiceException.class, () -> propertyIndexService.readExcelPropertyData(file, 1L));
    }

    /**
     * 测试场景：批量导入成功，匹配到桥梁并完成属性树构建，未匹配列表为空。
     */
    @Test
    void testBatchImportPropertyData_Success() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "batch.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildExcelBytes(150));

        Building matched = new Building();
        matched.setId(100L);
        matched.setName("测试桥");
        matched.setLine("L-1");
        matched.setStatus("0");

        when(buildingService.selectBuildingList(any(Building.class))).thenReturn(Collections.singletonList(matched));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        when(propertyMapper.insertProperty(any(Property.class))).thenReturn(1);
        when(buildingMapper.updateBuilding(any(Building.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            List<String> result = propertyIndexService.batchImportPropertyData(file, true);

            assertTrue(result.isEmpty());
            verify(buildingMapper, times(1)).updateBuilding(any(Building.class));
            verify(propertyMapper, times(146)).insertProperty(any(Property.class));
        }
    }

    /**
     * 测试场景：批量导入时Excel文件读取失败，抛出业务异常。
     */
    @Test
    void testBatchImportPropertyData_FileReadError() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("io error"));

        assertThrows(ServiceException.class, () -> propertyIndexService.batchImportPropertyData(file, false));
    }

    /**
     * 测试场景：处理单行数据成功，命中桥梁并完成删除旧树、保存新树、更新桥梁。
     */
    @Test
    void testProcessSingleRow_Success() {
        Row titleRow = buildRow(150, true);
        Row dataRow = buildRow(150, false);

        Building matched = new Building();
        matched.setId(200L);
        matched.setName("测试桥");
        matched.setLine("L-1");
        matched.setStatus("0");
        matched.setRootPropertyId(300L);

        Property oldRoot = new Property();
        oldRoot.setId(300L);

        when(propertyService.selectPropertyById(300L)).thenReturn(oldRoot);
        when(propertyMapper.insertProperty(any(Property.class))).thenReturn(1);
        when(buildingMapper.updateBuilding(any(Building.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            propertyIndexService.processSingleRow(Collections.singletonList(matched), dataRow, titleRow, true);

            verify(propertyService, times(1)).deletePropertyById(300L);
            verify(buildingMapper, times(1)).updateBuilding(any(Building.class));
            verify(propertyMapper, times(146)).insertProperty(any(Property.class));
        }
    }

    /**
     * 测试场景：处理单行数据时未匹配到桥梁，直接拦截并不执行保存逻辑。
     */
    @Test
    void testProcessSingleRow_NoMatchedBuilding() {
        Row titleRow = buildRow(150, true);
        Row dataRow = buildRow(150, false);

        propertyIndexService.processSingleRow(Collections.emptyList(), dataRow, titleRow, true);

        verify(propertyMapper, never()).insertProperty(any(Property.class));
        verify(buildingMapper, never()).updateBuilding(any(Building.class));
    }

    private byte[] buildExcelBytes(int columnSize) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            Row title = sheet.createRow(0);
            Row data = sheet.createRow(1);
            for (int i = 0; i < columnSize; i++) {
                title.createCell(i).setCellValue("标题" + i);
                data.createCell(i).setCellValue("值" + i);
            }
            data.getCell(1).setCellValue("测试桥");
            data.getCell(5).setCellValue("L-1");
            data.getCell(10).setCellValue("测试片区");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Row buildRow(int columnSize, boolean titleFlag) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("temp");
        Row row = sheet.createRow(0);
        for (int i = 0; i < columnSize; i++) {
            row.createCell(i).setCellValue(titleFlag ? ("标题" + i) : ("值" + i));
        }
        if (!titleFlag) {
            row.getCell(1).setCellValue("测试桥");
            row.getCell(5).setCellValue("L-1");
            row.getCell(10).setCellValue("测试片区");
        }
        return row;
    }
}
