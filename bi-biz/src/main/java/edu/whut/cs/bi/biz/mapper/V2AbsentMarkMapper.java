package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230-2026 应设未设部件标记 Mapper（App 同步实体）
 */
public interface V2AbsentMarkMapper {

    /**
     * 按任务查询全部有效标记（排除离线删除）
     */
    List<V2AbsentMark> selectByTaskId(Long taskId);

    /**
     * 按任务批量查询（下发 SQLite 打包用，排除离线删除）
     */
    List<V2AbsentMark> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    /**
     * 按 offlineUuid 查询（同步幂等去重）
     */
    V2AbsentMark selectByOfflineUuid(String offlineUuid);

    /**
     * 新增
     */
    int insert(V2AbsentMark row);

    /**
     * 修改
     */
    int update(V2AbsentMark row);

    /**
     * 按主键物理删除
     */
    int deleteById(Long id);
}
