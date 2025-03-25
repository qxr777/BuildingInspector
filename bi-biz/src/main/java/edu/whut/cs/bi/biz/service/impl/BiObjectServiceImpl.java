package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.text.Convert;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @Author:wanzheng
 * @Date:2025/3/20 22:22
 * @Description:
 **/
@Service
public class BiObjectServiceImpl implements IBiObjectService {
    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 查询桥梁构件
     *
     * @param id 桥梁构件主键
     * @return 桥梁构件
     */
    @Override
    public BiObject selectBiObjectById(Long id) {
        return biObjectMapper.selectBiObjectById(id);
    }

    /**
     * 查询桥梁构件列表
     *
     * @param biObject 桥梁构件
     * @return 桥梁构件
     */
    @Override
    public List<BiObject> selectBiObjectList(BiObject biObject) {
        return biObjectMapper.selectBiObjectList(biObject);
    }

    /**
     * 查询桥梁列表（顶级节点）
     */
    @Override
    public List<BiObject> selectBridges() {
        return biObjectMapper.selectBridges();
    }

    /**
     * 查询桥梁下的所有构件
     */
    @Override
    public List<BiObject> selectComponentsByBridgeId(Long bridgeId) {
        // 先获取桥梁本身
        BiObject bridge = selectBiObjectById(bridgeId);
        if (bridge != null) {
            List<BiObject> list = new ArrayList<>();
            list.add(bridge); // 添加桥梁节点
            // 获取所有子构件
            list.addAll(biObjectMapper.selectComponentsByBridgeId(bridgeId));
            return list;
        }
        return new ArrayList<>();
    }

