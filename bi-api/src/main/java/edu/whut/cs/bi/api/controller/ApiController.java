package edu.whut.cs.bi.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api")
public class ApiController
{

    @Resource
    private IBuildingService buildingService;

    @Resource
    private IPropertyService propertyService;

    /**
     * 无权限访问
     *
     * @return
     */
    @GetMapping("/list")
    public AjaxResult list()
    {
        return AjaxResult.success("list success");
    }

    /**
     * 菜单权限 system:user:list
     */
    @GetMapping("/user/list")
    @RequiresPermissions("system:user:list")
    public AjaxResult userlist()
    {
        return AjaxResult.success("user list success");
    }

    /**
     * 角色权限 admin
     */
    @GetMapping("/role/list")
    @RequiresRoles("admin")
    public AjaxResult rolelist()
    {
        return AjaxResult.success("role list success");
    }

    /**
     * 通过Building ID 获取对应桥梁 property
     */
    @GetMapping("/property")
    @RequiresPermissions("biz:building:view")
    public AjaxResult property(@RequestParam("bid") Long bid) throws JsonProcessingException {
        if (bid == null) {
            return AjaxResult.error("参数错误");
        }
        Building building = buildingService.selectBuildingById(bid);
        return AjaxResult.success("查询成功", propertyService.selectPropertyTree(building.getRootPropertyId()));
    }
}