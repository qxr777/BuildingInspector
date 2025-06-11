package edu.whut.cs.bi.biz.controller;

import java.io.File;
import java.util.*;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.service.AttachmentService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jetbrains.annotations.NotNull;
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
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IFileMapService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private MinioConfig minioConfig;

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

    @PostMapping("/uploadImage")
    @ResponseBody
    public AjaxResult uploadAttachment(@RequestParam("id") Long id, @RequestParam("file") MultipartFile file,@RequestParam("type") String type,@RequestParam("index") int index) {
        return uploadAttachmetBuildImage(id, file, type, index);
    }

    /**
     * 上传附件并构建图片信息，用于处理图片上传逻辑，包括新增或更新附件记录，并将文件信息存储到数据库和文件系统中。
     *
     * @param id    主体ID，表示该附件所属的业务实体（如某个表单或对象）的唯一标识 建筑id
     * @param file  MultipartFile类型，表示上传的文件本身
     * @param type  附件类型，用于区分上传的图片属于哪一类（如front正面、side侧面等）
     * @param index 索引值，用于标识该附件在一组附件中的位置或顺序（0或者1）
     * @return AjaxResult 返回操作结果，包含成功或失败的信息以及相关文件数据
     */
    private AjaxResult uploadAttachmetBuildImage(Long id, MultipartFile file, String type, int index) {
        try {
            if (file.isEmpty()) {
                return error("上传文件不能为空");
            }
            List<Attachment> attachmentList = attachmentService.getAttachmentList(id);
            for (Attachment value : attachmentList) {
                if (value.getName().split("_")[0].equals(String.valueOf(index))&&value.getName().split("_")[1].equals(type)) {
                    FileMap fileMap = fileMapService.handleFileUpload(file);
                    value.setMinioId(Long.valueOf(fileMap.getId()));
                    value.setName(index +"_"+ type +"_"+ file.getOriginalFilename());
                    attachmentService.updateAttachment(value);
                    AjaxResult ajaxResult = success("上传成功");
                    ajaxResult.put("fileId", fileMap.getId());
                    ajaxResult.put("fileName", fileMap.getOldName());
                    ajaxResult.put("newName", fileMap.getNewName());
                    ajaxResult.put("createTime", fileMap.getCreateTime());
                    ajaxResult.put("downloadUrl", "/biz/file/download/" + fileMap.getId());
                    return ajaxResult;
                }
            }
            Attachment attachment = new Attachment();
            attachment.setType(6);
            attachment.setName(index +"_"+ type +"_"+ file.getOriginalFilename());
            attachment.setSubjectId(id);
            FileMap fileMap = fileMapService.handleFileUpload(file);
            // 构建更详细的返回信息
            AjaxResult ajaxResult = success("上传成功");
            ajaxResult.put("fileId", fileMap.getId());
            ajaxResult.put("fileName", fileMap.getOldName());
            ajaxResult.put("newName", fileMap.getNewName());
            ajaxResult.put("createTime", fileMap.getCreateTime());
            ajaxResult.put("downloadUrl", "/biz/file/download/" + fileMap.getId());
            attachment.setMinioId(Long.valueOf(fileMap.getId()));
            attachmentService.insertAttachment(attachment);
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

    @PostMapping("/deleteImage")
    @ResponseBody
    public AjaxResult deleteImage(@RequestParam("id") Long id, @RequestParam("filename") String filename) {
        try {
            // 从文件名中解析出类型和索引
            String[] parts = filename.split("_");
            if (parts.length < 2) {
                return error("文件名格式不正确");
            }

            String index = parts[0];
            String type = parts[1];

            // 查找对应的附件记录
            List<Attachment> attachmentList = attachmentService.getAttachmentList(id);
            for (Attachment attachment : attachmentList) {
                String attachmentName = attachment.getName();
                if (attachmentName != null && attachmentName.startsWith(index + "_" + type)) {
                    // 删除附件记录及关联的文件
                    Long attachmentId = attachment.getId();
                    attachmentService.deleteAttachmentById(attachmentId);
                    return success("删除成功");
                }
            }

            return error("未找到对应的图片记录");
        } catch (Exception e) {
            return error("删除图片失败: " + e.getMessage());
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

    /**
     * 获取文件信息
     */
    @GetMapping("/get/{id}")
    @ResponseBody
    public AjaxResult getFileInfo(@PathVariable("id") Long id) {
        FileMap fileMap = fileMapService.selectFileMapById(id);
        if (fileMap == null) {
            return AjaxResult.error("文件不存在");
        }
        return AjaxResult.success(fileMap);
    }

    @GetMapping("/getImages")
    @ResponseBody
    public AjaxResult getImages(@RequestParam("id") Long id) {
        List<FileMap> fileMapList = getImageMaps(id);
        return AjaxResult.success(fileMapList);
    }

    /**
     * 获取与指定主体ID关联的图片文件列表。
     * 该方法从附件列表中筛选出类型为"front"(正面)或"side"(侧面)的附件，
     * 并根据附件中的Minio ID查询对应的FileMap对象，构造可访问的图片URL，
     * 最终返回包含图片信息的FileMap对象列表。
     *
     * @param id 主体ID，表示需要获取图片的业务实体（如建筑、设备等）的唯一标识
     * @return List<FileMap> 返回与指定主体ID关联的图片FileMap对象列表
     * 根据side和front来判断正立面照，0，1来判断第一张还是第二张正面或者立面照片
     * 例子：FileMap(id=163, oldName=1_side_OIP-C.jpg, newName=http://60.205.13.156:9000/public/e7/e77b080c5ceb48f3bd079f7116d71cc1.jpg, createTime=Sat Apr 26 09:45:44 SGT 2025, updateTime=null, createBy=admin)
     * FileMap(id=176, oldName=1_front_学海楼307.jpg, newName=http://60.205.13.156:9000/public/9a/9a55286e12f24c2caf49e3eb0c73020a.jpg, createTime=Mon Apr 28 23:10:24 SGT 2025, updateTime=null, createBy=admin)
     * FileMap(id=288, oldName=0_side_OIP-C.jpg, newName=http://60.205.13.156:9000/public/35/3576c7f8ff704ecfacc892c1de3c7246.jpg, createTime=Sat May 10 09:54:30 SGT 2025, updateTime=null, createBy=admin)
     * FileMap(id=289, oldName=0_front_1-200R1141258.jpg, newName=http://60.205.13.156:9000/public/24/24f8142839744eef80b8843c25b826d1.jpg, createTime=Sat May 10 09:56:37 SGT 2025, updateTime=null, createBy=admin)
     */
    @NotNull
    public List<FileMap> getImageMaps(Long id) {
        List<Attachment> bySubjectId = attachmentService.getAttachmentList(id);
        List<FileMap> fileMapList = bySubjectId.stream()
                .filter(e->{
                    String[] s = e.getName().split("_");
                    if(s.length>=2&&(s[1].equals("front")||s[1].equals("side"))){
                        return true;
                    }
                    return false;
                })
                .map(e -> fileMapService.selectFileMapById(e.getMinioId())) // 查询 FileMap
                .filter(Objects::nonNull) // 只保留非 null 的 FileMap
                .peek(e -> e.setNewName(minioConfig.getEndpoint()+ "/"+minioConfig.getBucketName()+"/"+e.getNewName().substring(0, 2) + "/" + e.getNewName())) // 修改 newName
                .toList();

        for(Attachment attachment: bySubjectId){
            for(FileMap fileMap:fileMapList){
                if((fileMap.getId()+0)==attachment.getMinioId()){
                    fileMap.setOldName(attachment.getName());
                }
            }
        }
        System.out.println(bySubjectId.size());
        return fileMapList;
    }

}
