package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.dto.CodeSegment;

import java.util.List;

/**
 * 构件管理Service接口
 */
public interface IComponentService {
    /**
     * 查询构件
     *
     * @param id 构件ID
     * @return 构件
     */
    public Component selectComponentById(Long id);

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
    public List<Component> selectComponentsByBiObjectId(String biObjectId);

    /**
     * 查询部件及其子部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    public List<Component> selectComponentsByBiObjectIdAndChildren(String biObjectId);

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
     * 批量删除构件
     *
     * @param ids 需要删除的构件ID
     * @return 结果
     */
    public int deleteComponentByIds(String ids);

    /**
     * 删除构件信息
     *
     * @param id 构件ID
     * @return 结果
     */
    public int deleteComponentById(Long id);

    /**
     * 批量生成构件
     *
     * @param biObjectId 部件ID
     * @param segments 编号片段列表
     * @return 结果
     */
    public int generateComponents(String biObjectId, List<CodeSegment> segments);
}