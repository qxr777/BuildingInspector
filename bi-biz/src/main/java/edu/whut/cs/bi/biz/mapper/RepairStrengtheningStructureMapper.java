package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.RepairStrengtheningStructure;

/**
 * 维修加固结构字典（bi_repair_strengthening_structure）Mapper 接口
 */
public interface RepairStrengtheningStructureMapper {

    /**
     * 查询加固结构列表（空条件 = 全量）
     */
    public List<RepairStrengtheningStructure> selectRepairStrengtheningStructureList(
            RepairStrengtheningStructure repairStrengtheningStructure);
}
