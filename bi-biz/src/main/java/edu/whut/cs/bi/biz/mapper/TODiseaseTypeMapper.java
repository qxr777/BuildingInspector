package edu.whut.cs.bi.biz.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 模板对象和病害类型Mapper接口
 *
 * @author: chenwenqi
 */
public interface TODiseaseTypeMapper
{
    /**
     * 批量插入模板对象和病害类型关联数据
     *
     * @param componentIds
     * @param id
     */
    void insertData(@Param("componentIds") List<Long> componentIds, @Param("id") Long id);

    /**
     * 查询模板对象和病害类型关联数据
     *
     * @param biObjectId
     * @return
     */
    List<Long> selectByTemplateObjectId(Long biObjectId);

    /**
     * 删除模板对象和病害类型关联数据
     *
     * @param templateObjectId
     * @param diseaseTypeId
     * @return
     */
    int deleteData(@Param("templateObjectId") Long templateObjectId, @Param("diseaseTypeId") Long diseaseTypeId);

    /**
     * 批量删除模板对象和病害类型关联数据
     *
     * @param templateObjectId
     * @param diseaseTypeIds
     * @return
     */
    int batchDeleteData(@Param("templateObjectId") Long templateObjectId, @Param("diseaseTypeIds") List<Long> diseaseTypeIds);

    /**
     * 批量查询多个模板对象的病害类型ID
     *
     * @param templateObjectIds 模板对象ID列表
     * @return 模板对象ID到病害类型ID列表的映射
     */
    Map<Long, List<Long>> batchSelectByTemplateObjectIds(@Param("templateObjectIds") List<Long> templateObjectIds);

    /**
     * 批量查询多个模板对象的病害类型关联数据
     *
     * @param templateObjectIds 模板对象ID列表
     * @return 关联数据列表，每项包含template_object_id和disease_type_id
     */
    List<Map<String, Object>> selectTemplateObjectDiseaseTypeMappings(@Param("templateObjectIds") List<Long> templateObjectIds);

    /**
     * 批量查询多个模板对象的病害类型数量
     *
     * @param templateObjectIds 模板对象ID列表
     * @return 病害类型数量列表，每个元素包含key(template_object_id)和value(count)
     */
    List<Map<String, Object>> countDiseaseTypesByTemplateObjectIds(@Param("templateObjectIds") List<Long> templateObjectIds);
}
