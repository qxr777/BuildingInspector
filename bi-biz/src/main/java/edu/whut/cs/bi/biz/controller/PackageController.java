package edu.whut.cs.bi.biz.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.service.IPackageService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 用户压缩包Controller
 * 
 * @author wanzheng
 * @date 2025-07-18
 */
@Controller
@RequestMapping("/biz/package")
public class PackageController extends BaseController
{
    private String prefix = "biz/package";

    @Autowired
    private IPackageService packageService;

    @RequiresPermissions("biz:package:view")
    @GetMapping()
    public String package1()
    {
        return prefix + "/package";
    }

    /**
     * 查询用户压缩包列表
     */
    @RequiresPermissions("biz:package:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Package package1)
    {
        startPage();
        List<Package> list = packageService.selectPackageList(package1);
        return getDataTable(list);
    }

}
