package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230 应设未设部件标记（bi_assess_absent_mark）Mapper。
 */
public interface AssessAbsentMarkMapper {

    AssessAbsentMark selectByOfflineUuid(String offlineUuid);

    List<AssessAbsentMark> selectByTaskId(@Param("taskId") Long taskId);

    /** 按任务ID集合批量查询（用户级离线包导出用） */
    List<AssessAbsentMark> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    int insertAssessAbsentMark(AssessAbsentMark row);

    int updateAssessAbsentMark(AssessAbsentMark row);

    int deleteAssessAbsentMarkById(Long id);
}
