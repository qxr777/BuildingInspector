package edu.whut.cs.bi.biz.service;

import java.util.List;
import edu.whut.cs.bi.biz.domain.BiObject;
import com.ruoyi.common.core.domain.Ztree;

/**
 * 对象Service接口
 *
 * @author ruoyi
 * @date 2025-03-27
 */
public interface IBiObjectService
{
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
}
