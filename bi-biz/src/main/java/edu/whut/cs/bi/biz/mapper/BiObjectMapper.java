package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.BiObject;
import org.apache.ibatis.annotations.Param;

/**
 * 对象Mapper接口
 *
 * @author ruoyi
 * @date 2025-03-27
 */
public interface BiObjectMapper
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
     * 根据根节点ID逻辑删除对象及其所有子节点
     *
     * @param rootObjectId 根节点ID
     * @param updateBy 更新人
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
}
