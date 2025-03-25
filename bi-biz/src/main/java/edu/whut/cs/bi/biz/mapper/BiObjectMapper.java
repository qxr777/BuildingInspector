package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.BiObject;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/3/20 22:20
 * @Description:
 **/
public interface BiObjectMapper
{
    /**
     * 查询桥梁构件
     *
     * @param id 桥梁构件主键
     * @return 桥梁构件
     */
    public BiObject selectBiObjectById(Long id);

    /**
     * 查询桥梁构件列表
     *
     * @param biObject 桥梁构件
     * @return 桥梁构件集合
     */
    public List<BiObject> selectBiObjectList(BiObject biObject);

    /**
     * 新增桥梁构件
     *
     * @param biObject 桥梁构件
     * @return 结果
     */
    public int insertBiObject(BiObject biObject);

    /**
     * 修改桥梁构件
     *
     * @param biObject 桥梁构件
     * @return 结果
     */
    public int updateBiObject(BiObject biObject);

    /**
     * 删除桥梁构件
     *
     * @param id 桥梁构件主键
     * @return 结果
     */
    public int deleteBiObjectById(Long id);

    /**
     * 批量删除桥梁构件
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBiObjectByIds(String[] ids);

    /**
     * 查询桥梁构件树结构
     *
     * @param parentId 父节点ID
     * @return 所有子节点
     */
    public List<BiObject> selectChildrenByParentId(Long parentId);

    /**
     * 根据ID查询所有子构件
     *
     * @param id 构件ID
     * @return 所有子构件
     */
    public List<BiObject> selectChildrenById(Long id);

    /**
     * 修改子元素关系
     *
     * @param biObjects 子元素
     * @return 结果
     */
    public int updateBiObjects(@Param("biObjects") List<BiObject> biObjects);

    /**
     * 根据ID查询所有父级构件
     *
     * @param id 构件ID
     * @return 所有父级构件
     */
    public List<BiObject> selectParentById(Long id);

    /**
     * 查询桥梁列表（顶级节点）
     *
     * @return 桥梁列表
     */
    public List<BiObject> selectBridges();

    /**
     * 查询桥梁下的所有构件
     *
     * @param bridgeId 桥梁ID
     * @return 构件列表
     */
    public List<BiObject> selectComponentsByBridgeId(Long bridgeId);
}