package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Package;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/7/10 22:27
 * @Description:
 **/
public interface PackageMapper {
    /**
     * 查询数据包信息
     *
     * @param id 数据包ID
     * @return 数据包信息
     */
    public Package selectPackageById(Long id);

    /**
     * 查询用户的数据包列表
     *
     * @param userId 用户ID
     * @return 数据包集合
     */
    public List<Package> selectPackageListByUserId(Long userId);

    /**
     * 查询数据包列表
     *
     * @param pkg 数据包信息
     * @return 数据包集合
     */
    public List<Package> selectPackageList(Package pkg);

    /**
     * 新增数据包
     *
     * @param pkg 数据包信息
     * @return 结果
     */
    public int insertPackage(Package pkg);

    /**
     * 修改数据包
     *
     * @param pkg 数据包信息
     * @return 结果
     */
    public int updatePackage(Package pkg);

    /**
     * 删除数据包
     *
     * @param id 数据包ID
     * @return 结果
     */
    public int deletePackageById(Long id);

    /**
     * 批量删除数据包
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deletePackageByIds(Long[] ids);

    /**
     * 查询所有更新时间大于打包时间的数据包
     *
     * @return 数据包集合
     */
    public List<Package> selectPackagesWithUpdateTimeGreaterThanPackageTime();

    /**
     * 批量更新数据包
     *
     * @param pkgList 数据包列表
     * @return 更新的记录数
     */
    public int batchUpdatePackage(List<Package> pkgList);

    /**
     * 批量新增数据包
     *
     * @param pkgList 数据包列表
     * @return 结果
     */
    public int batchInsertPackage(List<Package> pkgList);

    /**
     * 批量更新数据包时间
     *
     * @param ids 用户列表
     * @return 结果
     */
    int batchUpdateUpdateTimeNow(@Param("list") List<Long> ids);
}
