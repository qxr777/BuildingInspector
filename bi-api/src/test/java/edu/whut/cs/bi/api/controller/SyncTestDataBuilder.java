package edu.whut.cs.bi.api.controller;

import java.util.*;

/**
 * E2E 测试数据生成工具类
 *
 * 用于快速构建各种同步测试场景的载荷数据
 *
 * @author QiXin
 * @date 2026/04/22
 */
public class SyncTestDataBuilder {

    private final String syncUuid;
    private final List<Map<String, Object>> buildings;
    private final List<Map<String, Object>> objects;
    private final List<Map<String, Object>> components;
    private final List<Map<String, Object>> diseases;
    private final List<Map<String, Object>> diseaseDetails;
    private final List<Map<String, Object>> attachments;
    private final List<Map<String, Object>> biObjectComponents;
    private String clientInfo;

    private SyncTestDataBuilder() {
        this.syncUuid = UUID.randomUUID().toString();
        this.buildings = new ArrayList<>();
        this.objects = new ArrayList<>();
        this.components = new ArrayList<>();
        this.diseases = new ArrayList<>();
        this.diseaseDetails = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.biObjectComponents = new ArrayList<>();
        this.clientInfo = "TestDataBuilder/1.0";
    }

    public static SyncTestDataBuilder create() {
        return new SyncTestDataBuilder();
    }

