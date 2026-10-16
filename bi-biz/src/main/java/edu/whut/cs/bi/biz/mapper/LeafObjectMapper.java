package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.LeafObject;

/**
 * 叶子部件字典（bi_leaf_object）Mapper 接口
 */
public interface LeafObjectMapper {

    /**
     * 查询叶子部件字典列表（空条件 = 全量）
     */
    public List<LeafObject> selectLeafObjectList(LeafObject leafObject);
}
