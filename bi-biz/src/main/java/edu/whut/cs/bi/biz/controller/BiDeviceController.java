package edu.whut.cs.bi.biz.controller;

import cn.hutool.core.convert.Convert;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import edu.whut.cs.bi.biz.domain.BiDevice;
import edu.whut.cs.bi.biz.service.BiDeviceService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Controller
@RequestMapping("/biz/device")
public class BiDeviceController extends BaseController {
    private String prefix = "/biz/device";

    @Resource
    private BiDeviceService biDeviceService;

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
    public String add(){
        return prefix + "/add";
    }

    @RequiresPermissions("biz:device:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(BiDevice biDevice){
        int flag = biDeviceService.insertBiDevice(biDevice);
        return toAjax(flag);
    }

    @RequiresPermissions("biz:device:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap){
        BiDevice biDevice = biDeviceService.selectBiDeviceById(id);
        mmap.put("biDevice",biDevice);
        return prefix + "/edit";
    }

    @RequiresPermissions("biz:device:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(BiDevice biDevice){
        int flag = biDeviceService.updateBiDevice(biDevice);
        return toAjax(flag);
    }

    @RequiresPermissions("biz:device:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids){
        int flag = biDeviceService.deleteBiDeviceByIds(Convert.toStrArray(ids));
        return toAjax(flag);
    }

}
