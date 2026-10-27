package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230 单项控制指标命中标记（bi_assess_single_control_mark）Mapper。
 */
public interface AssessSingleControlMarkMapper {

    AssessSingleControlMark selectByOfflineUuid(String offlineUuid);

    List<AssessSingleControlMark> selectByTaskId(@Param("taskId") Long taskId);

    /** 按任务ID集合批量查询（用户级离线包导出用） */
    List<AssessSingleControlMark> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    /** 按任务+指标编号查询（直连录入幂等） */
    AssessSingleControlMark selectByTaskAndIndicator(@Param("taskId") Long taskId,
                                                     @Param("indicatorNo") Integer indicatorNo);

    int insertAssessSingleControlMark(AssessSingleControlMark row);

    int updateAssessSingleControlMark(AssessSingleControlMark row);

    int deleteAssessSingleControlMarkById(Long id);
}
