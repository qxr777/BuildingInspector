package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.SpanComponentPart;
import org.apache.ibatis.annotations.Param;

/**
 * 构件-桥跨-部件映射 Mapper 接口
 */
public interface SpanComponentPartMapper {

    /**
     * 查询映射
     */
    public SpanComponentPart selectSpanComponentPartById(Long id);

    /**
     * 查询映射列表
     */
    public List<SpanComponentPart> selectSpanComponentPartList(SpanComponentPart spanComponentPart);

    /**
     * 按 offlineUuid 查询（幂等去重用）
     */
    public SpanComponentPart selectByOfflineUuid(String offlineUuid);

    /**
     * 新增映射
     */
    public int insertSpanComponentPart(SpanComponentPart spanComponentPart);

    /**
     * 修改映射
     */
    public int updateSpanComponentPart(SpanComponentPart spanComponentPart);

    /**
     * 删除映射
     */
    public int deleteSpanComponentPartById(Long id);

    /**
     * 批量删除映射
     */
    public int deleteSpanComponentPartByIds(String[] ids);

    /**
     * 按桥跨节点ID批量查询有效锚定记录（新标评定用，排除离线删除）
     */
    public List<SpanComponentPart> selectBySpanIds(@Param("spanIds") List<Long> spanIds);
}