    public SyncTestDataBuilder withClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
        return this;
    }

    // ==================== Building 相关方法 ====================

    /**
     * 添加 Building（新增）
     */
    public SyncTestDataBuilder addBuilding(String offlineUuid, String name) {
        return addBuilding(offlineUuid, name, "测试区域", "测试线路", "0", "1", false);
    }

    /**
     * 添加 Building（完整参数）
     */
    public SyncTestDataBuilder addBuilding(String offlineUuid, String name, String area,
                                            String line, String status, String isLeaf,
                                            boolean deleted) {
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", offlineUuid);
        building.put("name", name);
        building.put("area", area);
        building.put("line", line);
        building.put("status", status);
        building.put("isLeaf", isLeaf);
        building.put("offlineDeleted", deleted ? 1 : 0);
        buildings.add(building);
        return this;
    }

    /**
     * 添加 Building（更新场景）
     */
    public SyncTestDataBuilder updateBuilding(String offlineUuid, String newName, String newArea) {
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", offlineUuid);
        building.put("name", newName);
        building.put("area", newArea);
        building.put("offlineDeleted", 0);
        buildings.add(building);
        return this;
    }

    /**
     * 添加 Building（删除场景）
     */
    public SyncTestDataBuilder deleteBuilding(String offlineUuid) {
        Map<String, Object> building = new HashMap<>();
        building.put("offlineUuid", offlineUuid);
        building.put("offlineDeleted", 1);
        buildings.add(building);
        return this;
    }

    // ==================== BiObject 相关方法 ====================

    /**
     * 添加 BiObject（根节点）
     */
    public SyncTestDataBuilder addRootObject(String offlineUuid, String name) {
        return addObject(offlineUuid, name, "0", null, false);
    }

    /**
     * 添加 BiObject（子节点）
     */
    public SyncTestDataBuilder addChildObject(String offlineUuid, String name,
                                               String parentUuid, String buildingUuid) {
        return addObject(offlineUuid, name, parentUuid, buildingUuid, false);
    }

    /**
     * 添加 BiObject（完整参数）
     */
    public SyncTestDataBuilder addObject(String offlineUuid, String name, String parentUuid,
                                          String buildingUuid, boolean deleted) {
        Map<String, Object> object = new HashMap<>();
        object.put("offlineUuid", offlineUuid);
        object.put("name", name);
        object.put("parentUuid", parentUuid);
        if (buildingUuid != null) {
            object.put("buildingUuid", buildingUuid);
        }
        object.put("status", "0");
        object.put("offlineDeleted", deleted ? 1 : 0);
        objects.add(object);
        return this;
    }

    /**
     * 删除 BiObject
     */
    public SyncTestDataBuilder deleteObject(String offlineUuid) {
        Map<String, Object> object = new HashMap<>();
        object.put("offlineUuid", offlineUuid);
        object.put("offlineDeleted", 1);
        objects.add(object);
        return this;
    }

    // ==================== Component 相关方法 ====================

    /**
     * 添加 Component
     */
    public SyncTestDataBuilder addComponent(String offlineUuid, String name,
                                            String code, String objectUuid) {
        return addComponent(offlineUuid, name, code, objectUuid, false);
    }

    /**
     * 添加 Component（完整参数）
     */
    public SyncTestDataBuilder addComponent(String offlineUuid, String name, String code,
                                            String objectUuid, boolean deleted) {
        Map<String, Object> component = new HashMap<>();
        component.put("offlineUuid", offlineUuid);
        component.put("name", name);
        component.put("code", code);
        component.put("objectUuid", objectUuid);
        component.put("status", "0");
        component.put("offlineDeleted", deleted ? 1 : 0);
        components.add(component);
        return this;
    }

    /**
     * 删除 Component
     */
    public SyncTestDataBuilder deleteComponent(String offlineUuid) {
        Map<String, Object> component = new HashMap<>();
        component.put("offlineUuid", offlineUuid);
        component.put("offlineDeleted", 1);
        components.add(component);
        return this;
    }

    // ==================== Disease 相关方法 ====================

    /**
     * 添加 Disease
     */
    public SyncTestDataBuilder addDisease(String offlineUuid, String description,
                                          String buildingUuid, String objectUuid,
                                          String componentUuid) {
        return addDisease(offlineUuid, description, buildingUuid, objectUuid,
                         componentUuid, "1", 2, 3, false);
    }

    /**
     * 添加 Disease（完整参数）
     */
    public SyncTestDataBuilder addDisease(String offlineUuid, String description,
                                          String buildingUuid, String objectUuid,
                                          String componentUuid, String type,
                                          int level, int quantity, boolean deleted) {
        Map<String, Object> disease = new HashMap<>();
        disease.put("offlineUuid", offlineUuid);
        disease.put("description", description);
        disease.put("buildingUuid", buildingUuid);
        disease.put("objectUuid", objectUuid);
        disease.put("componentUuid", componentUuid);
        disease.put("type", type);
        disease.put("level", level);
        disease.put("quantity", quantity);
        disease.put("offlineDeleted", deleted ? 1 : 0);
        diseases.add(disease);
        return this;
    }

    /**
     * 删除 Disease
     */
    public SyncTestDataBuilder deleteDisease(String offlineUuid) {
        Map<String, Object> disease = new HashMap<>();
        disease.put("offlineUuid", offlineUuid);
        disease.put("offlineDeleted", 1);
        diseases.add(disease);
        return this;
    }

    // ==================== DiseaseDetail 相关方法 ====================

    /**
     * 添加 DiseaseDetail
     */
    public SyncTestDataBuilder addDiseaseDetail(String diseaseUuid, String location,
                                                 double width, double length) {
        return addDiseaseDetail(null, diseaseUuid, location, width, length, false);
    }

    /**
     * 添加 DiseaseDetail（完整参数）
     */
    public SyncTestDataBuilder addDiseaseDetail(String offlineUuid, String diseaseUuid,
                                                 String location, double width, double length,
                                                 boolean deleted) {
        Map<String, Object> detail = new HashMap<>();
        if (offlineUuid != null) {
            detail.put("offlineUuid", offlineUuid);
        }
        detail.put("diseaseUuid", diseaseUuid);
        detail.put("reference1Location", location);
        detail.put("width", width);
        detail.put("length1", length);
        detail.put("offlineDeleted", deleted ? 1 : 0);
        diseaseDetails.add(detail);
        return this;
    }

    // ==================== BiObjectComponent 相关方法 ====================

    /**
     * 添加 BiObjectComponent
     */
    public SyncTestDataBuilder addBiObjectComponent(String offlineUuid, String componentUuid,
                                                     String objectUuid, double weight) {
        return addBiObjectComponent(offlineUuid, componentUuid, objectUuid, weight, false);
    }

    /**
     * 添加 BiObjectComponent（完整参数）
     */
    public SyncTestDataBuilder addBiObjectComponent(String offlineUuid, String componentUuid,
                                                     String objectUuid, double weight,
                                                     boolean deleted) {
        Map<String, Object> rel = new HashMap<>();
        rel.put("offlineUuid", offlineUuid);
        rel.put("componentUuid", componentUuid);
        rel.put("objectUuid", objectUuid);
        rel.put("weight", weight);
        rel.put("offlineDeleted", deleted ? 1 : 0);
        biObjectComponents.add(rel);
        return this;
    }

    // ==================== 构建方法 ====================

    /**
     * 构建最终的载荷 Map
     */
    public Map<String, Object> build() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", syncUuid);
        payload.put("clientInfo", clientInfo);
        payload.put("buildings", buildings);
        payload.put("objects", objects);
        payload.put("components", components);
        payload.put("diseases", diseases);
        payload.put("diseaseDetails", diseaseDetails);
        payload.put("attachments", attachments);
        payload.put("biObjectComponents", biObjectComponents);
        return payload;
    }

    /**
     * 构建为 JSON 字符串
     */
    public String buildJson() {
        return com.alibaba.fastjson.JSON.toJSONString(build());
    }

    // ==================== 预设场景 ====================

    /**
     * 场景：完整的桥梁检测数据（新建）
     */
    public static Map<String, Object> buildCompleteBridgeScenario() {
        String buildingUuid = "bridge-" + UUID.randomUUID();
        String objectUuid = "span-" + UUID.randomUUID();
        String componentUuid = "beam-" + UUID.randomUUID();
        String diseaseUuid = "disease-" + UUID.randomUUID();

        return create()
            .addBuilding(buildingUuid, "测试桥梁", "武汉市", "二环线", "0", "1", false)
            .addRootObject(objectUuid, "第一跨")
            .addComponent(componentUuid, "左侧梁", "L01", objectUuid)
            .addDisease(diseaseUuid, "梁体裂缝", buildingUuid, objectUuid, componentUuid)
            .addDiseaseDetail(diseaseUuid, "跨中", 0.3, 150.0)
            .build();
    }

    /**
     * 场景：仅 Building
     */
    public static Map<String, Object> buildMinimalScenario(String name) {
        return create()
            .addBuilding("building-" + UUID.randomUUID(), name)
            .build();
    }

    /**
     * 场景：删除操作
     */
    public static Map<String, Object> buildDeleteScenario(String buildingUuid) {
        return create()
            .deleteBuilding(buildingUuid)
            .build();
    }

    /**
     * 场景：树形结构（乱序提交）
     */
    public static Map<String, Object> buildTreeScenario() {
        String rootUuid = "root-" + UUID.randomUUID();
        String middleUuid = "middle-" + UUID.randomUUID();
        String childUuid = "child-" + UUID.randomUUID();

        SyncTestDataBuilder builder = create();

        // 故意乱序：子节点在前
        builder.addChildObject(childUuid, "子节点", middleUuid, null);
        builder.addChildObject(middleUuid, "中间节点", rootUuid, null);
        builder.addRootObject(rootUuid, "根节点");

        return builder.build();
    }

    /**
     * 场景：空载荷
     */
    public static Map<String, Object> buildEmptyScenario() {
        return create().build();
    }
}
