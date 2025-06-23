package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import java.util.Set;

import edu.whut.cs.bi.biz.domain.BiObject;
import org.apache.ibatis.annotations.Param;

/**
 * 对象Mapper接口
 *
 * @author ruoyi
 * @date 2025-03-27
 */
public interface BiObjectMapper {
    /**
     * 查询对象
     *
     * @param id 对象主键
     * @return 对象
     */
    public BiObject selectBiObjectById(Long id);

    /**
     * 查询对象列表
     *
     * @param biObject 对象
     * @return 对象集合
     */
    public List<BiObject> selectBiObjectList(BiObject biObject);

    /**
     * 查询对象列表
     *
     * @param biObject 对象
     * @return 对象集合
     */
    public List<BiObject> selectBiObjectListByName(BiObject biObject);

    /**
     * 新增对象
     *
     * @param biObject 对象
     * @return 结果
     */
    public int insertBiObject(BiObject biObject);

    /**
     * 修改对象
     *
     * @param biObject 对象
     * @return 结果
     */
    public int updateBiObject(BiObject biObject);

    /**
     * 删除对象
     *
     * @param id 对象主键
     * @return 结果
     */
    public int deleteBiObjectById(Long id);

    /**
     * 批量删除对象
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBiObjectByIds(String[] ids);

    /**
     * 查询指定节点的所有子节点
     *
     * @param id 节点ID
     * @return 所有子节点
     */
    public List<BiObject> selectChildrenById(Long id);

    /**
     * 查询指定节点的所有子节点 (不包含叶子节点)
     *
     * @param id 节点ID
     * @return 所有子节点
     */
    public List<BiObject> selectChildrenByIdRemoveLeaf(Long id);


    public Boolean isLeafNode(Long id);

    /**
     * 根据根节点ID逻辑删除对象及其所有子节点
     *
     * @param rootObjectId 根节点ID
     * @param updateBy     更新人
     * @return 结果
     */
    public int logicDeleteByRootObjectId(@Param("rootObjectId") Long rootObjectId, @Param("updateBy") String updateBy);

    /**
     * 修改子元素关系
     *
     * @param objects 子元素
     * @return 结果
     */
    int updateObjectChildren(@Param("objects") List<BiObject> objects);

    /**
     * 获得每棵树的叶子节点
     *
     * @param rootId 子元素
     * @return 结果
     */
    List<BiObject> selectLeafNodes(Long rootId);

    /**
     * 获得每棵树的直接孩子节点
     *
     * @param parentId 子元素
     * @return 结果
     */
    List<BiObject> selectChildrenByParentId(Long parentId);

    /**
     * 根据对象ID获取其直接父节点
     *
     * @param id 对象ID
     * @return 直接父节点
     */
    public BiObject selectDirectParentById(Long id);

    /**
     * 批量更新对象
     *
     * @param objects 需要更新的对象列表
     * @return 结果
     */
    public int updateBiObjects(@Param("objects") List<BiObject> objects);

    /**
     * 批量更新子节点的ancestors
     *
     * @param rootObjectId       根节点ID
     * @param oldAncestorsPrefix 旧的ancestors前缀
     * @param newAncestorsPrefix 新的ancestors前缀
     * @param updateBy           更新人
     * @return 更新的记录数
     */
    public int batchUpdateAncestors(@Param("rootObjectId") Long rootObjectId,
                                    @Param("oldAncestorsPrefix") String oldAncestorsPrefix,
                                    @Param("newAncestorsPrefix") String newAncestorsPrefix,
                                    @Param("updateBy") String updateBy);

    /**
     * 批量插入BiObject对象
     *
     * @param biObjects 要插入的BiObject对象列表
     * @return 插入的记录数
     */
    public int batchInsertBiObjects(@Param("list") List<BiObject> biObjects);

    /**
     * 查询biObject树列表
     */
    List<BiObject> selectBiObjects(@Param("rootObjectIds") Set<Long> rootObjectIds);

}
