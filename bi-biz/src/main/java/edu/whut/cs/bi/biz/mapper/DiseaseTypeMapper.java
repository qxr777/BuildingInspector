package edu.whut.cs.bi.biz.mapper;


import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.vo.TemplateDiseaseTypeVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 病害类型表 数据层
 * 
 * @author chenwenqi
 */
public interface DiseaseTypeMapper
{
    /**
     * 根据条件分页查询病害类型
     * 
     * @param diseaseType 病害类型信息
     * @return 病害类型集合信息
     */
    public List<DiseaseType> selectDiseaseTypeList(DiseaseType diseaseType);

    /**
     * 查询所有病害类型
     * 
     * @return 病害类型集合信息
     */
    public List<DiseaseType> selectDiseaseTypeAll();

    /**
     * 根据病害类型ID查询信息
     * 
     * @param id 病害类型ID
     * @return 病害类型
     */
    public DiseaseType selectDiseaseTypeById(Long id);

    /**
     * 根据病害类型查询信息
     * 
     * @param code 病害类型编码
     * @return 病害类型
     */
    public DiseaseType selectDiseaseTypeByCode(String code);

    /**
     * 通过病害ID删除病害信息
     * 
     * @param id 病害ID
     * @return 结果
     */
    public int deleteDiseaseTypeById(Long id);

    /**
     * 批量删除病害类型
     * 
     * @param ids 需要删除的数据
     * @return 结果
     */
    public int deleteDiseaseTypeByIds(Long[] ids);

    /**
     * 查询病害类型
     *
     * @param ids 需要删除的数据
     * @return 结果
     */
    public List<DiseaseType> selectDiseaseTypeListByIds(@Param("ids") List<Long> ids);

    /**
     * 新增病害类型信息
     * 
     * @param diseaseType 病害类型信息
     * @return 结果
     */
    public int insertDiseaseType(DiseaseType diseaseType);

    /**
     * 修改病害类型信息
     * 
     * @param diseaseType 病害类型信息
     * @return 结果
     */
    public int updateDiseaseType(DiseaseType diseaseType);

    /**
     * 校验病害类型称是否唯一
     * 
     * @param code 病害类型编码
     * @return 结果
     */
    public DiseaseType checkDiseaseTypeUnique(String code);

    /**
     * 更新最大病害最大标度
     *
     * @param id
     * @param scale
     * @return
     */
    int updateMaxScale(@Param("id") Long id, @Param("scale") Integer scale);

    /**
     * 查询病害类型列表，包含是否已选信息
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseType 病害类型信息
     * @return 病害类型集合
     */
    public List<TemplateDiseaseTypeVO> selectTemplateDiseaseTypeList(@Param("templateObjectId") Long templateObjectId, @Param("diseaseType") DiseaseType diseaseType);
}
