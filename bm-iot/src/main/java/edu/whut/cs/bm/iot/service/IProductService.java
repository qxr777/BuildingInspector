package edu.whut.cs.bm.iot.service;

import java.util.List;
import edu.whut.cs.bm.iot.domain.Product;

/**
 * 物联网产品Service接口
 * 
 * @author qixin
 * @date 2021-08-04
 */
public interface IProductService 
{
    /**
     * 查询物联网产品
     * 
     * @param id 物联网产品ID
     * @return 物联网产品
     */
    public Product selectProductById(Long id);

    /**
     * 查询物联网产品列表
     * 
     * @param product 物联网产品
     * @return 物联网产品集合
     */
    public List<Product> selectProductList(Product product);

    /**
     * 新增物联网产品
     * 
     * @param product 物联网产品
     * @return 结果
     */
    public int insertProduct(Product product);

    /**
     * 修改物联网产品
     * 
     * @param product 物联网产品
     * @return 结果
     */
    public int updateProduct(Product product);

    /**
     * 批量删除物联网产品
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteProductByIds(String ids);

    /**
     * 删除物联网产品信息
     * 
     * @param id 物联网产品ID
     * @return 结果
     */
    public int deleteProductById(Long id);
}
