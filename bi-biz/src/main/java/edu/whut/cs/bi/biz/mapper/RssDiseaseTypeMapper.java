package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.RssDiseaseType;

/**
 * 维修加固结构-病害类型绑定（bi_rss_disease_type）Mapper 接口
 */
public interface RssDiseaseTypeMapper {

    /**
     * 查询绑定列表（空条件 = 全量）
     */
    public List<RssDiseaseType> selectRssDiseaseTypeList(RssDiseaseType rssDiseaseType);
}
