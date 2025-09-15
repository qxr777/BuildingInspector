package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Disease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Date;
import java.util.List;

/**
 * 病害Mapper接口
 *
 * @author: chenwenqi
 * @date: 2025-04-09
 */
@Mapper
public interface DiseaseMapper {
    /**
     * 查询病害
     *
     * @param id 病害ID
     * @return 病害
     */
    Disease selectDiseaseById(Long id);

    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害集合
     */
    List<Disease> selectDiseaseList(Disease disease);

    /**
     * 新增病害
     *
     * @param disease 病害
     * @return 结果
     */
    Integer insertDisease(Disease disease);


    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
    int updateDisease(Disease disease);

    /**
     * 删除病害
     *
     * @param id 病害ID
     * @return 结果
     */
    int deleteDiseaseById(Long id);


    /**
     * 批量删除病害
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteDiseaseByIds(String[] ids);

    /**
     * 根据biObjectId查询病害
     *
     * @param biObjectIds
     * @return
     */
    List<Disease> selectDiseaseListByBiObjectIds(@Param("biObjectIds") List<Long> biObjectIds,  @Param("projectId") Long projectId);

    /**
     * 批量插入病害
     *
     * @param diseaseSet
     * @return
     */
    int batchInsertDiseases(List<Disease> diseaseSet);
    /**
     * 根据localId列表查询病害列表
     *
     * @param disease 包含localIds参数的病害查询对象
     * @return 病害集合
     */
    List<Disease> selectDiseaseListByLocalIds(Disease disease);

    /**
     * 批量更新病害信息
     *
     * @param diseaseList 病害列表
     * @return 处理结果
     */
    int batchUpdateDiseases(@Param("diseaseList") List<Disease> diseaseList);

    /**
     * 批量查询构件病害数据（用于病害选择器）
     *
     * @param biObjectIds 构件ID列表
     * @param buildingId 建筑物ID
     * @param year 年份
     * @return 病害列表
     */
    List<Disease> selectDiseaseComponentData(@Param("biObjectIds") List<Long> biObjectIds,
                                             @Param("buildingId") Long buildingId,
                                             @Param("year") Integer year);


    // 修复数据
    List<Disease> selectErrorDiseases(@Param("updateTime") Date updateTime);

}
