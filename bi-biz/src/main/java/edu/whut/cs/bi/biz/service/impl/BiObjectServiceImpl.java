package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
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
        BiObject newParentObject = biObjectMapper.selectBiObjectById(biObject.getParentId());
        BiObject oldBiObject = biObjectMapper.selectBiObjectById(biObject.getId());
        if (StringUtils.isNotNull(newParentObject) && StringUtils.isNotNull(oldBiObject)) {
            // 这里要判断一下其是否存在祖先节点, 否则存储到数据库中的值会出现 null,1 这样情况
            String newAncestors = (newParentObject.getAncestors() != null ? newParentObject.getAncestors() : "") + "," + newParentObject.getId();
            String oldAncestors = oldBiObject.getAncestors();
            biObject.setAncestors(newAncestors);
            updateObjectChildren(biObject.getId(), newAncestors, oldAncestors);
        }
        return biObjectMapper.updateBiObject(biObject);
    }

    /**
     * 修改子元素关系
     *
     * @param objectId     被修改的对象ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    public void updateObjectChildren(Long objectId, String newAncestors, String oldAncestors) {
        List<BiObject> children = biObjectMapper.selectChildrenById(objectId);
        for (BiObject child : children) {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
        }
        if (children.size() > 0) {
            biObjectMapper.updateObjectChildren(children);
        }
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

    /**
     * 根据根节点ID逻辑删除对象及其所有子节点
     *
     * @param rootObjectId 根节点ID
     * @param updateBy     更新人
     * @return 结果
     */
    @Override
    public int logicDeleteByRootObjectId(Long rootObjectId, String updateBy) {
        return biObjectMapper.logicDeleteByRootObjectId(rootObjectId, updateBy);
    }

    /**
     * 更新子节点的ancestors
     *
     * @param parentId  父节点ID
     * @param ancestors 父节点的ancestors
     * @return 结果
     */
    @Override
    public int updateChildrenAncestors(Long parentId, String ancestors) {
        // 查询所有直接子节点
        BiObject query = new BiObject();
        query.setParentId(parentId);
        List<BiObject> children = biObjectMapper.selectBiObjectList(query);

        int count = 0;
        for (BiObject child : children) {
            // 设置新的ancestors
            child.setAncestors(ancestors + "," + parentId);
            // 更新子节点
            count += biObjectMapper.updateBiObject(child);
            // 递归更新子节点的子节点
            count += updateChildrenAncestors(child.getId(), child.getAncestors());
        }

        return count;
    }

    @Override
    public List<BiObject> selectLeafNodes(Long rootId) {
        return biObjectMapper.selectLeafNodes(rootId);
    }

    @Override
    public List<BiObject> selectDirectChildrenByParentId(Long parentId) {
        return biObjectMapper.selectChildrenByParentId(parentId);
    }

    @Override
    public BiObject selectDirectParentById(Long id) {
        return biObjectMapper.selectDirectParentById(id);
    }

    /**
     * 导出JSON桥梁结构数据
     */
    @Override
    public String bridgeStructureJson(Long id) throws Exception {
        BiObject root = selectBiObjectById(id);
        if (root == null) {
            throw new Exception("未找到指定的构件");
        }

        // 构建树结构
        buildTreeStructure(root);

        // 配置序列化选项
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return objectMapper.writeValueAsString(root);
    }

    /**
     * 递归构建树结构
     */
    private void buildTreeStructure(BiObject node) {
        // 使用selectChildrenByParentId直接获取子节点
        List<BiObject> children = biObjectMapper.selectChildrenByParentId(node.getId());
        if (!children.isEmpty()) {
            node.setChildren(children);
            // 递归处理每个子节点
            for (BiObject child : children) {
                buildTreeStructure(child);
            }
        }
    }
}
