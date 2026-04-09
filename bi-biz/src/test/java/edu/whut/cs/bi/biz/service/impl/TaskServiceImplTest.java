package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import com.ruoyi.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @InjectMocks
    private TaskServiceImpl taskService;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private BuildingMapper buildingMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private IBiEvaluationService evaluationService;

    @Mock
    private PropertyMapper propertyMapper;

    @Mock
    private IBiObjectService biObjectService;

    @Mock
    private IBiTemplateObjectService biTemplateObjectService;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private ProjectUserMapper projectUserMapper;

    @Mock
    private PackageMapper packageMapper;

    @Mock
    private SqliteService sqliteService;

    /**
     * 测试 selectTaskById：任务存在且桥梁信息可正常加载。
     */
    @Test
    void testSelectTaskById_Success() {
        Task task = new Task();
        task.setId(1L);
        task.setBuildingId(100L);

        Building building = new Building();
        building.setId(100L);

        when(taskMapper.selectTaskById(1L)).thenReturn(task);
        when(buildingMapper.selectBuildingById(100L)).thenReturn(building);

        Task result = taskService.selectTaskById(1L);

        assertNotNull(result);
        assertEquals(100L, result.getBuilding().getId());
        verify(taskMapper, times(1)).selectTaskById(1L);
        verify(buildingMapper, times(1)).selectBuildingById(100L);
    }

    /**
     * 测试 selectTaskById：桥梁被删除时抛出运行时异常。
     */
    @Test
    void testSelectTaskById_BuildingDeleted() {
        Task task = new Task();
        task.setId(1L);
        task.setBuildingId(100L);

        when(taskMapper.selectTaskById(1L)).thenReturn(task);
        when(buildingMapper.selectBuildingById(100L)).thenThrow(new RuntimeException("building not found"));

        assertThrows(RuntimeException.class, () -> taskService.selectTaskById(1L));
        verify(buildingMapper, times(1)).selectBuildingById(100L);
    }

    /**
     * 测试 insertTask：新增任务后同步更新巡检包时间与项目更新时间。
     */
    @Test
    void testInsertTask_Success() {
        Task task = new Task();
        task.setProjectId(10L);
        task.setBuildingId(20L);

        when(taskMapper.insertTask(task)).thenReturn(1);
        when(projectUserMapper.selectUserIdsByProjectAndRole(10L, ProjectUserRoleEnum.INSPECTOR.getValue()))
                .thenReturn(Arrays.asList(11L, 12L));

        int result = taskService.insertTask(task);

        assertEquals(1, result);
        verify(taskMapper, times(1)).insertTask(task);
        verify(packageMapper, times(1)).batchUpdateUpdateTimeNow(Arrays.asList(11L, 12L));
        verify(projectMapper, times(1)).updateProjectTimeByProjectId(20L);
    }

    /**
     * 测试 insertTask：无巡检人员时仅更新任务与项目，不更新巡检包时间。
     */
    @Test
    void testInsertTask_EmptyInspectorList() {
        Task task = new Task();
        task.setProjectId(10L);
        task.setBuildingId(20L);

        when(taskMapper.insertTask(task)).thenReturn(1);
        when(projectUserMapper.selectUserIdsByProjectAndRole(10L, ProjectUserRoleEnum.INSPECTOR.getValue()))
                .thenReturn(Collections.emptyList());

        int result = taskService.insertTask(task);

        assertEquals(1, result);
        verify(taskMapper, times(1)).insertTask(task);
        verify(packageMapper, never()).batchUpdateUpdateTimeNow(any());
        verify(projectMapper, times(1)).updateProjectTimeByProjectId(20L);
    }

    /**
     * 测试 batchInsertTasks：参数合法时批量新增任务并更新项目时间。
     */
    @Test
    void testBatchInsertTasks_Success() {
        Long projectId = 99L;
        Project project = new Project();
        project.setId(projectId);

        when(projectMapper.selectProjectById(projectId)).thenReturn(project);
        when(projectMapper.updateProject(project)).thenReturn(1);
        when(taskMapper.batchInsertTask(eq(projectId), eq(Arrays.asList(1L, 2L)), eq("tester"))).thenReturn(2);
        when(projectUserMapper.selectUserIdsByProjectAndRole(projectId, ProjectUserRoleEnum.INSPECTOR.getValue()))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<ShiroUtils> shiroUtilsMockedStatic = mockStatic(ShiroUtils.class)) {
            shiroUtilsMockedStatic.when(ShiroUtils::getLoginName).thenReturn("tester");

            int result = taskService.batchInsertTasks(projectId, Arrays.asList(1L, 2L));

            assertEquals(2, result);
            verify(projectMapper, times(1)).selectProjectById(projectId);
            verify(projectMapper, times(1)).updateProject(project);
            verify(taskMapper, times(1)).batchInsertTask(projectId, Arrays.asList(1L, 2L), "tester");
        }
    }

    /**
     * 测试 batchInsertTasks：参数为空时抛出业务异常。
     */
    @Test
    void testBatchInsertTasks_InvalidParam() {
        assertThrows(ServiceException.class, () -> taskService.batchInsertTasks(null, Arrays.asList(1L, 2L)));
        assertThrows(ServiceException.class, () -> taskService.batchInsertTasks(1L, null));

        verify(projectMapper, never()).selectProjectById(anyLong());
    }
}