    /**
     * 构建树结构
     */
    @Override
    public List<BiObject> buildTree(List<BiObject> list) {
        List<BiObject> returnList = new ArrayList<BiObject>();
        List<Long> tempList = new ArrayList<Long>();
        for (BiObject item : list) {
            tempList.add(item.getId());
        }
        for (BiObject item : list) {
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (!tempList.contains(item.getParentId())) {
                recursionFn(list, item);
                returnList.add(item);
            }
        }
        if (returnList.isEmpty()) {
            returnList = list;
        }
        return returnList;
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<BiObject> list, BiObject t) {
        // 得到子节点列表
        List<BiObject> childList = getChildList(list, t);
        t.setChildren(childList);
        for (BiObject tChild : childList) {
            if (hasChild(list, tChild)) {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<BiObject> getChildList(List<BiObject> list, BiObject t) {
        List<BiObject> tlist = new ArrayList<BiObject>();
        for (BiObject n : list) {
            if (n.getParentId() != null && n.getParentId().longValue() == t.getId().longValue()) {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<BiObject> list, BiObject t) {
        return getChildList(list, t).size() > 0;
    }

    /**
     * 新增桥梁构件
     *
     * @param biObject 桥梁构件
     * @return 结果
     */
    @Override
    public int insertBiObject(BiObject biObject) {
        return biObjectMapper.insertBiObject(biObject);
    }

    /**
     * 修改桥梁构件
     *
     * @param biObject 桥梁构件
     * @return 结果
     */
    @Override
    public int updateBiObject(BiObject biObject) {
        return biObjectMapper.updateBiObject(biObject);
    }

    /**
     * 删除桥梁构件
     */
    @Override
    public int deleteBiObjectByIds(String ids) {
        return biObjectMapper.deleteBiObjectByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除桥梁构件信息
     *
     * @param id 桥梁构件主键
     * @return 结果
     */
    @Override
    public int deleteBiObjectById(Long id) {
        return biObjectMapper.deleteBiObjectById(id);
    }

    /**
     * 导入JSON数据
     */
    @Override
    public String importData(String jsonData, boolean updateSupport, String operName) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonData);

        Counter successCounter = new Counter();
        StringBuilder successMsg = new StringBuilder();

        // 遍历桥梁
        Iterator<Map.Entry<String, JsonNode>> bridges = rootNode.fields();
        while (bridges.hasNext()) {
            Map.Entry<String, JsonNode> bridge = bridges.next();
            String bridgeName = bridge.getKey();
            JsonNode bridgeNode = bridge.getValue();

            // 创建桥梁节点
            BiObject bridgeObj = new BiObject();
            bridgeObj.setName(bridgeName);
            bridgeObj.setParentId(0L); // 顶级节点
            bridgeObj.setOrderNum(0); // 桥梁节点的orderNum设为0
            bridgeObj.setStatus("0");
            bridgeObj.setCreateBy(operName);
            bridgeObj.setAncestors("0"); // 顶级节点的ancestors为"0"
            insertBiObject(bridgeObj);
            Long bridgeId = bridgeObj.getId(); // 获取插入后的ID
            successCounter.increment();

            // 遍历桥梁的子节点
            processJsonNode(bridgeNode, bridgeId, "0", operName, updateSupport, successCounter);
        }

        if (successCounter.getValue() > 0) {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successCounter.getValue() + " 条。");
        }
        return successMsg.toString();
    }

    // 计数器类，用于跟踪成功导入的记录数
    private static class Counter {
        private int value = 0;

        public void increment() {
            value++;
        }

        public int getValue() {
            return value;
        }
    }

    private void processJsonNode(JsonNode node, Long parentId, String parentAncestors, String operName, boolean updateSupport,
                                 Counter successCounter) {
        if (node.isObject()) {
            // 获取同级节点数量用于设置orderNum
            int childCount = node.size();
            int currentOrder = 0;

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                JsonNode childNode = field.getValue();

                // 创建当前节点
                BiObject obj = new BiObject();
                obj.setName(name);
                obj.setCreateBy(operName);
                obj.setParentId(parentId); // 设置父节点ID
                obj.setOrderNum(currentOrder); // 设置当前层级的序号
                obj.setStatus("0");
                obj.setAncestors(parentAncestors + "," + parentId);
                insertBiObject(obj);
                Long currentId = obj.getId(); // 获取当前节点的ID
                successCounter.increment();
                currentOrder++; // 递增序号

                // 递归处理子节点，传递当前节点的ID作为父节点ID
                processJsonNode(childNode, currentId, obj.getAncestors(), operName, updateSupport, successCounter);
            }
        } else if (node.isArray()) {
            int currentOrder = 0;
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    BiObject obj = new BiObject();
                    obj.setName(item.asText());
                    obj.setCreateBy(operName);
                    obj.setParentId(parentId); // 设置父节点ID
                    obj.setOrderNum(currentOrder); // 设置当前层级的序号
                    obj.setStatus("0");
                    obj.setAncestors(parentAncestors + "," + parentId);
                    insertBiObject(obj);
                    successCounter.increment();
                    currentOrder++; // 递增序号
                }
            }
        }
    }

    /**
     * 导出JSON数据
     */
    @Override
    public String exportData(Long id) throws Exception {
        BiObject root = selectBiObjectById(id);
        if (root == null) {
            throw new Exception("未找到指定的构件");
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(buildJsonTree(root));
    }

    private JsonNode buildJsonTree(BiObject root) {
        ObjectMapper mapper = new ObjectMapper();
        BiObject query = new BiObject();
        query.setParentId(root.getId());
        List<BiObject> children = selectBiObjectList(query);

        if (children.isEmpty()) {
            return mapper.valueToTree(root.getName());
        }

        return mapper.createObjectNode().set(root.getName(),
                mapper.valueToTree(children.stream().map(this::buildJsonTree).toArray()));
    }

    /**
     * 导入Excel数据
     *
     * @param biObjectList  Excel数据列表
     * @param updateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName      操作用户
     * @return 结果
     */
    @Override
    public String importExcelData(List<BiObject> biObjectList, boolean updateSupport, String operName) {
        if (biObjectList == null || biObjectList.isEmpty()) {
            throw new RuntimeException("导入数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();

        for (BiObject biObject : biObjectList) {
            try {
                insertBiObject(biObject);
                successNum++;
                successMsg.append("<br/>" + successNum + "、构件 " + biObject.getName() + " 导入成功");
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、构件 " + biObject.getName() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
            }
        }

        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new RuntimeException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }
}
