package edu.whut.cs.bi.api.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import javax.annotation.Resource;

/**
 * API接口 V2
 */
@Slf4j
@RestController
@RequestMapping("/api/v2")
@Api("API接口 V2")
public class ApiControllerV2 {

    @Resource
    private IBuildingService buildingService;

    @Autowired
    private SqliteService sqliteService;

    /**
     * 获取项目关联的离线 SQLite 数据库文件下载地址 (全量)
     */
    @GetMapping("/project/{id}/sqlite")
    @ResponseBody
    @ApiOperation("获取项目SQLite下载地址")
    public AjaxResult getProjectSqliteUrl(@PathVariable("id") Long projectId) {
        try {
            SqliteVo sqliteInfo = sqliteService.getProjectSqliteUrl(projectId);
            if (sqliteInfo == null) {
                return AjaxResult.error("SQLite 文件未生成或不存在，请稍候再试");
            }
            return AjaxResult.success("获取成功", sqliteInfo);
        } catch (Exception e) {
            log.error("获取项目 SQLite 文件失败, projectId: {}", projectId, e);
            return AjaxResult.error("获取 SQLite 文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的离线 SQLite 数据库文件下载地址 (3张核心表)
     */
    @GetMapping("/user/{id}/sqlite")
    @ResponseBody
    @ApiOperation("获取用户级SQLite下载地址")
    public AjaxResult getUserSqliteUrl(@PathVariable("id") Long userId) {
        try {
            SqliteVo sqliteInfo = sqliteService.getUserSqliteUrl(userId);
            if (sqliteInfo == null) {
                return AjaxResult.error("用户级 SQLite 文件未生成或不存在，请稍候再试");
            }
            return AjaxResult.success("获取成功", sqliteInfo);
        } catch (Exception e) {
            log.error("获取用户 SQLite 文件失败, userId: {}", userId, e);
            return AjaxResult.error("获取 SQLite 文件失败: " + e.getMessage());
        }
    }

    /**
     * 触发为用户生成 SQLite 离线包
     */
    @PostMapping("/user/{id}/sqlite")
    @ResponseBody
    @ApiOperation("手动触发生成用户级SQLite")
    public AjaxResult generateUserSqlite(@PathVariable("id") Long userId) {
        sqliteService.generateUserSqliteAsync(userId);
        return AjaxResult.success("已交由后台处理，请稍后查询下载地址");
    }


    /**
     * 生成建筑物附件离线包 (ZIP)
     */
    @PostMapping("/building/{id}/package")
    @ResponseBody
    @ApiOperation("生成建筑物附件离线包")
    public AjaxResult generateBuildingPackage(@PathVariable("id") Long buildingId) {
        if (buildingId == null) {
            return AjaxResult.error("参数错误，buildingId不能为空");
        }
        return buildingService.generateBuildingPackage(buildingId);
    }

    /**
     * 获取建筑物最新的病害离线包信息 (ZIP)
     */
    @GetMapping("/building/{id}/package")
    @ResponseBody
    @ApiOperation("获取建筑物离线包信息")
    public AjaxResult getBuildingPackage(@PathVariable("id") Long buildingId) {
        if (buildingId == null) {
            return AjaxResult.error("参数错误，buildingId不能为空");
        }
        edu.whut.cs.bi.biz.domain.BuildingPackage query = new edu.whut.cs.bi.biz.domain.BuildingPackage();
        query.setBuildingId(buildingId);
        List<edu.whut.cs.bi.biz.domain.BuildingPackage> list = buildingService.selectBuildingPackageList(query);
        if (list != null && !list.isEmpty()) {
            return AjaxResult.success("查询成功", list.get(0));
        }
        return AjaxResult.error("该建筑物暂未生成数据包");
    }
}
