package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import java.io.IOException;
import java.util.stream.Collectors;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.ISysDeptService;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.dto.ProjectUserAssignment;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import edu.whut.cs.bi.biz.mapper.ProjectBuildingMapper;
import edu.whut.cs.bi.biz.mapper.ProjectUserMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.whut.cs.bi.biz.mapper.ProjectMapper;
import edu.whut.cs.bi.biz.service.IProjectService;

import com.ruoyi.common.core.text.Convert;


import javax.annotation.Resource;

/**
 * 项目Service业务层处理
 *
 * @author chenwenqi
 * @date 2025-04-02
 */
@Service
public class ProjectServiceImpl implements IProjectService {
    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private SysDeptMapper deptMapper;

    @Resource
    private ISysDeptService deptService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ProjectUserMapper projectUserMapper;

    @Resource
    private ProjectBuildingMapper projectBuildingMapper;

    @Resource
    private TaskMapper taskMapper;
    /**
     * 查询项目
     *
     * @param id 项目主键
     * @return 项目
     */
    @Override
    public Project selectProjectById(Long id) {
        return projectMapper.selectProjectById(id);
    }

    /**
     * 查询项目列表
     *
     * @param project 项目
     * @return 项目
     */
    @Override
    public List<Project> selectProjectList(Project project) {
        Long currentUserId = ShiroUtils.getUserId();
        String role = sysUserMapper.selectUserRoleByUserId(currentUserId);

        List<Project> projects = null;
        if (role.equals("admin")) {
            // 超级管理员
            projects = projectMapper.selectProjectList(project, null);
        } else {
            projects = projectMapper.selectProjectList(project, currentUserId);
        }

        projects.forEach(pj -> {
            // 承担单位
            SysDept dept = deptService.selectDeptById(pj.getDeptId());
            pj.setDept(dept);
            // 所属单位
            SysDept ownerDept = deptService.selectDeptById(pj.getOwnerDeptId());
            pj.setOwnerDept(ownerDept);
        });
        return projects;
    }

    /**
     * 新增项目
     *
     * @param project 项目
     * @return 结果
     */
    @Override
    @Transactional
    public int insertProject(Project project) {
        project.setCreateTime(DateUtils.getNowDate());

        return projectMapper.insertProject(project);
    }

    /**
     * 修改项目
     *
     * @param project 项目
     * @return 结果
     */
    @Override
    public int updateProject(Project project) {
        project.setUpdateTime(DateUtils.getNowDate());
        return projectMapper.updateProject(project);
    }

