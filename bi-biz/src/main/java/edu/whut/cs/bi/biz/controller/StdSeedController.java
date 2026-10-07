package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.service.std.StdSeedReport;
import edu.whut.cs.bi.biz.service.std.StdSeedScope;
import edu.whut.cs.bi.biz.service.std.StdSeedService;
import lombok.Data;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 正式稿 JTG/T 5230—2026 标准数据种子（仅管理员显式触发，不在启动时执行）。
 */
@RestController
@RequestMapping("/biz/stdSeed")
public class StdSeedController extends BaseController {

    @Resource
    private StdSeedService stdSeedService;

    /**
     * 执行/预演种子。建议先 dryRun=true 查看报告再正式执行。
     */
    @Log(title = "标准数据种子", businessType = BusinessType.UPDATE)
    @RequiresPermissions("biz:std:seed")
    @PostMapping("/run")
    public AjaxResult run(@RequestBody StdSeedHttpRequest request) {
        StdSeedScope scope = parseScope(request.getScope());
        StdSeedReport report = stdSeedService.seed(scope, Boolean.TRUE.equals(request.getDryRun()),
                request.getBridgeTypes());
        AjaxResult result = report.getErrors().isEmpty()
                ? AjaxResult.success(report)
                : AjaxResult.error("种子执行存在错误").put("report", report);
        return result.put("summary", report.summary());
    }

    private StdSeedScope parseScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return StdSeedScope.ALL;
        }
        try {
            return StdSeedScope.valueOf(scope.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return StdSeedScope.ALL;
        }
    }

    /** HTTP 请求体。 */
    @Data
    public static class StdSeedHttpRequest {
        /** SCHEMA / TYPES / TEMPLATES / BINDINGS / ALL，默认 ALL */
        private String scope;

        /** true 时只预演不写入，默认 false */
        private Boolean dryRun;

        /** 桥型子集，如 ["B01","B02"]；为空表示全部 */
        private List<String> bridgeTypes;
    }
}
