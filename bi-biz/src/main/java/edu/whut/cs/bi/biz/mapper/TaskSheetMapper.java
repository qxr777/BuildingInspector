package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.TaskSheet;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务表格Mapper接口
 */
public interface TaskSheetMapper {

    /**
     * 根据任务ID查询该任务下所有已提交的表格记录
     */
    List<TaskSheet> selectListByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID和表格类型查询记录
     */
    TaskSheet selectByTaskIdAndType(@Param("taskId") Long taskId, @Param("type") String type);

    /**
     * 新增任务表格记录
     */
    int insert(TaskSheet taskSheet);

    /**
     * 更新MinIO文件ID（覆盖时使用）
     */
    int updateSheetMinioId(TaskSheet taskSheet);

    /**
     * 根据主键删除
     */
    int deleteById(Long id);
}
