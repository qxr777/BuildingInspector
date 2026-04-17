package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.DiseasePosition;

import java.util.List;

/**
 * 病害位置字典 Service 接口
 */
public interface IDiseasePositionService {

    /**
     * 查询病害位置列表
     */
    List<DiseasePosition> selectDiseasePositionList(DiseasePosition diseasePosition);

    /**
     * 根据ID查询病害位置
     */
    DiseasePosition selectDiseasePositionById(Long id);

    /**
     * 新增病害位置
     */
    int insertDiseasePosition(DiseasePosition diseasePosition);

    /**
     * 修改病害位置
     */
    int updateDiseasePosition(DiseasePosition diseasePosition);

    /**
     * 删除病害位置（逻辑删除）
     */
    int deleteDiseasePositionByIds(String ids);
}
