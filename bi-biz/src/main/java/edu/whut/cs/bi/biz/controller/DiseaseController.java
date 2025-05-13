package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.service.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 病害Controller
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
@Controller
@RequestMapping("/biz/disease")
public class DiseaseController extends BaseController
{
    private String prefix = "biz/disease";

    @Resource
    private IDiseaseService diseaseService;

    @Resource
    private ITaskService taskService;

    @Resource
    private IBiObjectService biObjectService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private MinioConfig minioConfig;
    @RequiresPermissions("biz:disease:view")
    @GetMapping()
    public String disease()
    {
        return prefix + "/disease";
    }

    /**
     * 查询病害列表
     */
    @RequiresPermissions("biz:disease:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Disease disease)
    {
        List<Disease> properties = diseaseService.selectDiseaseList(disease);
        return getDataTable(properties);
    }

    /**
     * 导出病害列表
     */
    @RequiresPermissions("biz:disease:export")
    @Log(title = "病害", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Disease disease)
    {
        List<Disease> list = diseaseService.selectDiseaseList(disease);
        ExcelUtil<Disease> util = new ExcelUtil<Disease>(Disease.class);
        return util.exportExcel(list, "病害数据");
    }

    /**
     * 新增病害
     */
    @GetMapping(value = { "/add/{taskId}/{biObjectId}" })
    public String add(@PathVariable("taskId") Long taskId, @PathVariable("biObjectId") Long biObjectId, ModelMap mmap)
    {
        if (taskId != null) {
            mmap.put("task", taskService.selectTaskById(taskId));
        }
        if (biObjectId != null) {
            mmap.put("biObject", biObjectService.selectBiObjectById(biObjectId));
        }

        return prefix + "/add";
    }

    /**
     * 新增保存病害
     */
    @RequiresPermissions("biz:disease:add")
    @Log(title = "病害", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid Disease disease,@RequestParam(value = "files", required = false) MultipartFile[] files)
    {
        disease.setCreateBy(ShiroUtils.getLoginName());
        diseaseService.insertDisease(disease);
        System.out.println(disease.getId());
        if(files!=null) {
            diseaseService.handleDiseaseAttachment(files,disease.getId());
        }
        return toAjax(Math.toIntExact(disease.getId()));
    }


    /**
     * 修改病害
     */
    @RequiresPermissions("biz:disease:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Disease disease = diseaseService.selectDiseaseById(id);
        mmap.put("disease", disease);
        mmap.put("biObject", disease.getBiObject());
        return prefix + "/edit";
    }

    /**
     * 修改保存病害
     */
    @RequiresPermissions("biz:disease:edit")
    @Log(title = "病害", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(
            @Valid Disease disease,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "existingAttachmentIds", required = false) String existingAttachmentIds,
            @RequestParam(value = "deletedAttachmentIds", required = false) String[] deletedAttachmentIds
    ) {
        disease.setUpdateBy(ShiroUtils.getLoginName());
        diseaseService.handleDiseaseAttachment(files,disease.getId());
        return toAjax(diseaseService.updateDisease(disease));
    }

    /**
     * 删除
     */
    @RequiresPermissions("biz:disease:remove")
    @Log(title = "病害", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(diseaseService.deleteDiseaseByIds(ids));
    }

    /**
     * 修改病害
     */
    @RequiresPermissions("biz:disease:list")
    @GetMapping("/showDiseaseDetail/{id}")
    public String showDiseaseDetail(@PathVariable("id") Long id, ModelMap mmap)
    {
        Disease disease = diseaseService.selectDiseaseById(id);
        mmap.put("disease", disease);
        mmap.put("biObject", disease.getBiObject());

        return prefix + "/detail";
    }

    /**
     * 构件扣分
     */
    @RequiresPermissions("biz:disease:add")
    @GetMapping("/computeDeductPoints")
    @ResponseBody
    public AjaxResult computeDeductPoints(int maxScale, int scale) {

        if (StringUtils.isNull(maxScale) || StringUtils.isNull(scale)) {
            return AjaxResult.error("参数错误");
        }

        return AjaxResult.success(diseaseService.computeDeductPoints(maxScale, scale));
    }



    @GetMapping("/attachments/{id}")
    @ResponseBody  // 添加此注解以返回JSON数据
    public AjaxResult getAttachments(@PathVariable("id") Long id)
    {
        try {
            // 获取病害对应的附件列表
            List<Attachment> attachments = attachmentService.getAttachmentList(id).stream().filter(e->e.getName().startsWith("disease")).toList();

            // 转换为前端需要的格式
            List<Map<String, Object>> result = new ArrayList<>();
            for (Attachment attachment : attachments) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", attachment.getId());
                map.put("fileName", attachment.getName().split("_")[1]);
                FileMap fileMap = fileMapService.selectFileMapById(attachment.getMinioId());
                if(fileMap == null)continue;
                String s = fileMap.getNewName();
                map.put("url",minioConfig.getEndpoint()+ "/"+minioConfig.getBucketName()+"/"+s.substring(0,2)+"/"+s);
                // 根据文件后缀判断是否为图片
                map.put("isImage", isImageFile(attachment.getName()));
                result.add(map);
            }

            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("获取附件列表失败：" + e.getMessage());
        }
    }

    // 判断文件是否为图片的辅助方法
    private boolean isImageFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String extension = fileName.toLowerCase();
        return extension.endsWith(".jpg") ||
                extension.endsWith(".jpeg") ||
                extension.endsWith(".png") ||
                extension.endsWith(".gif") ||
                extension.endsWith(".bmp");
    }


    @DeleteMapping("/attachment/delete/{fileId}")
    @ResponseBody  // 添加此注解以返回JSON数据
    public AjaxResult deleteAttachment(@PathVariable("fileId") Long id)
    {
        attachmentService.deleteAttachmentById(id);
        return AjaxResult.success("success");
    }
}