    /**
     * 批量删除项目
     *
     * @param ids 需要删除的项目主键
     * @return 结果
     */
    @Override
    public int deleteProjectByIds(String ids) {
        return projectMapper.deleteProjectByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除项目信息
     *
     * @param id 项目主键
     * @return 结果
     */
    @Override
    public int deleteProjectById(Long id) {
        return projectMapper.deleteProjectById(id);
    }

    /**
     * 查询承担单位树列表
     *
     * @param sysDept
     * @return
     */
    @Override
    public List<Ztree> selectDeptTree(SysDept sysDept) {

        List<SysDept> deptList = deptMapper.selectDeptList(sysDept);
        // 设置业主自检的选项
        SysDept dept = new SysDept();
        dept.setDeptId(-1L);
        dept.setParentId(0L);
        dept.setStatus("0");
        dept.setDeptName("业主自检");
        deptList.add(dept);

        List<Ztree> ztrees = deptService.initZtree(deptList);
        return ztrees;
    }

    /**
     * 查询所属单位树列表
     *
     * @param sysDept
     * @return
     */
    @Override
    public List<Ztree> selectOwnerDeptTree(SysDept sysDept) {

        List<SysDept> deptList = deptMapper.selectDeptList(sysDept);

        List<Ztree> ztrees = deptService.initZtree(deptList);
        return ztrees;
    }

    /**
     * 查询项目封装实体
     *
     * @param id 项目主键
     * @return 项目
     */
    @Override
    public Project selectProjectVOById(Long id) {
        Project project = projectMapper.selectProjectById(id);
        if (ObjectUtil.isEmpty(project)) {
            return project;
        }

        // 承担单位
        SysDept dept = deptService.selectDeptById(project.getDeptId());
        project.setDept(dept);
        // 所属单位
        SysDept ownerDept = deptService.selectDeptById(project.getOwnerDeptId());
        project.setOwnerDept(ownerDept);

        // 检测人员
        List<Long> inspectorIds = projectUserMapper.selectUserIdsByProjectAndRole(id, ProjectUserRoleEnum.INSPECTOR.getValue());
        List<SysUser> inspectors = inspectorIds.stream().map(inspectorId -> sysUserMapper.selectUserById(inspectorId)).collect(Collectors.toList());
        project.setInspectors(inspectors);

        // 报告编写人员
        Long authorId = projectUserMapper.selectUserIdsByProjectAndRole(id, ProjectUserRoleEnum.AUTHOR.getValue()).get(0);
        SysUser author = sysUserMapper.selectUserById(authorId);
        project.setAuthor(author);

        // 报告审核人员
        Long reviewerId = projectUserMapper.selectUserIdsByProjectAndRole(id, ProjectUserRoleEnum.REVIEWER.getValue()).get(0);
        SysUser reviewer = sysUserMapper.selectUserById(reviewerId);
        project.setReviewer(reviewer);

        // 报告批准人员
        Long approverId = projectUserMapper.selectUserIdsByProjectAndRole(id, ProjectUserRoleEnum.APPROVER.getValue()).get(0);
        SysUser approver = sysUserMapper.selectUserById(approverId);
        project.setApprover(approver);

        // 关联的建筑列表
//        private List<Building> buildings;
        // 关联的任务列表
//        private List<Task> tasks;
        // 关联的标准列表
//        private List<Standard> standards;
        // 关联的设备列表
//        private List<BiDevice> biDevices;

        return project;
    }

    /**
     * 根据项目ID查询用户列表 （这里是根据项目的承担单位id查询用户)
     *
     * @param projectId 项目ID
     * @return 用户信息集合信息
     */
    @Override
    public List<SysUser> selectUserList(Long projectId) {
        Project project = projectMapper.selectProjectById(projectId);
        // 获取承担单位ID
        if (ObjUtil.isEmpty(project)) {
            return List.of();
        }
        Long deptId = project.getDeptId();
        SysUser sysUser = new SysUser();
        sysUser.setDeptId(deptId);

        return sysUserMapper.selectUserList(sysUser);
    }

    /**
     * 查询项目人员信息
     *
     * @param projectId
     * @return
     */
    @Override
    public ProjectUserAssignment selectProjectUsers(Long projectId) {
        ProjectUserAssignment projectUserAssignment = new ProjectUserAssignment();

        if (projectId == null) {
            return projectUserAssignment;
        }
        projectUserAssignment.setProjectId(projectId);

        // 检测人员
        List<Long> inspectorIds = projectUserMapper.selectUserIdsByProjectAndRole(projectId, ProjectUserRoleEnum.INSPECTOR.getValue());
        projectUserAssignment.setInspectorIds(inspectorIds);
        // 报告编写人员
        List<Long> author = projectUserMapper.selectUserIdsByProjectAndRole(projectId, ProjectUserRoleEnum.AUTHOR.getValue());
        if (author != null && author.size() > 0) {
            projectUserAssignment.setAuthorId(author.get(0));
        }
        // 报告审核人员
        List<Long> reviewer = projectUserMapper.selectUserIdsByProjectAndRole(projectId, ProjectUserRoleEnum.REVIEWER.getValue());
        if (reviewer != null && reviewer.size() > 0) {
            projectUserAssignment.setReviewerId(reviewer.get(0));
        }
        // 报告批准人员
        List<Long> approver = projectUserMapper.selectUserIdsByProjectAndRole(projectId, ProjectUserRoleEnum.APPROVER.getValue());
        if (approver != null && approver.size() > 0) {
            projectUserAssignment.setApproverId(approver.get(0));
        }

        return projectUserAssignment;
    }

    /**
     * 保存项目人员信息
     *
     * @param assignment
     * @return
     */
    @Override
    public int saveProjectUserAssignments(ProjectUserAssignment assignment) {
        if (ObjUtil.isEmpty(assignment) || assignment.getProjectId() == null) {
            throw new ServiceException("传入的参数不能为空");
        }

        Long projectId = assignment.getProjectId();
        // 如果存在项目相关人员，则删除，再添加
        int count = projectUserMapper.countProjectUser(projectId);
        if (count > 0) {
            projectUserMapper.deleteProjectUser(projectId);
        }
        List<Long> inspectorIds = assignment.getInspectorIds();
        int save = projectUserMapper.saveProjectUser(projectId, inspectorIds, ProjectUserRoleEnum.INSPECTOR.getValue());

        Long authorId = assignment.getAuthorId();
        save += projectUserMapper.saveProjectUser(projectId, List.of(authorId), ProjectUserRoleEnum.AUTHOR.getValue());

        Long reviewerId = assignment.getReviewerId();
        save += projectUserMapper.saveProjectUser(projectId, List.of(reviewerId), ProjectUserRoleEnum.REVIEWER.getValue());

        Long approverId = assignment.getApproverId();
        save += projectUserMapper.saveProjectUser(projectId, List.of(approverId), ProjectUserRoleEnum.APPROVER.getValue());

        return save;
    }

}
