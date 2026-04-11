package edu.whut.cs.bi.api.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.impl.SqliteService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.springframework.web.multipart.MultipartFile;
import edu.whut.cs.bi.biz.domain.FileMap;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import java.util.Map;

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

    @Autowired
    private ISyncUploadService syncUploadService;

    @Resource
    private IFileMapService fileMapService;

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
        SqliteVo sqliteInfo = sqliteService.generateUserSqliteSync(userId);
        if (sqliteInfo != null) {
            return AjaxResult.success("已成功生成离线包并获取地址", sqliteInfo);
        }
        return AjaxResult.error("离线包生成失败");
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

    /**
     * 生成通用基础 SQLite 离线包 (包含模板、病害类型、标度等)
     */
    @PostMapping("/common/sqlite")
    @ResponseBody
    @ApiOperation("获取/生成通用基础SQLite")
    public AjaxResult generateCommonSqlite() {
        try {
            SqliteVo sqliteInfo = sqliteService.generateCommonBaseSqlite();
            if (sqliteInfo == null) {
                return AjaxResult.error("通用基础 SQLite 生成失败");
            }
            return AjaxResult.success("获取成功", sqliteInfo);
        } catch (Exception e) {
            log.error("获取通用基础 SQLite 文件失败", e);
            return AjaxResult.error("生成基础数据包失败：" + e.getMessage());
        }
    }

    /**
     * 上传同步离线端采集的数据 JSON 包
     */
    @PostMapping("/sync/upload")
    @ResponseBody
    @ApiOperation("离线数据同步上传")
    public AjaxResult syncUpload(@RequestBody Map<String, Object> dataMap) {
        try {
            edu.whut.cs.bi.biz.domain.vo.SyncResultVo result = syncUploadService.syncUpload(dataMap);
            
            // 闭环覆盖方案：同步成功后同步生成用户级 SQLite 并直接返回给 App 端
            try {
                Long userId = com.ruoyi.common.utils.ShiroUtils.getUserId();
                if (userId != null) {
                    log.info("同步上传成功，即刻生成用户 {} 的 SQLite 离线包", userId);
                    SqliteVo sqliteInfo = sqliteService.generateUserSqliteSync(userId);
                    if (sqliteInfo != null) {
                        return AjaxResult.success("同步完成", sqliteInfo);
                    } else {
                        return AjaxResult.error("离线包生成失败，未能获取下载地址");
                    }
                }
            } catch (Exception se) {
                log.warn("同步触发 SQLite 生成失败: {}", se.getMessage());
                return AjaxResult.error("同步上传成功，但重新打包数据库时发生异常: " + se.getMessage());
            }

            return AjaxResult.success("同步完成，但未识别到当前用户", result);
        } catch (Exception e) {
            log.error("离线数据上传同步失败", e);
            return AjaxResult.error("离线数据上传并同步处理异常: " + e.getMessage());
        }
    }

    /**
     * 离线图片/附件预上传
     * App端在同步JSON前，先循环上传所有图片，获取对应的 serverId (minioId)
     */
    @PostMapping("/sync/attachment")
    @ResponseBody
    @ApiOperation("离线附件预上传")
    public AjaxResult uploadSyncAttachment(@RequestParam("file") MultipartFile file) {
        try {
            FileMap fileMap = fileMapService.handleFileUpload(file);
            if (fileMap != null) {
                return AjaxResult.success("上传成功", fileMap.getId());
            }
            return AjaxResult.error("上传失败，未获取到文件记录");
        } catch (Exception e) {
            log.error("同步附件上传失败", e);
            return AjaxResult.error("附件上传异常: " + e.getMessage());
        }
    }
}
