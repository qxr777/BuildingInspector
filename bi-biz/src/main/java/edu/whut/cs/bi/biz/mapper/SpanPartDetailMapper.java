package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.SpanPartDetail;

import java.util.List;

/**
 * 5230-2026 桥跨×部件评定明细 Mapper
 */
public interface SpanPartDetailMapper {

    /**
     * 按任务查询全部桥跨部件明细
     */
    List<SpanPartDetail> selectByTaskId(Long taskId);

    /**
     * 批量新增
     */
    int batchInsert(List<SpanPartDetail> list);

    /**
     * 按任务删除（全量重算）
     */
    int deleteByTaskId(Long taskId);
}
