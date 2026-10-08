package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230-2026 构件人工评定输入 Mapper（App 同步实体）
 */
public interface V2ComponentInputMapper {

    /**
     * 按任务查询全部有效构件输入（排除离线删除）
     */
    List<V2ComponentInput> selectByTaskId(Long taskId);

    /**
     * 按任务批量查询（下发 SQLite 打包用，排除离线删除）
     */
    List<V2ComponentInput> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    /**
     * 按 offlineUuid 查询（同步幂等去重）
     */
    V2ComponentInput selectByOfflineUuid(String offlineUuid);

    /**
     * 新增
     */
    int insert(V2ComponentInput row);

    /**
     * 修改
     */
    int update(V2ComponentInput row);

    /**
     * 按主键物理删除
     */
    int deleteById(Long id);
}
