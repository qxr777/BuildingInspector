package edu.whut.cs.bi.biz.controller;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author:wanzheng
 * @Date:2025/3/20 22:18
 * @Description:桥梁构件
 **/

@Controller
@RequestMapping("/system/biobject")
public class BiObjectController extends BaseController {
    private String prefix = "system/biobject";

    @Autowired
    private IBiObjectService biObjectService;


    @RequiresPermissions("system:biobject:view")
    @GetMapping()
    public String biObject(ModelMap mmap) {
        // 获取桥梁列表（顶级节点）
        List<BiObject> bridges = biObjectService.selectBridges();
        mmap.put("bridges", bridges);
        return prefix + "/index";
    }

    /**
     * 新增桥梁构件
     */
    @GetMapping("/add/{parentId}")
    public String add(@PathVariable("parentId") Long parentId, ModelMap mmap) {
        // 如果parentId为-1，表示新增顶级节点（桥梁）
        if (parentId == -1) {
            BiObject biObject = new BiObject();
            biObject.setParentId(0L);
            biObject.setAncestors("0");
            mmap.put("biObject", biObject);
            return prefix + "/add";
        }

        // 否则是新增子节点
        BiObject parentObject = biObjectService.selectBiObjectById(parentId);

        mmap.put("biObject", parentObject);
        // 获取桥梁ID（从ancestors中获取第一个ID，也就是桥梁ID）
        if (parentObject.getAncestors() != null) {
            String[] ancestors = parentObject.getAncestors().split(",");
            if (ancestors.length > 1) {  // 因为ancestors格式是 "0,桥梁ID,..."
                Long bridgeId = Long.parseLong(ancestors[1]);
                mmap.put("bridgeId", bridgeId);
            } else if (parentObject.getParentId() == 0) {  // 如果是桥梁本身
                mmap.put("bridgeId", parentObject.getId());
            }
        }
        return prefix + "/add";
    }

