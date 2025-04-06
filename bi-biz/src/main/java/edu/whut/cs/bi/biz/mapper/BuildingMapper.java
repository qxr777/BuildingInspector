package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.vo.ProjectBuildingVO;
import org.apache.ibatis.annotations.Param;

/**
 * 建筑Mapper接口
 * 
 * @author wanzheng
 * @date 2025-03-27
 */
public interface BuildingMapper 
{
    /**
     * 查询建筑
     * 
     * @param id 建筑主键
     * @return 建筑
     */
    public Building selectBuildingById(Long id);

    /**
     * 查询建筑列表
     * 
     * @param building 建筑
     * @return 建筑集合
     */
    public List<Building> selectBuildingList(Building building);

    /**
     * 查询建筑列表
     *
     * @param building 建筑
     * @return 建筑集合
     */
    public List<ProjectBuildingVO> selectProjectBuildingVOList(@Param("building") ProjectBuildingVO building, @Param("projectId") Long projectId);

    /**
     * 新增建筑
     * 
     * @param building 建筑
     * @return 结果
     */
    public int insertBuilding(Building building);

    /**
     * 修改建筑
     * 
     * @param building 建筑
     * @return 结果
     */
    public int updateBuilding(Building building);

    /**
     * 删除建筑
     * 
     * @param id 建筑主键
     * @return 结果
     */
    public int deleteBuildingById(Long id);

    /**
     * 批量删除建筑
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBuildingByIds(String[] ids);
}
