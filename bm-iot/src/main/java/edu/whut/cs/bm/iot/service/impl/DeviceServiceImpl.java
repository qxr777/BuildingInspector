package edu.whut.cs.bm.iot.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bm.common.base.ResultVo;
import edu.whut.cs.bm.common.dto.DeviceDto;
import edu.whut.cs.bm.common.manager.IHubManager;
import edu.whut.cs.bm.iot.domain.Device;
import edu.whut.cs.bm.iot.mapper.DeviceMapper;
import edu.whut.cs.bm.iot.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 物联网设备Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-04
 */
@Service
public class DeviceServiceImpl implements IDeviceService 
{
    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private IHubManager iHubManager;

    /**
     * 查询物联网设备
     * 
     * @param id 物联网设备ID
     * @return 物联网设备
     */
    @Override
    public Device selectDeviceById(Long id)
    {
        return deviceMapper.selectDeviceById(id);
    }

    /**
     * 查询物联网设备列表
     * 
     * @param device 物联网设备
     * @return 物联网设备
     */
    @Override
    public List<Device> selectDeviceList(Device device)
    {
        return deviceMapper.selectDeviceList(device);
    }

    /**
     * 新增物联网设备
     * 
     * @param device 物联网设备
     * @return 结果
     */
    @Override
    public int insertDevice(Device device)
    {
        DeviceDto deviceDto = iHubManager.register(device.getProductName());
        device.setName(deviceDto.getDeviceName());
        device.setProductName(deviceDto.getProductName());
        device.setSecret(deviceDto.getSecret());
        device.setCreateTime(DateUtils.getNowDate());
        return deviceMapper.insertDevice(device);
    }

    /**
     * 修改物联网设备
     * 
     * @param device 物联网设备
     * @return 结果
     */
    @Override
    public int updateDevice(Device device)
    {
        device.setUpdateTime(DateUtils.getNowDate());
        return deviceMapper.updateDevice(device);
    }

    /**
     * 删除物联网设备对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteDeviceByIds(String ids)
    {
        return deviceMapper.deleteDeviceByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除物联网设备信息
     * 
     * @param id 物联网设备ID
     * @return 结果
     */
    @Override
    public int deleteDeviceById(Long id)
    {
        return deviceMapper.deleteDeviceById(id);
    }

    /**
     * 发送指令至物联网设备
     *
     * @param id
     * @param command
     * @return
     */
    @Override
    public int commandDevice(Long id, String command) {
        Device device = deviceMapper.selectDeviceById(id);
        String productName = device.getProductName();
        String deviceName = device.getName();
        ResultVo resultVo = iHubManager.command(productName, deviceName, command);
        return resultVo.getCode();
    }
}
