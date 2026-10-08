package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.ComponentEvaluation;

import java.util.List;

/**
 * 5230-2026 构件技术状况评定结果 Mapper
 */
public interface ComponentEvaluationMapper {

    /**
     * 按任务查询全部构件评定结果
     */
    List<ComponentEvaluation> selectByTaskId(Long taskId);

    /**
     * 批量新增
     */
    int batchInsert(List<ComponentEvaluation> list);

    /**
     * 按任务删除（全量重算）
     */
    int deleteByTaskId(Long taskId);
}
