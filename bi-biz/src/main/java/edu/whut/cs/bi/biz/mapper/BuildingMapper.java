package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.Building;

/**
 * 建筑demoMapper接口
 * 
 * @author wanzheng
 * @date 2025-03-27
 */
public interface BuildingMapper 
{
    /**
     * 查询建筑demo
     * 
     * @param id 建筑demo主键
     * @return 建筑demo
     */
    public Building selectBuildingById(Long id);

    /**
     * 查询建筑demo列表
     * 
     * @param building 建筑demo
     * @return 建筑demo集合
     */
    public List<Building> selectBuildingList(Building building);

    /**
     * 新增建筑demo
     * 
     * @param building 建筑demo
     * @return 结果
     */
    public int insertBuilding(Building building);

    /**
     * 修改建筑demo
     * 
     * @param building 建筑demo
     * @return 结果
     */
    public int updateBuilding(Building building);

    /**
     * 删除建筑demo
     * 
     * @param id 建筑demo主键
     * @return 结果
     */
    public int deleteBuildingById(Long id);

    /**
     * 批量删除建筑demo
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBuildingByIds(String[] ids);
}
