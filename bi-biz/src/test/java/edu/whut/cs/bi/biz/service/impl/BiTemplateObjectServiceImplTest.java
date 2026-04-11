package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.IDiseaseTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BiTemplateObjectServiceImplTest {

    @InjectMocks
    private BiTemplateObjectServiceImpl biTemplateObjectService;

    @Mock
    private BiTemplateObjectMapper biTemplateObjectMapper;

    @Mock
    private TODiseaseTypeMapper toDiseaseTypeMapper;

    @Mock
    private DiseaseTypeMapper diseaseTypeMapper;

    @Mock
    private IDiseaseTypeService diseaseTypeService;

    /**
     * 测试 insertBiTemplateObject：父节点存在时正确设置父链并完成新增。
     */
    @Test
    void testInsertBiTemplateObject_Success() {
        BiTemplateObject parent = new BiTemplateObject();
        parent.setId(10L);
        parent.setAncestors("0,1");

        BiTemplateObject input = new BiTemplateObject();
        input.setParentId(10L);

        when(biTemplateObjectMapper.selectBiTemplateObjectById(10L)).thenReturn(parent);
        when(biTemplateObjectMapper.insertBiTemplateObject(any(BiTemplateObject.class))).thenReturn(1);

        int result = biTemplateObjectService.insertBiTemplateObject(input);

        assertEquals(1, result);
        assertEquals(10L, input.getParentId());
        assertEquals("0,1,10", input.getAncestors());
        verify(biTemplateObjectMapper, times(1)).insertBiTemplateObject(input);
    }

    /**
     * 测试 insertBiTemplateObject：新增时底层Mapper异常能够向上抛出。
     */
    @Test
    void testInsertBiTemplateObject_Exception() {
        BiTemplateObject input = new BiTemplateObject();
        input.setParentId(99L);

        when(biTemplateObjectMapper.selectBiTemplateObjectById(99L)).thenReturn(null);
        when(biTemplateObjectMapper.insertBiTemplateObject(any(BiTemplateObject.class)))
                .thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class, () -> biTemplateObjectService.insertBiTemplateObject(input));
    }

    /**
     * 测试 selectChildrenById：子节点存在时正确回填病害类型数量。
     */
    @Test
    void testSelectChildrenById_Success() {
        BiTemplateObject c1 = new BiTemplateObject();
        c1.setId(101L);
        BiTemplateObject c2 = new BiTemplateObject();
        c2.setId(102L);

        Map<String, Object> m1 = new HashMap<>();
        m1.put("key", 101L);
        m1.put("value", 3);
        Map<String, Object> m2 = new HashMap<>();
        m2.put("key", 102L);
        m2.put("value", 5);

        when(biTemplateObjectMapper.selectChildrenById(1L)).thenReturn(Arrays.asList(c1, c2));
        when(toDiseaseTypeMapper.countDiseaseTypesByTemplateObjectIds(Arrays.asList(101L, 102L)))
                .thenReturn(Arrays.asList(m1, m2));

        List<BiTemplateObject> result = biTemplateObjectService.selectChildrenById(1L);

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getDiseaseTypeCount());
        assertEquals(5, result.get(1).getDiseaseTypeCount());
        verify(toDiseaseTypeMapper, times(1)).countDiseaseTypesByTemplateObjectIds(anyList());
    }

    /**
     * 测试 selectChildrenById：统计结果结构异常时抛出运行时异常。
     */
    @Test
    void testSelectChildrenById_InvalidCountMap() {
        BiTemplateObject c1 = new BiTemplateObject();
        c1.setId(101L);

        Map<String, Object> broken = new HashMap<>();
        broken.put("key", null);
        broken.put("value", 3);

        when(biTemplateObjectMapper.selectChildrenById(1L)).thenReturn(Collections.singletonList(c1));
        when(toDiseaseTypeMapper.countDiseaseTypesByTemplateObjectIds(Collections.singletonList(101L)))
                .thenReturn(Collections.singletonList(broken));

        assertThrows(RuntimeException.class, () -> biTemplateObjectService.selectChildrenById(1L));
    }

    /**
     * 测试 batchInsertTemplateDiseaseType：批量病害类型新增时依次写入关联。
     */
    @Test
    void testBatchInsertTemplateDiseaseType_Success() {
        BiTemplateObject current = new BiTemplateObject();
        current.setId(8L);
        current.setAncestors("0,2");

        BiTemplateObject root = new BiTemplateObject();
        root.setId(2L);

        when(biTemplateObjectMapper.selectBiTemplateObjectById(8L)).thenReturn(current);
        when(biTemplateObjectMapper.selectBiTemplateObjectById(2L)).thenReturn(root);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = biTemplateObjectService.batchInsertTemplateDiseaseType(8L, "11,12");

            assertEquals(2, result);
            verify(toDiseaseTypeMapper, times(2)).insertData(anyList(), anyLong());
            verify(biTemplateObjectMapper, times(1)).updateBiTemplateObject(root);
        }
    }

    /**
     * 测试 batchInsertTemplateDiseaseType：病害类型ID字符串非法时抛出异常。
     */
    @Test
    void testBatchInsertTemplateDiseaseType_InvalidIds() {
        BiTemplateObject current = new BiTemplateObject();
        current.setId(8L);
        current.setAncestors("0,2");

        BiTemplateObject root = new BiTemplateObject();
        root.setId(2L);

        when(biTemplateObjectMapper.selectBiTemplateObjectById(8L)).thenReturn(current);
        when(biTemplateObjectMapper.selectBiTemplateObjectById(2L)).thenReturn(root);

        assertThrows(RuntimeException.class,
                () -> biTemplateObjectService.batchInsertTemplateDiseaseType(8L, "11,abc"));
    }

    /**
     * 测试 updateRootBitemplateObject：能够正确定位根节点并更新修改人信息。
     */
    @Test
    void testUpdateRootBitemplateObject_Success() {
        BiTemplateObject current = new BiTemplateObject();
        current.setId(9L);
        current.setAncestors("0,3,9");

        BiTemplateObject root = new BiTemplateObject();
        root.setId(3L);

        when(biTemplateObjectMapper.selectBiTemplateObjectById(9L)).thenReturn(current);
        when(biTemplateObjectMapper.selectBiTemplateObjectById(3L)).thenReturn(root);
        when(biTemplateObjectMapper.updateBiTemplateObject(root)).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");

            biTemplateObjectService.updateRootBitemplateObject(9L);

            assertEquals("tester", root.getUpdateBy());
            verify(biTemplateObjectMapper, times(1)).updateBiTemplateObject(root);
        }
    }

    /**
     * 测试 updateRootBitemplateObject：当前模板对象不存在时抛出异常。
     */
    @Test
    void testUpdateRootBitemplateObject_CurrentTemplateNotFound() {
        when(biTemplateObjectMapper.selectBiTemplateObjectById(9L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> biTemplateObjectService.updateRootBitemplateObject(9L));
    }

    /**
     * 测试 exportTemplateFiles：根节点存在时可正常导出ZIP字节流。
     */
    @Test
    void testExportTemplateFiles_Success() {
        BiTemplateObject root = new BiTemplateObject();
        root.setId(1L);
        root.setParentId(0L);
        root.setName("root");
        root.setUpdateTime(new Date());

        BiTemplateObject child = new BiTemplateObject();
        child.setId(2L);
        child.setParentId(1L);
        child.setName("child");

        when(biTemplateObjectMapper.selectBiTemplateObjectList(any(BiTemplateObject.class)))
                .thenReturn(Collections.singletonList(root));
        when(biTemplateObjectMapper.selectChildrenById(1L)).thenReturn(Collections.singletonList(child));

        Map<Long, List<DiseaseType>> diseaseMap = new HashMap<>();
        diseaseMap.put(1L, Collections.emptyList());
        diseaseMap.put(2L, Collections.emptyList());
        when(diseaseTypeService.batchSelectDiseaseTypeListByTemplateObjectIds(anyList())).thenReturn(diseaseMap);

        byte[] result = biTemplateObjectService.exportTemplateFiles();

        assertNotNull(result);
        assertTrue(result.length > 0);
        verify(diseaseTypeService, times(1)).batchSelectDiseaseTypeListByTemplateObjectIds(anyList());
    }

    /**
     * 测试 exportTemplateFiles：根节点列表包含空元素时抛出异常。
     */
    @Test
    void testExportTemplateFiles_NullRootElement() {
        when(biTemplateObjectMapper.selectBiTemplateObjectList(any(BiTemplateObject.class)))
                .thenReturn(Collections.singletonList(null));

        assertThrows(RuntimeException.class, () -> biTemplateObjectService.exportTemplateFiles());
    }
}
