package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AssessBridgeResult;
import org.apache.ibatis.annotations.Param;

/**
 * 5230 全桥评定结果（bi_assess_bridge_result）Mapper。
 * 重算语义：按 task_id 先删后插，保证幂等（uk_assess_bridge_task）。
 */
public interface AssessBridgeResultMapper {

    /** 按任务删除全桥结果（重算前清理） */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /** 插入全桥结果 */
    int insertAssessBridgeResult(AssessBridgeResult row);

    /** 按任务查询全桥结果 */
    AssessBridgeResult selectByTaskId(@Param("taskId") Long taskId);
}
