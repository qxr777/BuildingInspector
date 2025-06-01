package edu.whut.cs.bi.biz.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import com.ruoyi.common.core.text.Convert;
import org.springframework.transaction.annotation.Transactional;


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

    @Autowired
    private ComponentServiceImpl componentService;

    @Autowired
    private DiseaseTypeServiceImpl diseaseTypeService;

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
    @Transactional
    public int deleteBiObjectById(Long id) {
        // 1. 获取要删除的部件信息
        BiObject biObject = biObjectMapper.selectBiObjectById(id);
        if (biObject == null) {
            return 0;
        }

        // 2. 获取同级部件列表（相同parentId的部件）
        BiObject query = new BiObject();
        query.setParentId(biObject.getParentId());
        List<BiObject> siblings = biObjectMapper.selectBiObjectList(query);

        // 3. 如果有同级部件且被删除部件有权重，则重新分配权重
        if (siblings != null && siblings.size() > 1 && biObject.getWeight() != null) {
            BigDecimal deletedWeight = biObject.getWeight();
            BigDecimal totalRemainingWeight = BigDecimal.ZERO;
            List<BiObject> siblingToUpdate = new ArrayList<>();

            // 单次遍历计算总权重并收集需要更新的兄弟节点
            for (BiObject sibling : siblings) {
                if (!sibling.getId().equals(id) && sibling.getWeight() != null) {
                    totalRemainingWeight = totalRemainingWeight.add(sibling.getWeight());
                    siblingToUpdate.add(sibling);
                }
            }

            // 如果剩余权重大于0，则重新分配权重
            if (totalRemainingWeight.compareTo(BigDecimal.ZERO) > 0) {
                // 准备批量更新的对象
                for (BiObject sibling : siblingToUpdate) {
                    // 计算新权重：原权重 + (删除权重 * 原权重/剩余总权重)
                    BigDecimal newWeight = sibling.getWeight().add(
                            deletedWeight.multiply(sibling.getWeight())
                                    .divide(totalRemainingWeight, 4, RoundingMode.HALF_UP)
                    );

                    sibling.setWeight(newWeight);
                    sibling.setUpdateTime(DateUtils.getNowDate());
                    sibling.setUpdateBy(ShiroUtils.getLoginName());
                }

                // 批量更新所有兄弟节点的权重
                if (!siblingToUpdate.isEmpty()) {
                    biObjectMapper.updateBiObjects(siblingToUpdate);
                }
            }
        }

        // 4. 删除目标部件
        return biObjectMapper.logicDeleteByRootObjectId(id, ShiroUtils.getLoginName());
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
        List<Component> components = componentService.selectComponentsByBiObjectIdApi(node.getId());
        List<DiseaseType> diseaseTypes = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(node.getTemplateObjectId());
        node.setComments(components);
        node.setDiseaseTypes(diseaseTypes);
        if (!children.isEmpty()) {
            node.setChildren(children);
            // 递归处理每个子节点
            for (BiObject child : children) {
                buildTreeStructure(child);
            }
        }
    }

    /**
     * 递归更新BiObject树结构
     *
     * @param biObject 当前处理的节点
     * @return 更新的节点数量
     */
    @Override
    public int updateBiObjectTreeRecursively(BiObject biObject) {
        if (biObject.getStatus() == null || biObject.getStatus().equals("3")) {
            throw new RuntimeException(biObject.getName() + "结构已初始化，不允许修改");
        }
        int updateCount = 0;

        // 1. 更新当前节点
        BiObject existingObject = biObjectMapper.selectBiObjectById(biObject.getId());
        if (existingObject == null) {
            throw new RuntimeException("未找到ID为 " + biObject.getId() + " 的节点");
        }

        biObject.setUpdateBy(ShiroUtils.getLoginName());
        biObject.setUpdateTime(new Date());
        biObjectMapper.updateBiObject(biObject);
        updateCount++;

        // 2. 处理构件更新
        List<Component> newComponents = biObject.getComments();
        if (newComponents != null && !newComponents.isEmpty()) {
            // 逻辑删除该BiObject下所有的构件（一次数据库操作）
            componentService.deleteComponentsByBiObjectId(biObject.getId());

            for (Component component : newComponents) {
                // 设置必要的系统字段
                component.setCreateBy(ShiroUtils.getLoginName());
                component.setCreateTime(new Date());
                component.setUpdateBy(ShiroUtils.getLoginName());
                component.setUpdateTime(new Date());
                component.setStatus("0"); // 默认正常状态
                component.setDelFlag("0"); // 默认存在
            }

            // 批量插入所有构件（一次数据库操作）
            componentService.batchInsertComponents(newComponents);
        }

        // 3. 递归处理子节点
        List<BiObject> children = biObject.getChildren();
        if (children != null && !children.isEmpty()) {
            for (BiObject child : children) {
                updateCount += updateBiObjectTreeRecursively(child);
            }
        }

        return updateCount;
    }
}
