package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Component;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 构件管理Mapper接口
 */
public interface ComponentMapper {
    /**
     * 查询构件
     *
     * @param id 构件ID
     * @return 构件
     */
    public Component selectComponentById(Long id);

    /**
     * 查询构件
     *
     * @param id 构件ID
     * @return 构件
     */
    public Component selectComponentByIdApi(Long id);

    /**
     * 查询构件列表
     *
     * @param component 构件
     * @return 构件集合
     */
    public List<Component> selectComponentList(Component component);

    /**
     * 查询部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    public List<Component> selectComponentsByBiObjectId(Long biObjectId);

    /**
     * 查询部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    public List<Component> selectComponentsByBiObjectIdApi(Long biObjectId);


    /**
     * 查询部件及其子部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    public List<Component> selectComponentsByBiObjectIdAndChildren(Long biObjectId);

    /**
     * 新增构件
     *
     * @param component 构件
     * @return 结果
     */
    public int insertComponent(Component component);

    /**
     * 修改构件
     *
     * @param component 构件
     * @return 结果
     */
    public int updateComponent(Component component);

    /**
     * 删除构件
     *
     * @param id 构件ID
     * @return 结果
     */
    public int deleteComponentById(Long id);

    /**
     * 批量删除构件
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteComponentByIds(String[] ids);

    /**
     * 批量插入构件
     *
     * @param components 构件集合
     * @return 结果
     */
    public int batchInsertComponents(@Param("components") List<Component> components);

    /**
     * 批量更新构件
     *
     * @param components 构件集合
     * @return 结果
     */
    public int batchUpdateComponents(@Param("components") List<Component> components);

    /**
     * 查询构件
     *
     * @param component 构件
     * @return 构件
     */
    Component selectComponent(Component component);


    /**
     * 批量插入构件
     *
     * @param components 构件集合
     * @return 结果
     */
    public int batchAddComponents(@Param("components") Set<Component> components);

    /**
     * 根据ID列表批量查询构件
     *
     * @param ids 构件ID列表
     * @return 构件列表
     */
    public List<Component> selectComponentsByIds(List<Long> ids);

}