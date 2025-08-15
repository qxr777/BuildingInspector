package edu.whut.cs.bi.biz.service.impl;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.bean.BeanUtils;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import com.ruoyi.common.core.text.Convert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


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
    private AttachmentService attachmentService;

    @Autowired
    private DiseaseTypeServiceImpl diseaseTypeService;

    @Autowired
    private IBiObjectService biObjectService;

    @Autowired
    private FileMapServiceImpl fileMapServiceImpl;

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
    @Transactional(rollbackFor = Exception.class)
    public int updateBiObject(BiObject biObject) {
        BiObject oldBiObject = biObjectMapper.selectBiObjectById(biObject.getId());
        biObject.setUpdateTime(DateUtils.getNowDate());
        biObject.setUpdateBy(ShiroUtils.getLoginName());
        // 处理构件数量变更传播
        if (!"其他".equals(biObject.getName()) && oldBiObject != null && !biObject.getCount().equals(oldBiObject.getCount())) {
            // 计算构件数量变化量
            if (oldBiObject.getCount() == null) {
                oldBiObject.setCount(0);
            }
            int deltaCount = biObject.getCount() - oldBiObject.getCount();

            // 如果有变化且不是根节点，则向上传播变更
            if (deltaCount != 0 && oldBiObject.getParentId() != 0L) {
                // 获取祖先节点列表（不包括根节点0）
                String ancestors = oldBiObject.getAncestors();
                if (ancestors != null && !ancestors.isEmpty()) {
                    String[] ancestorIds = ancestors.split(",");
                    List<Long> ancestorIdList = IntStream.range(0, ancestorIds.length)
                            .skip(2)
                            .mapToObj(i -> ancestorIds[i])
                            .map(id -> {
                                try {
                                    return Long.parseLong(id);
                                } catch (NumberFormatException e) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    // 如果有祖先节点，则批量更新它们的构件数量
                    if (!ancestorIdList.isEmpty()) {
                        biObjectMapper.updateAncestorsCount(ancestorIdList, deltaCount, ShiroUtils.getLoginName());
                    }
                }
            }
        }
        return biObjectMapper.updateBiObject(biObject);
    }

    /**
     * 重新分配权重
     *
     * @param parentId             父节点ID
     * @param weightToRedistribute 需要重新分配的权重
     * @param excludeObjectId      排除的对象ID
     * @return 更新的记录数
     */
    private int redistributeWeight(Long parentId, BigDecimal weightToRedistribute, Long excludeObjectId) {
        // 获取同级部件列表（相同parentId的部件）
        BiObject query = new BiObject();
        query.setParentId(parentId);
        List<BiObject> siblings = biObjectMapper.selectBiObjectList(query);

        // 如果有同级部件且被停用部件有权重，则重新分配权重
        if (siblings != null && siblings.size() > 1) {
            BigDecimal totalRemainingWeight = BigDecimal.ZERO;
            List<BiObject> siblingToUpdate = new ArrayList<>();

            // 单次遍历计算总权重并收集需要更新的兄弟节点
            for (BiObject sibling : siblings) {
                // 排除当前节点和已停用的节点
                if (!sibling.getId().equals(excludeObjectId) &&
                        !"1".equals(sibling.getStatus()) &&
                        sibling.getWeight() != null) {
                    totalRemainingWeight = totalRemainingWeight.add(sibling.getWeight());
                    siblingToUpdate.add(sibling);
                }
            }

            // 如果剩余权重大于0，则重新分配权重
            if (totalRemainingWeight.compareTo(BigDecimal.ZERO) > 0) {
                // 准备批量更新的对象
                for (BiObject sibling : siblingToUpdate) {
                    // 计算新权重：原权重 + (停用节点权重 * 原权重/剩余总权重)
                    BigDecimal newWeight = sibling.getWeight().add(
                            weightToRedistribute.multiply(sibling.getWeight())
                                    .divide(totalRemainingWeight, 4, RoundingMode.HALF_UP)
                    );

                    sibling.setWeight(newWeight);
                    sibling.setUpdateTime(DateUtils.getNowDate());
                    sibling.setUpdateBy(ShiroUtils.getLoginName());
                }

                // 批量更新所有兄弟节点的权重
                if (!siblingToUpdate.isEmpty()) {
                    return biObjectMapper.updateBiObjects(siblingToUpdate);
                }
            }
        }
        return 0;
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
    @Transactional(rollbackFor = Exception.class)
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
            redistributeWeight(biObject.getParentId(), biObject.getWeight(), id);
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
    public int updateBiObjectTreeRecursively(BiObject biObject, Map<String, Path> extractedFiles) {
        // 结构信息已确认的桥梁不让再次修改
        if ("3".equals(biObject.getStatus())) {
            return 0;
        }
        // 1. 检查根节点是否存在
        BiObject existingObject = biObjectMapper.selectBiObjectById(biObject.getId());
        if (existingObject == null) {
            throw new RuntimeException("未找到ID为 " + biObject.getId() + " 的节点");
        }
        // web修改的结构信息已确认状态的桥梁不让再次修改
        if ("3".equals(existingObject.getStatus())) {
            return 0;
        }
        // 2. 收集所有需要更新的节点
        List<BiObject> nodesToUpdate = new ArrayList<>();
        List<BiObject> photoUpdate = new ArrayList<>();
        collectNodesToUpdate(biObject, nodesToUpdate, photoUpdate);
        // 1. 收集所有 BiObject 的 id
        List<Long> ids = photoUpdate.stream()
                .map(BiObject::getId)
                .toList();
        Set<String> attachmentMap = new HashSet<>();
        if (!ids.isEmpty()) {
            attachmentMap = attachmentService.getAttachmentBySubjectIds(ids).stream()
                    .filter(e -> e.getType() == 8
                            && e.getName() != null
                            && e.getName().startsWith("biObject_"))
                    .map(e -> {
                        String namePart = e.getName().substring("biObject_".length());
                        String remarkPart = e.getRemark() == null ? "" : e.getRemark();
                        return remarkPart.isEmpty() ? namePart : namePart + "_" + remarkPart;
                    })
                    .collect(Collectors.toSet());
        }


        // 3. 设置更新时间和更新人
        String updateBy = ShiroUtils.getLoginName();
        Date updateTime = new Date();
        for (BiObject node : nodesToUpdate) {
            node.setUpdateBy(updateBy);
            node.setUpdateTime(updateTime);
        }

        for (BiObject node : photoUpdate) {
            List<String> photos = node.getPhoto();
            List<String> informations = node.getInformation();
            List<MultipartFile> multipartImagesFiles = new ArrayList<>();
            List<String> filteredInformations = new ArrayList<>();

            // 处理结构图片
            if (photos != null && !photos.isEmpty()) {
                for (int i = 0; i < photos.size(); i++) {
                    String photo = photos.get(i);
                    if (photo != null && !photo.isEmpty()) {
                        String photoName = photo.substring(photo.lastIndexOf('/') + 1);
                        String remarkPart = informations.get(i).isEmpty() ? "" : ("_" + informations.get(i));

                        // 重复名字且备注一样则跳过
                        if (attachmentMap.contains(photoName + remarkPart)) {
                            continue;
                        }

                        String fullPath = photo;

                        // 查找文件
                        if (extractedFiles.containsKey(fullPath)) {
                            File imageFile = extractedFiles.get(fullPath).toFile();
                            try {
                                byte[] fileContent = Files.readAllBytes(imageFile.toPath());
                                MockMultipartFile mockFile = new MockMultipartFile(
                                        "file",
                                        imageFile.getName(),
                                        Files.probeContentType(imageFile.toPath()),
                                        fileContent
                                );
                                multipartImagesFiles.add(mockFile);
                                filteredInformations.add(informations.get(i));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }

            if (!multipartImagesFiles.isEmpty()) {
                fileMapServiceImpl.handleBiObjectAttachment(
                        multipartImagesFiles.toArray(new MultipartFile[0]),
                        node.getId(),
                        8,
                        filteredInformations
                );
            }
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
    private void collectNodesToUpdate(BiObject biObject, List<BiObject> nodesToUpdate, List<BiObject> photos) {
        // 只有数量的才会添加当前节点
        if (biObject.getCount() != 0) {
            nodesToUpdate.add(biObject);
        }
        // 照片添加当前节点
        if (biObject.getPhoto() != null && !biObject.getPhoto().isEmpty()) {
            photos.add(biObject);
        }
        // 递归处理子节点
        List<BiObject> children = biObject.getChildren();
        if (children != null && !children.isEmpty()) {
            for (BiObject child : children) {
                collectNodesToUpdate(child, nodesToUpdate, photos);
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

    /**
     * 一键修正权重
     *
     * @param rootObjectId 根对象ID
     * @return 更新的记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int correctAllWeights(Long rootObjectId) {
        int updateCount = 0;

        // 1. 获取所有部件
        List<BiObject> allObjects = selectBiObjectAndChildrenRemoveLeaf(rootObjectId);
        if (allObjects == null || allObjects.isEmpty()) {
            return 0;
        }

        // 2. 初始化所有部件的权重为其标准权重值
        for (BiObject obj : allObjects) {
            // 使用对象自己的standardWeight，如果为空则使用默认值1.0
            if (obj.getStandardWeight() == null) {
                obj.setWeight(BigDecimal.ZERO);
            } else {
                obj.setWeight(obj.getStandardWeight());
            }
            //  如果一开始是停用但是构件数量不为0则启用
            if (obj.getCount() != null && obj.getCount() > 0 && "1".equals(obj.getStatus())) {
                obj.setStatus("0");
            }
            obj.setUpdateTime(DateUtils.getNowDate());
            obj.setUpdateBy(ShiroUtils.getLoginName());
        }
        updateCount += biObjectMapper.updateBiObjects(allObjects);

        // 3. 获取所有第3层部件
        List<BiObject> thirdLevelObjects = allObjects.stream()
                .filter(obj -> {
                    String[] ancestors = obj.getAncestors().split(",");
                    return ancestors.length == 3; // 第3层部件的祖先数量为3（0,1,2）
                })
                .collect(Collectors.toList());

        // 4. 处理第3层部件
        for (BiObject obj : thirdLevelObjects) {
            // 对构件数量为0或者为null的部件，将权重分配给同级节点并设置为停用
            if ((obj.getCount() == null || obj.getCount() == 0) && obj.getStandardWeight() != null) {
                // 重新查询该部件的最新信息，确保获取最新的权重值
                BiObject latestObj = biObjectMapper.selectBiObjectById(obj.getId());
                if (latestObj == null) {
                    continue; // 如果部件不存在，跳过处理
                }

                // 使用最新的权重值进行重分配
                BigDecimal weightToRedistribute = latestObj.getWeight() != null ? latestObj.getWeight() : BigDecimal.ZERO;

                // 将部件状态设置为停用
                latestObj.setStatus("1");
                latestObj.setWeight(BigDecimal.ZERO);
                latestObj.setUpdateTime(DateUtils.getNowDate());
                latestObj.setUpdateBy(ShiroUtils.getLoginName());
                biObjectMapper.updateBiObject(latestObj);

                // 重新分配权重
                redistributeWeight(latestObj.getParentId(), weightToRedistribute, latestObj.getId());
            }
        }

        return updateCount;
    }
}
