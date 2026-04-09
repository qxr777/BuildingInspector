package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiDevice;
import edu.whut.cs.bi.biz.mapper.BiDeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BiDeviceServiceImplTest {

    @InjectMocks
    private BiDeviceServiceImpl biDeviceService;

    @Mock
    private BiDeviceMapper biDeviceMapper;

    /**
     * 测试场景：新增设备时，正常设置创建信息并写入数据库。
     */
    @Test
    void testInsertBiDevice_Success() {
        BiDevice biDevice = new BiDevice();

        when(biDeviceMapper.insertBiDevice(any(BiDevice.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("device_creator");

            biDeviceService.insertBiDevice(biDevice);
        }

        assertNotNull(biDevice.getCreateTime());
        verify(biDeviceMapper, times(1)).insertBiDevice(biDevice);
    }

    /**
     * 测试场景：新增设备时，若Mapper执行异常应向上抛出。
     */
    @Test
    void testInsertBiDevice_InsertFailed() {
        BiDevice biDevice = new BiDevice();

        doThrow(new RuntimeException("insert failed")).when(biDeviceMapper).insertBiDevice(any(BiDevice.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("device_creator");

            assertThrows(RuntimeException.class, () -> biDeviceService.insertBiDevice(biDevice));
        }
    }

    /**
     * 测试场景：更新设备时，正常设置更新信息并更新成功。
     */
    @Test
    void testUpdateBiDevice_Success() {
        BiDevice biDevice = new BiDevice();
        biDevice.setId(1L);

        when(biDeviceMapper.updateBiDevice(any(BiDevice.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("device_updater");

            biDeviceService.updateBiDevice(biDevice);
        }

        assertNotNull(biDevice.getUpdateTime());
        verify(biDeviceMapper, times(1)).updateBiDevice(biDevice);
    }

    /**
     * 测试场景：更新设备时，若Mapper更新报错应抛出异常。
     */
    @Test
    void testUpdateBiDevice_UpdateFailed() {
        BiDevice biDevice = new BiDevice();
        biDevice.setId(1L);

        doThrow(new RuntimeException("update failed")).when(biDeviceMapper).updateBiDevice(any(BiDevice.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("device_updater");

            assertThrows(RuntimeException.class, () -> biDeviceService.updateBiDevice(biDevice));
        }
    }
}
