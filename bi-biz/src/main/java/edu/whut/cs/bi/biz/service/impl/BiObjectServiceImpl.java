package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import java.util.ArrayList;

import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.Property;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import com.ruoyi.common.core.text.Convert;

/**
 * 对象Service业务层处理
 *
 * @author ruoyi
 * @date 2025-03-27
 */
@Service
public class BiObjectServiceImpl implements IBiObjectService {
    @Autowired
    private BiObjectMapper biObjectMapper;

    /**
     * 查询对象
     *
     * @param id 对象主键
     * @return 对象
     */
    @Override
    public BiObject selectBiObjectById(Long id) {
        return biObjectMapper.selectBiObjectById(id);
    }

    /**
     * 查询对象列表
     *
     * @param biObject 对象
     * @return 对象
     */
    @Override
    public List<BiObject> selectBiObjectList(BiObject biObject) {
        return biObjectMapper.selectBiObjectList(biObject);
    }

    /**
     * 新增对象
     *
     * @param biObject 对象
     * @return 结果
     */
    @Override
    public int insertBiObject(BiObject biObject) {
        biObject.setCreateTime(DateUtils.getNowDate());
        // 如果parentId为0，说明是根节点
        if (biObject.getParentId() == 0L) {
            biObject.setAncestors("0");
        } else {
            BiObject info = biObjectMapper.selectBiObjectById(biObject.getParentId());
            // 如果父节点不存在，则不允许新增
            if (info == null) {
                throw new ServiceException("父节点不存在，不允许新增");
            }
            biObject.setAncestors(info.getAncestors() + "," + biObject.getParentId());
        }
        return biObjectMapper.insertBiObject(biObject);
    }

    /**
     * 修改对象
     *
     * @param biObject 对象
     * @return 结果
     */
    @Override
    public int updateBiObject(BiObject biObject) {
        biObject.setUpdateTime(DateUtils.getNowDate());
        return biObjectMapper.updateBiObject(biObject);
    }

    /**
     * 批量删除对象
     *
     * @param ids 需要删除的对象主键
     * @return 结果
     */
    @Override
    public int deleteBiObjectByIds(String ids) {
        return biObjectMapper.deleteBiObjectByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除对象信息
     *
     * @param id 对象主键
     * @return 结果
     */
    @Override
    public int deleteBiObjectById(Long id) {
        return biObjectMapper.deleteBiObjectById(id);
    }

    /**
     * 查询对象树列表
     *
     * @return 所有对象信息
     */
    @Override
    public List<Ztree> selectBiObjectTree(Long rootObjectId) {
        List<BiObject> biObjectList;
        if (rootObjectId != null) {
            biObjectList = selectBiObjectAndChildren(rootObjectId);
        } else {
            biObjectList = biObjectMapper.selectBiObjectList(new BiObject());
        }
        List<Ztree> ztrees = new ArrayList<Ztree>();
        for (BiObject biObject : biObjectList) {
            Ztree ztree = new Ztree();
            ztree.setId(biObject.getId());
            ztree.setpId(biObject.getParentId());
            ztree.setName(biObject.getName());
            ztree.setTitle(biObject.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

    /**
     * 查询根节点及其所有子节点
     *
     * @param rootId 根节点ID
     * @return 根节点及其所有子节点列表
     */
    @Override
    public List<BiObject> selectBiObjectAndChildren(Long rootId) {
        List<BiObject> list = new ArrayList<>();
        // 添加根节点
        BiObject rootNode = selectBiObjectById(rootId);
        if (rootNode != null) {
            list.add(rootNode);
            // 查询所有子节点
            list.addAll(biObjectMapper.selectChildrenById(rootId));
        }
        return list;
    }
}
