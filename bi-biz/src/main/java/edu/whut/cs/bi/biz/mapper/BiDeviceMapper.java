package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.BiDevice;

import java.util.List;

public interface BiDeviceMapper {
    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public BiDevice selectBiDeviceById(Long id);

    public List<BiDevice> selectBiDeviceList(BiDevice biDevice);

    public int insertBiDevice(BiDevice biDevice);

    public int updateBiDevice(BiDevice biDevice);

    public int deleteBiDeviceById(Long id);

    public int deleteBiDeviceByIds(String[] ids);
}
