package edu.whut.cs.bi.biz.controller;

import java.util.List;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IFileMapService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.net.URLEncoder;

/**
 * 文件管理Controller
 * 
 * @author zzzz
 * @date 2025-03-29
 */
@Controller
@RequestMapping("/biz/file")
public class FileMapController extends BaseController {
    private String prefix = "biz/file";

    @Autowired
    private IFileMapService fileMapService;

    @RequiresPermissions("biz:file:view")
    @GetMapping()
    public String file() {
        return prefix + "/file";
    }

    /**
     * 查询文件管理列表
     */
    @RequiresPermissions("biz:file:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(FileMap fileMap) {
        startPage();
        List<FileMap> list = fileMapService.selectFileMapList(fileMap);
        return getDataTable(list);
    }

    /**
     * 导出文件管理列表
     */
    @RequiresPermissions("biz:file:export")
    @Log(title = "文件管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(FileMap fileMap) {
        List<FileMap> list = fileMapService.selectFileMapList(fileMap);
        ExcelUtil<FileMap> util = new ExcelUtil<FileMap>(FileMap.class);
        return util.exportExcel(list, "文件管理数据");
    }

    /**
     * 新增文件管理
     */
    @RequiresPermissions("biz:file:add")
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存文件管理
     */
    @RequiresPermissions("biz:file:add")
    @Log(title = "文件管理", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(FileMap fileMap) {
        return toAjax(fileMapService.insertFileMap(fileMap));
    }

    /**
     * 修改文件管理
     */
    @RequiresPermissions("biz:file:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        FileMap fileMap = fileMapService.selectFileMapById(id);
        mmap.put("fileMap", fileMap);
        return prefix + "/edit";
    }

    /**
     * 修改保存文件管理
     */
    @RequiresPermissions("biz:file:edit")
    @Log(title = "文件管理", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(FileMap fileMap) {
        return toAjax(fileMapService.updateFileMap(fileMap));
    }

    /**
     * 删除文件管理
     */
    @RequiresPermissions("biz:file:remove")
    @Log(title = "文件管理", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(fileMapService.deleteFileMapByIds(ids));
    }

    /**
     * 上传文件
     */
    @RequiresPermissions("biz:file:upload")
    @Log(title = "文件上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    @ResponseBody
    public AjaxResult upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return error("上传文件不能为空");
            }

            FileMap fileMap = fileMapService.handleFileUpload(file);
            // 构建更详细的返回信息
            AjaxResult ajaxResult = success("上传成功");
            ajaxResult.put("fileId", fileMap.getId());
            ajaxResult.put("fileName", fileMap.getOldName());
            ajaxResult.put("newName", fileMap.getNewName());
            ajaxResult.put("createTime", fileMap.getCreateTime());
            ajaxResult.put("downloadUrl", "/biz/file/download/" + fileMap.getId());

            return ajaxResult;
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * 批量上传文件
     */
    @RequiresPermissions("biz:file:upload")
    @Log(title = "批量文件上传", businessType = BusinessType.INSERT)
    @PostMapping("/batchUpload")
    @ResponseBody
    public AjaxResult batchUpload(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files.length == 0) {
                return error("上传文件不能为空");
            }

            // 文件检查
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return error("上传文件不能为空");
                }
            }

            List<FileMap> fileMaps = fileMapService.handleBatchFileUpload(files);

            // 构建更详细的返回信息
            AjaxResult ajaxResult = success("批量上传成功");
            List<Object> fileInfos = new ArrayList<>();
            for (FileMap fileMap : fileMaps) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileId", fileMap.getId());
                fileInfo.put("fileName", fileMap.getOldName());
                fileInfo.put("newName", fileMap.getNewName());
                fileInfo.put("createTime", fileMap.getCreateTime());
                fileInfo.put("downloadUrl", "/biz/file/download/" + fileMap.getId());
                fileInfos.add(fileInfo);
            }
            ajaxResult.put("files", fileInfos);

            return ajaxResult;
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * 通过newName下载文件
     */
    @GetMapping("/download/{newName}")
    public void downloadByNewName(@PathVariable("newName") String newName, HttpServletResponse response)
            throws IOException {
        ServletOutputStream outputStream = null;
        try {
            // 获取文件数据
            byte[] fileBytes = fileMapService.handleFileDownloadByNewName(newName);

            // 获取文件信息
            FileMap fileMap = fileMapService.selectFileMapByNewName(newName);
            if (fileMap == null) {
                throw new RuntimeException("文件不存在");
            }

            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(fileMap.getOldName(), "UTF-8"));

            // 写入响应
            outputStream = response.getOutputStream();
            outputStream.write(fileBytes);
            outputStream.flush();
        } catch (Exception e) {
            response.setContentType("text/html;charset=utf-8");
            response.getWriter().write("文件下载失败：" + e.getMessage());
        } finally {
            // 确保输出流关闭
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // 记录关闭流失败但不抛出异常
                }
            }
        }
    }

    /**
     * 批量下载文件
     */
    @RequiresPermissions("biz:file:download")
    @GetMapping("/batchDownload")
    public void batchDownload(@RequestParam("ids") String ids, HttpServletResponse response) throws IOException {
        ServletOutputStream outputStream = null;
        try {
            if (StringUtils.isEmpty(ids)) {
                throw new RuntimeException("未选择要下载的文件");
            }

            // 转换ID列表
            String[] idArray = ids.split(",");
            Long[] fileIds = new Long[idArray.length];
            for (int i = 0; i < idArray.length; i++) {
                fileIds[i] = Long.valueOf(idArray[i]);
            }

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment;filename=files.zip");

            byte[] data = fileMapService.handleBatchFileDownload(fileIds);

            outputStream = response.getOutputStream();
            outputStream.write(data);
            outputStream.flush();
        } catch (Exception e) {
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"code\":500,\"msg\":\"" + e.getMessage() + "\"}");
        } finally {
            // 确保输出流关闭
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // 记录关闭流失败但不抛出异常
                }
            }
        }
    }

    /**
     * 复制文件
     */
    @RequiresPermissions("biz:file:copy")
    @Log(title = "文件复制", businessType = BusinessType.INSERT)
    @PostMapping("/copy/{id}")
    @ResponseBody
    public AjaxResult copyFile(@PathVariable("id") Long id) {
        try {
            FileMap fileMap = fileMapService.copyFile(id);
            AjaxResult ajaxResult = success("文件复制成功: " + fileMap.getOldName());
            ajaxResult.put("fileId", fileMap.getId());
            ajaxResult.put("fileName", fileMap.getOldName());
            ajaxResult.put("newName", fileMap.getNewName());
            ajaxResult.put("createTime", fileMap.getCreateTime());
            ajaxResult.put("createBy", fileMap.getCreateBy());
            ajaxResult.put("downloadUrl", "/biz/file/download/" + fileMap.getId());

            return ajaxResult;
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}
