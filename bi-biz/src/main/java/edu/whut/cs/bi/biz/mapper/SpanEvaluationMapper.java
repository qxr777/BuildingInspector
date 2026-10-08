package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.SpanEvaluation;

import java.util.List;

/**
 * 桥跨技术状况评定（JTG/T 5230—2026）Mapper
 */
public interface SpanEvaluationMapper {

    /**
     * 按任务查询全部桥跨评定结果
     */
    List<SpanEvaluation> selectByTaskId(Long taskId);

    /**
     * 新增桥跨评定结果
     */
    int insert(SpanEvaluation spanEvaluation);

    /**
     * 按任务删除（全量重算）
     */
    int deleteByTaskId(Long taskId);
}
