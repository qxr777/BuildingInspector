package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiDevice;

import java.util.List;

public interface BiDeviceService {
    public BiDevice selectBiDeviceById(Long id);

    public List<BiDevice> selectBiDeviceList(BiDevice biDevice);

    public int insertBiDevice(BiDevice biDevice);

    public int updateBiDevice(BiDevice biDevice);

    public int deleteBiDeviceById(Long id);

    public int deleteBiDeviceByIds(String[] ids);
}
