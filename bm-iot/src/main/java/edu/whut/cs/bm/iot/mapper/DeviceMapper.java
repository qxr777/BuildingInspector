package edu.whut.cs.bm.iot.mapper;

import java.util.List;
import edu.whut.cs.bm.iot.domain.Device;

/**
 * 物联网设备Mapper接口
 * 
 * @author qixin
 * @date 2021-08-04
 */
public interface DeviceMapper 
{
    /**
     * 查询物联网设备
     * 
     * @param id 物联网设备ID
     * @return 物联网设备
     */
    public Device selectDeviceById(Long id);

    /**
     * 查询物联网设备列表
     * 
     * @param device 物联网设备
     * @return 物联网设备集合
     */
    public List<Device> selectDeviceList(Device device);

    /**
     * 新增物联网设备
     * 
     * @param device 物联网设备
     * @return 结果
     */
    public int insertDevice(Device device);

    /**
     * 修改物联网设备
     * 
     * @param device 物联网设备
     * @return 结果
     */
    public int updateDevice(Device device);

    /**
     * 删除物联网设备
     * 
     * @param id 物联网设备ID
     * @return 结果
     */
    public int deleteDeviceById(Long id);

    /**
     * 批量删除物联网设备
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteDeviceByIds(String[] ids);
}
