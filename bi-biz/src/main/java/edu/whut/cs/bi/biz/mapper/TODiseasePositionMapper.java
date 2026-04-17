package edu.whut.cs.bi.biz.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 模板构件-病害位置关联 Mapper 接口
 *
 * @author QiXin
 * @date 2026/04/11
 */
public interface TODiseasePositionMapper {

    /**
     * 查询全量 模板构件↔病害位置 关联数据（供 SQLite 全量导出）
     */
    List<Map<String, Object>> selectAllMappings();

    /**
     * 批量插入关联数据
     */
    int batchInsert(@Param("templateObjectId") Long templateObjectId,
                    @Param("diseasePositionIds") List<Long> diseasePositionIds);

    /**
     * 删除指定模板构件的某个病害位置关联
     */
    int deleteMapping(@Param("templateObjectId") Long templateObjectId,
                      @Param("diseasePositionId") Long diseasePositionId);

    /**
     * 根据模板构件ID查询关联的病害位置ID列表
     */
    List<Long> selectByTemplateObjectId(Long templateObjectId);

    /**
     * 批量统计多个模板对象的病害位置数量
     */
    List<Map<String, Object>> countDiseasePositionsByTemplateObjectIds(@Param("templateObjectIds") List<Long> templateObjectIds);

    /**
     * 批量删除指定模板构件的病害位置关联
     */
    int batchDeleteData(@Param("templateObjectId") Long templateObjectId,
                        @Param("diseasePositionIds") List<Long> diseasePositionIds);
}
