package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.util.ObjUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.mapper.SysUserMapper;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;

import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.ITaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * 任务Service业务层处理
 *
 * @author chenwenqi
 * @date 2025-04-07
 */
@Service
public class TaskServiceImpl implements ITaskService {
    @Resource
    private TaskMapper taskMapper;

    @Resource
    private BuildingMapper buildingMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    /**
     * 查询任务
     *
     * @param id 任务主键
     * @return 任务
     */
    @Override
    public Task selectTaskById(Long id) {
        Task task = taskMapper.selectTaskById(id);
        if (ObjUtil.isNotEmpty(task)) {
            task.setBuilding(buildingMapper.selectBuildingById(task.getBuildingId()));
        }
        return task;
    }

    /**
     * 查询任务列表
     *
     * @param task 任务
     * @return 任务
     */
    @Override
    public List<Task> selectTaskList(Task task) {
        String select =  task.getSelect();

        Long currentUserId = ShiroUtils.getUserId();
        List<String> roles = sysUserMapper.selectUserRoleByUserId(currentUserId);
        SysUser sysUser = sysUserMapper.selectUserById(currentUserId);

        // 检查用户是否有admin角色
        boolean isAdmin = roles.stream().anyMatch(role -> "admin".equals(role));

        PageUtils.startPage();
        List<Task> tasks = null;
        if (isAdmin || select.equals("platform")) {
            // 超级管理员, 所有数据都能看到
            tasks = taskMapper.selectTaskList(task, null);
        } else {
            // 部门管理员
            if (select.equals("department")) {
                // 当前登录用户所属Department与bi_project表中ower_dept_id 或 dept_id一致的所有业务实体
                task.setSelectDeptId(sysUser.getDeptId());
                tasks = taskMapper.selectTaskList(task, null);
            } else {
                // 当前登录用户关联的业务实体
                tasks = taskMapper.selectTaskList(task, currentUserId);
            }
        }

        return tasks;
    }

    /**
     * 查询任务列表
     *
     * @param task 任务
     * @return 任务
     */
    @Override
    public List<Task> selectTaskList(Task task, String select) {
        // 权限区分
        task.setSelect(select);

        return selectTaskList(task);
    }
    /**
     * 新增任务
     *
     * @param task 任务
     * @return 结果
     */
    @Override
    @Transactional
    public int insertTask(Task task) {
        task.setCreateTime(DateUtils.getNowDate());
        return taskMapper.insertTask(task);
    }

    /**
     * 修改任务
     *
     * @param task 任务
     * @return 结果
     */
    @Override
    public int updateTask(Task task) {
        task.setUpdateTime(DateUtils.getNowDate());
        return taskMapper.updateTask(task);
    }

    /**
     * 批量删除任务
     *
     * @param ids 需要删除的任务主键
     * @return 结果
     */
    @Override
    public int deleteTaskByIds(String ids) {
        return taskMapper.deleteTaskByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除任务信息
     *
     * @param id 任务主键
     * @return 结果
     */
    @Override
    public int deleteTaskById(Long id) {
        return taskMapper.deleteTaskById(id);
    }

    /**
     * 批量保存任务
     *
     * @param projectId
     * @param buildingIds
     * @return
     */
    @Override
    public int batchInsertTasks(Long projectId, List<Long> buildingIds) {
        if (ObjUtil.isEmpty(projectId) || ObjUtil.isEmpty(buildingIds)) {
            throw new ServiceException("传入的参数不能为空");
        }

        return taskMapper.batchInsertTask(projectId, buildingIds, ShiroUtils.getLoginName());
    }

    /**
     * 删除任务
     *
     * @param projectId
     * @param buildingId
     * @return
     */
    @Override
    public int removeTask(Long projectId, Long buildingId) {
        if (ObjUtil.isEmpty(projectId) || ObjUtil.isEmpty(buildingId)) {
            throw new ServiceException("传入的参数不能为空");
        }

        return taskMapper.deleteTaskByProjectIdAndBuildingId(projectId, buildingId);
    }

    /**
     * 批量删除项目关联建筑信息
     *
     * @param projectId
     * @param buildingIds
     * @return
     */
    @Override
    public int batchRemoveTasks(Long projectId, List<Long> buildingIds) {
        if (ObjUtil.isEmpty(projectId) || ObjUtil.isEmpty(buildingIds)) {
            throw new ServiceException("传入的参数不能为空");
        }

        return taskMapper.batchDeleteTaskByProjectIdAndBuildingIds(projectId, buildingIds);
    }

}
