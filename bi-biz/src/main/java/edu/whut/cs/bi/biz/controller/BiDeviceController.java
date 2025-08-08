package edu.whut.cs.bi.biz.controller;

import cn.hutool.core.convert.Convert;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import edu.whut.cs.bi.biz.domain.BiDevice;
import edu.whut.cs.bi.biz.domain.BiProjectDevice;
import edu.whut.cs.bi.biz.service.BiDeviceService;
import edu.whut.cs.bi.biz.service.BiProjectDeviceService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/biz/device")
public class BiDeviceController extends BaseController {
    private String prefix = "/biz/device";

    @Resource
    private BiDeviceService biDeviceService;

    @Resource
    private BiProjectDeviceService biProjectDeviceService;

    @RequiresPermissions("biz:device:view")
    @GetMapping()
    public String device() {
        return prefix + "/device";
    }

    @RequiresPermissions("biz:device:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(BiDevice biDevice) {
        startPage();
        List<BiDevice> list = biDeviceService.selectBiDeviceList(biDevice);
        return getDataTable(list);
    }

    @RequiresPermissions("biz:device:add")
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    @RequiresPermissions("biz:device:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(BiDevice biDevice) {
        int flag = biDeviceService.insertBiDevice(biDevice);
        return toAjax(flag);
    }

    @RequiresPermissions("biz:device:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        BiDevice biDevice = biDeviceService.selectBiDeviceById(id);
        mmap.put("biDevice", biDevice);
        return prefix + "/edit";
    }

    @RequiresPermissions("biz:device:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(BiDevice biDevice) {
        int flag = biDeviceService.updateBiDevice(biDevice);
        return toAjax(flag);
    }

    @RequiresPermissions("biz:device:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        int flag = biDeviceService.deleteBiDeviceByIds(Convert.toStrArray(ids));
        return toAjax(flag);
    }

    @RequiresPermissions("biz:project:edit")
    @GetMapping("/list/{projectId}")
    public String assignUsers(@PathVariable("projectId") String projectId, ModelMap mmap) {
        mmap.put("projectId", projectId);
        return "biz/project/selectDevice";
    }

    @RequiresPermissions("biz:project:edit")
    @PostMapping("/listVO")
    @ResponseBody
    public TableDataInfo listVO(BiDevice biDevice, @RequestParam(value = "projectId", required = false) Long projectId, 
                               @RequestParam(value = "isSelected", required = false) String isSelected) {
        startPage();
        
        // 如果指定了项目ID，需要查询项目设备关联信息
        if (projectId != null) {
            // 查询设备列表
            List<BiDevice> list = biDeviceService.selectBiDeviceList(biDevice);
            
            // 设置isSelected字段
            for (BiDevice device : list) {
                boolean isDeviceSelected = biProjectDeviceService.checkBiProjectDeviceExists(projectId, device.getId());
                device.setIsSelected(isDeviceSelected);
            }
            
            // 如果指定了isSelected参数，需要过滤结果
            if (isSelected != null && !isSelected.trim().isEmpty()) {
                boolean selected = "1".equals(isSelected.trim());
                list = list.stream().filter(device -> selected == device.getIsSelected()).collect(Collectors.toList());
            }
            
            return getDataTable(list);
        } else {
            // 普通设备查询
            List<BiDevice> list = biDeviceService.selectBiDeviceList(biDevice);
            return getDataTable(list);
        }
    }

    @RequiresPermissions("biz:project:edit")
    @PostMapping("/addProjectDevice")
    @ResponseBody
    public AjaxResult addProjectDevice(Long projectId, Long deviceId) {
        // 检查是否已经存在关联
        if (biProjectDeviceService.checkBiProjectDeviceExists(projectId, deviceId)) {
            return AjaxResult.error("该设备已经添加到项目中");
        }
        
        // 创建项目设备关联
        BiProjectDevice biProjectDevice = new BiProjectDevice();
        biProjectDevice.setProjectId(projectId);
        biProjectDevice.setDeviceId(deviceId);
        
        int flag = biProjectDeviceService.insertBiProjectDevice(biProjectDevice);
        return toAjax(flag);
    }

    @RequiresPermissions("biz:project:edit")
    @PostMapping("/cancelProjectDevice")
    @ResponseBody
    public AjaxResult cancelProjectDevice(Long projectId, Long deviceId) {
        // 删除项目设备关联
        int flag = biProjectDeviceService.deleteBiProjectDeviceByProjectAndDevice(projectId, deviceId);
        return toAjax(flag);
    }

    @RequiresPermissions("biz:project:edit")
    @PostMapping("/batchAddProjectDevice")
    @ResponseBody
    public AjaxResult batchAddProjectDevice(Long projectId, String deviceIds) {
        if (deviceIds == null || deviceIds.trim().isEmpty()) {
            return AjaxResult.error("请选择要添加的设备");
        }
        
        String[] deviceIdArray = deviceIds.split(",");
        int successCount = 0;
        int failCount = 0;
        
        for (String deviceIdStr : deviceIdArray) {
            try {
                Long deviceId = Long.parseLong(deviceIdStr.trim());
                
                // 检查是否已经存在关联
                if (!biProjectDeviceService.checkBiProjectDeviceExists(projectId, deviceId)) {
                    // 创建项目设备关联
                    BiProjectDevice biProjectDevice = new BiProjectDevice();
                    biProjectDevice.setProjectId(projectId);
                    biProjectDevice.setDeviceId(deviceId);
                    
                    if (biProjectDeviceService.insertBiProjectDevice(biProjectDevice) > 0) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } else {
                    failCount++; // 已存在，算作失败
                }
            } catch (NumberFormatException e) {
                failCount++;
            }
        }
        
        if (failCount == 0) {
            return AjaxResult.success("批量添加成功，共添加 " + successCount + " 个设备");
        } else if (successCount == 0) {
            return AjaxResult.error("批量添加失败，可能设备已存在或参数错误");
        } else {
            return AjaxResult.success("批量添加完成，成功 " + successCount + " 个，失败 " + failCount + " 个");
        }
    }

    @RequiresPermissions("biz:project:edit")
    @PostMapping("/batchCancelProjectDevice")
    @ResponseBody
    public AjaxResult batchCancelProjectDevice(Long projectId, String deviceIds) {
        if (deviceIds == null || deviceIds.trim().isEmpty()) {
            return AjaxResult.error("请选择要取消的设备");
        }
        
        String[] deviceIdArray = deviceIds.split(",");
        int successCount = 0;
        int failCount = 0;
        
        for (String deviceIdStr : deviceIdArray) {
            try {
                Long deviceId = Long.parseLong(deviceIdStr.trim());
                
                // 删除项目设备关联
                if (biProjectDeviceService.deleteBiProjectDeviceByProjectAndDevice(projectId, deviceId) > 0) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (NumberFormatException e) {
                failCount++;
            }
        }
        
        if (failCount == 0) {
            return AjaxResult.success("批量取消成功，共取消 " + successCount + " 个设备");
        } else if (successCount == 0) {
            return AjaxResult.error("批量取消失败");
        } else {
            return AjaxResult.success("批量取消完成，成功 " + successCount + " 个，失败 " + failCount + " 个");
        }
    }

}
