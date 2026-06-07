package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.service.ITaskSheetService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

/**
 * 检测任务表格数据（Web 管理端）
 */
@Controller
@RequestMapping("/biz/taskSheet")
public class TaskSheetController extends BaseController {

    private final String prefix = "biz/taskSheet";

    @Autowired
    private ITaskSheetService taskSheetService;

    /**
     * 表格列表弹窗页（bi_task.type == 1 表示 App 端已提交病害数据）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/sheets/{taskId}")
    public String sheets(@PathVariable("taskId") Long taskId,
                         @RequestParam(value = "buildingName", required = false) String buildingName,
                         ModelMap mmap) {
        mmap.put("taskId", taskId);
        mmap.put("buildingName", buildingName != null ? buildingName : "");
        boolean hasDiseaseData = taskSheetService.hasDiseaseDataByTaskId(taskId);
        mmap.put("hasDiseaseData", hasDiseaseData);
        mmap.put("sheets", taskSheetService.listSheetStatusByTaskId(taskId));
        mmap.put("sheetTypeTechnicalCondition", ITaskSheetService.SHEET_TYPE_TECHNICAL_CONDITION);
        return prefix + "/sheets";
    }

    /**
     * 查询任务下全部表格及提交状态
     */
    @RequiresPermissions("biz:task:view")
    @PostMapping("/list/{taskId}")
    @ResponseBody
    public AjaxResult list(@PathVariable("taskId") Long taskId) {
        return AjaxResult.success(taskSheetService.listSheetStatusByTaskId(taskId));
    }

    /**
     * JSON 查看页
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/view/{taskId}/{type}")
    public String view(@PathVariable("taskId") Long taskId,
                       @PathVariable("type") String type,
                       @RequestParam(value = "sheetName", required = false) String sheetName,
                       ModelMap mmap) {
        mmap.put("taskId", taskId);
        mmap.put("type", type);
        mmap.put("sheetName", sheetName != null ? sheetName : type);
        return prefix + "/view";
    }

    /**
     * 获取表格 JSON 内容（供查看页 AJAX 加载）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/content/{taskId}/{type}")
    @ResponseBody
    public AjaxResult content(@PathVariable("taskId") Long taskId,
                              @PathVariable("type") String type) {
        String json = taskSheetService.getSheetJsonContent(taskId, type);
        return AjaxResult.success().put("content", json);
    }

    /**
     * JGLP05017 预览页（仅传 taskId，DOCX 渲染由前端 docx-preview 完成）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/preview/jglp05017/{taskId}")
    public String previewJglp05017(@PathVariable("taskId") Long taskId, ModelMap mmap) {
        mmap.put("taskId", taskId);
        return prefix + "/jglp05017";
    }

    /**
     * 实时生成 JGLP05017 DOCX 字节流返回给前端（不存库、不存文件）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/render/jglp05017/{taskId}")
    public void renderJglp05017(@PathVariable("taskId") Long taskId,
                                HttpServletResponse response) throws IOException {
        if (!taskSheetService.hasDiseaseDataByTaskId(taskId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "该任务尚未提交病害数据，无法查看");
            return;
        }
        byte[] bytes = taskSheetService.generateJglp05017WordBytes(taskId);
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "inline");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    /**
     * 已提交表格 JSON 文件下载（App 端提交的检测记录表）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/download/{taskId}/{type}")
    public void downloadSheet(@PathVariable("taskId") Long taskId,
                              @PathVariable("type") String type,
                              @RequestParam(value = "sheetName", required = false) String sheetName,
                              HttpServletResponse response) throws IOException {
        byte[] bytes = taskSheetService.downloadSheetBytes(taskId, type);
        String baseName = sheetName != null && !sheetName.isEmpty() ? sheetName : type;
        String fileName = URLEncoder.encode(baseName + "_" + taskId + ".json", "UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    /**
     * JGLP05017 Word 下载
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/download/jglp05017/{taskId}")
    public void downloadJglp05017(@PathVariable Long taskId,
                                  HttpServletResponse response) throws IOException {
        if (!taskSheetService.hasDiseaseDataByTaskId(taskId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "该任务尚未提交病害数据，无法下载");
            return;
        }
        byte[] bytes = taskSheetService.generateAndSaveJglp05017Word(taskId);
        String fileName = URLEncoder.encode("桥梁结构桥梁技术状况检测记录表_" + taskId + ".docx", "UTF-8");
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
