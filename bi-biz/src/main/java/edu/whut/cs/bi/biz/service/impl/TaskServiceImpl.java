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
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;

import edu.whut.cs.bi.biz.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    @Resource
    private IBiEvaluationService evaluationService;

    @Resource
    private PropertyMapper propertyMapper;

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
     * 查询任务列表
     *
     * @param task 任务
     * @return 任务
     */
    @Override
    public List<Task> selectTaskVOList(Task task) {
        // 1. 同步查询基础任务列表
        List<Task> tasks = taskMapper.selectTaskList(task, null);

        if (ObjUtil.isEmpty(tasks)) {
            return tasks;
        }

        // 2. 准备异步任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 3. 为每个任务创建异步处理
        for (Task t : tasks) {
            CompletableFuture<Void> future = CompletableFuture
                    // 异步获取评价结果
                    .supplyAsync(() -> {
                        try {
                            return Optional.ofNullable(evaluationService.selectBiEvaluationByTaskId(t.getId()));
                        } catch (Exception e) {
                            return Optional.<BiEvaluation>empty();
                        }
                    })
                    .thenCombineAsync(
                            // 异步获取属性数据
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    if (t.getBuilding() != null && t.getBuilding().getRootPropertyId() != null) {
                                        return Optional.ofNullable(
                                                propertyMapper.selectAllChildrenById(t.getBuilding().getRootPropertyId())
                                        );
                                    }
                                    return Optional.<List<Property>>empty();
                                } catch (Exception e) {
                                    return Optional.<List<Property>>empty();
                                }
                            }),
                            // 合并两个异步结果并设置到Task对象
                            (biEvaluationOpt, propertiesOpt) -> {
                                biEvaluationOpt.ifPresent(biEvaluation ->
                                        t.setEvaluationResult(biEvaluation.getLevel())
                                );

                                propertiesOpt.ifPresent(properties -> {
                                    Building building = t.getBuilding();
                                    building.setBuildingCode(findPropertyValue(properties, "桥梁编号"));
                                    building.setRouteCode(findPropertyValue(properties, "路线编号"));
                                    building.setRouteName(findPropertyValue(properties, "路线名称"));
                                    building.setBridgePileNumber(findPropertyValue(properties, "桥位桩号"));
                                    building.setBridgeLength(findPropertyValue(properties, "桥梁全长(m)"));
                                    t.setBuilding(building);
                                });
                                return null;
                            }
                    );

            futures.add(future);
        }

        // 4. 等待所有异步任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            throw new RuntimeException("异步处理任务数据时出错", e);
        }

        return tasks;
    }

    /**
     * 从属性树中查找特定属性值
     */
    private String findPropertyValue(List<Property> propertyTree, String propertyName) {
        return propertyTree.stream()
                .filter(p -> propertyName.equals(p.getName()))
                .findFirst()
                .map(Property::getValue)
                .orElse(null);
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
