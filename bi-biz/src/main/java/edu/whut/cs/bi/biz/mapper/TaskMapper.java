package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Task;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务Mapper接口
 *
 * @author chenwenqi
 * @date 2025-04-06
 */
public interface TaskMapper
{
    /**
     * 查询任务
     *
     * @param id 任务主键
     * @return 任务
     */
    public Task selectTaskById(Long id);

    /**
     * 查询任务列表
     *
     * @param task 任务
     * @return 任务集合
     */
    public List<Task> selectTaskList(@Param("task") Task task, @Param("currentUserId") Long currentUserId);

    /**
     * 新增任务
     *
     * @param task 任务
     * @return 结果
     */
    public int insertTask(Task task);

    /**
     * 修改任务
     *
     * @param task 任务
     * @return 结果
     */
    public int updateTask(Task task);

    /**
     * 删除任务
     *
     * @param id 任务主键
     * @return 结果
     */
    public int deleteTaskById(Long id);

    /**
     * 批量删除任务
     */
    public int deleteTaskByIds(String[] ids);

    /**
     * 批量新增任务
     */
    int batchInsertTask(@Param("projectId") Long projectId, @Param("buildingIds") List<Long> buildingIds, @Param("loginName") String loginName);

    /**
     * 通过项目id和桥梁id删除任务
     */
    int deleteTaskByProjectIdAndBuildingId(@Param("projectId") Long projectId, @Param("buildingId") Long buildingId);

    /**
     * 批量删除任务（通过项目id和桥梁ids）
     */
    int batchDeleteTaskByProjectIdAndBuildingIds(@Param("projectId") Long projectId, @Param("buildingIds") List<Long> buildingIds);

    /**
     * 通过项目id查询任务列表
     */
    List<Task> selectTaskListByProjectId(Long id);

    /**
     * 更新任务的更新时间
     */
    void updateTaskTime(Long buildingId);

    /**
     * 通过ids查询任务列表
     */
    List<Task> selectTaskByIds(@Param("ids") String ids);

    /**
     * 通过ids批量查询任务列表（包含Building信息）
     */
    List<Task> selectTaskListByIds(@Param("ids") List<Long> ids);

    /**
     * 通过桥梁id删除任务
     */
    int deleteTaskByBuildingId(@Param("buildingId") Long buildingId);

    /**
     * 根据项目id删除任务
     */
    int deleteTaskByProjectId(@Param("projectId") Long projectId);

    /**
     * 通过项目id查询完整任务列表
     */
    List<Task> selectFullTaskListByProjectId(Long id);
}
