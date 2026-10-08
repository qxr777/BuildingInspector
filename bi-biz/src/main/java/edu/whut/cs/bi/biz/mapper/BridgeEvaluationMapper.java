package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.BridgeEvaluation;

/**
 * 全桥技术状况评定（JTG/T 5230—2026）Mapper
 */
public interface BridgeEvaluationMapper {

    /**
     * 按任务查询全桥评定结果（每任务唯一）
     */
    BridgeEvaluation selectByTaskId(Long taskId);

    /**
     * 新增全桥评定结果
     */
    int insert(BridgeEvaluation bridgeEvaluation);

    /**
     * 按任务删除（全量重算）
     */
    int deleteByTaskId(Long taskId);
}
