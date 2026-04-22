package edu.whut.cs.bi.api.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.domain.BuildingPackage;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiControllerV2Test {

    @InjectMocks
    private ApiControllerV2 apiControllerV2;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private SqliteService sqliteService;

    @Test
    void testGetProjectSqliteUrl_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://localhost:9000/bucket/aa/file.db");

        doReturn(vo).when(sqliteService).getProjectSqliteUrl(1L);

        AjaxResult result = apiControllerV2.getProjectSqliteUrl(1L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("获取成功", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testGetProjectSqliteUrl_NotFound() {
        doReturn(null).when(sqliteService).getProjectSqliteUrl(2L);

        AjaxResult result = apiControllerV2.getProjectSqliteUrl(2L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("SQLite 文件未生成或不存在，请稍候再试", result.get(AjaxResult.MSG_TAG));
        assertNull(result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testGetProjectSqliteUrl_Exception() {
        doThrow(new RuntimeException("boom")).when(sqliteService).getProjectSqliteUrl(3L);

        AjaxResult result = apiControllerV2.getProjectSqliteUrl(3L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("获取 SQLite 文件失败"));
    }

    @Test
    void testGetUserSqliteUrl_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://localhost:9000/bucket/bb/user.db");

        doReturn(vo).when(sqliteService).getUserSqliteUrl(11L);

        AjaxResult result = apiControllerV2.getUserSqliteUrl(11L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("获取成功", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testGenerateUserSqlite_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://test.url");
        doReturn(vo).when(sqliteService).generateUserSqliteSync(20L);

        AjaxResult result = apiControllerV2.generateUserSqlite(20L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("已成功生成离线包并获取地址", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
        verify(sqliteService, times(1)).generateUserSqliteSync(20L);
    }

    @Test
    void testGenerateBuildingPackage_BuildingIdNull() {
        AjaxResult result = apiControllerV2.generateBuildingPackage(null);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("参数错误，buildingId不能为空", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void testGenerateBuildingPackage_Success() {
        AjaxResult expected = AjaxResult.success("ok");
        doReturn(expected).when(buildingService).generateBuildingPackage(100L);

        AjaxResult result = apiControllerV2.generateBuildingPackage(100L);

        assertSame(expected, result);
    }

    @Test
    void testGetBuildingPackage_Success() {
        BuildingPackage item = new BuildingPackage();
        item.setBuildingId(8L);
        item.setFileName("pkg.zip");

        doReturn(Collections.singletonList(item)).when(buildingService).selectBuildingPackageList(org.mockito.ArgumentMatchers.any(BuildingPackage.class));

        AjaxResult result = apiControllerV2.getBuildingPackage(8L);

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("查询成功", result.get(AjaxResult.MSG_TAG));
        assertNotNull(result.get(AjaxResult.DATA_TAG));
        BuildingPackage data = (BuildingPackage) result.get(AjaxResult.DATA_TAG);
        assertEquals("pkg.zip", data.getFileName());
    }

    @Test
    void testGetBuildingPackage_Empty() {
        doReturn(Collections.emptyList()).when(buildingService).selectBuildingPackageList(org.mockito.ArgumentMatchers.any(BuildingPackage.class));

        AjaxResult result = apiControllerV2.getBuildingPackage(9L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("该建筑物暂未生成数据包", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void testGenerateCommonSqlite_Success() {
        SqliteVo vo = new SqliteVo();
        vo.setUrl("http://localhost:9000/bucket/cc/common.db");
        vo.setTimestamp(new Date());
        vo.setSize("10MB");

        doReturn(vo).when(sqliteService).generateCommonBaseSqlite();

        AjaxResult result = apiControllerV2.generateCommonSqlite();

        assertEquals(0, result.get(AjaxResult.CODE_TAG));
        assertEquals("获取成功", result.get(AjaxResult.MSG_TAG));
        assertSame(vo, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void testGenerateCommonSqlite_Null() {
        doReturn(null).when(sqliteService).generateCommonBaseSqlite();

        AjaxResult result = apiControllerV2.generateCommonSqlite();

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("通用基础 SQLite 生成失败", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void testGenerateCommonSqlite_Exception() {
        doThrow(new RuntimeException("io error")).when(sqliteService).generateCommonBaseSqlite();

        AjaxResult result = apiControllerV2.generateCommonSqlite();

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("生成基础数据包失败"));
    }
}
