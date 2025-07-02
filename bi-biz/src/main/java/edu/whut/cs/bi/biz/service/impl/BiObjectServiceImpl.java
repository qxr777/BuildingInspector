package edu.whut.cs.bi.biz.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.bean.BeanUtils;
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

    @Autowired
    private IBiObjectService biObjectService;

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
                    BigDecimal newWeight = sibling.getWeight().add(deletedWeight.multiply(sibling.getWeight()).divide(totalRemainingWeight, 4, RoundingMode.HALF_UP));

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
            biObjectList = selectBiObjectAndChildrenRemoveLeaf(rootObjectId);
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
     * 查询根节点及其所有子节点
     *
     * @param rootId 根节点ID
     * @return 根节点及其所有子节点列表
     */
    @Override
    public List<BiObject> selectBiObjectAndChildrenRemoveLeaf(Long rootId) {
        List<BiObject> list = new ArrayList<>();
        // 验证根节点
        BiObject rootNode = selectBiObjectById(rootId);
        if (rootNode != null) {
            // 查询所有子节点
            list.addAll(biObjectMapper.selectChildrenByIdRemoveLeaf(rootId));
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

        // 一次性获取所有节点，避免递归查询
        List<BiObject> allNodes = biObjectService.selectBiObjectAndChildren(id);

        // 获取所有节点的ID
        List<Long> allNodeIds = allNodes.stream().map(BiObject::getId).collect(Collectors.toList());

        // 一次性获取所有节点的疾病类型
        Map<Long, List<DiseaseType>> diseaseTypeMap = new HashMap<>();
        if (!allNodeIds.isEmpty()) {
            // 获取所有模板对象ID
            List<Long> templateObjectIds = allNodes.stream().filter(node -> node.getTemplateObjectId() != null).map(BiObject::getTemplateObjectId).distinct().collect(Collectors.toList());

            if (!templateObjectIds.isEmpty()) {
                // 批量查询所有疾病类型
                Map<Long, List<DiseaseType>> tempDiseaseTypeMap = diseaseTypeService.batchSelectDiseaseTypeListByTemplateObjectIds(templateObjectIds);
                if (tempDiseaseTypeMap != null) {
                    diseaseTypeMap = tempDiseaseTypeMap;
                }
            }
        }

        // 构建节点ID到节点的映射
        Map<Long, BiObject> nodeMap = new HashMap<>();
        for (BiObject node : allNodes) {
            // 深拷贝节点，避免修改原始数据
            BiObject newNode = new BiObject();
            BeanUtils.copyProperties(node, newNode);
            newNode.setChildren(new ArrayList<>());

            // 设置疾病类型
            if (node.getTemplateObjectId() != null && diseaseTypeMap.containsKey(node.getTemplateObjectId())) {
                newNode.setDiseaseTypes(diseaseTypeMap.get(node.getTemplateObjectId()));
            } else {
                newNode.setDiseaseTypes(new ArrayList<>());
            }

            nodeMap.put(newNode.getId(), newNode);
        }

        // 构建树结构
        BiObject rootNode = null;
        for (BiObject node : allNodes) {
            BiObject newNode = nodeMap.get(node.getId());

            if (node.getId().equals(id)) {
                rootNode = newNode; // 找到根节点
            }

            // 将当前节点添加到父节点的children列表中
            if (node.getParentId() != null && node.getParentId() != 0 && nodeMap.containsKey(node.getParentId())) {
                BiObject parent = nodeMap.get(node.getParentId());
                parent.getChildren().add(newNode);
            }
        }

        // 配置序列化选项
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return objectMapper.writeValueAsString(rootNode);
    }

    /**
     * 递归更新BiObject树结构（批量更新版本）
     *
     * @param biObject 当前处理的节点
     * @return 更新的节点数量
     */
    @Override
    public int updateBiObjectTreeRecursively(BiObject biObject) {
        // 结构信息已确认的桥梁不让再次修改
        if ("3".equals(biObject.getStatus())) {
            return 0;
        }
        // 1. 检查根节点是否存在
        BiObject existingObject = biObjectMapper.selectBiObjectById(biObject.getId());
        if (existingObject == null) {
            throw new RuntimeException("未找到ID为 " + biObject.getId() + " 的节点");
        }
        // 2. 收集所有需要更新的节点
        List<BiObject> nodesToUpdate = new ArrayList<>();
        collectNodesToUpdate(biObject, nodesToUpdate);

        // 3. 设置更新时间和更新人
        String updateBy = ShiroUtils.getLoginName();
        Date updateTime = new Date();
        for (BiObject node : nodesToUpdate) {
            if (biObject.getWeight() == null || biObject.getWeight().equals(BigDecimal.ZERO)) {
                biObject.setCount(0);
            }
            node.setUpdateBy(updateBy);
            node.setUpdateTime(updateTime);
        }

        // 4. 批量更新节点
        if (!nodesToUpdate.isEmpty()) {
            biObjectMapper.updateBiObjects(nodesToUpdate);
        }

        return nodesToUpdate.size();
    }

    /**
     * 递归收集需要更新的节点
     *
     * @param biObject      当前节点
     * @param nodesToUpdate 收集的节点列表
     */
    private void collectNodesToUpdate(BiObject biObject, List<BiObject> nodesToUpdate) {
        // 添加当前节点
        nodesToUpdate.add(biObject);

        // 递归处理子节点
        List<BiObject> children = biObject.getChildren();
        if (children != null && !children.isEmpty()) {
            for (BiObject child : children) {
                if (child.getCount() > 0) {
                    collectNodesToUpdate(child, nodesToUpdate);
                }
            }
        }
    }

    @Override
    public Boolean isLeafNode(Long id) {
        return biObjectMapper.isLeafNode(id);
    }

    /**
     * 批量更新子节点的ancestors
     *
     * @param rootObjectId       根节点ID
     * @param oldAncestorsPrefix 旧的ancestors前缀
     * @param newAncestorsPrefix 新的ancestors前缀
     * @param updateBy           更新人
     * @return 更新的记录数
     */
    @Override
    public int batchUpdateAncestors(Long rootObjectId, String oldAncestorsPrefix, String newAncestorsPrefix, String updateBy) {
        // 使用SQL批量更新，避免逐个查询和更新
        return biObjectMapper.batchUpdateAncestors(rootObjectId, oldAncestorsPrefix, newAncestorsPrefix, updateBy);
    }

    /**
     * 批量插入BiObject对象
     *
     * @param biObjects 要插入的BiObject对象列表
     * @return 插入的记录数
     */
    @Override
    public int batchInsertBiObjects(List<BiObject> biObjects) {
        if (biObjects == null || biObjects.isEmpty()) {
            return 0;
        }

        // 使用批量插入SQL，提高性能
        return biObjectMapper.batchInsertBiObjects(biObjects);
    }

    @Override
    public List<Ztree> selectBiObjectThreeLevelTree(Long rootObjectId) {
        List<BiObject> biObjectList;
        if (rootObjectId != null) {
            biObjectList = biObjectMapper.selectBiObjectAndChildrenThreeLevel(rootObjectId);
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
}
