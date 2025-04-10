package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.util.ObjUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.TaskMapper;

import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
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

    /**
     * 查询任务
     *
     * @param id 任务主键
     * @return 任务
     */
    @Override
    public Task selectTaskById(Long id) {
        return taskMapper.selectTaskById(id);
    }

    /**
     * 查询任务列表
     *
     * @param task 任务
     * @return 任务
     */
    @Override
    public List<Task> selectTaskList(Task task) {
        return taskMapper.selectTaskList(task);
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
