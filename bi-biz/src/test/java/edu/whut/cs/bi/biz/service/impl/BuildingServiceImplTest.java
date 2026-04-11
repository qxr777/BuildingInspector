package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import edu.whut.cs.bi.biz.service.ITaskService;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.mapper.SysDictDataMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildingServiceImplTest {

    private MockedStatic<ShiroUtils> shiroUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        shiroUtilsMockedStatic = org.mockito.Mockito.mockStatic(ShiroUtils.class);
        shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("testUser");
    }

    @AfterEach
    void tearDown() {
        if (shiroUtilsMockedStatic != null) {
            shiroUtilsMockedStatic.close();
        }
    }

    @InjectMocks
    private BuildingServiceImpl buildingService;

    @Mock
    private BuildingMapper buildingMapper;

    @Mock
    private IBiObjectService biObjectService;

    @Mock
    private IBiTemplateObjectService biTemplateObjectService;

    @Mock
    private ITaskService taskService;

    @Mock
    private SysDictDataMapper sysDictDataMapper;

    /**
     * 测试场景：新增组合桥（无父桥）时，正常创建根节点并保存建筑。
     */
    @Test
    void testInsertBuilding_Success() {
        Building input = new Building();
        input.setName("武汉长江大桥");
        input.setArea("A1");
        input.setLine("L1");
        input.setIsLeaf("0");

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            BiObject root = invocation.getArgument(0);
            root.setId(100L);
            return null;
        }).when(biObjectService).insertBiObject(any(BiObject.class));
        when(buildingMapper.insertBuilding(any(Building.class))).thenReturn(1);

        int rows = buildingService.insertBuilding(input);

        assertEquals(1, rows);
        assertEquals(100L, input.getRootObjectId());
        verify(biObjectService, times(1)).insertBiObject(any(BiObject.class));
        verify(buildingMapper, times(1)).insertBuilding(input);
    }

    /**
     * 测试场景：新增桥跨时挂载到父桥根节点，并创建空根节点。
     */
    @Test
    void testInsertBuilding_SpanWithParent_NoTemplate() {
        Building input = new Building();
        input.setName("K1跨");
        input.setIsLeaf("2");
        input.setParentId(10L);

        Building parent = new Building();
        parent.setId(10L);
        parent.setRootObjectId(200L);

        BiObject parentRoot = new BiObject();
        parentRoot.setId(200L);
        parentRoot.setAncestors("0,9");

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(10L)).thenReturn(parent);
        when(biObjectService.selectBiObjectById(200L)).thenReturn(parentRoot);
        doAnswer(invocation -> {
            BiObject root = invocation.getArgument(0);
            root.setId(300L);
            return null;
        }).when(biObjectService).insertBiObject(any(BiObject.class));
        when(buildingMapper.insertBuilding(any(Building.class))).thenReturn(1);

        int rows = buildingService.insertBuilding(input);

        assertEquals(1, rows);
        assertEquals(300L, input.getRootObjectId());
        verify(biObjectService, times(1)).insertBiObject(argThat(obj ->
                obj.getParentId().equals(200L)
                        && "0,9,200".equals(obj.getAncestors())
                        && "K1跨".equals(obj.getName())
        ));
    }

    /**
     * 测试场景：新增桥跨并指定模板时，进入维护树生成分支并回填 rootObjectId。
     */
    @Test
    void testInsertBuilding_SpanWithParent_WithTemplate() {
        Building input = new Building();
        input.setName("K2跨");
        input.setIsLeaf("2");
        input.setParentId(10L);
        input.setTemplateId(500L);

        Building parent = new Building();
        parent.setId(10L);
        parent.setRootObjectId(200L);

        BiTemplateObject template = new BiTemplateObject();
        template.setId(500L);
        template.setName("梁桥模板");
        template.setParentId(999L);

        BiTemplateObject childTemplate = new BiTemplateObject();
        childTemplate.setId(501L);
        childTemplate.setParentId(500L);
        childTemplate.setName("上部结构");
        childTemplate.setOrderNum(1);

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(10L)).thenReturn(parent);
        when(biTemplateObjectService.selectBiTemplateObjectById(500L)).thenReturn(template);
        when(biTemplateObjectService.selectChildrenById(500L)).thenReturn(Collections.singletonList(childTemplate));

        doAnswer(invocation -> {
            BiObject root = invocation.getArgument(0);
            root.setId(300L);
            return null;
        }).when(biObjectService).insertBiObject(argThat(obj ->
                "K2跨(梁桥模板)".equals(obj.getName()) && obj.getParentId().equals(200L)
        ));

        doAnswer(invocation -> {
            List<BiObject> nodes = invocation.getArgument(0);
            if (nodes != null && !nodes.isEmpty()) {
                nodes.get(0).setId(301L);
            }
            return null;
        }).when(biObjectService).batchInsertBiObjects(any(List.class));

        when(buildingMapper.insertBuilding(any(Building.class))).thenReturn(1);

        int rows = buildingService.insertBuilding(input);

        assertEquals(1, rows);
        assertEquals(300L, input.getRootObjectId());
        verify(biTemplateObjectService, times(1)).selectBiTemplateObjectById(500L);
        verify(biTemplateObjectService, times(1)).selectChildrenById(500L);
        verify(biObjectService, times(1)).insertBiObject(argThat(obj ->
                "K2跨(梁桥模板)".equals(obj.getName())
                        && obj.getParentId().equals(200L)
        ));
        verify(biObjectService, times(1)).batchInsertBiObjects(any(List.class));
    }

    /**
     * 测试场景：新增建筑时若片区+线路+桥名重复，应抛出业务异常。
     */
    @Test
    void testInsertBuilding_DuplicateName() {
        Building input = new Building();
        input.setName("重复桥");
        input.setArea("A1");
        input.setLine("L1");
        input.setIsLeaf("0");

        Building existed = new Building();
        existed.setId(1L);
        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.singletonList(existed));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> buildingService.insertBuilding(input));

        assertEquals("该片区线路桥梁已存在", ex.getMessage());
    }

    /**
     * 测试场景：修改建筑时父桥发生变化，需迁移根节点并批量更新子节点祖先路径。
     */
    @Test
    void testUpdateBuilding_Success() {
        Building updateParam = new Building();
        updateParam.setId(1L);
        updateParam.setName("新桥名");
        updateParam.setArea("A1");
        updateParam.setLine("L1");
        updateParam.setParentId(2L);

        Building oldBuilding = new Building();
        oldBuilding.setId(1L);
        oldBuilding.setName("旧桥名");
        oldBuilding.setArea("A1");
        oldBuilding.setLine("L1");
        oldBuilding.setParentId(null);
        oldBuilding.setRootObjectId(100L);

        Building newParent = new Building();
        newParent.setId(2L);
        newParent.setRootObjectId(200L);

        BiObject oldRootObject = new BiObject();
        oldRootObject.setId(100L);
        oldRootObject.setAncestors("0,1");

        BiObject newParentObject = new BiObject();
        newParentObject.setId(200L);
        newParentObject.setAncestors("0,9");

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id.equals(1L)) {
                return oldBuilding;
            }
            if (id.equals(2L)) {
                return newParent;
            }
            return null;
        });
        when(biObjectService.selectBiObjectById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id.equals(100L)) {
                return oldRootObject;
            }
            if (id.equals(200L)) {
                return newParentObject;
            }
            return null;
        });
        when(buildingMapper.updateBuilding(any(Building.class))).thenReturn(1);

        int rows = buildingService.updateBuilding(updateParam);

        assertEquals(1, rows);
        verify(biObjectService, times(1)).updateBiObject(any(BiObject.class));
        verify(biObjectService, times(1)).batchUpdateAncestors(
                eq(100L),
                eq("0,1,100"),
                eq("0,9,200,100"),
                anyString()
        );
        verify(buildingMapper, times(1)).updateBuilding(updateParam);
    }

    /**
     * 测试场景：修改建筑时若检测到重复记录（非自身ID），应抛出业务异常。
     */
    @Test
    void testUpdateBuilding_DuplicateName() {
        Building updateParam = new Building();
        updateParam.setId(10L);
        updateParam.setName("重复桥");
        updateParam.setArea("A1");
        updateParam.setLine("L1");

        Building another = new Building();
        another.setId(99L);
        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.singletonList(another));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> buildingService.updateBuilding(updateParam));

        assertEquals("该片区线路桥梁已存在", ex.getMessage());
    }

    /**
     * 测试场景：删除组合桥时应先删除子桥任务与记录，再逻辑删除部件树并删除当前建筑。
     */
    @Test
    void testDeleteBuildingById_Success() {
        Building current = new Building();
        current.setId(1L);
        current.setIsLeaf("0");
        current.setRootObjectId(100L);

        Building child1 = new Building();
        child1.setId(2L);
        Building child2 = new Building();
        child2.setId(3L);

        when(buildingMapper.selectBuildingById(1L)).thenReturn(current);
        when(buildingMapper.selectAllChildBridges(100L)).thenReturn(Arrays.asList(child1, child2));
        when(buildingMapper.deleteBuildingById(1L)).thenReturn(1);

        int rows = buildingService.deleteBuildingById(1L);

        assertEquals(1, rows);
        verify(taskService, times(1)).deleteTaskByBuildingId(2L);
        verify(taskService, times(1)).deleteTaskByBuildingId(3L);
        verify(buildingMapper, times(1)).deleteBuildingByIds(any(String[].class));
        verify(biObjectService, times(1)).logicDeleteByRootObjectId(eq(100L), anyString());
        verify(buildingMapper, times(1)).deleteBuildingById(1L);
    }

    /**
     * 测试场景：删除组合桥过程中发生异常时，应统一包装为“删除建筑失败”异常抛出。
     */
    @Test
    void testDeleteBuildingById_Exception() {
        Building current = new Building();
        current.setId(1L);
        current.setIsLeaf("0");
        current.setRootObjectId(100L);

        when(buildingMapper.selectBuildingById(1L)).thenReturn(current);
        when(buildingMapper.selectAllChildBridges(100L)).thenThrow(new RuntimeException("db error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> buildingService.deleteBuildingById(1L));

        assertEquals("删除建筑失败", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    /**
     * 测试场景：新增桥幅/桥跨指定模板但模板不存在时，应抛出异常。
     */
    @Test
    void testInsertBuilding_WithTemplateButTemplateNotFound() {
        Building input = new Building();
        input.setName("K3跨");
        input.setIsLeaf("1");
        input.setTemplateId(999L);

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        when(biTemplateObjectService.selectBiTemplateObjectById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> buildingService.insertBuilding(input));

        assertEquals("未找到指定模版!", ex.getMessage());
    }

    /**
     * 测试场景：修改建筑时若记录不存在，应直接返回0。
     */
    @Test
    void testUpdateBuilding_WhenOldBuildingNotFound() {
        Building updateParam = new Building();
        updateParam.setId(100L);
        updateParam.setName("不存在的桥");
        updateParam.setArea("A1");
        updateParam.setLine("L1");

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(100L)).thenReturn(null);

        int rows = buildingService.updateBuilding(updateParam);

        assertEquals(0, rows);
        verify(buildingMapper, times(0)).updateBuilding(any(Building.class));
    }

    /**
     * 测试场景：仅修改名称时，应同步更新根节点名称。
     */
    @Test
    void testUpdateBuilding_NameChangedOnly() {
        Building updateParam = new Building();
        updateParam.setId(1L);
        updateParam.setName("新桥名");
        updateParam.setArea("A1");
        updateParam.setLine("L1");

        Building oldBuilding = new Building();
        oldBuilding.setId(1L);
        oldBuilding.setName("旧桥名");
        oldBuilding.setRootObjectId(100L);

        when(buildingMapper.checkBuildingUnique(any(Building.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(1L)).thenReturn(oldBuilding);
        when(buildingMapper.updateBuilding(any(Building.class))).thenReturn(1);

        int rows = buildingService.updateBuilding(updateParam);

        assertEquals(1, rows);
        verify(biObjectService, times(1)).updateBiObject(argThat(obj ->
                obj.getId().equals(100L) && "新桥名".equals(obj.getName())
        ));
        verify(biObjectService, times(0)).batchUpdateAncestors(anyLong(), anyString(), anyString(), anyString());
    }

    /**
     * 测试场景：删除非组合桥时应删除任务、逻辑删除部件树并删除当前记录。
     */
    @Test
    void testDeleteBuildingById_NonCompositeWithRoot() {
        Building current = new Building();
        current.setId(5L);
        current.setIsLeaf("1");
        current.setRootObjectId(500L);

        when(buildingMapper.selectBuildingById(5L)).thenReturn(current);
        when(buildingMapper.deleteBuildingById(5L)).thenReturn(1);

        int rows = buildingService.deleteBuildingById(5L);

        assertEquals(1, rows);
        verify(biObjectService, times(1)).logicDeleteByRootObjectId(eq(500L), anyString());
        verify(taskService, times(1)).deleteTaskByBuildingId(5L);
        verify(buildingMapper, times(1)).deleteBuildingById(5L);
    }

    /**
     * 测试场景：删除建筑时若ID不存在，应返回0。
     */
    @Test
    void testDeleteBuildingById_NotFound() {
        when(buildingMapper.selectBuildingById(999L)).thenReturn(null);

        int rows = buildingService.deleteBuildingById(999L);

        assertEquals(0, rows);
        verify(buildingMapper, times(0)).deleteBuildingById(anyLong());
        verify(taskService, times(0)).deleteTaskByBuildingId(anyLong());
    }

    /**
     * 测试场景：查询子桥ID时若当前组合桥不存在或无根节点，应返回空列表。
     */
    @Test
    void testSelectChildBuildingIds_EmptyCases() {
        when(buildingMapper.selectBuildingById(1L)).thenReturn(null);

        List<Long> result1 = buildingService.selectChildBuildingIds(1L);
        assertEquals(0, result1.size());

        Building noRoot = new Building();
        noRoot.setId(2L);
        noRoot.setRootObjectId(null);
        when(buildingMapper.selectBuildingById(2L)).thenReturn(noRoot);

        List<Long> result2 = buildingService.selectChildBuildingIds(2L);
        assertEquals(0, result2.size());
    }
}

