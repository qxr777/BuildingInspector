package edu.whut.cs.bi.biz.service;

import com.ruoyi.common.core.domain.Ztree;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 病害类型 业务层
 * 
 * @author chenwenqi
 */
public interface IDiseaseTypeService
{
    /**
     * 根据条件分页查询病害类型
     * 
     * @param diseaseType 病害类型信息
     * @return 病害类型集合信息
     */
    public List<DiseaseType> selectDiseaseTypeList(DiseaseType diseaseType);

    /**
     * 根据所有病害类型
     * 
     * @return 病害类型集合信息
     */
    public List<DiseaseType> selectDiseaseTypeAll();

    /**
     * 根据病害类型查询病害类型数据
     * 
     * @param code 病害类型编码
     * @return 病害类型数据集合信息
     */
    public List<DiseaseScale> selectDiseaseScaleByCode(String code);

    /**
     * 根据病害类型ID查询信息
     * 
     * @param diseaseTypeId 病害类型ID
     * @return 病害类型
     */
    public DiseaseType selectDiseaseTypeById(Long diseaseTypeId);

    /**
     * 根据病害类型查询信息
     * 
     * @param code 病害类型
     * @return 病害类型
     */
    public DiseaseType selectDiseaseTypeByCode(String code);

    /**
     * 批量删除病害类型
     * 
     * @param ids 需要删除的数据
     */
    public void deleteDiseaseTypeByIds(String ids);


    /**
     * 新增保存病害类型信息
     * 
     * @param diseaseType 病害类型信息
     * @return 结果
     */
    public int insertDiseaseType(DiseaseType diseaseType);

    /**
     * 修改保存病害类型信息
     * 
     * @param diseaseType 病害类型信息
     * @return 结果
     */
    public int updateDiseaseType(DiseaseType diseaseType);

    /**
     * 校验病害类型称是否唯一
     * 
     * @param diseaseType 病害类型
     * @return 结果
     */
    public boolean checkDiseaseTypeUnique(DiseaseType diseaseType);

    /**
     * 查询病害类型树
     * 
     * @param diseaseType 病害类型
     * @return 所有病害类型
     */
    public List<Ztree> selectDiseaseTypeTree(DiseaseType diseaseType);

    /**
     * 读取json文件, 并保存到数据库中
     *
     * @param file
     * @return
     */
    Boolean readJsonFile(MultipartFile file, DiseaseType diseaseType);

    /**
     * 通过模板对象id查询病害类型
     *
     * @param biObjectId
     * @return
     */
    List<DiseaseType> selectDiseaseTypeListByTemplateObjectId(Long biObjectId);
}
