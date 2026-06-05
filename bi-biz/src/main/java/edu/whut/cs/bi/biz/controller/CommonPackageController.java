package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IPackageService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 公共数据包Controller。
 */
@Controller
@RequestMapping("/biz/commonPackage")
public class CommonPackageController extends BaseController {
    private final String prefix = "biz/common_package";

    @Autowired
    private IPackageService packageService;

    @RequiresPermissions("biz:commonPackage:view")
    @GetMapping()
    public String commonPackage() {
        return prefix + "/common_package";
    }

    @RequiresPermissions("biz:commonPackage:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(FileMap fileMap) {
        startPage();
        List<FileMap> list = packageService.selectCommonTemplatePackageList(fileMap);
        return getDataTable(list);
    }

    @RequiresPermissions("biz:commonPackage:add")
    @Log(title = "生成公共数据包", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    @ResponseBody
    public AjaxResult generate() {
        return packageService.generateCommonTemplatePackage();
    }
}
