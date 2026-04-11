package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiProjectDevice;
import edu.whut.cs.bi.biz.mapper.BiProjectDeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BiProjectDeviceServiceImplTest {

    @InjectMocks
    private BiProjectDeviceServiceImpl biProjectDeviceService;

    @Mock
    private BiProjectDeviceMapper biProjectDeviceMapper;

    /**
     * 测试场景：新增项目设备关联时，正常设置创建信息并入库。
     */
    @Test
    void testInsertBiProjectDevice_Success() {
        BiProjectDevice relation = new BiProjectDevice();
        relation.setProjectId(1L);
        relation.setDeviceId(2L);

        when(biProjectDeviceMapper.insertBiProjectDevice(any(BiProjectDevice.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("relation_creator");

            biProjectDeviceService.insertBiProjectDevice(relation);
        }

        assertNotNull(relation.getCreateTime());
        verify(biProjectDeviceMapper, times(1)).insertBiProjectDevice(relation);
    }

    /**
     * 测试场景：新增项目设备关联时，Mapper异常应向上抛出。
     */
    @Test
    void testInsertBiProjectDevice_InsertFailed() {
        BiProjectDevice relation = new BiProjectDevice();

        doThrow(new RuntimeException("insert relation failed"))
                .when(biProjectDeviceMapper).insertBiProjectDevice(any(BiProjectDevice.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("relation_creator");

            assertThrows(RuntimeException.class, () -> biProjectDeviceService.insertBiProjectDevice(relation));
        }
    }

    /**
     * 测试场景：更新项目设备关联时，正常设置更新信息并更新成功。
     */
    @Test
    void testUpdateBiProjectDevice_Success() {
        BiProjectDevice relation = new BiProjectDevice();
        relation.setId(10L);

        when(biProjectDeviceMapper.updateBiProjectDevice(any(BiProjectDevice.class))).thenReturn(1);

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("relation_updater");

            biProjectDeviceService.updateBiProjectDevice(relation);
        }

        assertNotNull(relation.getUpdateTime());
        verify(biProjectDeviceMapper, times(1)).updateBiProjectDevice(relation);
    }

    /**
     * 测试场景：更新项目设备关联时，Mapper异常应向上抛出。
     */
    @Test
    void testUpdateBiProjectDevice_UpdateFailed() {
        BiProjectDevice relation = new BiProjectDevice();
        relation.setId(10L);

        doThrow(new RuntimeException("update relation failed"))
                .when(biProjectDeviceMapper).updateBiProjectDevice(any(BiProjectDevice.class));

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("relation_updater");

            assertThrows(RuntimeException.class, () -> biProjectDeviceService.updateBiProjectDevice(relation));
        }
    }

    /**
     * 测试场景：检查关联存在时，计数大于0应返回true。
     */
    @Test
    void testCheckBiProjectDeviceExists_Success() {
        when(biProjectDeviceMapper.checkBiProjectDeviceExists(1L, 2L)).thenReturn(1);

        boolean exists = biProjectDeviceService.checkBiProjectDeviceExists(1L, 2L);

        assertTrue(exists);
        verify(biProjectDeviceMapper, times(1)).checkBiProjectDeviceExists(1L, 2L);
    }

    /**
     * 测试场景：检查关联不存在时，计数为0应返回false。
     */
    @Test
    void testCheckBiProjectDeviceExists_NotExists() {
        when(biProjectDeviceMapper.checkBiProjectDeviceExists(1L, 2L)).thenReturn(0);

        boolean exists = biProjectDeviceService.checkBiProjectDeviceExists(1L, 2L);

        assertFalse(exists);
        verify(biProjectDeviceMapper, times(1)).checkBiProjectDeviceExists(1L, 2L);
    }
}
