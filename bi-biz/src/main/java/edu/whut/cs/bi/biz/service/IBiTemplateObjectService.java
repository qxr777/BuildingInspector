package edu.whut.cs.bi.biz.service;

import java.util.List;

import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import com.ruoyi.common.core.domain.Ztree;

/**
 * 桥梁构件模版Service接口
 *
 * @author wanzheng
 * @date 2025-04-02
 */
public interface IBiTemplateObjectService {
    /**
     * 查询桥梁构件模版
     *
     * @param id 桥梁构件模版主键
     * @return 桥梁构件模版
     */
    public BiTemplateObject selectBiTemplateObjectById(Long id);

    /**
     * 查询桥梁构件模版列表
     *
     * @param biTemplateObject 桥梁构件模版
     * @return 桥梁构件模版集合
     */
    public List<BiTemplateObject> selectBiTemplateObjectList(BiTemplateObject biTemplateObject);

    /**
     * 新增桥梁构件模版
     *
     * @param biTemplateObject 桥梁构件模版
     * @return 结果
     */
    public int insertBiTemplateObject(BiTemplateObject biTemplateObject);

    /**
     * 修改桥梁构件模版
     *
     * @param biTemplateObject 桥梁构件模版
     * @return 结果
     */
    public int updateBiTemplateObject(BiTemplateObject biTemplateObject);

    /**
     * 批量删除桥梁构件模版
     *
     * @param ids 需要删除的桥梁构件模版主键集合
     * @return 结果
     */
    public int deleteBiTemplateObjectByIds(String ids);

    /**
     * 删除桥梁构件模版信息
     *
     * @param id 桥梁构件模版主键
     * @return 结果
     */
    public int deleteBiTemplateObjectById(Long id);

    /**
     * 查询桥梁构件模版树列表
     *
     * @return 所有桥梁构件模版信息
     */
    public List<Ztree> selectBiTemplateObjectTree();

    /**
     * 查询是否存在子节点
     *
     * @param ids 部门ID列表
     * @return 结果
     */
    public boolean hasChildByIds(String ids);

    /**
     * 查询指定节点的所有子节点
     *
     * @param id 节点ID
     * @return 子节点列表
     */
    public List<BiTemplateObject> selectChildrenById(Long id);
}
