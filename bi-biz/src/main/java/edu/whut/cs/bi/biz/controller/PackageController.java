package edu.whut.cs.bi.biz.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import edu.whut.cs.bi.biz.config.MinioConfig;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.service.IPackageService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 用户压缩包Controller
 *
 * @author wanzheng
 * @date 2025-07-18
 */
@Controller
@RequestMapping("/biz/package")
public class PackageController extends BaseController {
    private String prefix = "biz/package";

    @Autowired
    private IPackageService packageService;

    @Autowired
    private MinioConfig minioConfig;

    @RequiresPermissions("biz:package:view")
    @GetMapping()
    public String package1(ModelMap mmap) {
        mmap.put("minioUrl", minioConfig.getUrl());
        mmap.put("minioBucket", minioConfig.getBucketName());
        return prefix + "/packages";
    }

    /**
     * 查询用户压缩包列表
     */
    @RequiresPermissions("biz:package:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Package package1) {
        startPage();
        List<Package> list = packageService.selectPackageList(package1);
        return getDataTable(list);
    }

    /**
     * 批量生成用户数据包
     */
    @RequiresPermissions("biz:package:add")
    @Log(title = "批量生成用户压缩包", businessType = BusinessType.INSERT)
    @PostMapping("/batchGenerate")
    @ResponseBody
    public AjaxResult batchGenerate(String userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return AjaxResult.error("未选择用户");
        }

        // 解析用户ID字符串
        List<Long> userIdList = Arrays.stream(userIds.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 异步处理生成压缩包
        packageService.batchGeneratePackagesAsync(userIdList);

        return AjaxResult.success("已开始生成压缩包，请稍后刷新页面查看结果");
    }

}
