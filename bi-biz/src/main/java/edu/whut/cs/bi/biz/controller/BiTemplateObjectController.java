package edu.whut.cs.bi.biz.controller;

import java.util.List;

import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.vo.TemplateDiseaseTypeVO;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.core.domain.Ztree;

/**
 * 桥梁构件模版Controller
 *
 * @author wanzheng
 * @date 2025-04-02
 */
@Controller
@RequestMapping("/biz/template_object")
public class BiTemplateObjectController extends BaseController {
    private String prefix = "biz/template_object";

    @Autowired
    private IBiTemplateObjectService biTemplateObjectService;

    @RequiresPermissions("biz:template_object:view")
    @GetMapping()
    public String template_object(ModelMap mmap) {
        BiTemplateObject query = new BiTemplateObject();
        query.setParentId(0L);
        List<BiTemplateObject> rootList = biTemplateObjectService.selectBiTemplateObjectList(query);
        mmap.put("rootList", rootList);
        return prefix + "/template_object";
    }

    /**
     * 查询桥梁构件模版树列表
     */
    @RequiresPermissions("biz:template_object:list")
    @PostMapping("/list")
    @ResponseBody
    public List<BiTemplateObject> list(BiTemplateObject biTemplateObject) {
        List<BiTemplateObject> list;
        if (biTemplateObject != null && biTemplateObject.getId() != null) {
            // 如果指定了parentId，则返回该节点及其所有子节点
            list = biTemplateObjectService.selectChildrenById(biTemplateObject.getId());
            list.add(0,biTemplateObject);
        } else {
            // 否则返回所有节点
            list = biTemplateObjectService.selectBiTemplateObjectList(biTemplateObject);
        }
        return list;
    }

