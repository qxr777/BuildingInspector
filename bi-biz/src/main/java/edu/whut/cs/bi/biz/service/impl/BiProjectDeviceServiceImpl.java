package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiProjectDevice;
import edu.whut.cs.bi.biz.mapper.BiProjectDeviceMapper;
import edu.whut.cs.bi.biz.service.BiProjectDeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 项目设备关联Service业务层处理
 * 
 * @author QiXin
 * @date 2025-01-01
 */
@Transactional
@Service
public class BiProjectDeviceServiceImpl implements BiProjectDeviceService 
{
    @Resource
    private BiProjectDeviceMapper biProjectDeviceMapper;

    /**
     * 查询项目设备关联
     * 
     * @param id 项目设备关联主键
     * @return 项目设备关联
     */
    @Override
    public BiProjectDevice selectBiProjectDeviceById(Long id)
    {
        return biProjectDeviceMapper.selectBiProjectDeviceById(id);
    }

    /**
     * 查询项目设备关联列表
     * 
     * @param biProjectDevice 项目设备关联
     * @return 项目设备关联
     */
    @Override
    public List<BiProjectDevice> selectBiProjectDeviceList(BiProjectDevice biProjectDevice)
    {
        return biProjectDeviceMapper.selectBiProjectDeviceList(biProjectDevice);
    }

    /**
     * 新增项目设备关联
     * 
     * @param biProjectDevice 项目设备关联
     * @return 结果
     */
    @Override
    public int insertBiProjectDevice(BiProjectDevice biProjectDevice)
    {
        biProjectDevice.setCreateTime(DateUtils.getNowDate());
        biProjectDevice.setCreateBy(ShiroUtils.getLoginName());
        return biProjectDeviceMapper.insertBiProjectDevice(biProjectDevice);
    }

    /**
     * 修改项目设备关联
     * 
     * @param biProjectDevice 项目设备关联
     * @return 结果
     */
    @Override
    public int updateBiProjectDevice(BiProjectDevice biProjectDevice)
    {
        biProjectDevice.setUpdateTime(DateUtils.getNowDate());
        biProjectDevice.setUpdateBy(ShiroUtils.getLoginName());
        return biProjectDeviceMapper.updateBiProjectDevice(biProjectDevice);
    }

    /**
     * 批量删除项目设备关联
     * 
     * @param ids 需要删除的项目设备关联主键
     * @return 结果
     */
    @Override
    public int deleteBiProjectDeviceByIds(String[] ids)
    {
        return biProjectDeviceMapper.deleteBiProjectDeviceByIds(ids);
    }

    /**
     * 删除项目设备关联信息
     * 
     * @param id 项目设备关联主键
     * @return 结果
     */
    @Override
    public int deleteBiProjectDeviceById(Long id)
    {
        return biProjectDeviceMapper.deleteBiProjectDeviceById(id);
    }

    /**
     * 根据项目ID和设备ID删除关联
     * 
     * @param projectId 项目ID
     * @param deviceId 设备ID
     * @return 结果
     */
    @Override
    public int deleteBiProjectDeviceByProjectAndDevice(Long projectId, Long deviceId)
    {
        return biProjectDeviceMapper.deleteBiProjectDeviceByProjectAndDevice(projectId, deviceId);
    }

    /**
     * 检查项目设备关联是否存在
     * 
     * @param projectId 项目ID
     * @param deviceId 设备ID
     * @return 结果
     */
    @Override
    public boolean checkBiProjectDeviceExists(Long projectId, Long deviceId)
    {
        int count = biProjectDeviceMapper.checkBiProjectDeviceExists(projectId, deviceId);
        return count > 0;
    }
} 