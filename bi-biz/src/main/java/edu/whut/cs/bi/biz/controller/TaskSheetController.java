package edu.whut.cs.bi.biz.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.service.ITaskSheetService;
import edu.whut.cs.bi.biz.service.sheet.Jglp05017WordRenderer;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 检测任务表格数据（Web 管理端）
 */
@Controller
@RequestMapping("/biz/taskSheet")
public class TaskSheetController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TaskSheetController.class);

    private final String prefix = "biz/taskSheet";

    @Autowired
    private ITaskSheetService taskSheetService;

    @Autowired
    private ITaskService taskService;

    /**
     * 表格列表弹窗页
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
        mmap.put("jsonSheetWordTypes", taskSheetService.listJsonSheetWordTypes());
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
     * 表格 JSON 编辑页
     */
    @RequiresPermissions("biz:task:edit")
    @GetMapping("/edit/{taskId}/{type}")
    public String edit(@PathVariable("taskId") Long taskId,
                       @PathVariable("type") String type,
                       @RequestParam(value = "sheetName", required = false) String sheetName,
                       ModelMap mmap) {
        if (!taskSheetService.supportsWebEdit(type)) {
            throw new ServiceException("该表格类型不支持网页端编辑：" + type);
        }
        mmap.put("taskId", taskId);
        mmap.put("type", type);
        mmap.put("sheetName", sheetName != null && !sheetName.isEmpty()
                ? sheetName
                : (ITaskSheetService.SHEET_TYPE_TECHNICAL_CONDITION.equals(type)
                ? Jglp05017WordRenderer.SHEET_TITLE
                : taskSheetService.resolveJsonSheetDownloadBaseName(type)));
        return prefix + "/sheetEdit";
    }

    /**
     * 加载表格数据供编辑（JSON 表格或技术状况表病害数据）
     */
    @RequiresPermissions("biz:task:edit")
    @GetMapping("/load/{taskId}/{type}")
    @ResponseBody
    public AjaxResult loadForEdit(@PathVariable("taskId") Long taskId,
                                  @PathVariable("type") String type) {
        if (!taskSheetService.supportsWebEdit(type)) {
            return AjaxResult.error("该表格类型不支持网页端编辑");
        }
        if (ITaskSheetService.SHEET_TYPE_TECHNICAL_CONDITION.equals(type)) {
            return AjaxResult.success()
                    .put("content", taskSheetService.getTechnicalConditionJsonForEdit(taskId))
                    .put("defaultHeader", taskSheetService.buildTechnicalConditionDefaultHeader(taskId))
                    .put("templateFixedFields", taskSheetService.getSheetTemplateFixedFields(type));
        }
        String json = taskSheetService.getSheetJsonForEdit(taskId, type);
        return AjaxResult.success()
                .put("content", json)
                .put("defaultHeader", taskSheetService.buildDefaultSheetHeader(taskId))
                .put("templateFixedFields", taskSheetService.getSheetTemplateFixedFields(type));
    }

    /**
     * 保存网页端编辑的表格数据
     */
    @Log(title = "检测记录表", businessType = BusinessType.UPDATE)
    @RequiresPermissions("biz:task:edit")
    @PostMapping("/save/{taskId}/{type}")
    @ResponseBody
    public AjaxResult save(@PathVariable("taskId") Long taskId,
                           @PathVariable("type") String type,
                           @RequestBody String jsonBody) {
        if (!taskSheetService.supportsWebEdit(type)) {
            return AjaxResult.error("该表格类型不支持网页端编辑");
        }
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            return AjaxResult.error("未找到检测任务，taskId=" + taskId);
        }
        if (task.getBuildingId() == null) {
            return AjaxResult.error("任务未关联桥梁，无法保存表格");
        }
        if (jsonBody == null) {
            return AjaxResult.error("请求体不能为空");
        }
        byte[] jsonBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        if (ITaskSheetService.SHEET_TYPE_TECHNICAL_CONDITION.equals(type)) {
            if (!taskSheetService.saveTechnicalConditionFromWeb(taskId, jsonBytes)) {
                return AjaxResult.error("暂无病害记录");
            }
            return AjaxResult.success("保存成功");
        }
        JSONObject sheetJson;
        try {
            sheetJson = JSON.parseObject(jsonBody);
        } catch (Exception e) {
            return AjaxResult.error("不是合法 JSON");
        }
        if (!hasSavableSheetRecords(sheetJson)) {
            return AjaxResult.error("暂无表格数据");
        }
        taskSheetService.saveSheetFromWeb(taskId, task.getBuildingId(), type, jsonBytes);
        return AjaxResult.success("保存成功");
    }

    private boolean hasSavableSheetRecords(JSONObject sheetJson) {
        JSONArray pages = sheetJson != null ? sheetJson.getJSONArray("pages") : null;
        if (pages == null || pages.isEmpty()) {
            return false;
        }
        for (int i = 0; i < pages.size(); i++) {
            JSONObject page = pages.getJSONObject(i);
            JSONArray records = page != null ? page.getJSONArray("records") : null;
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (int j = 0; j < records.size(); j++) {
                Object record = records.get(j);
                if (hasMeaningfulJsonValue(record)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMeaningfulJsonValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                if (hasMeaningfulJsonValue(array.get(i))) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : object.keySet()) {
                if (hasMeaningfulJsonValue(object.get(key))) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * App 上传 JSON 表格 Word 预览页（docx-preview 渲染）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/preview/sheet/{taskId}/{type}")
    public String previewJsonSheet(@PathVariable("taskId") Long taskId,
                                   @PathVariable("type") String type,
                                   @RequestParam(value = "sheetName", required = false) String sheetName,
                                   ModelMap mmap) {
        if (!taskSheetService.supportsJsonSheetWord(type)) {
            throw new ServiceException("该表格类型暂不支持 Word 预览：" + type);
        }
        mmap.put("taskId", taskId);
        mmap.put("type", type);
        mmap.put("sheetName", sheetName != null && !sheetName.isEmpty()
                ? sheetName : taskSheetService.resolveJsonSheetDownloadBaseName(type));
        return prefix + "/sheetPreview";
    }

    /**
     * 从 MinIO JSON 实时生成表格 DOCX 字节流（预览/下载共用，不写 MinIO）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/render/sheet/{taskId}/{type}")
    public void renderJsonSheet(@PathVariable("taskId") Long taskId,
                                @PathVariable("type") String type,
                                HttpServletResponse response) {
        try {
            byte[] bytes = taskSheetService.generateJsonSheetWordBytes(taskId, type);
            writeDocxInline(response, bytes);
        } catch (ServiceException e) {
            log.warn("renderJsonSheet failed taskId={} type={}: {}", taskId, type, e.getMessage(), e);
            writeRenderError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("renderJsonSheet failed taskId={} type={}", taskId, type, e);
            writeRenderError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "生成 Word 失败：" + e.getMessage());
        }
    }

    /**
     * App 上传 JSON 表格 Word 下载（实时生成，页码在页眉，不写入 MinIO）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/download/sheet/{taskId}/{type}")
    public void downloadJsonSheetWord(@PathVariable("taskId") Long taskId,
                                      @PathVariable("type") String type,
                                      @RequestParam(value = "sheetName", required = false) String sheetName,
                                      HttpServletResponse response) throws IOException {
        byte[] bytes = taskSheetService.generateJsonSheetWordDownloadBytes(taskId, type);
        String baseName = sheetName != null && !sheetName.isEmpty()
                ? sheetName : taskSheetService.resolveJsonSheetDownloadBaseName(type);
        String fileName = URLEncoder.encode(baseName + "_" + taskId + ".docx", "UTF-8");
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
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
                                HttpServletResponse response) {
        try {
            if (!taskSheetService.hasDiseaseDataByTaskId(taskId)) {
                writeRenderError(response, HttpServletResponse.SC_BAD_REQUEST, "该任务尚未提交病害数据，无法查看");
                return;
            }
            byte[] bytes = taskSheetService.generateJglp05017WordBytes(taskId);
            writeDocxInline(response, bytes);
        } catch (ServiceException e) {
            log.warn("renderJglp05017 failed taskId={}: {}", taskId, e.getMessage());
            writeRenderError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("renderJglp05017 failed taskId={}", taskId, e);
            writeRenderError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "生成 Word 失败：" + e.getMessage());
        }
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
     * JGLP05017 Word 下载（实时生成，不存 MinIO）
     */
    @RequiresPermissions("biz:task:view")
    @GetMapping("/download/jglp05017/{taskId}")
    public void downloadJglp05017(@PathVariable Long taskId,
                                  HttpServletResponse response) throws IOException {
        if (!taskSheetService.hasDiseaseDataByTaskId(taskId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "该任务尚未提交病害数据，无法下载");
            return;
        }
        byte[] bytes = taskSheetService.generateJglp05017WordDownloadBytes(taskId);
        String fileName = URLEncoder.encode("桥梁结构桥梁技术状况检测记录表_" + taskId + ".docx", "UTF-8");
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private void writeDocxInline(HttpServletResponse response, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 4) {
            writeRenderError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "生成的 Word 文件为空");
            return;
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "inline");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private void writeRenderError(HttpServletResponse response, int status, String message) {
        try {
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.sendError(status, message);
            }
        } catch (IOException ex) {
            log.error("writeRenderError failed", ex);
        }
    }
}
