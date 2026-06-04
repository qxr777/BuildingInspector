package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.AppPackage;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IAppPackageService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;

/**
 * 移动端安装包Controller。
 */
@Controller
@RequestMapping("/biz/appPackage")
public class AppPackageController extends BaseController {
    private final String prefix = "biz/app_package";

    @Autowired
    private IAppPackageService appPackageService;

    @Autowired
    private IFileMapService fileMapService;

    @RequiresPermissions("biz:appPackage:view")
    @GetMapping()
    public String appPackage() {
        return prefix + "/app_package";
    }

    @RequiresPermissions("biz:appPackage:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(AppPackage appPackage) {
        startPage();
        List<AppPackage> list = appPackageService.selectAppPackageList(appPackage);
        return getDataTable(list);
    }

    @RequiresPermissions("biz:appPackage:add")
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    @RequiresPermissions("biz:appPackage:add")
    @Log(title = "移动端安装包", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(AppPackage appPackage, @RequestParam("file") MultipartFile file) {
        try {
            return toAjax(appPackageService.insertAppPackage(appPackage, file));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequiresPermissions("biz:appPackage:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        AppPackage appPackage = appPackageService.selectAppPackageById(id);
        mmap.put("appPackage", appPackage);
        return prefix + "/edit";
    }

    @RequiresPermissions("biz:appPackage:edit")
    @Log(title = "移动端安装包", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(AppPackage appPackage, @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            return toAjax(appPackageService.updateAppPackage(appPackage, file));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequiresPermissions("biz:appPackage:remove")
    @Log(title = "移动端安装包", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        try {
            return toAjax(appPackageService.deleteAppPackageByIds(ids));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequiresPermissions("biz:appPackage:download")
    @GetMapping("/download/{id}")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        AppPackage appPackage = appPackageService.selectAppPackageById(id);
        if (appPackage == null || appPackage.getMinioId() == null) {
            throw new RuntimeException("安装包不存在");
        }
        FileMap fileMap = fileMapService.selectFileMapById(appPackage.getMinioId());
        if (fileMap == null) {
            throw new RuntimeException("安装包文件不存在");
        }
        byte[] fileBytes = fileMapService.handleFileDownloadByNewName(fileMap.getNewName());
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("安装包文件不存在");
        }
        response.setContentType("application/vnd.android.package-archive");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileMap.getOldName(), "UTF-8"));
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(fileBytes);
            outputStream.flush();
        }
    }
}
