package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AssessManualInput;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 5230 构件级人工判定录入（bi_assess_manual_input）Mapper。
 */
public interface AssessManualInputMapper {

    /** 按离线 UUID 查询（同步幂等） */
    AssessManualInput selectByOfflineUuid(String offlineUuid);

    /** 按任务查询全部人工判定（评定计算用） */
    List<AssessManualInput> selectByTaskId(@Param("taskId") Long taskId);

    /** 按任务ID集合批量查询（用户级离线包导出用） */
    List<AssessManualInput> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    /** 按任务+构件UUID查询（直连录入幂等） */
    AssessManualInput selectByTaskAndElement(@Param("taskId") Long taskId,
                                             @Param("elementUuid") String elementUuid);

    int insertAssessManualInput(AssessManualInput row);

    int updateAssessManualInput(AssessManualInput row);

    int deleteAssessManualInputById(Long id);
}