    /**
     * 新增保存桥梁构件
     */
    @RequiresPermissions("system:biobject:add")
    @Log(title = "桥梁构件", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(BiObject biObject) {
        if (biObject.getParentId() == 0) {
            biObject.setAncestors("0");
            return toAjax(biObjectService.insertBiObject(biObject));
        }
        // 获取父节点信息
        BiObject parentObject = biObjectService.selectBiObjectById(biObject.getParentId());
        if (parentObject == null) {
            return error("父节点不存在");
        }

        // 如果父节点不为"正常"状态,则不允许新增子节点
        if (!"0".equals(parentObject.getStatus())) {
            return error("父节点已停用，不允许新增子节点");
        }
        biObject.setAncestors(parentObject.getAncestors() + "," + parentObject.getId());
        return toAjax(biObjectService.insertBiObject(biObject));
    }

    /**
     * 修改桥梁构件
     */
    @RequiresPermissions("system:biobject:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        BiObject biObject = biObjectService.selectBiObjectById(id);
        mmap.put("biObject", biObject);
        // 从ancestors中获取桥梁ID（第二个值）
        if (biObject != null && biObject.getAncestors() != null) {
            String[] ancestors = biObject.getAncestors().split(",");
            if (ancestors.length > 1) {  // 因为ancestors格式是 "0,桥梁ID,..."
                Long bridgeId = Long.parseLong(ancestors[1]);
                mmap.put("bridgeId", bridgeId);
            } else if (biObject.getParentId() == 0) {  // 如果是桥梁本身
                mmap.put("bridgeId", biObject.getId());
            }
        }
        return prefix + "/edit";
    }

    /**
     * 修改保存桥梁构件
     */
    @RequiresPermissions("system:biobject:edit")
    @Log(title = "桥梁构件", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(BiObject biObject) {
        return toAjax(biObjectService.updateBiObject(biObject));
    }

    /**
     * 删除桥梁构件
     */
    @RequiresPermissions("system:biobject:remove")
    @Log(title = "桥梁构件", businessType = BusinessType.DELETE)
    @GetMapping("/remove/{ids}")
    @ResponseBody
    public AjaxResult remove(@PathVariable String ids) {
        return toAjax(biObjectService.deleteBiObjectByIds(ids));
    }

    /**
     * 选择桥梁构件树
     */
    @GetMapping("/selectBiObjectTree/{id}")
    public String selectBiObjectTree(@PathVariable("id") Long id, Long bridgeId, ModelMap mmap) {
        if (id != null) {
            BiObject biObject = biObjectService.selectBiObjectById(id);
            if (id == 0) {
                // 如果是新增桥梁，创建一个虚拟的根节点
                biObject = new BiObject();
                biObject.setId(0L);
                biObject.setName("主目录");
                biObject.setParentId(-1L);  // 使用-1作为虚拟根节点的parentId
            }
            mmap.put("biObject", biObject);
        }

        if (bridgeId == null) {
            // 如果没有指定桥梁ID，获取第一个桥梁
            List<BiObject> bridges = biObjectService.selectBridges();
            if (!bridges.isEmpty()) {
                bridgeId = bridges.get(0).getId();
            }
        }
        mmap.put("bridgeId", bridgeId);
        return prefix + "/tree";
    }

    /**
     * 查询桥梁构件树列表
     */
    @RequiresPermissions("system:biobject:list")
    @PostMapping("/list")
    @ResponseBody
    public List<BiObject> list(BiObject biObject) {
        // 如果是空表或没有传入ancestors，返回空列表
        if (biObject == null || !StringUtils.hasText(biObject.getAncestors())) {
            return new ArrayList<>();
        }

        try {
            String[] ancestors = biObject.getAncestors().split(",");
            // 确保ancestors数组至少有两个元素且第二个元素不为空
            if (ancestors.length > 1 && StringUtils.hasText(ancestors[1])) {
                Long bridgeId = Long.parseLong(ancestors[1]);
                List<BiObject> list = biObjectService.selectComponentsByBridgeId(bridgeId);

                // 应用搜索条件
                if (list != null && !list.isEmpty()) {
                    return list.stream().filter(item -> {
                        boolean match = true;
                        // 构件名称
                        if (StringUtils.hasText(biObject.getName())) {
                            match = match && item.getName().contains(biObject.getName());
                        }
                        // 构件状态
                        if (StringUtils.hasText(biObject.getStatus())) {
                            match = match && biObject.getStatus().equals(item.getStatus());
                        }
                        // 管理部门
                        if (StringUtils.hasText(biObject.getAdminDept())) {
                            match = match && item.getAdminDept() != null &&
                                    item.getAdminDept().contains(biObject.getAdminDept());
                        }
                        return match;
                    }).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            logger.error("解析ancestors出错", e);
        }
        return new ArrayList<>();
    }

    /**
     * 加载桥梁构件树
     */
    @GetMapping("/treeData")
    @ResponseBody
    public List<Ztree> treeData(@RequestParam(required = false) Long bridgeId) {
        List<BiObject> list;
        if (bridgeId != null) {
            // 如果指定了桥梁ID，查询该桥梁下的所有构件
            list = biObjectService.selectComponentsByBridgeId(bridgeId);
        } else {
            // 如果没有指定桥梁ID，获取所有构件
            BiObject query = new BiObject();
            list = biObjectService.selectBiObjectList(query);
        }

        List<Ztree> ztrees = new ArrayList<Ztree>();
        for (BiObject biObject : list) {
            Ztree ztree = new Ztree();
            ztree.setId(biObject.getId());
            ztree.setpId(biObject.getParentId());
            ztree.setName(biObject.getName());
            ztree.setTitle(biObject.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

    /**
     * 获取桥梁构件详细信息
     */
    @RequiresPermissions("system:biobject:query")
    @GetMapping("/{id}")
    @ResponseBody
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(biObjectService.selectBiObjectById(id));
    }


    /**
     * 显示导入页面
     */
    @GetMapping("/importData")
    public String importData() {
        return prefix + "/importData";
    }

    /**
     * 导入JSON数据（支持批量）
     */
    @RequiresPermissions("system:biobject:importJson")
    @Log(title = "桥梁构件", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    @ResponseBody
    public AjaxResult importData(@RequestBody List<String> jsonDataList) {
        String operName = ShiroUtils.getLoginName();
        List<String> messages = new ArrayList<>();

        for (String jsonData : jsonDataList) {
            try {
                String message = biObjectService.importData(jsonData, true, operName);
                messages.add(message);
            } catch (Exception e) {
                messages.add("导入失败：" + e.getMessage());
            }
        }

        return success(String.join("\n", messages));
    }

    /**
     * 导出JSON数据
     */
    @RequiresPermissions("system:biobject:export")
    @Log(title = "桥梁构件", businessType = BusinessType.EXPORT)
    @GetMapping("/exportData")
    @ResponseBody
    public AjaxResult exportData(@RequestParam Long bridgeId) {
        try {
            String jsonData = biObjectService.exportData(bridgeId);
            // 将字符串解析为JSON对象，然后重新格式化，以去除转义字符
            Object jsonObject = JSON.parse(jsonData);
            String formattedJson = JSON.toJSONString(jsonObject, true);
            return success(formattedJson);
        } catch (Exception e) {
            return error("导出失败：" + e.getMessage());
        }
    }

    /**
     * JSON导入模板
     */
    @GetMapping("/importTemplate")
    @ResponseBody
    public AjaxResult importTemplate() {
        // 返回示例的JSON模板
        String template = "{\n" + "  \"宜都长江大桥\": {\n" + "    \"主桥（钢桁梁悬索桥）\": {\n" + "      \"上部结构\": [\"加劲梁\", \"索塔\"],\n" + "      \"下部结构\": [\"锚碇\", \"索塔基础\"],\n" + "      \"桥面系\": [\"桥面铺装\", \"伸缩缝装置\"]\n" + "    }\n" + "  }\n" + "}";
        return success(template);
    }

    /**
     * 导出Excel数据
     */
    @RequiresPermissions("system:biobject:exportExcle")
    @Log(title = "桥梁构件", businessType = BusinessType.EXPORT)
    @PostMapping("/exportExcel")
    public void exportExcel(HttpServletResponse response, BiObject biObject) {
        List<BiObject> list = biObjectService.selectBiObjectList(biObject);
        ExcelUtil<BiObject> util = new ExcelUtil<BiObject>(BiObject.class);
        util.exportExcel(response, list, "桥梁构件数据");
    }

    /**
     * 导入Excel数据
     */
    @RequiresPermissions("system:biobject:importExcle")
    @Log(title = "桥梁构件", businessType = BusinessType.IMPORT)
    @PostMapping("/importExcel")
    public AjaxResult importExcel(MultipartFile file, boolean updateSupport) throws Exception {
        ExcelUtil<BiObject> util = new ExcelUtil<BiObject>(BiObject.class);
        List<BiObject> biObjectList = util.importExcel(file.getInputStream());
        String operName = (String) SecurityUtils.getSubject().getPrincipal();
        String message = biObjectService.importExcelData(biObjectList, updateSupport, operName);
        return success(message);
    }
}