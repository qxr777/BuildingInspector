package edu.whut.cs.bi.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ruoyi.common.core.domain.Ztree;
import edu.whut.cs.bi.biz.domain.Property;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 属性Service接口
 *
 * @author qixin
 * @date 2021-08-10
 */
public interface IPropertyService {
    /**
     * 查询属性
     *
     * @param id 属性ID
     * @return 属性
     */
    Property selectPropertyById(Long id);

    /**
     * 读取json文件, 并保存到数据库中
     *
     * @param file
     * @param property
     * @param buildingId
     * @return
     */
    Boolean readJsonFile(MultipartFile file, Property property, Long buildingId);

    /**
     * 查询属性列表
     *
     * @param property 属性
     * @return 属性集合
     */
    List<Property> selectPropertyList(Property property);

    /**
     * 新增属性
     *
     * @param property 属性
     * @return 结果
     */
    int insertProperty(Property property);

    /**
     * 修改属性
     *
     * @param property 属性
     * @return 结果
     */
    int updateProperty(Property property);

    /**
     * 批量删除属性
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deletePropertyByIds(String ids);

    /**
     * 删除属性信息
     *
     * @param id 属性ID
     * @return 结果
     */
    int deletePropertyById(Long id);

    /**
     * 查询属性树列表
     *
     * @return 所有属性信息
     */
    List<Ztree> selectPropertyTree();

    /**
     * 得到祖级对象名层次
     * 
     * @param objectId
     * @return
     */
    String getAncestorNames(Long objectId);



    /**
     * api 提供给前端的属性树
     *
     * @param rootId
     * @return
     */
    public Property selectPropertyTree(Long rootId);
}
