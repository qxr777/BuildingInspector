package edu.whut.cs.bm.iot.controller;

import java.util.Date;
import java.util.List;

import edu.whut.cs.bm.iot.vo.EmqXEventVo;
import edu.whut.cs.bm.iot.vo.MqttMessageVo;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bm.iot.domain.Device;
import edu.whut.cs.bm.iot.service.IDeviceService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

import javax.validation.Valid;

/**
 * 物联网设备Controller
 *
 * @author qixin
 * @date 2021-08-04
 */
@Controller
@RequestMapping("/iot/device")
public class DeviceController extends BaseController
{
    private String prefix = "iot/device";

    @Autowired
    private IDeviceService deviceService;

    @RequiresPermissions("iot:device:view")
    @GetMapping()
    public String device()
    {
        return prefix + "/device";
    }

    /**
     * 查询物联网设备列表
     */
    @RequiresPermissions("iot:device:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Device device)
    {
        startPage();
        List<Device> list = deviceService.selectDeviceList(device);
        return getDataTable(list);
    }

    /**
     * 导出物联网设备列表
     */
    @RequiresPermissions("iot:device:export")
    @Log(title = "物联网设备", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Device device)
    {
        List<Device> list = deviceService.selectDeviceList(device);
        ExcelUtil<Device> util = new ExcelUtil<Device>(Device.class);
        return util.exportExcel(list, "物联网设备数据");
    }

    /**
     * 新增物联网设备
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存物联网设备
     */
    @RequiresPermissions("iot:device:add")
    @Log(title = "物联网设备", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Device device)
    {
        return toAjax(deviceService.insertDevice(device));
    }

    /**
     * 修改物联网设备
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Device device = deviceService.selectDeviceById(id);
        mmap.put("device", device);
        return prefix + "/edit";
    }

    /**
     * 修改保存物联网设备
     */
    @RequiresPermissions("iot:device:edit")
    @Log(title = "物联网设备", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid Device device)
    {
        return toAjax(deviceService.updateDevice(device));
    }

    /**
     * 接收update_status消息，更新设备status
     */
    @PostMapping("/api/status")
    @ResponseBody
    public MqttMessageVo update(@RequestBody MqttMessageVo mqttMessageVo) {
        String deviceName = mqttMessageVo.getClientid().split("/")[1];
        Device queryDevice = new Device();
        queryDevice.setName(deviceName);
        List<Device> devices = deviceService.selectDeviceList(queryDevice);
        for (Device device : devices) {
            device.setDeviceStatus(mqttMessageVo.getPayload());
            device.setLastStatusUpdateAt(new Date());
            deviceService.updateDevice(device);
        }
        return mqttMessageVo;
    }

    /**
     * emqx 通知系统事件
     */
    @PostMapping("/api/connection")
    @ResponseBody
    public EmqXEventVo notify(@RequestBody EmqXEventVo emqXEventVo)
    {
        String deviceName = emqXEventVo.getClientid().split("/")[1];
        String event = emqXEventVo.getEvent();
        Device queryDevice = new Device();
        queryDevice.setName(deviceName);
        List<Device> devices = deviceService.selectDeviceList(queryDevice);
        for (Device device : devices) {
            switch (event) {
                case "client.connected" :
                    device.setConnected(1);
                    device.setConnectedAt(new Date());
                    break;
                case "client.disconnected" :
                    device.setConnected(0);
                    device.setDisconnectedAt(new Date());
                    break;
            }
            deviceService.updateDevice(device);
        }
        return emqXEventVo;
    }

    /**
     * 物联网设备指令
     */
    @GetMapping("/command/{id}")
    public String command(@PathVariable("id") Long id, ModelMap mmap)
    {
        Device device = deviceService.selectDeviceById(id);
        mmap.put("device", device);
        return prefix + "/command";
    }

    /**
     * 发送指令至物联网设备
     */
    @RequiresPermissions("iot:device:command")
    @Log(title = "物联网设备", businessType = BusinessType.OTHER)
    @PostMapping("/command")
    @ResponseBody
    public AjaxResult sendCommand(Device device, String command)
    {
        return toAjax(deviceService.commandDevice(device.getId(), command));
    }

    /**
     * 删除物联网设备
     */
    @RequiresPermissions("iot:device:remove")
    @Log(title = "物联网设备", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(deviceService.deleteDeviceByIds(ids));
    }
}
