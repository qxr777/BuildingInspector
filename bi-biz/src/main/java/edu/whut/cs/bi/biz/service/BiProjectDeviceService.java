package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiProjectDevice;

import java.util.List;

/**
 * 项目设备关联Service接口
 * 
 * @author QiXin
 * @date 2025-01-01
 */
public interface BiProjectDeviceService 
{
    /**
     * 查询项目设备关联
     * 
     * @param id 项目设备关联主键
     * @return 项目设备关联
     */
    public BiProjectDevice selectBiProjectDeviceById(Long id);

    /**
     * 查询项目设备关联列表
     * 
     * @param biProjectDevice 项目设备关联
     * @return 项目设备关联集合
     */
    public List<BiProjectDevice> selectBiProjectDeviceList(BiProjectDevice biProjectDevice);

    /**
     * 新增项目设备关联
     * 
     * @param biProjectDevice 项目设备关联
     * @return 结果
     */
    public int insertBiProjectDevice(BiProjectDevice biProjectDevice);

    /**
     * 修改项目设备关联
     * 
     * @param biProjectDevice 项目设备关联
     * @return 结果
     */
    public int updateBiProjectDevice(BiProjectDevice biProjectDevice);

    /**
     * 批量删除项目设备关联
     * 
     * @param ids 需要删除的项目设备关联主键集合
     * @return 结果
     */
    public int deleteBiProjectDeviceByIds(String[] ids);

    /**
     * 删除项目设备关联信息
     * 
     * @param id 项目设备关联主键
     * @return 结果
     */
    public int deleteBiProjectDeviceById(Long id);

    /**
     * 根据项目ID和设备ID删除关联
     * 
     * @param projectId 项目ID
     * @param deviceId 设备ID
     * @return 结果
     */
    public int deleteBiProjectDeviceByProjectAndDevice(Long projectId, Long deviceId);

    /**
     * 检查项目设备关联是否存在
     * 
     * @param projectId 项目ID
     * @param deviceId 设备ID
     * @return 结果
     */
    public boolean checkBiProjectDeviceExists(Long projectId, Long deviceId);
} 