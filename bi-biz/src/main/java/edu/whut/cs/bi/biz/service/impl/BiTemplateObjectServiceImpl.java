package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import java.util.ArrayList;

import com.ruoyi.common.core.domain.Ztree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import com.ruoyi.common.core.text.Convert;

/**
 * 桥梁构件模版Service业务层处理
 *
 * @author wanzheng
 * @date 2025-04-02
 */
@Service
public class BiTemplateObjectServiceImpl implements IBiTemplateObjectService {
    @Autowired
    private BiTemplateObjectMapper biTemplateObjectMapper;

    /**
     * 查询桥梁构件模版
     *
     * @param id 桥梁构件模版主键
     * @return 桥梁构件模版
     */
    @Override
    public BiTemplateObject selectBiTemplateObjectById(Long id) {
        return biTemplateObjectMapper.selectBiTemplateObjectById(id);
    }

    /**
     * 查询桥梁构件模版列表
     *
     * @param biTemplateObject 桥梁构件模版
     * @return 桥梁构件模版
     */
    @Override
    public List<BiTemplateObject> selectBiTemplateObjectList(BiTemplateObject biTemplateObject) {
        return biTemplateObjectMapper.selectBiTemplateObjectList(biTemplateObject);
    }

    /**
     * 新增桥梁构件模版
     *
     * @param biTemplateObject 桥梁构件模版
     * @return 结果
     */
    @Override
    public int insertBiTemplateObject(BiTemplateObject biTemplateObject) {
        BiTemplateObject info = biTemplateObjectMapper.selectBiTemplateObjectById(biTemplateObject.getParentId());
        // 如果选择的是根节点，则设置父节点id为0
        if (info == null) {
            biTemplateObject.setParentId(0L);
            biTemplateObject.setAncestors("0");
        } else {
            biTemplateObject.setParentId(info.getId());
            biTemplateObject.setAncestors(info.getAncestors() + "," + info.getId());
        }
        return biTemplateObjectMapper.insertBiTemplateObject(biTemplateObject);
    }

    /**
     * 修改桥梁构件模版
     *
     * @param biTemplateObject 桥梁构件模版
     * @return 结果
     */
    @Override
    public int updateBiTemplateObject(BiTemplateObject biTemplateObject) {
        return biTemplateObjectMapper.updateBiTemplateObject(biTemplateObject);
    }

    /**
     * 批量删除桥梁构件模版
     *
     * @param ids 需要删除的桥梁构件模版主键
     * @return 结果
     */
    @Override
    public int deleteBiTemplateObjectByIds(String ids) {
        return biTemplateObjectMapper.deleteBiTemplateObjectByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除桥梁构件模版信息
     *
     * @param id 桥梁构件模版主键
     * @return 结果
     */
    @Override
    public int deleteBiTemplateObjectById(Long id) {
        return biTemplateObjectMapper.deleteBiTemplateObjectById(id);
    }

    /**
     * 查询桥梁构件模版树
     *
     * @return 所有桥梁构件模版树
     */
    @Override
    public List<Ztree> selectBiTemplateObjectTree() {
        List<BiTemplateObject> biTemplateObjectList = biTemplateObjectMapper.selectBiTemplateObjectList(new BiTemplateObject());
        List<Ztree> ztrees = new ArrayList<Ztree>();
        for (BiTemplateObject biTemplateObject : biTemplateObjectList) {
            Ztree ztree = new Ztree();
            ztree.setId(biTemplateObject.getId());
            ztree.setpId(biTemplateObject.getParentId());
            ztree.setName(biTemplateObject.getName());
            ztree.setTitle(biTemplateObject.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

    /**
     * 查询是否存在子节点
     *
     * @param ids 部门ID列表
     * @return 结果
     */
    @Override
    public boolean hasChildByIds(String ids) {
        Long[] templateIds = Convert.toLongArray(ids);
        int result = biTemplateObjectMapper.hasChildByIds(templateIds);
        return result > 0;
    }

    /**
     * 查询指定节点的所有子节点
     *
     * @param id 节点ID
     * @return 子节点列表
     */
    @Override
    public List<BiTemplateObject> selectChildrenById(Long id) {
        return biTemplateObjectMapper.selectChildrenById(id);
    }
}
