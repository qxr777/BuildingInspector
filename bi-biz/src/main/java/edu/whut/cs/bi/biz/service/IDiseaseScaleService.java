package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.DiseaseScale;

import java.util.List;

/**
 * 标度 业务层
 *
 * @author chenwenqi
 */
public interface IDiseaseScaleService
{
    /**
     * 根据条件分页查询标度数据
     *
     * @param diseaseScale 标度数据信息
     * @return 标度数据集合信息
     */
    public List<DiseaseScale> selectDiseaseScaleList(DiseaseScale diseaseScale);

    /**
     * 根据标度数据ID查询信息
     *
     * @param scaleId 标度数据ID
     * @return 标度数据
     */
    public DiseaseScale selectDiseaseScaleById(Long scaleId);

    /**
     * 批量删除标度数据
     *
     * @param ids 需要删除的数据
     */
    public void deleteDiseaseScaleByIds(String ids);

    /**
     * 新增保存标度数据信息
     *
     * @param diseaseScale 标度数据信息
     * @return 结果
     */
    public int insertDiseaseScale(DiseaseScale diseaseScale);

    /**
     * 修改保存标度数据信息
     *
     * @param diseaseScale 标度数据信息
     * @return 结果
     */
    public int updateDiseaseScale(DiseaseScale diseaseScale);
}
