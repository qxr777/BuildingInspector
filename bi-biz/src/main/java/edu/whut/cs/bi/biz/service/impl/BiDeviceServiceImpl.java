package edu.whut.cs.bi.biz.service.impl;


import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiDevice;
import edu.whut.cs.bi.biz.mapper.BiDeviceMapper;
import edu.whut.cs.bi.biz.service.BiDeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Transactional
@Service
public class BiDeviceServiceImpl implements BiDeviceService {
    @Resource
    private BiDeviceMapper biDeviceMapper;

    @Override
    public BiDevice selectBiDeviceById(Long id) {
        return biDeviceMapper.selectBiDeviceById(id);
    }

    @Override
    public List<BiDevice> selectBiDeviceList(BiDevice biDevice) {
        return biDeviceMapper.selectBiDeviceList(biDevice);
    }

    @Override
    public int insertBiDevice(BiDevice biDevice) {
        biDevice.setCreateTime(DateUtils.getNowDate());
        biDevice.setCreateBy(ShiroUtils.getLoginName());
        return biDeviceMapper.insertBiDevice(biDevice);
    }

    @Override
    public int updateBiDevice(BiDevice biDevice) {
        biDevice.setUpdateTime(DateUtils.getNowDate());
        biDevice.setUpdateBy(ShiroUtils.getLoginName());
        return biDeviceMapper.updateBiDevice(biDevice);
    }

    @Override
    public int deleteBiDeviceById(Long id) {
        return biDeviceMapper.deleteBiDeviceById(id);
    }

    @Override
    public int deleteBiDeviceByIds(String[] ids) {
        return biDeviceMapper.deleteBiDeviceByIds(ids);
    }
}
