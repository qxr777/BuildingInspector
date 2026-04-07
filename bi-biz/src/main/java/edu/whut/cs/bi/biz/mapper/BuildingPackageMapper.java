package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.BuildingPackage;
import java.util.List;

/**
 * 建筑物病害离线包Mapper接口
 */
public interface BuildingPackageMapper {
    /**
     * 查询建筑物病害离线包
     * 
     * @param id 建筑物病害离线包主键
     * @return 建筑物病害离线包
     */
    public BuildingPackage selectBuildingPackageById(Long id);

    /**
     * 查询建筑物病害离线包列表
     * 
     * @param buildingPackage 建筑物病害离线包
     * @return 建筑物病害离线包集合
     */
    public List<BuildingPackage> selectBuildingPackageList(BuildingPackage buildingPackage);

    /**
     * 新增建筑物病害离线包
     * 
     * @param buildingPackage 建筑物病害离线包
     * @return 结果
     */
    public int insertBuildingPackage(BuildingPackage buildingPackage);

    /**
     * 修改建筑物病害离线包
     * 
     * @param buildingPackage 建筑物病害离线包
     * @return 结果
     */
    public int updateBuildingPackage(BuildingPackage buildingPackage);

    /**
     * 删除建筑物病害离线包
     * 
     * @param id 建筑物病害离线包主键
     * @return 结果
     */
    public int deleteBuildingPackageById(Long id);

    /**
     * 批量删除建筑物病害离线包
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBuildingPackageByIds(Long[] ids);
}
