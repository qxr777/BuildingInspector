package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.ProjectMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;

@ExtendWith(MockitoExtension.class)
class PropertyServiceImplTest {

    @Spy
    @InjectMocks
    private PropertyServiceImpl propertyService;

    @Mock
    private PropertyMapper propertyMapper;
    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private IFileMapService fileMapService;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private FileMapController fileMapController;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;

    /**
     * 测试 readJsonFile 正常流程：解析成功并更新建筑与根节点属性
     */
    @Test
    void testReadJsonFile_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "a.json", "application/json", "{\"k\":\"v\"}".getBytes());
        Property oldProperty = new Property();
        oldProperty.setId(1L);

        Building building = new Building();
        building.setId(99L);
        building.setName("测试桥梁");
        building.setRootPropertyId(null);

        doReturn(888L).when(propertyService).buildTree(any(), eq(oldProperty));
        when(buildingMapper.selectBuildingById(99L)).thenReturn(building);

        try (MockedStatic<ShiroUtils> shiroUtils = Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtils.when(ShiroUtils::getLoginName).thenReturn("tester");
            Boolean result = propertyService.readJsonFile(file, oldProperty, 99L);

            assertTrue(result);
            verify(buildingMapper, times(1)).updateBuilding(any(Building.class));
            verify(propertyMapper, times(1)).updateProperty(argThat(p ->
                    Objects.equals(p.getId(), 888L) && "测试桥梁".equals(p.getName())));
        }
    }

    /**
     * 测试 readJsonFile 异常分支：上传空文件抛出业务异常
     */
    @Test
    void testReadJsonFile_EmptyFile() {
        MultipartFile emptyFile = new MockMultipartFile("file", "a.json", "application/json", new byte[0]);

        assertThrows(ServiceException.class, () ->
                propertyService.readJsonFile(emptyFile, new Property(), 1L));
    }

    /**
     * 测试 insertProperty 正常流程：设置祖先与顺序并写入成功
     */
    @Test
    void testInsertProperty_Success() {
        Property input = new Property();
        input.setParentId(10L);

        Property parent = new Property();
        parent.setId(10L);
        parent.setAncestors("0");

        when(propertyMapper.selectPropertyById(10L)).thenReturn(parent);
        when(propertyMapper.getOrderNum(10L)).thenReturn(2);
        when(propertyMapper.insertProperty(any(Property.class))).thenReturn(1);
        when(buildingMapper.selectBuildingList(any(Building.class))).thenReturn(Collections.emptyList());

        int rows = propertyService.insertProperty(input);

        assertEquals(1, rows);
        verify(propertyMapper, times(1)).insertProperty(argThat(p ->
                "0,10".equals(p.getAncestors()) && Objects.equals(p.getOrderNum(), 3)));
        verify(buildingMapper, times(1)).selectBuildingList(any(Building.class));
    }

    /**
     * 测试 insertProperty 异常分支：父节点不存在时抛出业务异常
     */
    @Test
    void testInsertProperty_ParentNotFound() {
        Property input = new Property();
        input.setParentId(999L);

        when(propertyMapper.selectPropertyById(999L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> propertyService.insertProperty(input));
    }

    /**
     * 测试 deletePropertyById 正常流程：先删子树再删自身
     */
    @Test
    void testDeletePropertyById_Success() {
        Property p = new Property();
        p.setId(7L);
        p.setAncestors("0,7");

        when(propertyMapper.selectPropertyById(7L)).thenReturn(p);
        when(buildingMapper.selectBuildingList(any(Building.class))).thenReturn(Collections.emptyList());
        when(propertyMapper.deletePropertyById(7L)).thenReturn(1);

        int rows = propertyService.deletePropertyById(7L);

        assertEquals(1, rows);
        verify(propertyMapper, times(1)).deleteObjectChildren(7L);
        verify(propertyMapper, times(1)).deletePropertyById(7L);
    }

    /**
     * 测试 deletePropertyById 异常分支：属性不存在时抛出业务异常
     */
    @Test
    void testDeletePropertyById_NotFound() {
        when(propertyMapper.selectPropertyById(1000L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> propertyService.deletePropertyById(1000L));
    }

    /**
     * 测试 selectPropertyTree 正常流程：递归组装子节点树
     */
    @Test
    void testSelectPropertyTree_Success() {
        Property root = new Property();
        root.setId(1L);

        Property child = new Property();
        child.setId(2L);

        when(propertyMapper.selectPropertyById(1L)).thenReturn(root);
        when(propertyMapper.selectChildrenByParentId(1L)).thenReturn(List.of(child));
        when(propertyMapper.selectChildrenByParentId(2L)).thenReturn(Collections.emptyList());

        Property result = propertyService.selectPropertyTree(1L);

        assertNotNull(result);
        assertNotNull(result.getChildren());
        assertEquals(1, result.getChildren().size());
        assertEquals(2L, result.getChildren().get(0).getId());
    }

    /**
     * 测试 selectPropertyTree 异常分支：根节点不存在时抛出业务异常
     */
    @Test
    void testSelectPropertyTree_RootNotFound() {
        when(propertyMapper.selectPropertyById(123L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> propertyService.selectPropertyTree(123L));
    }

    /**
     * 测试 readWordFile 正常流程：事务内写入成功并返回 true
     */
    @Test
    void testReadWordFile_Success() throws Exception {
        MockMultipartFile wordFile = new MockMultipartFile("file", "a.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "dummy".getBytes());

        Property property = new Property();
        property.setId(5L);

        Building building = new Building();
        building.setId(66L);
        building.setName("桥A");
        building.setRootPropertyId(null);
        building.setIsLeaf("0");

        doReturn("{\"a\":\"b\"}").when(propertyService).getJsonData(wordFile);
        doReturn(500L).when(propertyService).buildTree(any(), eq(property));
        doNothing().when(propertyService).extractImagesFromWord(any(byte[].class), eq(66L));
        doNothing().when(propertyService).updateTaskAndProject(66L);

        when(buildingMapper.selectBuildingById(66L)).thenReturn(building);
        when(fileMapController.getImageMaps(eq(66L), eq("front"), eq("side"))).thenReturn(Collections.<FileMap>emptyList());

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        try (MockedStatic<ShiroUtils> shiroUtils = Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtils.when(ShiroUtils::getLoginName).thenReturn("tester");

            Boolean result = propertyService.readWordFile(wordFile, property, 66L);
            assertTrue(result);

            verify(transactionTemplate, times(1)).execute(any());
            verify(buildingMapper, atLeastOnce()).updateBuilding(any(Building.class));
            verify(propertyMapper, atLeastOnce()).updateProperty(any(Property.class));
        }

        TimeUnit.MILLISECONDS.sleep(100);
    }

    /**
     * 测试 readWordFile 异常分支：buildingId 为空时抛出业务异常
     */
    @Test
    void testReadWordFile_BuildingIdNull() {
        MockMultipartFile wordFile = new MockMultipartFile("file", "a.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "dummy".getBytes());

        assertThrows(ServiceException.class, () ->
                propertyService.readWordFile(wordFile, new Property(), null));
    }

    /**
     * 测试 extractImagesFromWord 正常流程：无图片文档也能稳定执行
     */
    @Test
    void testExtractImagesFromWord_Success() throws Exception {
        byte[] docxBytes = buildEmptyDocxBytes();

        when(fileMapService.handleBatchFileUpload(any(MultipartFile[].class))).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> propertyService.extractImagesFromWord(docxBytes, 10L));
        verify(fileMapService, times(1)).handleBatchFileUpload(any(MultipartFile[].class));
        verify(fileMapController, never()).uploadAttachment(anyLong(), any(MultipartFile.class), anyString(), anyInt());
    }

    /**
     * 测试 extractImagesFromWord 异常分支：非法文件字节抛出业务异常
     */
    @Test
    void testExtractImagesFromWord_InvalidBytes() {
        // 1. 制造一段绝对不是 Word 格式的假字节
        byte[] invalidBytes = "not-a-docx".getBytes();

        // 2. 随便造一个假的关联 ID
        Long dummyId = 1L;

        // 3. 执行断言，期待它抛出 POI 的异常
        assertThrows(NotOfficeXmlFileException.class, () -> {
            // 直接传入 byte[] 和 Long，完美匹配你的真实方法签名！
            propertyService.extractImagesFromWord(invalidBytes, dummyId);
        });
    }

    private byte[] buildEmptyDocxBytes() throws IOException {
        org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        document.close();
        return out.toByteArray();
    }
}