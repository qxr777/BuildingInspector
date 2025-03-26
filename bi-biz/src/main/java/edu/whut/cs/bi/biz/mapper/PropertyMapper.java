package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Property;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 属性Mapper接口
 */
public interface PropertyMapper {
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
     * 根据ID查询所有子对象
     *
     * @param objectId 对象ID
     * @return 对象列表
     */
     List<Property> selectChildrenObjectById(Long objectId);

    /**
     * 新增属性
     *
     * @param property 属性
     * @return 结果
     */
    int insertProperty(Property property);

    /**
     * 根据父节点的id值，得到当前的orderNum
     *
     * @param id
     */
    int getOrderNum(Long id);

    /**
     * 修改属性
     *
     * @param property 属性
     * @return 结果
     */
     int updateProperty(Property property);

    /**
     * 修改子元素关系
     *
     * @param objects 子元素
     * @return 结果
     */
     int updateObjectChildren(@Param("objects") List<Property> objects);

    /**
     * 删除属性
     *
     * @param id 属性ID
     * @return 结果
     */
     int deletePropertyById(Long id);

    /**
     * 通过id删除其子对象
     * @param objectId
     */
    void deleteObjectChildren(Long objectId);

    /**
     * 批量删除子对象
     *
     * @param ids
     */
    void deleteObjectChildrenByIds(@Param("ids") String[] ids);

    /**
     * 批量删除属性
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
     int deletePropertyByIds(String[] ids);

    /**
     * 获取父节点为空的节点个数
     *
     * @return
     */
    int getParentIdIsNullNum();
}
