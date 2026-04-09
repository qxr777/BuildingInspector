package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BiObjectServiceImplTest {

    @InjectMocks
    private BiObjectServiceImpl biObjectServiceImpl;

    @Mock
    private BiObjectMapper biObjectMapper;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private DiseaseTypeServiceImpl diseaseTypeService;

    @Mock
    private IBiObjectService biObjectService;

    @Mock
    private FileMapServiceImpl fileMapServiceImpl;

    @Mock
    private ComponentMapper componentMapper;

    @Mock
    private DiseaseMapper diseaseMapper;

    @Mock
    private IFileMapService fileMapService;

    /**
     * 测试场景：新增根节点对象时，正常设置祖先并成功入库。
     */
    @Test
    void testInsertBiObject_Success() {
        BiObject object = new BiObject();
        object.setParentId(0L);
        object.setName("上部结构");

        when(biObjectMapper.insertBiObject(any(BiObject.class))).thenReturn(1);

        int result = biObjectServiceImpl.insertBiObject(object);

        assertEquals(1, result);
        assertEquals("0", object.getAncestors());
        verify(biObjectMapper, times(1)).insertBiObject(object);
    }

    /**
     * 测试场景：新增子节点时父节点不存在，触发业务异常拦截。
     */
    @Test
    void testInsertBiObject_ParentNotExists() {
        BiObject object = new BiObject();
        object.setParentId(100L);

        when(biObjectMapper.selectBiObjectById(100L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> biObjectServiceImpl.insertBiObject(object));
        verify(biObjectMapper, never()).insertBiObject(any(BiObject.class));
    }

    /**
     * 测试场景：修改对象数量发生变化时，触发向上递归更新父节点数量。
     */
    @Test
    void testUpdateBiObject_Success() {
        BiObject oldObj = new BiObject();
        oldObj.setId(1L);
        oldObj.setParentId(100L);
        oldObj.setCount(5);

        BiObject updateObj = new BiObject();
        updateObj.setId(1L);
        updateObj.setName("主梁");
        updateObj.setCount(8);

        BiObject sibling1 = new BiObject();
        sibling1.setId(1L);
        sibling1.setStatus("0");
        sibling1.setCount(8);

        BiObject sibling2 = new BiObject();
        sibling2.setId(2L);
        sibling2.setStatus("0");
        sibling2.setCount(2);

        BiObject parent = new BiObject();
        parent.setId(100L);
        parent.setParentId(0L);
        parent.setName("父节点");

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(oldObj);
        when(biObjectMapper.updateBiObject(any(BiObject.class))).thenReturn(1);
        when(biObjectMapper.selectBiObjectList(any(BiObject.class))).thenReturn(List.of(sibling1, sibling2));
        when(biObjectMapper.selectBiObjectById(100L)).thenReturn(parent);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = biObjectServiceImpl.updateBiObject(updateObj);

            assertEquals(1, result);
            verify(biObjectMapper, times(2)).updateBiObject(any(BiObject.class));
        }
    }

    /**
     * 测试场景：修改对象时底层更新失败，抛出运行时异常。
     */
    @Test
    void testUpdateBiObject_UpdateException() {
        BiObject oldObj = new BiObject();
        oldObj.setId(1L);
        oldObj.setParentId(100L);
        oldObj.setCount(5);

        BiObject updateObj = new BiObject();
        updateObj.setId(1L);
        updateObj.setName("主梁");
        updateObj.setCount(8);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(oldObj);
        doThrow(new RuntimeException("db error")).when(biObjectMapper).updateBiObject(any(BiObject.class));

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            assertThrows(RuntimeException.class, () -> biObjectServiceImpl.updateBiObject(updateObj));
        }
    }

    /**
     * 测试场景：删除对象时存在同级节点，先重分配权重再执行逻辑删除。
     */
    @Test
    void testDeleteBiObjectById_Success() {
        Long id = 10L;
        Long parentId = 20L;

        BiObject target = new BiObject();
        target.setId(id);
        target.setParentId(parentId);
        target.setWeight(new BigDecimal("0.2000"));

        BiObject sibling1 = new BiObject();
        sibling1.setId(id);
        sibling1.setStatus("0");
        sibling1.setWeight(new BigDecimal("0.2000"));

        BiObject sibling2 = new BiObject();
        sibling2.setId(11L);
        sibling2.setStatus("0");
        sibling2.setWeight(new BigDecimal("0.8000"));

        when(biObjectMapper.selectBiObjectById(id)).thenReturn(target);
        when(biObjectMapper.selectBiObjectList(any(BiObject.class))).thenReturn(List.of(sibling1, sibling2));
        when(biObjectMapper.updateBiObjects(any(List.class))).thenReturn(1);
        when(biObjectMapper.logicDeleteByRootObjectId(id, "tester")).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = biObjectServiceImpl.deleteBiObjectById(id);

            assertEquals(1, result);
            verify(biObjectMapper, times(1)).updateBiObjects(any(List.class));
            verify(biObjectMapper, times(1)).logicDeleteByRootObjectId(id, "tester");
        }
    }

    /**
     * 测试场景：删除对象时逻辑删除阶段失败，抛出运行时异常。
     */
    @Test
    void testDeleteBiObjectById_DeleteException() {
        Long id = 10L;

        BiObject target = new BiObject();
        target.setId(id);
        target.setParentId(20L);
        target.setWeight(new BigDecimal("0.2000"));

        BiObject sibling1 = new BiObject();
        sibling1.setId(id);
        sibling1.setStatus("0");
        sibling1.setWeight(new BigDecimal("0.2000"));

        BiObject sibling2 = new BiObject();
        sibling2.setId(11L);
        sibling2.setStatus("0");
        sibling2.setWeight(new BigDecimal("0.8000"));

        when(biObjectMapper.selectBiObjectById(id)).thenReturn(target);
        when(biObjectMapper.selectBiObjectList(any(BiObject.class))).thenReturn(List.of(sibling1, sibling2));
        when(biObjectMapper.updateBiObjects(any(List.class))).thenReturn(1);
        when(biObjectMapper.logicDeleteByRootObjectId(anyLong(), anyString())).thenThrow(new RuntimeException("delete failed"));

        try (MockedStatic<ShiroUtils> shiroUtilsMock = org.mockito.Mockito.mockStatic(ShiroUtils.class)) {
            shiroUtilsMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            assertThrows(RuntimeException.class, () -> biObjectServiceImpl.deleteBiObjectById(id));
        }
    }

    /**
     * 测试场景：导出桥梁结构JSON时，正常组装树结构与病害类型并返回JSON字符串。
     */
    @Test
    void testBridgeStructureJson_Success() throws Exception {
        Long rootId = 1L;

        BiObject root = new BiObject();
        root.setId(rootId);
        root.setParentId(0L);
        root.setName("桥梁");
        root.setTemplateObjectId(100L);

        BiObject child = new BiObject();
        child.setId(2L);
        child.setParentId(rootId);
        child.setName("主梁");
        child.setTemplateObjectId(200L);

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(300L);

        when(biObjectMapper.selectBiObjectById(rootId)).thenReturn(root);
        when(biObjectService.selectBiObjectAndChildren(rootId)).thenReturn(List.of(root, child));
        when(diseaseTypeService.batchSelectDiseaseTypeListByTemplateObjectIds(any(List.class)))
                .thenReturn(Map.of(100L, List.of(diseaseType), 200L, new ArrayList<>()));

        String json = biObjectServiceImpl.bridgeStructureJson(rootId);

        assertNotNull(json);
        assertTrue(json.contains("桥梁"));
        assertTrue(json.contains("主梁"));
        verify(biObjectService, times(1)).selectBiObjectAndChildren(rootId);
        verify(diseaseTypeService, times(1)).batchSelectDiseaseTypeListByTemplateObjectIds(any(List.class));
    }

    /**
     * 测试场景：导出桥梁结构JSON时根节点不存在，抛出业务异常。
     */
    @Test
    void testBridgeStructureJson_RootNotFound() {
        Long rootId = 1L;
        when(biObjectMapper.selectBiObjectById(rootId)).thenReturn(null);

        assertThrows(Exception.class, () -> biObjectServiceImpl.bridgeStructureJson(rootId));
        verify(biObjectService, never()).selectBiObjectAndChildren(anyLong());
    }

    /**
     * 测试场景：导出带图片的桥梁结构JSON时，正常组装图片与备注信息并返回JSON。
     */
    @Test
    void testBridgeStructureJsonWithPictures_Success() throws Exception {
        Long rootId = 1L;

        BiObject root = new BiObject();
        root.setId(rootId);
        root.setParentId(0L);
        root.setName("桥梁");

        BiObject child = new BiObject();
        child.setId(2L);
        child.setParentId(rootId);
        child.setName("主梁");

        FileMap rootPhoto = new FileMap();
        rootPhoto.setSubjectId(rootId);
        rootPhoto.setUrl("/a/root.jpg");
        rootPhoto.setAttachmentRemark("root-info");

        FileMap childPhoto = new FileMap();
        childPhoto.setSubjectId(2L);
        childPhoto.setUrl("/a/child.jpg");
        childPhoto.setAttachmentRemark("child-info");

        when(biObjectMapper.selectBiObjectById(rootId)).thenReturn(root);
        when(biObjectService.selectBiObjectAndChildren(rootId)).thenReturn(List.of(root, child));
        when(fileMapService.selectBiObjectPhotoList(rootId)).thenReturn(List.of(rootPhoto, childPhoto));

        String json = biObjectServiceImpl.bridgeStructureJsonWithPictures(rootId);

        assertNotNull(json);
        assertTrue(json.contains("root.jpg"));
        assertTrue(json.contains("child.jpg"));
        verify(fileMapService, times(1)).selectBiObjectPhotoList(rootId);
    }

    /**
     * 测试场景：导出带图片的桥梁结构JSON时根节点不存在，抛出业务异常。
     */
    @Test
    void testBridgeStructureJsonWithPictures_RootNotFound() {
        Long rootId = 1L;
        when(biObjectMapper.selectBiObjectById(rootId)).thenReturn(null);

        assertThrows(Exception.class, () -> biObjectServiceImpl.bridgeStructureJsonWithPictures(rootId));
        verify(fileMapService, never()).selectBiObjectPhotoList(anyLong());
    }
}
