package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.BiEvalComponentDetail;
import java.util.List;

public interface BiEvalComponentDetailMapper {
    int insertBiEvalComponentDetail(BiEvalComponentDetail biEvalComponentDetail);
    int batchInsert(List<BiEvalComponentDetail> list);
    List<BiEvalComponentDetail> selectList(BiEvalComponentDetail query);
}
