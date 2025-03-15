package edu.whut.cs.bm.iot.controller;

import java.io.IOException;
import java.util.List;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.file.FileUploadUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bm.iot.domain.Product;
import edu.whut.cs.bm.iot.service.IProductService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 物联网产品Controller
 *
 * @author qixin
 * @date 2021-08-04
 */
@Controller
@RequestMapping("/iot/product")
public class ProductController extends BaseController
{
    private String prefix = "iot/product";

    @Autowired
    private IProductService productService;

    @RequiresPermissions("iot:product:view")
    @GetMapping()
    public String product()
    {
        return prefix + "/product";
    }

    @GetMapping("/mini")
    public String miniProduct()
    {
        return prefix + "/miniproduct";
    }

    /**
     * 查询物联网产品列表
     */
    @RequiresPermissions("iot:product:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Product product)
    {
        startPage();
        List<Product> list = productService.selectProductList(product);
        return getDataTable(list);
    }

    /**
     * 导出物联网产品列表
     */
    @RequiresPermissions("iot:product:export")
    @Log(title = "物联网产品", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Product product)
    {
        List<Product> list = productService.selectProductList(product);
        ExcelUtil<Product> util = new ExcelUtil<Product>(Product.class);
        return util.exportExcel(list, "物联网产品数据");
    }

    /**
     * 新增物联网产品
     */
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增保存物联网产品
     */
    @RequiresPermissions("iot:product:add")
    @Log(title = "物联网产品", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Valid Product product)
    {
        return toAjax(productService.insertProduct(product));
    }

    /**
     * 修改物联网产品
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap)
    {
        Product product = productService.selectProductById(id);
        mmap.put("product", product);
        return prefix + "/edit";
    }

    /**
     * 修改保存物联网产品
     */
    @RequiresPermissions("iot:product:edit")
    @Log(title = "物联网产品", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Valid Product product) {
        return toAjax(productService.updateProduct(product));
    }

    /**
     * 更新物联网产品图片
     */
    @GetMapping("/updateIcon/{id}")
    public String updateIcon(@PathVariable("id") Long id, ModelMap mmap)
    {
        Product product = productService.selectProductById(id);
        mmap.put("product", product);
        return prefix + "/updateIcon";
    }

    /**
     * 更新保存物联网产品图片
     */
    @RequiresPermissions("iot:product:edit")
    @Log(title = "物联网产品", businessType = BusinessType.UPDATE)
    @PostMapping("/updateIcon")
    @ResponseBody
    public AjaxResult updateIconSave(@RequestParam("file") MultipartFile file, Long id) throws IOException {
        // 上传文件路径
        String filePath = RuoYiConfig.getUploadPath();
        // 上传并返回新文件名称
        String fileName = FileUploadUtils.upload(filePath, file);
        Product product = productService.selectProductById(id);
        product.setImgUrl(fileName);

        return toAjax(productService.updateProduct(product));
    }

    /**
     * 删除物联网产品
     */
    @RequiresPermissions("iot:product:remove")
    @Log(title = "物联网产品", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(productService.deleteProductByIds(ids));
    }
}
