package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.DiseaseScale;

import java.util.List;

/**
 * 病害标度表 数据层
 * 
 * @author chenwenqi
 */
public interface DiseaseScaleMapper
{
    /**
     * 根据条件分页查询病害标度
     * 
     * @param diseaseScale 病害标度信息
     * @return 病害标度集合信息
     */
    public List<DiseaseScale> selectDiseaseScaleList(DiseaseScale diseaseScale);

    /**
     * 根据病害类型查询病害标度
     * 
     * @param typeCode 病害类型编码
     * @return 病害标度集合信息
     */
    public List<DiseaseScale> selectDiseaseScaleByTypeCode(String typeCode);

    /**
     * 根据病害标度ID查询信息
     * 
     * @param id 病害标度ID
     * @return 病害标度
     */
    public DiseaseScale selectDiseaseScaleById(Long id);

    /**
     * 查询病害标度
     * 
     * @param typeCode 病害类型编码
     * @return 病害标度
     */
    public int countDiseaseScaleByTypeCode(String typeCode);

    /**
     * 通过ID删除病害标度信息
     * 
     * @param id 病害标度ID
     * @return 结果
     */
    public int deleteDiseaseScaleById(Long id);

    /**
     * 批量删除病害标度
     * 
     * @param ids 需要删除的数据
     * @return 结果
     */
    public int deleteDiseaseScaleByIds(Long[] ids);

    /**
     * 新增病害标度信息
     * 
     * @param diseaseScale 病害标度信息
     * @return 结果
     */
    public int insertDiseaseScale(DiseaseScale diseaseScale);

    /**
     * 修改病害标度信息
     * 
     * @param diseaseScale 病害标度信息
     * @return 结果
     */
    public int updateDiseaseScale(DiseaseScale diseaseScale);
}
