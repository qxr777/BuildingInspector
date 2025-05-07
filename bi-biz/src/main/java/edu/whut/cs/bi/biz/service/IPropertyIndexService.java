package edu.whut.cs.bi.biz.service;


import com.ruoyi.common.core.domain.Ztree;
import edu.whut.cs.bi.biz.domain.Property;


import java.util.List;
import java.util.Map;

/**
 * 属性Service接口
 *
 * @author qixin
 * @date 2021-08-10
 */
public interface IPropertyIndexService {
    /**
     * 查询属性
     *
     * @param id 属性ID
     * @return 属性
     */
    Property selectPropertyById(Long id);


    /**
     * 查询属性列表
     *
     * @param property 属性
     * @return 属性集合
     */
     List<Property> selectPropertyList(Property property);

    /**
     * 根据name查询属性树结构
     *
     * @param name
     * @return
     */
    List<Ztree> selectPropertyTree(String name);

    /**
     * 根据id查询属性树结构
     *
     * @param rootPropertyId
     * @return
     */
    List<Ztree> selectPropertyTree(Long rootPropertyId);
}
