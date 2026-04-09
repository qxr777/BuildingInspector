package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.dto.ProjectUserAssignment;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.PackageMapper;
import edu.whut.cs.bi.biz.mapper.ProjectMapper;
import edu.whut.cs.bi.biz.mapper.ProjectUserMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private SysDeptMapper deptMapper;

    @Mock
    private ISysDeptService deptService;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private ProjectUserMapper projectUserMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private PackageMapper packageMapper;

    /**
     * 测试 selectProjectList：部门管理员查询项目列表并完成项目信息补全。
     */
    @Test
    void testSelectProjectList_Success() {
        Project query = new Project();
        query.setSelect("department");

        SysUser currentUser = new SysUser();
        currentUser.setUserId(1L);
        currentUser.setDeptId(10L);

        SysDept currentDept = new SysDept();
        currentDept.setDeptId(10L);
        currentDept.setParentId(100L);

        Project dbProject = new Project();
        dbProject.setId(101L);
        dbProject.setDeptId(100L);
        dbProject.setOwnerDeptId(200L);

        SysDept dept = new SysDept();
        dept.setDeptId(100L);
        SysDept ownerDept = new SysDept();
        ownerDept.setDeptId(200L);

        Task task = new Task();

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class);
             MockedStatic<PageUtils> pageUtilsMock = org.mockito.Mockito.mockStatic(PageUtils.class)) {
            shiroMock.when(ShiroUtils::getUserId).thenReturn(1L);
            pageUtilsMock.when(PageUtils::startPage).thenAnswer(invocation -> null);

            when(sysUserMapper.selectUserRoleByUserId(1L)).thenReturn(Collections.singletonList("department_business_admin"));
            when(sysUserMapper.selectUserById(1L)).thenReturn(currentUser);
            when(deptService.selectDeptById(10L)).thenReturn(currentDept);
            when(projectMapper.selectProjectList(any(Project.class), eq(null), eq(null))).thenReturn(Collections.singletonList(dbProject));
            when(deptService.selectDeptById(100L)).thenReturn(dept);
            when(deptService.selectDeptById(200L)).thenReturn(ownerDept);
            when(taskMapper.selectTaskListByProjectId(101L)).thenReturn(Collections.singletonList(task));

            List<Project> result = projectService.selectProjectList(query);

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getBridgeCount());
            assertEquals(100L, result.get(0).getParentDeptId());
            verify(projectMapper, times(1)).selectProjectList(any(Project.class), eq(null), eq(null));
            verify(taskMapper, times(1)).selectTaskListByProjectId(101L);
        }
    }

    /**
     * 测试 selectProjectList：当前用户为空时触发空指针异常。
     */
    @Test
    void testSelectProjectList_Exception() {
        Project query = new Project();
        query.setSelect("department");

        try (MockedStatic<ShiroUtils> shiroMock = org.mockito.Mockito.mockStatic(ShiroUtils.class);
             MockedStatic<PageUtils> pageUtilsMock = org.mockito.Mockito.mockStatic(PageUtils.class)) {
            shiroMock.when(ShiroUtils::getUserId).thenReturn(1L);
            pageUtilsMock.when(PageUtils::startPage).thenAnswer(invocation -> null);

            when(sysUserMapper.selectUserRoleByUserId(1L)).thenReturn(Collections.singletonList("user"));
            when(sysUserMapper.selectUserById(1L)).thenReturn(null);

            assertThrows(NullPointerException.class, () -> projectService.selectProjectList(query));
        }
    }

    /**
     * 测试 updateProject：带状态更新时同步更新项目下所有任务状态。
     */
    @Test
    void testUpdateProject_Success() {
        Project project = new Project();
        project.setId(1L);
        project.setStatus("1");

        Task task1 = new Task();
        task1.setId(11L);
        Task task2 = new Task();
        task2.setId(12L);

        when(taskMapper.selectTaskListByProjectId(1L)).thenReturn(Arrays.asList(task1, task2));
        when(projectMapper.updateProject(project)).thenReturn(1);

        int result = projectService.updateProject(project);

        assertEquals(1, result);
        verify(taskMapper, times(2)).updateTask(any(Task.class));
        verify(projectMapper, times(1)).updateProject(project);
    }

    /**
     * 测试 updateProject：任务列表为空时触发异常分支。
     */
    @Test
    void testUpdateProject_Exception() {
        Project project = new Project();
        project.setId(1L);
        project.setStatus("1");

        when(taskMapper.selectTaskListByProjectId(1L)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> projectService.updateProject(project));
    }

    /**
     * 测试 selectProjectVOById：查询项目详情并补全人员与任务信息。
     */
    @Test
    void testSelectProjectVOById_Success() {
        Project project = new Project();
        project.setId(1L);
        project.setDeptId(10L);
        project.setOwnerDeptId(20L);

        SysDept dept = new SysDept();
        dept.setDeptId(10L);
        SysDept ownerDept = new SysDept();
        ownerDept.setDeptId(20L);

        SysUser inspector1 = new SysUser();
        inspector1.setUserId(11L);
        SysUser inspector2 = new SysUser();
        inspector2.setUserId(12L);
        SysUser author = new SysUser();
        author.setUserId(21L);
        SysUser reviewer = new SysUser();
        reviewer.setUserId(31L);
        SysUser approver = new SysUser();
        approver.setUserId(41L);

        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(deptService.selectDeptById(10L)).thenReturn(dept);
        when(deptService.selectDeptById(20L)).thenReturn(ownerDept);
        when(projectUserMapper.selectUserIdsByProjectAndRole(1L, ProjectUserRoleEnum.INSPECTOR.getValue())).thenReturn(Arrays.asList(11L, 12L));
        when(projectUserMapper.selectUserIdsByProjectAndRole(1L, ProjectUserRoleEnum.AUTHOR.getValue())).thenReturn(Collections.singletonList(21L));
        when(projectUserMapper.selectUserIdsByProjectAndRole(1L, ProjectUserRoleEnum.REVIEWER.getValue())).thenReturn(Collections.singletonList(31L));
        when(projectUserMapper.selectUserIdsByProjectAndRole(1L, ProjectUserRoleEnum.APPROVER.getValue())).thenReturn(Collections.singletonList(41L));
        when(sysUserMapper.selectUserById(11L)).thenReturn(inspector1);
        when(sysUserMapper.selectUserById(12L)).thenReturn(inspector2);
        when(sysUserMapper.selectUserById(21L)).thenReturn(author);
        when(sysUserMapper.selectUserById(31L)).thenReturn(reviewer);
        when(sysUserMapper.selectUserById(41L)).thenReturn(approver);
        when(taskMapper.selectTaskListByProjectId(1L)).thenReturn(Collections.singletonList(new Task()));

        Project result = projectService.selectProjectVOById(1L);

        assertNotNull(result);
        assertEquals(2, result.getInspectors().size());
        assertEquals(21L, result.getAuthor().getUserId());
        assertEquals(31L, result.getReviewer().getUserId());
        assertEquals(41L, result.getApprover().getUserId());
        assertEquals(1, result.getTasks().size());
        verify(taskMapper, times(1)).selectTaskListByProjectId(1L);
    }

    /**
     * 测试 selectProjectVOById：查询人员信息失败时抛出运行时异常。
     */
    @Test
    void testSelectProjectVOById_Exception() {
        Project project = new Project();
        project.setId(1L);
        project.setDeptId(10L);
        project.setOwnerDeptId(20L);

        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(deptService.selectDeptById(anyLong())).thenReturn(new SysDept());
        when(projectUserMapper.selectUserIdsByProjectAndRole(1L, ProjectUserRoleEnum.INSPECTOR.getValue())).thenReturn(Collections.singletonList(11L));
        when(sysUserMapper.selectUserById(11L)).thenThrow(new RuntimeException("查询用户失败"));

        assertThrows(RuntimeException.class, () -> projectService.selectProjectVOById(1L));
    }

    /**
     * 测试 saveProjectUserAssignments：正常保存并更新变更检测人员相关数据。
     */
    @Test
    void testSaveProjectUserAssignments_Success() {
        ProjectUserAssignment assignment = new ProjectUserAssignment();
        assignment.setProjectId(1L);
        assignment.setInspectorIds(Arrays.asList(1L, 2L));
        assignment.setAuthorId(3L);
        assignment.setReviewerId(4L);
        assignment.setApproverId(5L);

        when(projectUserMapper.countProjectUser(1L)).thenReturn(1);
        when(projectUserMapper.selectUserIdsByProjectAndRole(1L, ProjectUserRoleEnum.INSPECTOR.getValue())).thenReturn(Arrays.asList(2L, 6L));
        when(projectUserMapper.saveProjectUser(1L, Arrays.asList(1L, 2L), ProjectUserRoleEnum.INSPECTOR.getValue())).thenReturn(2);
        when(projectUserMapper.saveProjectUser(1L, Collections.singletonList(3L), ProjectUserRoleEnum.AUTHOR.getValue())).thenReturn(1);
        when(projectUserMapper.saveProjectUser(1L, Collections.singletonList(4L), ProjectUserRoleEnum.REVIEWER.getValue())).thenReturn(1);
        when(projectUserMapper.saveProjectUser(1L, Collections.singletonList(5L), ProjectUserRoleEnum.APPROVER.getValue())).thenReturn(1);

        int result = projectService.saveProjectUserAssignments(assignment);

        assertEquals(5, result);
        verify(projectUserMapper, times(1)).deleteProjectUser(1L);
        verify(packageMapper, times(1)).batchUpdateUpdateTimeNow(Arrays.asList(6L, 1L));
        verify(projectMapper, times(1)).updateProjectTimeByProjectId(1L);
    }

    /**
     * 测试 saveProjectUserAssignments：传入空参数时抛出业务异常。
     */
    @Test
    void testSaveProjectUserAssignments_Exception() {
        assertThrows(ServiceException.class, () -> projectService.saveProjectUserAssignments(null));
    }

    /**
     * 测试 selectProjectListByUserIdAndRole：正常查询并异步补全关联信息。
     */
    @Test
    void testSelectProjectListByUserIdAndRole_Success() {
        Project query = new Project();
        Project project = new Project();
        project.setId(1L);
        project.setDeptId(10L);
        project.setOwnerDeptId(20L);

        SysDept dept = new SysDept();
        dept.setDeptId(10L);
        SysDept ownerDept = new SysDept();
        ownerDept.setDeptId(20L);

        when(projectMapper.selectProjectList(query, 100L, "inspector")).thenReturn(Collections.singletonList(project));
        when(deptMapper.selectDeptById(10L)).thenReturn(dept);
        when(deptMapper.selectDeptById(20L)).thenReturn(ownerDept);
        when(projectUserMapper.selectUsersByProjectAndRole(1L, ProjectUserRoleEnum.INSPECTOR.getValue())).thenReturn(Collections.singletonList(new SysUser()));

        List<Project> result = projectService.selectProjectListByUserIdAndRole(query, 100L, "inspector");

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getDept());
        assertNotNull(result.get(0).getOwnerDept());
        assertEquals(1, result.get(0).getInspectors().size());
        verify(projectUserMapper, times(1)).selectUsersByProjectAndRole(1L, ProjectUserRoleEnum.INSPECTOR.getValue());
    }

    /**
     * 测试 selectProjectListByUserIdAndRole：异步补全部门信息失败时抛出异常。
     */
    @Test
    void testSelectProjectListByUserIdAndRole_Exception() {
        Project query = new Project();
        Project project = new Project();
        project.setId(1L);
        project.setDeptId(10L);
        project.setOwnerDeptId(20L);

        when(projectMapper.selectProjectList(query, 100L, "inspector")).thenReturn(Collections.singletonList(project));
        when(deptMapper.selectDeptById(10L)).thenThrow(new RuntimeException("部门查询失败"));

        assertThrows(CompletionException.class, () -> projectService.selectProjectListByUserIdAndRole(query, 100L, "inspector"));
    }
}
