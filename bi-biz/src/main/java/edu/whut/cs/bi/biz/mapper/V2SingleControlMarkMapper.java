package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230-2026 单项控制指标（16项）标记 Mapper（App 同步实体）
 */
public interface V2SingleControlMarkMapper {

    /**
     * 按任务查询全部有效标记（排除离线删除）
     */
    List<V2SingleControlMark> selectByTaskId(Long taskId);

    /**
     * 按任务批量查询（下发 SQLite 打包用，排除离线删除）
     */
    List<V2SingleControlMark> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    /**
     * 按 offlineUuid 查询（同步幂等去重）
     */
    V2SingleControlMark selectByOfflineUuid(String offlineUuid);

    /**
     * 新增
     */
    int insert(V2SingleControlMark row);

    /**
     * 修改
     */
    int update(V2SingleControlMark row);

    /**
     * 按主键物理删除
     */
    int deleteById(Long id);
}
