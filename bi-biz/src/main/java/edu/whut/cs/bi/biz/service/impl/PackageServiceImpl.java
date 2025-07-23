package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.PackageMapper;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.service.IPackageService;

/**
 * 用户压缩包Service业务层处理
 * 
 * @author wanzheng
 * @date 2025-07-18
 */
@Service
public class PackageServiceImpl implements IPackageService 
{
    @Autowired
    private PackageMapper packageMapper;

    /**
     * 查询用户压缩包
     * 
     * @param id 用户压缩包主键
     * @return 用户压缩包
     */
    @Override
    public Package selectPackageById(Long id)
    {
        return packageMapper.selectPackageById(id);
    }

    /**
     * 查询用户压缩包列表
     * 
     * @param package1 用户压缩包
     * @return 用户压缩包
     */
    @Override
    public List<Package> selectPackageList(Package package1)
    {
        return packageMapper.selectPackageList(package1);
    }

    /**
     * 新增用户压缩包
     * 
     * @param package1 用户压缩包
     * @return 结果
     */
    @Override
    public int insertPackage(Package package1)
    {
        return packageMapper.insertPackage(package1);
    }

    /**
     * 修改用户压缩包
     * 
     * @param package1 用户压缩包
     * @return 结果
     */
    @Override
    public int updatePackage(Package package1)
    {
        package1.setUpdateTime(DateUtils.getNowDate());
        return packageMapper.updatePackage(package1);
    }

    /**
     * 批量删除用户压缩包
     *
     * @param ids 需要删除的用户压缩包主键
     * @return 结果
     */
    @Override
    public int deletePackageByIds(String ids)
    {
        String[] stringArray = {"1", "2", "3"};
        Long[] longArray = new Long[stringArray.length];

        for (int i = 0; i < stringArray.length; i++) {
            longArray[i] = Long.parseLong(stringArray[i]);
        }

        return packageMapper.deletePackageByIds(longArray);
    }

    /**
     * 删除用户压缩包信息
     *
     * @param id 用户压缩包主键
     * @return 结果
     */
    @Override
    public int deletePackageById(Long id)
    {
        return packageMapper.deletePackageById(id);
    }
}
