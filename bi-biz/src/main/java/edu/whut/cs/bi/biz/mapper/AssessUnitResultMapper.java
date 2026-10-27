package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AssessUnitResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230 评定单元结果（bi_assess_unit_result）Mapper。
 * 重算语义：按 task_id 先删后插，保证幂等。
 */
public interface AssessUnitResultMapper {

    /** 按任务删除全部单元结果（重算前清理） */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /** 插入单元结果 */
    int insertAssessUnitResult(AssessUnitResult row);

    /** 按任务查询单元结果 */
    List<AssessUnitResult> selectByTaskId(@Param("taskId") Long taskId);
}
