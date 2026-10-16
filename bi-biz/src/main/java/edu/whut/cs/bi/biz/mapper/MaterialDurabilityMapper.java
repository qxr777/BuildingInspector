package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.MaterialDurability;

/**
 * 材料耐久性分类字典（bi_material_durability）Mapper 接口
 */
public interface MaterialDurabilityMapper {

    /**
     * 查询材料分类列表（空条件 = 全量）
     */
    public List<MaterialDurability> selectMaterialDurabilityList(MaterialDurability materialDurability);
}
