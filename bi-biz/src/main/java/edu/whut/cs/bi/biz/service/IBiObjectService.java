package edu.whut.cs.bi.biz.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import edu.whut.cs.bi.biz.domain.BiObject;
import com.ruoyi.common.core.domain.Ztree;

/**
 * 对象Service接口
 *
 * @author ruoyi
 * @date 2025-03-27
 */
public interface IBiObjectService {
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
     * 批量删除对象
     *
     * @param ids 需要删除的对象主键集合
     * @return 结果
     */
    public int deleteBiObjectByIds(String ids);

    /**
     * 删除对象信息
     *
     * @param id 对象主键
     * @return 结果
     */
    public int deleteBiObjectById(Long id);

    /**
     * 查询对象树列表
     *
     * @return 所有对象信息
     */
    public List<Ztree> selectBiObjectTree(Long rootObjectId);

    /**
     * 查询根节点及其所有子节点
     *
     * @param rootId 根节点ID
     * @return 根节点及其所有子节点列表
     */
    public List<BiObject> selectBiObjectAndChildren(Long rootId);

    /**
     * 查询根节点及其所有子节点 移除叶子节点
     *
     * @param rootId 根节点ID
     * @return 根节点及其所有子节点列表
     */
    public List<BiObject> selectBiObjectAndChildrenRemoveLeaf(Long rootId);

    /**
     * 根据根节点ID逻辑删除对象及其所有子节点
     *
     * @param rootObjectId 根节点ID
     * @param updateBy     更新人
     * @return 结果
     */
    public int logicDeleteByRootObjectId(Long rootObjectId, String updateBy);

    /**
     * 更新子节点的ancestors
     *
     * @param parentId  父节点ID
     * @param ancestors 父节点的ancestors
     * @return 结果
     */
    public int updateChildrenAncestors(Long parentId, String ancestors);

    /**
     * 查询指定部件树下的所有叶子节点
     *
     * @param rootId 根节点ID
     * @return 叶子节点列表
     */
    public List<BiObject> selectLeafNodes(Long rootId);

    /**
     * 查询指定节点下的所有孩子节点
     *
     * @param parentId 根节点ID
     * @return 叶子节点列表
     */
    public List<BiObject> selectDirectChildrenByParentId(Long parentId);

    /**
     * 查询指定节点的直接父节点
     *
     * @param id 根节点ID
     * @return BiObject
     */
    public BiObject selectDirectParentById(Long id);

    String bridgeStructureJson(Long id) throws Exception;

    /**
     * 递归更新BiObject树结构
     *
     * @param biObject 当前处理的节点
     * @return 更新的节点数量
     */
    public int updateBiObjectTreeRecursively(BiObject biObject ,Map<String, Path> extractedFiles) ;

    /**
     * 判断是否为叶子节点
     *
     * @param id
     * @return
     */
    public Boolean isLeafNode(Long id);

    /**
     * 批量更新子节点的ancestors
     *
     * @param rootObjectId       根节点ID
     * @param oldAncestorsPrefix 旧的ancestors前缀
     * @param newAncestorsPrefix 新的ancestors前缀
     * @param updateBy           更新人
     * @return 更新的记录数
     */
    public int batchUpdateAncestors(Long rootObjectId, String oldAncestorsPrefix, String newAncestorsPrefix, String updateBy);

    /**
     * 批量插入BiObject对象
     *
     * @param biObjects 要插入的BiObject对象列表
     * @return 插入的记录数
     */
    public int batchInsertBiObjects(List<BiObject> biObjects);

    /**
     * 查询部件树（至三级节点）
     *
     * @param rootObjectId
     * @return
     */
    List<Ztree> selectBiObjectThreeLevelTree(Long rootObjectId);

    /**
     * 一键修正权重
     *
     * @param rootObjectId 根对象ID
     * @return 更新的记录数
     */
    int correctAllWeights(Long rootObjectId);
}
