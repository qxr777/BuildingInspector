package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.MdDiseaseType;

/**
 * 材料耐久性-病害类型绑定（bi_md_disease_type）Mapper 接口
 */
public interface MdDiseaseTypeMapper {

    /**
     * 查询绑定列表（空条件 = 全量）
     */
    public List<MdDiseaseType> selectMdDiseaseTypeList(MdDiseaseType mdDiseaseType);
}
