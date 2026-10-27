package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AssessElementResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230 构件级评定结果（bi_assess_element_result）Mapper。
 * 重算语义：按 task_id 先删后批量插入，保证幂等。
 */
public interface AssessElementResultMapper {

    /** 按任务删除全部构件评定结果（重算前清理） */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /** 批量插入构件评定结果 */
    int batchInsert(@Param("list") List<AssessElementResult> list);

    /** 按任务查询构件评定结果（按单元、跨、部件顺序） */
    List<AssessElementResult> selectByTaskId(@Param("taskId") Long taskId);
}
