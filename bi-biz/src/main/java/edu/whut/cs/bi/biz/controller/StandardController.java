package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Standard;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.StandardService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;

/**
 * 标准Controller
 *
 * @author ruoyi
 * @date 2025-03-23
 */
@Controller
@RequestMapping("/biz/standard")
public class StandardController extends BaseController
{
    private String prefix = "biz/standard";

    @Autowired
    private StandardService standardService;
    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private IFileMapService fileMapService;

    @RequiresPermissions("biz:standard:view")
    @GetMapping()
    public String standard()
    {
        return prefix + "/standard";
    }

    /**
     * 查询标准列表
     */
    @RequiresPermissions("biz:standard:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Standard standard)
    {
        startPage();
        List<Standard> list = standardService.selectStandardList(standard);
        return getDataTable(list);
    }

    /**
     * 导出标准列表
     */
    @RequiresPermissions("biz:standard:export")
//    @Log(title = "标准", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Standard standard)
    {
        List<Standard> list = standardService.selectStandardList(standard);
        ExcelUtil<Standard> util = new ExcelUtil<Standard>(Standard.class);
        return util.exportExcel(list, "标准数据");
    }

    /**
     * 新增标准
     */
    @RequiresPermissions("biz:standard:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存标准
     */
    @RequiresPermissions("biz:standard:add")
//    @Log(title = "标准", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Standard standard,@RequestParam("file") MultipartFile file)
    {
        // 处理文件上传
        if (!file.isEmpty()) {
                FileMap fileMap = fileMapService.handleFileUpload(file);
                Attachment attachment = null;
                standard.setAttachment(attachment);
                int flag = standardService.insertStandard(standard);
                attachment = new Attachment();
                attachment.setName(standard.getName());
                attachment.setSubjectId(standard.getId());
                attachment.setType(5);
                attachment.setMinioId(Long.valueOf(fileMap.getId()));
                // MioId
                attachmentService.insertAttachment(attachment);
                standardService.setAttachmentId(attachment.getId(),standard.getId());
                return toAjax(flag);

        }
        return toAjax(0);
    }

    /**
     * 修改标准
     */
    @RequiresPermissions("biz:standard:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Standard standard = standardService.selectStandardById(id);
        mmap.put("standard", standard);
        return prefix + "/edit";
    }

    /**
     * 修改保存标准
     */
    @RequiresPermissions("biz:standard:edit")
//    @Log(title = "标准", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Standard standard)
    {
        int flag = standardService.updateStandard(standard);
        return toAjax(flag);
    }

    /**
     * 删除标准
     */
    @RequiresPermissions("biz:standard:remove")
//    @Log(title = "标准", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        // service 中 对附件 进行删除
        int flag = standardService.deleteStandardByIds(ids);
        return toAjax(flag);
    }
    
    /**
     * 下载标准文件
     */
    @RequiresPermissions("biz:standard:download")
    @GetMapping("/download/{id}")
    public void downloadFile(@PathVariable("id") Long id, HttpServletResponse response) throws IOException
    {
        Standard standard = standardService.selectStandardById(id);
        if (standard == null || standard.getAttachment() == null) {
            throw new RuntimeException("文件不存在");
        }
        Long minioId = attachmentService.getAttachmentById(standard.getAttachment().getId()).getMinioId();
        // 使用 FileMapService 下载文件
        FileMap filemap = fileMapService.selectFileMapById(minioId);
        byte[] fileBytes = fileMapService.handleFileDownloadByNewName(filemap.getNewName());
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("文件不存在");
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode(filemap.getOldName(), "UTF-8"));

        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(fileBytes);
            outputStream.flush();
        }
    }
}
