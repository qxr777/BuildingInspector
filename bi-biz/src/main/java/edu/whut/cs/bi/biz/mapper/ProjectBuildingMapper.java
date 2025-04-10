package edu.whut.cs.bi.biz.mapper;


import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目建筑Mapper接口
 * 
 * @author: chenwenqi
 * @date: 2025-04-02
 * @deprecated 此接口已废弃
 */
public interface ProjectBuildingMapper
{

    /**
     * 插入项目建筑关联
     *
     * @param projectId
     * @param buildingId
     * @return
     */
    int insertProjectBuilding(@Param("projectId") Long projectId, @Param("buildingId") Long buildingId);

    /**
     * 批量插入项目建筑关联
     *
     * @param projectId
     * @param buildingIds
     * @return
     */
    int batchInsertProjectBuilding(@Param("projectId") Long projectId, @Param("buildingIds") List<Long> buildingIds);

    /**
     * 删除项目建筑关联
     *
     * @param projectId
     * @param buildingId
     * @return
     */
    int removeProjectBuilding(@Param("projectId") Long projectId, @Param("buildingId") Long buildingId);

    /**
     * 批量删除项目建筑关联
     *
     * @param projectId
     * @param buildingIds
     * @return
     */
    int batchRemoveProjectBuilding(@Param("projectId") Long projectId, @Param("buildingIds") List<Long> buildingIds);

    /**
     * 查询项目建筑关联是否存在
     *
     * @param projectId
     * @param buildingId
     * @return
     */
    int countProjectBuilding(@Param("projectId") Long projectId, @Param("buildingId") Long buildingId);
}
