package edu.whut.cs.bi.biz.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.vo.TemplateDiseaseTypeVO;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.IDiseaseTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import com.ruoyi.common.core.text.Convert;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private TODiseaseTypeMapper toDiseaseTypeMapper;

    @Autowired
    private DiseaseTypeMapper diseaseTypeMapper;

    @Autowired
    private IDiseaseTypeService diseaseTypeService;

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
        List<BiTemplateObject> list = biTemplateObjectMapper.selectChildrenById(id);
        // 查询并填充每个模板对象的病害类型数量
        if (list != null && !list.isEmpty()) {
            // 收集所有模板对象的ID
            List<Long> templateIds = list.stream()
                    .map(BiTemplateObject::getId)
                    .collect(Collectors.toList());

            // 一次性批量查询所有模板对象的病害类型数量
            List<Map<String, Object>> diseaseTypeCountsList = toDiseaseTypeMapper.countDiseaseTypesByTemplateObjectIds(templateIds);

            // 将结果转换为Map<Long, Integer>形式
            Map<Long, Integer> diseaseTypeCounts = new HashMap<>();
            for (Map<String, Object> map : diseaseTypeCountsList) {
                Long key = ((Number) map.get("key")).longValue();
                Integer value = ((Number) map.get("value")).intValue();
                diseaseTypeCounts.put(key, value);
            }

            // 填充病害类型数量
            for (BiTemplateObject template : list) {
                Integer count = diseaseTypeCounts.get(template.getId());
                template.setDiseaseTypeCount(count);
            }
        }

        return list;

    }

    /**
     * 添加模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeId 病害类型ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTemplateDiseaseType(Long templateObjectId, Long diseaseTypeId) {
        List<Long> componentIds = new ArrayList<>();
        componentIds.add(templateObjectId);
        toDiseaseTypeMapper.insertData(componentIds, diseaseTypeId);
        updateRootBitemplateObject(templateObjectId);
        return 1;
    }

    /**
     * 删除模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeId 病害类型ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTemplateDiseaseType(Long templateObjectId, Long diseaseTypeId) {
        updateRootBitemplateObject(templateObjectId);
        return toDiseaseTypeMapper.deleteData(templateObjectId, diseaseTypeId);
    }

    /**
     * 批量添加模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeIds 病害类型ID字符串，逗号分隔
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertTemplateDiseaseType(Long templateObjectId, String diseaseTypeIds) {
        String[] ids = Convert.toStrArray(diseaseTypeIds);
        List<Long> componentIds = new ArrayList<>();
        componentIds.add(templateObjectId);
        updateRootBitemplateObject(templateObjectId);
        for (String diseaseTypeId : ids) {
            toDiseaseTypeMapper.insertData(componentIds, Long.valueOf(diseaseTypeId));
        }
        return ids.length;
    }

    /**
     * 批量添加模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param templateObjectId 病害类型ID字符串，逗号分隔
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRootBitemplateObject(Long templateObjectId) {
        BiTemplateObject biTemplateObject = biTemplateObjectMapper.selectBiTemplateObjectById(templateObjectId);
        String ancestors = biTemplateObject.getAncestors();
        Long biobjectId = 0L;
        if (ancestors != null && !ancestors.isEmpty()) {
            String[] parts = ancestors.split(",");
            if (parts.length >= 2) {
                biobjectId = Long.valueOf(parts[1]);
            } else {
                biobjectId = Long.valueOf(parts[0]);
            }
        }
        BiTemplateObject biTemplateObjectRoot = biTemplateObjectMapper.selectBiTemplateObjectById(biobjectId);
        if(biTemplateObjectRoot != null) {
            biTemplateObjectRoot.setUpdateTime(new Date());
            biTemplateObjectRoot.setUpdateBy(ShiroUtils.getLoginName());
            biTemplateObjectMapper.updateBiTemplateObject(biTemplateObjectRoot);
        }
    }

    /**
     * 批量删除模板对象和病害类型关联
     *
     * @param templateObjectId 模板对象ID
     * @param diseaseTypeIds 病害类型ID字符串，逗号分隔
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteTemplateDiseaseType(Long templateObjectId, String diseaseTypeIds) {
        String[] ids = Convert.toStrArray(diseaseTypeIds);
        updateRootBitemplateObject(templateObjectId);
        return toDiseaseTypeMapper.batchDeleteData(templateObjectId, Arrays.stream(ids)
                .map(Long::valueOf)
                .collect(Collectors.toList()));
    }

    /**
     * 获取病害类型列表，包含是否已选信息
     *
     * @param diseaseType 病害类型信息
     * @param templateObjectId 模板对象ID
     * @return 病害类型列表
     */
    @Override
    public List<TemplateDiseaseTypeVO> selectDiseaseTypeVOList(TemplateDiseaseTypeVO diseaseType, Long templateObjectId) {
        return diseaseTypeMapper.selectTemplateDiseaseTypeList(templateObjectId, diseaseType);
    }

    @Override
    public byte[] exportTemplateFiles() {
        // 查询所有根节点模板
        BiTemplateObject query = new BiTemplateObject();
        query.setParentId(0L);
        List<BiTemplateObject> rootList = selectBiTemplateObjectList(query);

        if (rootList == null || rootList.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ObjectMapper objectMapper = new ObjectMapper();

            for (BiTemplateObject root : rootList) {
                // 构建完整的模板树
                BiTemplateObject fullTree = buildCompleteTemplateTree(root);

                // 创建文件名：id_updatetime.json
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                String updateTime = "";
                if (fullTree.getUpdateTime() != null) {
                    updateTime = sdf.format(fullTree.getUpdateTime());
                } else {
                    updateTime = sdf.format(new Date());
                }
                String fileName = fullTree.getId() + "_" + updateTime + ".json";

                // 将模板树转换为JSON
                byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(fullTree);

                // 添加到ZIP文件
                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(jsonBytes);
                zos.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return baos.toByteArray();
    }

    /**
     * 构建完整的模板树，包括子节点和病害类型
     *
     * @param root 根节点
     * @return 完整的模板树
     */
    private BiTemplateObject buildCompleteTemplateTree(BiTemplateObject root) {
        if (root == null) {
            return null;
        }

        // 查询所有子节点
        List<BiTemplateObject> children = biTemplateObjectMapper.selectChildrenById(root.getId());

        // 构建节点ID列表，用于批量查询病害类型
        List<Long> nodeIds = new ArrayList<>();
        nodeIds.add(root.getId());
        children.forEach(child -> nodeIds.add(child.getId()));

        // 批量查询所有节点的病害类型
        Map<Long, List<DiseaseType>> diseaseTypesMap = diseaseTypeService.batchSelectDiseaseTypeListByTemplateObjectIds(nodeIds);

        // 设置根节点的病害类型
        if (diseaseTypesMap.containsKey(root.getId())) {
            root.setDiseaseTypes(diseaseTypesMap.get(root.getId()));
        }

        // 构建树形结构
        Map<Long, BiTemplateObject> nodeMap = new HashMap<>();
        nodeMap.put(root.getId(), root);

        // 将所有子节点添加到nodeMap
        for (BiTemplateObject child : children) {
            // 设置病害类型
            if (diseaseTypesMap.containsKey(child.getId())) {
                child.setDiseaseTypes(diseaseTypesMap.get(child.getId()));
            }
            nodeMap.put(child.getId(), child);
        }

        // 构建树形结构
        for (BiTemplateObject node : children) {
            Long parentId = node.getParentId();
            if (nodeMap.containsKey(parentId)) {
                BiTemplateObject parent = nodeMap.get(parentId);
                parent.getChildren().add(node);
            }
        }

        return root;
    }
}
