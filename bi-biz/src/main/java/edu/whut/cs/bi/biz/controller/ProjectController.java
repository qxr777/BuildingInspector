package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.dto.ProjectUserAssignment;
import edu.whut.cs.bi.biz.service.IProjectService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 项目Controller
 *
 * @author chenwenqi
 * @date 2025-04-02
 */
@Controller
@RequestMapping("/biz/project")
public class ProjectController extends BaseController {
    private String prefix = "biz/project";

    @Autowired
    private IProjectService projectService;

    @RequiresPermissions("biz:project:view")
    @GetMapping("/{select}")
    public String project(@PathVariable("select") String select, ModelMap mmap) {
        mmap.put("select", select);
        return prefix + "/project";
    }

    /**
     * 查询项目列表
     */
    @RequiresPermissions("biz:project:list")
    @PostMapping("/list/{select}")
    @ResponseBody
    public TableDataInfo list(@PathVariable("select") String select, Project project) {
        // 供权限区分
        project.setSelect(select);

        List<Project> list = projectService.selectProjectList(project);
        return getDataTable(list);
    }

    /**
     * 导出项目列表
     */
    @RequiresPermissions("biz:project:export")
    @Log(title = "项目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Project project) {
        List<Project> list = projectService.selectProjectList(project);
        ExcelUtil<Project> util = new ExcelUtil<Project>(Project.class);
        return util.exportExcel(list, "项目数据");
    }

    /**
     * 新增项目
     */
    @RequiresPermissions("biz:project:add")
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存项目
     */
    @RequiresPermissions("biz:project:add")
    @Log(title = "项目", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Project project) {
        project.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(projectService.insertProject(project));
    }

    /**
     * 修改项目
     */
    @RequiresPermissions("biz:project:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        Project project = projectService.selectProjectVOById(id);

        mmap.put("project", project);
        return prefix + "/edit";
    }

    /**
     * 修改保存项目
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "项目", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Project project) {
        project.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(projectService.updateProject(project));
    }

    /**
     * 删除项目
     */
    @RequiresPermissions("biz:project:remove")
    @Log(title = "项目", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(projectService.deleteProjectByIds(ids));
    }

    /**
     * 选择承担单位部门树
     */
    @RequiresPermissions("biz:project:list")
    @GetMapping("/selectDeptTree")
    public String selectDeptTree()
    {
        return prefix + "/deptTree";
    }

    /**
     * 加载承担单位部门树
     */
    @RequiresPermissions("biz:project:list")
    @GetMapping("/deptTreeData")
    @ResponseBody
    public List<Ztree> deptTreeData()
    {
        List<Ztree> ztrees = projectService.selectDeptTree(new SysDept());
        return ztrees;
    }

    /**
     * 选择所属单位部门树
     */
    @RequiresPermissions("biz:project:list")
    @GetMapping("/selectOwnerDeptTree")
    public String selectOwnerDeptTree()
    {
        return prefix + "/ownerDeptTree";
    }

    /**
     * 加载承担单位部门树
     */
    @RequiresPermissions("biz:project:list")
    @GetMapping("/deptOwnerTreeData")
    @ResponseBody
    public List<Ztree> deptOwnerTreeData()
    {
        List<Ztree> ztrees = projectService.selectOwnerDeptTree(new SysDept());
        return ztrees;
    }

    /**
     * 项目安排人员
     */
    @RequiresPermissions("biz:project:edit")
    @GetMapping("/assignUsers/{projectId}")
    public String assignUsers(@PathVariable("projectId") String projectId, ModelMap mmap) {
        mmap.put("projectId", projectId);
        return prefix + "/assignUsers";
    }

    /**
     * 项目安排人员列表
     */
    @RequiresPermissions("system:user:list")
    @GetMapping("/usersList/{projectId}")
    @ResponseBody
    public List<SysUser> userslist(@PathVariable("projectId") Long projectId)
    {
        return projectService.selectUserList(projectId);
    }

    /**
     * 获取项目安排人员
     */
    @RequiresPermissions("biz:project:list")
    @PostMapping("/getAssignUsers")
    @ResponseBody
    public ProjectUserAssignment getAssignUsers(Long projectId)
    {
        return projectService.selectProjectUsers(projectId);
    }

    /**
     * 保存项目安排人员
     */
    @RequiresPermissions("biz:project:edit")
    @Log(title = "项目", businessType = BusinessType.UPDATE)
    @PostMapping("/saveAssignUsers")
    @ResponseBody
    public AjaxResult saveAssignUsers(ProjectUserAssignment assignment)
    {
        return toAjax(projectService.saveProjectUserAssignments(assignment));
    }

}
