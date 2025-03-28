package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import java.io.IOException;

import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import com.ruoyi.common.core.text.Convert;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;

/**
 * 建筑demoService业务层处理
 *
 * @author wanzheng
 * @date 2025-03-27
 */
@Service
public class BuildingServiceImpl implements IBuildingService {
    @Autowired
    private BuildingMapper buildingMapper;

    @Autowired
    private IBiObjectService biObjectService;

    /**
     * 查询建筑demo
     *
     * @param id 建筑demo主键
     * @return 建筑demo
     */
    @Override
    public Building selectBuildingById(Long id) {
        return buildingMapper.selectBuildingById(id);
    }

    /**
     * 查询建筑demo列表
     *
     * @param building 建筑demo
     * @return 建筑demo
     */
    @Override
    public List<Building> selectBuildingList(Building building) {
        return buildingMapper.selectBuildingList(building);
    }

    /**
     * 新增建筑demo
     *
     * @param building 建筑demo
     * @return 结果
     */
    @Override
    @Transactional
    public int insertBuilding(Building building) {
        building.setCreateTime(DateUtils.getNowDate());
        int rows = buildingMapper.insertBuilding(building);

        // 创建BiObject根节点
        if (rows > 0) {
            BiObject rootObject = new BiObject();
            rootObject.setName(building.getName());
            rootObject.setParentId(0L); // 设置为根节点
            rootObject.setAncestors("0"); // 根节点的ancestors为"0"
            rootObject.setOrderNum(0); // 默认排序号
            rootObject.setStatus("0"); // 默认状态为正常

            // 插入BiObject根节点
            biObjectService.insertBiObject(rootObject);

            // 更新Building的rootObjectId
            building.setRootObjectId(rootObject.getId().toString());
            buildingMapper.updateBuilding(building);
        }

        return rows;
    }

    /**
     * 修改建筑demo
     *
     * @param building 建筑demo
     * @return 结果
     */
    @Override
    public int updateBuilding(Building building) {
        building.setUpdateTime(DateUtils.getNowDate());
        return buildingMapper.updateBuilding(building);
    }

    /**
     * 批量删除建筑demo
     *
     * @param ids 需要删除的建筑demo主键
     * @return 结果
     */
    @Override
    public int deleteBuildingByIds(String ids) {
        return buildingMapper.deleteBuildingByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除建筑demo信息
     *
     * @param id 建筑demo主键
     * @return 结果
     */
    @Override
    public int deleteBuildingById(Long id) {
        return buildingMapper.deleteBuildingById(id);
    }

    @Override
    @Transactional
    public int importJson(MultipartFile file) throws IOException {
        String jsonContent = new String(file.getBytes(), "UTF-8");
        int successCount = 0;

        try {
            // 首先尝试解析为JSON数组
            JSONArray jsonArray = JSONArray.parseArray(jsonContent);
            if (jsonArray != null) {
                // 处理JSON数组格式
                for (int i = 0; i < jsonArray.size(); i++) {
                    JSONObject buildingJson = jsonArray.getJSONObject(i);
                    successCount += processBuilding(buildingJson);
                }
            }
        } catch (Exception e) {
            // 如果不是JSON数组，尝试解析为单个JSON对象
            try {
                JSONObject jsonObject = JSONObject.parseObject(jsonContent);
                if (jsonObject != null) {
                    successCount += processBuilding(jsonObject);
                }
            } catch (Exception ex) {
                throw new RuntimeException("无效的JSON格式");
            }
        }

        return successCount;
    }

    /**
     * 处理单个建筑的JSON数据
     */
    private int processBuilding(JSONObject jsonObject) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            return 0;
        }

        // 获取第一个key作为建筑名称
        String buildingName = jsonObject.keySet().iterator().next();
        JSONObject buildingData = jsonObject.getJSONObject(buildingName);

        Building building = new Building();
        building.setName(buildingName);
        building.setStatus("0"); // 默认正常状态
        building.setArea("0"); // 默认片区
        building.setLine("0"); // 默认线路
        building.setCreateTime(DateUtils.getNowDate());

        // 创建Building
        int rows = buildingMapper.insertBuilding(building);
        if (rows > 0) {
            // 创建BiObject根节点
            BiObject rootObject = new BiObject();
            rootObject.setName(buildingName);
            rootObject.setParentId(0L);
            rootObject.setAncestors("0");
            rootObject.setOrderNum(0); // 根节点序号为0
            rootObject.setStatus("0");

            // 插入BiObject根节点
            biObjectService.insertBiObject(rootObject);

            // 更新Building的rootObjectId
            building.setRootObjectId(rootObject.getId().toString());
            buildingMapper.updateBuilding(building);

            // 递归创建子节点
            createBiObjectTree(buildingData, rootObject.getId(), rootObject.getAncestors(), 1);

            return 1;
        }

        return 0;
    }

    /**
     * 递归创建BiObject树
     *
     * @param jsonObject      JSON对象
     * @param parentId        父节点ID
     * @param parentAncestors 父节点的祖先字符串
     * @param level           当前层级
     */
    private void createBiObjectTree(JSONObject jsonObject, Long parentId, String parentAncestors, int level) {
        if (jsonObject == null) {
            return;
        }

        int orderNum = 0; // 从0开始
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);

            BiObject biObject = new BiObject();
            biObject.setName(key);
            biObject.setParentId(parentId);
            biObject.setAncestors(parentAncestors + "," + parentId);
            biObject.setOrderNum(orderNum); // 直接使用orderNum作为排序号
            biObject.setStatus("0");

            // 插入节点
            biObjectService.insertBiObject(biObject);

            // 如果值是JSONObject，继续递归创建子节点
            if (value instanceof JSONObject) {
                createBiObjectTree((JSONObject) value, biObject.getId(), biObject.getAncestors(), level + 1);
            }
            // 如果值是JSONArray，为每个元素创建子节点
            else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                int childOrderNum = 0; // 子节点从0开始计数
                for (int i = 0; i < array.size(); i++) {
                    Object arrayValue = array.get(i);
                    if (arrayValue instanceof JSONObject) {
                        createBiObjectTree((JSONObject) arrayValue, biObject.getId(), biObject.getAncestors(), level + 1);
                    }
                    // 处理数组中的字符串元素，为每个字符串创建一个子节点
                    else if (arrayValue instanceof String || arrayValue instanceof Number || arrayValue instanceof Boolean) {
                        BiObject childObject = new BiObject();
                        childObject.setName(arrayValue.toString());
                        childObject.setParentId(biObject.getId());
                        childObject.setAncestors(biObject.getAncestors() + "," + biObject.getId());
                        childObject.setOrderNum(childOrderNum++); // 子节点按顺序编号
                        childObject.setStatus("0");

                        // 插入子节点
                        biObjectService.insertBiObject(childObject);
                    }
                }
            }
            // 如果值是基本类型，将值存储在remark中
            else if (value != null) {
                biObject.setRemark(value.toString());
                biObjectService.updateBiObject(biObject);
            }

            orderNum++;
        }
    }
}
