package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.SpanEvaluation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 桥跨技术状况评定结果（bi_span_evaluation，JTG/T 5230—2026）Mapper。
 * 5230 引擎按 task_id 先删后插；uk_task_span 保证单任务单跨唯一。
 */
public interface SpanEvaluationMapper {

    /** 按任务删除全部桥跨结果（重算前清理） */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /** 插入桥跨评定结果 */
    int insertSpanEvaluation(SpanEvaluation row);

    /** 按任务查询桥跨结果（按单元、跨顺序） */
    List<SpanEvaluation> selectByTaskId(@Param("taskId") Long taskId);
}
