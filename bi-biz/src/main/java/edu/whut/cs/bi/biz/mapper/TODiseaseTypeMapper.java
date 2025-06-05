package edu.whut.cs.bi.biz.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}
