package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.LoDiseasePosition;

/**
 * 叶子部件-病害位置（bi_lo_disease_position）Mapper 接口
 */
public interface LoDiseasePositionMapper {

    /**
     * 查询病害位置列表（空条件 = 全量）
     */
    public List<LoDiseasePosition> selectLoDiseasePositionList(LoDiseasePosition loDiseasePosition);
}
