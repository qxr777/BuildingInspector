package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.service.ITaskService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 任务Controller
 *
 * @author chenwenqi
 * @date 2025-04-09
 */
@Controller
@RequestMapping("/biz/task")
public class TaskController extends BaseController {
    private String prefix = "biz/task";

    @Resource
    private ITaskService taskService;

    @RequiresPermissions("biz:task:view")
    @GetMapping("/{select}")
    public String task(@PathVariable("select") String select, ModelMap mmap) {
        mmap.put("select", select);
        return prefix + "/task";
    }

    /**
     * 查询任务列表
     */
    @RequiresPermissions("biz:task:list")
    @PostMapping("/list/{select}")
    @ResponseBody
    public TableDataInfo list(@PathVariable("select") String select, Task task) {
        // 权限区分
        task.setSelect(select);

        startPage();
        List<Task> list = taskService.selectTaskList(task);
        return getDataTable(list);
    }

    /**
     * 修改任务
     */
    @RequiresPermissions("biz:project:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Task task = taskService.selectTaskById(id);

        mmap.put("task", task);
        return prefix + "/edit";
    }

    /**
     * 修改任务
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "任务", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult changeStatus(Task task) {
        task.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(taskService.updateTask(task));
    }

    /**
     * 新增任务
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "项目-任务", businessType = BusinessType.INSERT)
    @PostMapping("/addProjectBuilding")
    @ResponseBody
    public AjaxResult addProjectBuilding(Task task) {
        task.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(taskService.insertTask(task));
    }

    /**
     * 批量新增任务
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "项目-任务", businessType = BusinessType.INSERT)
    @PostMapping("/batchAddProjectBuilding")
    @ResponseBody
    public AjaxResult batchAddProjectBuilding(Long projectId, @RequestParam List<Long> buildingIds) {

        return toAjax(taskService.batchInsertTasks(projectId, buildingIds));
    }

    /**
     * 删除项目桥梁
     */
    @RequiresPermissions("biz:project:remove")
    @Log(title = "项目-任务", businessType = BusinessType.DELETE)
    @PostMapping("/cancelProjectBuilding")
    @ResponseBody
    public AjaxResult cancelProjectBuilding(Long projectId, Long buildingId) {
        return toAjax(taskService.removeTask(projectId, buildingId));
    }

    /**
     * 批量新增项目桥梁
     */
    @RequiresPermissions("biz:project:remove")
    @Log(title = "项目-任务", businessType = BusinessType.DELETE)
    @PostMapping("/batchCancelProjectBuilding")
    @ResponseBody
    public AjaxResult batchCancelProjectBuilding(Long projectId, @RequestParam List<Long> buildingIds) {

        return toAjax(taskService.batchRemoveTasks(projectId, buildingIds));
    }

    /**
     * 病害列表
     */
    @RequiresPermissions("biz:disease:view")
    @GetMapping("/inspect/{taskId}")
    public String userslist(@PathVariable("taskId") Long taskId, ModelMap mmap)
    {
        Task task = taskService.selectTaskById(taskId);

        mmap.put("task", task);
        return "biz/disease/disease";
    }
}
