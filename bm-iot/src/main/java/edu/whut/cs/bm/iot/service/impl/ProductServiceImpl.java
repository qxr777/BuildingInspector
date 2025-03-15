package edu.whut.cs.bm.iot.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import edu.whut.cs.bm.iot.domain.Channel;
import edu.whut.cs.bm.iot.mapper.ProductMapper;
import edu.whut.cs.bm.iot.domain.Product;
import edu.whut.cs.bm.iot.service.IProductService;
import com.ruoyi.common.core.text.Convert;

/**
 * 物联网产品Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-04
 */
@Service
public class ProductServiceImpl implements IProductService 
{
    @Autowired
    private ProductMapper productMapper;

    /**
     * 查询物联网产品
     * 
     * @param id 物联网产品ID
     * @return 物联网产品
     */
    @Override
    public Product selectProductById(Long id)
    {
        return productMapper.selectProductById(id);
    }

    /**
     * 查询物联网产品列表
     * 
     * @param product 物联网产品
     * @return 物联网产品
     */
    @Override
    public List<Product> selectProductList(Product product)
    {
        return productMapper.selectProductList(product);
    }

    /**
     * 新增物联网产品
     * 
     * @param product 物联网产品
     * @return 结果
     */
    @Transactional
    @Override
    public int insertProduct(Product product)
    {
        product.setCreateTime(DateUtils.getNowDate());
        int rows = productMapper.insertProduct(product);
        insertChannel(product);
        return rows;
    }

    /**
     * 修改物联网产品
     * 
     * @param product 物联网产品
     * @return 结果
     */
    @Transactional
    @Override
    public int updateProduct(Product product)
    {
        product.setUpdateTime(DateUtils.getNowDate());
        productMapper.deleteChannelByProductId(product.getId());
        insertChannel(product);
        return productMapper.updateProduct(product);
    }

    /**
     * 删除物联网产品对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteProductByIds(String ids)
    {
        productMapper.deleteChannelByProductIds(Convert.toStrArray(ids));
        return productMapper.deleteProductByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除物联网产品信息
     * 
     * @param id 物联网产品ID
     * @return 结果
     */
    @Override
    public int deleteProductById(Long id)
    {
        productMapper.deleteChannelByProductId(id);
        return productMapper.deleteProductById(id);
    }

    /**
     * 新增数据通道管理信息
     * 
     * @param product 物联网产品对象
     */
    public void insertChannel(Product product)
    {
        List<Channel> channelList = product.getChannelList();
        Long id = product.getId();
        if (StringUtils.isNotNull(channelList))
        {
            List<Channel> list = new ArrayList<Channel>();
            for (Channel channel : channelList)
            {
                channel.setProductId(id);
                list.add(channel);
            }
            if (list.size() > 0)
            {
                productMapper.batchChannel(list);
            }
        }
    }
}
