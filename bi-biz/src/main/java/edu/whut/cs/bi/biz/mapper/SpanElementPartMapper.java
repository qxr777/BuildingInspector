package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.SpanElementPart;
import org.apache.ibatis.annotations.Param;

/**
 * 构件-桥跨-部件映射（bi_span_element_part）Mapper 接口
 */
public interface SpanElementPartMapper {

    /**
     * 查询映射列表（空条件 = 全量）
     */
    public List<SpanElementPart> selectSpanElementPartList(SpanElementPart spanElementPart);

    /**
     * 按桥跨节点 ID 集合批量查询锚定关系（不按 offline_deleted 过滤，云端表本身无此数据）
     */
    public List<SpanElementPart> selectBySpanIds(@Param("spanIds") List<Long> spanIds);
}
