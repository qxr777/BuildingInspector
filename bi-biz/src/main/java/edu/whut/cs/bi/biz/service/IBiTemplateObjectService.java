package edu.whut.cs.bi.biz.service;

import java.util.List;

import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import com.ruoyi.common.core.domain.Ztree;
import edu.whut.cs.bi.biz.domain.vo.TemplateDiseaseTypeVO;

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

    /**
     * 添加模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeId 病害类型ID
     * @return 结果
     */
    public int insertTemplateDiseaseType(Long templateObjectId, Long diseaseTypeId);

    /**
     * 删除模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeId 病害类型ID
     * @return 结果
     */
    public int deleteTemplateDiseaseType(Long templateObjectId, Long diseaseTypeId);

    /**
     * 批量添加模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeIds 病害类型ID字符串，逗号分隔
     * @return 结果
     */
    public int batchInsertTemplateDiseaseType(Long templateObjectId, String diseaseTypeIds);

    /**
     * 批量删除模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeIds 病害类型ID字符串，逗号分隔
     * @return 结果
     */
    public int batchDeleteTemplateDiseaseType(Long templateObjectId, String diseaseTypeIds);


    /**
     * 获取病害类型列表，包含是否已选信息
     *
     * @param diseaseType 病害类型信息
     * @param templateObjectId 模板对象ID
     * @return 病害类型列表
     */
    public List<TemplateDiseaseTypeVO> selectDiseaseTypeVOList(TemplateDiseaseTypeVO diseaseType, Long templateObjectId);
}
