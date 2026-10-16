package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.LoDiseaseType;

/**
 * 叶子部件-病害类型绑定（bi_lo_disease_type）Mapper 接口
 */
public interface LoDiseaseTypeMapper {

    /**
     * 查询绑定列表（空条件 = 全量）
     */
    public List<LoDiseaseType> selectLoDiseaseTypeList(LoDiseaseType loDiseaseType);
}
