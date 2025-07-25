package edu.whut.cs.bi.biz.service;

import java.util.List;
import edu.whut.cs.bi.biz.domain.Package;

/**
 * 用户压缩包Service接口
 * 
 * @author wanzheng
 * @date 2025-07-18
 */
public interface IPackageService 
{
    /**
     * 查询用户压缩包
     * 
     * @param id 用户压缩包主键
     * @return 用户压缩包
     */
    public Package selectPackageById(Long id);

    /**
     * 查询用户压缩包列表
     * 
     * @param package1 用户压缩包
     * @return 用户压缩包集合
     */
    public List<Package> selectPackageList(Package package1);

    /**
     * 新增用户压缩包
     * 
     * @param package1 用户压缩包
     * @return 结果
     */
    public int insertPackage(Package package1);

    /**
     * 修改用户压缩包
     * 
     * @param package1 用户压缩包
     * @return 结果
     */
    public int updatePackage(Package package1);

    /**
     * 批量删除用户压缩包
     * 
     * @param ids 需要删除的用户压缩包主键集合
     * @return 结果
     */
    public int deletePackageByIds(String ids);

    /**
     * 删除用户压缩包信息
     * 
     * @param id 用户压缩包主键
     * @return 结果
     */
    public int deletePackageById(Long id);
}