    /**
     * 导出桥梁构件模版列表
     */
    @RequiresPermissions("biz:template_object:export")
    @Log(title = "桥梁构件模版", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(BiTemplateObject biTemplateObject) {
        List<BiTemplateObject> list = biTemplateObjectService.selectBiTemplateObjectList(biTemplateObject);
        ExcelUtil<BiTemplateObject> util = new ExcelUtil<BiTemplateObject>(BiTemplateObject.class);
        return util.exportExcel(list, "桥梁构件模版数据");
    }

    /**
     * 新增桥梁构件模版
     */
    @GetMapping(value = {"/add/{id}", "/add/"})
    public String add(@PathVariable(value = "id", required = false) Long id, ModelMap mmap) {
        if (StringUtils.isNotNull(id)) {
            BiTemplateObject biTemplateObject = biTemplateObjectService.selectBiTemplateObjectById(id);
            mmap.put("biTemplateObject", biTemplateObject);
        }
        return prefix + "/add";
    }

    /**
     * 新增保存桥梁构件模版
     */
    @RequiresPermissions("biz:template_object:add")
    @Log(title = "桥梁构件模版", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(BiTemplateObject biTemplateObject) {
        biTemplateObject.setCreateBy(ShiroUtils.getLoginName());
        return toAjax(biTemplateObjectService.insertBiTemplateObject(biTemplateObject));
    }

    /**
     * 修改桥梁构件模版
     */
    @RequiresPermissions("biz:template_object:edit")
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        BiTemplateObject biTemplateObject = biTemplateObjectService.selectBiTemplateObjectById(id);
        mmap.put("biTemplateObject", biTemplateObject);
        return prefix + "/edit";
    }

    /**
     * 修改保存桥梁构件模版
     */
    @RequiresPermissions("biz:template_object:edit")
    @Log(title = "桥梁构件模版", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(BiTemplateObject biTemplateObject) {
        biTemplateObject.setUpdateBy(ShiroUtils.getLoginName());
        return toAjax(biTemplateObjectService.updateBiTemplateObject(biTemplateObject));
    }

    /**
     * 删除
     */
    @RequiresPermissions("biz:template_object:remove")
    @Log(title = "桥梁构件模版", businessType = BusinessType.DELETE)
    @GetMapping("/remove/{id}")
    @ResponseBody
    public AjaxResult remove(@PathVariable("id") Long id) {
        if (biTemplateObjectService.hasChildByIds(id.toString())) {
            return error("存在子节点,不允许删除");
        }
        return toAjax(biTemplateObjectService.deleteBiTemplateObjectById(id));
    }

    /**
     * 选择桥梁构件模版树
     */
    @GetMapping(value = {"/selectTemplate_objectTree/{id}", "/selectTemplate_objectTree/"})
    public String selectTemplate_objectTree(@PathVariable(value = "id", required = false) Long id, ModelMap mmap) {
        if (StringUtils.isNotNull(id)) {
            mmap.put("biTemplateObject", biTemplateObjectService.selectBiTemplateObjectById(id));
        }
        return prefix + "/tree";
    }

    /**
     * 加载桥梁构件模版树列表
     */
    @GetMapping("/treeData")
    @ResponseBody
    public List<Ztree> treeData() {
        List<Ztree> ztrees = biTemplateObjectService.selectBiTemplateObjectTree();
        return ztrees;
    }

    /**
     * 选择病害类型页面
     */
    @RequiresPermissions("biz:template_object:edit")
    @GetMapping("/selectDiseaseType/{templateObjectId}")
    public String selectDiseaseType(@PathVariable("templateObjectId") Long templateObjectId, ModelMap mmap) {
        mmap.put("templateObjectId", templateObjectId);
        return prefix + "/selectDiseaseType";
    }

    /**
     * 添加单个病害类型
     */
    @RequiresPermissions("biz:template_object:edit")
    @Log(title = "桥梁构件模版", businessType = BusinessType.INSERT)
    @PostMapping("/addTemplateDiseaseType")
    @ResponseBody
    public AjaxResult addTemplateDiseaseType(Long templateObjectId, Long diseaseTypeId) {
        return toAjax(biTemplateObjectService.insertTemplateDiseaseType(templateObjectId, diseaseTypeId));
    }

    /**
     * 取消单个病害类型
     */
    @RequiresPermissions("biz:template_object:edit")
    @Log(title = "桥梁构件模版", businessType = BusinessType.DELETE)
    @PostMapping("/cancelTemplateDiseaseType")
    @ResponseBody
    public AjaxResult cancelTemplateDiseaseType(Long templateObjectId, Long diseaseTypeId) {
        return toAjax(biTemplateObjectService.deleteTemplateDiseaseType(templateObjectId, diseaseTypeId));
    }

    /**
     * 批量添加病害类型
     */
    @RequiresPermissions("biz:template_object:edit")
    @Log(title = "桥梁构件模版", businessType = BusinessType.INSERT)
    @PostMapping("/batchAddTemplateDiseaseType")
    @ResponseBody
    public AjaxResult batchAddTemplateDiseaseType(Long templateObjectId, String diseaseTypeIds) {
        return toAjax(biTemplateObjectService.batchInsertTemplateDiseaseType(templateObjectId, diseaseTypeIds));
    }

    /**
     * 批量取消病害类型
     */
    @RequiresPermissions("biz:template_object:edit")
    @Log(title = "桥梁构件模版", businessType = BusinessType.DELETE)
    @PostMapping("/batchCancelTemplateDiseaseType")
    @ResponseBody
    public AjaxResult batchCancelTemplateDiseaseType(Long templateObjectId, String diseaseTypeIds) {
        return toAjax(biTemplateObjectService.batchDeleteTemplateDiseaseType(templateObjectId, diseaseTypeIds));
    }

    /**
     * 获取病害类型列表
     */
    @RequiresPermissions("biz:template_object:edit")
    @PostMapping("/listDiseaseType")
    @ResponseBody
    public TableDataInfo listDiseaseType(TemplateDiseaseTypeVO diseaseType, Long templateObjectId) {
        startPage();
        List<TemplateDiseaseTypeVO> list = biTemplateObjectService.selectDiseaseTypeVOList(diseaseType, templateObjectId);
        return getDataTable(list);
    }

    /**
     * 导出模板文件
     */
    @RequiresPermissions("biz:template_object:export")
    @Log(title = "桥梁构件模版", businessType = BusinessType.EXPORT)
    @GetMapping("/exportTemplateFiles")
    public ResponseEntity<byte[]> exportTemplateFiles() {
        byte[] zipBytes = biTemplateObjectService.exportTemplateFiles();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "template_files.zip");

        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }
}
