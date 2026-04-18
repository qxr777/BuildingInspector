package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson.JSON;

import java.util.*;

/**
 * 离线数据上传同步服务实现类
 *
 * @author QiXin
 * @date 2026/04/13
 */
@Slf4j
@Service
public class SyncUploadServiceImpl implements ISyncUploadService {

    @Autowired
    private BuildingMapper buildingMapper;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private ComponentMapper componentMapper;
    @Autowired
    private DiseaseMapper diseaseMapper;
    @Autowired
    private DiseaseDetailMapper diseaseDetailMapper;
    @Autowired
    private AttachmentMapper attachmentMapper;
    @Autowired
    private IdMappingMapper idMappingMapper;
    @Autowired
    private SyncLogMapper syncLogMapper;
    @Autowired
    private BiObjectComponentMapper biObjectComponentMapper;
    @Autowired
    private edu.whut.cs.bi.biz.engine.BridgeEvaluationEngine bridgeEvaluationEngine;
    @Autowired
    private edu.whut.cs.bi.biz.mapper.BiEvalComponentDetailMapper biEvalComponentDetailMapper;

    private static final String ENTITY_BUILDING = "Building";
    private static final String ENTITY_OBJECT = "BiObject";
    private static final String ENTITY_COMPONENT = "Component";
    private static final String ENTITY_DISEASE = "Disease";
    private static final String ENTITY_DISEASE_DETAIL = "DiseaseDetail";
    private static final String ENTITY_ATTACHMENT = "Attachment";
    private static final String ENTITY_OBJECT_COMPONENT = "BiObjectComponent";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncResultVo syncUpload(Map<String, Object> dataMap) {
        String syncUuid = (String) dataMap.get("syncUuid");
        if (syncUuid == null)
            syncUuid = UUID.randomUUID().toString();
        log.info("开始处理离线同步请求, UUID: {}", syncUuid);

        SyncResultVo result = new SyncResultVo();

        // 1. 记录同步日志
        try {
            Long userId = null;
            try {
                userId = ShiroUtils.getUserId();
            } catch (Exception ignored) {
            }

            SyncLog syncLog = SyncLog.builder()
                    .syncUuid(syncUuid)
                    .userId(userId)
                    .status(0)
                    .clientInfo((String) dataMap.get("clientInfo"))
                    .remark("开始同步")
                    .build();
            syncLogMapper.insert(syncLog);
        } catch (Exception e) {
            log.warn("无法插入同步日志记录: {}", e.getMessage());
        }

        String loginName = "system";
        try {
            loginName = ShiroUtils.getLoginName();
        } catch (Exception ignored) {
        }

        try {
            Map<String, Long> uuidMap = new HashMap<>();

            log.info("处理 Building...");
            processBuildings(dataMap.get("buildings"), syncUuid, uuidMap, result, loginName);

            log.info("处理 BiObject...");
            processBiObjects(dataMap.get("objects"), syncUuid, uuidMap, result, loginName);

            log.info("补充处理 Building RootIDs...");
            processBuildingsRootIds(dataMap.get("buildings"), uuidMap);

            log.info("处理 Component...");
            processComponents(dataMap.get("components"), syncUuid, uuidMap, result, loginName);

            log.info("处理 Disease...");
            processDiseases(dataMap.get("diseases"), syncUuid, uuidMap, result, loginName);

            log.info("处理 DiseaseDetail...");
            processDiseaseDetails(dataMap.get("diseaseDetails"), syncUuid, uuidMap, result);

            log.info("处理 Attachment...");
            processAttachments(dataMap.get("attachments"), syncUuid, uuidMap, result, loginName);

            // 阶段 8: 触发 2026 新标评定
            try {
                Set<Long> affectedSpanIds = new HashSet<>();
                processBiObjectComponents(dataMap.get("biObjectComponents"), syncUuid, uuidMap, result, loginName,
                        affectedSpanIds);

                // 同时也要考虑普通构件归属的变化对跨评定的影响
                List<Component> clientComponents = parseList(dataMap.get("components"), Component.class);
                for (Component clientComp : clientComponents) {
                    Long serverId = uuidMap.get(clientComp.getOfflineUuid());
                    if (serverId != null) {
                        Component serverComp = componentMapper.selectComponentById(serverId);
                        if (serverComp != null && serverComp.getBiObjectId() != null) {
                            affectedSpanIds.add(serverComp.getBiObjectId());
                        }
                    }
                }

                if (!affectedSpanIds.isEmpty()) {
                    Long taskId = null;
                    Object taskIdObj = dataMap.get("taskId");
                    if (taskIdObj != null)
                        taskId = Long.valueOf(taskIdObj.toString());

                    if (taskId == null) {
                        List<Disease> diseases = parseList(dataMap.get("diseases"), Disease.class);
                        if (!diseases.isEmpty() && diseases.get(0).getTaskId() != null) {
                            taskId = diseases.get(0).getTaskId();
                        }
                    }

                    if (taskId != null) {
                        log.info("开始进行构件评定标度数据转移...");
                        List<edu.whut.cs.bi.biz.domain.BiEvalComponentDetail> detailList = new ArrayList<>();
                        for (Long spanId : affectedSpanIds) {
                            // 使用专用的评定构件检索方法 (包含物理归属与逻辑关联)
                            List<Component> components = componentMapper.selectComponentsByObjectIdForEval(spanId);
                            for (Component c : components) {
                                Disease diseaseQuery = new Disease();
                                diseaseQuery.setTaskId(taskId);
                                diseaseQuery.setComponentId(c.getId());
                                List<Disease> diseasesOfComp = diseaseMapper.selectDiseaseList(diseaseQuery);

                                Integer edi = null;
                                Integer efi = 0;
                                Integer eai = -1;
                                boolean hasDiseaseMetrics = false;

                                if (diseasesOfComp != null && !diseasesOfComp.isEmpty()) {
                                    for (Disease d : diseasesOfComp) {
                                        if (d.getEdi() != null) {
                                            hasDiseaseMetrics = true;
                                            if (edi == null || d.getEdi() > edi)
                                                edi = d.getEdi();
                                        }
                                        if (d.getEfi() != null) {
                                            hasDiseaseMetrics = true;
                                            if (d.getEfi() > efi)
                                                efi = d.getEfi();
                                        }
                                        if (d.getEai() != null) {
                                            hasDiseaseMetrics = true;
                                            if (d.getEai() > eai)
                                                eai = d.getEai();
                                        }
                                    }
                                }

                                if (!hasDiseaseMetrics) {
                                    edi = c.getEdi();
                                    if (c.getEfi() != null)
                                        efi = c.getEfi();
                                    if (c.getEai() != null)
                                        eai = c.getEai();
                                }

                                if (edi != null || c.getEdi() != null) {
                                    edu.whut.cs.bi.biz.domain.BiEvalComponentDetail detail = new edu.whut.cs.bi.biz.domain.BiEvalComponentDetail();
                                    detail.setTaskId(taskId);
                                    detail.setSpanId(spanId);
                                    detail.setComponentId(c.getId());
                                    detail.setEdi(edi != null ? edi : c.getEdi());
                                    detail.setEfi(efi);
                                    detail.setEai(eai);
                                    detailList.add(detail);
                                }
                            }
                        }
                        if (!detailList.isEmpty()) {
                            biEvalComponentDetailMapper.batchInsert(detailList);
                            log.info("成功转移 {} 条评定细目", detailList.size());
                        }

                        log.info("开始触发分跨评定, 共 {} 跨", affectedSpanIds.size());
                        for (Long spanId : affectedSpanIds) {
                            bridgeEvaluationEngine.evaluate("SPAN", spanId, taskId);
                        }

                        Long buildingId = null;
                        List<Building> buildings = parseList(dataMap.get("buildings"), Building.class);
                        if (!buildings.isEmpty()) {
                            buildingId = uuidMap.get(buildings.get(0).getOfflineUuid());
                        }
                        if (buildingId != null) {
                            bridgeEvaluationEngine.evaluate("BRIDGE", buildingId, taskId);
                        }
                    }
                }
            } catch (Exception ee) {
                log.error("自动评定失败: {}", ee.getMessage());
            }

            syncLogMapper.updateStatus(syncUuid, 1, "同步成功");

        } catch (Exception e) {
            log.error("离线同步失败", e);
            syncLogMapper.updateStatus(syncUuid, 2, "异常: " + e.getMessage());
            throw e;
        }

        return result;
    }

    private void processBuildings(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        List<Building> list = parseList(data, Building.class);
        for (Building item : list) {
            try {
                Building existing = buildingMapper.selectByOfflineUuid(item.getOfflineUuid());
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        buildingMapper.deleteBuildingById(item.getId());
                        continue;
                    }
                    buildingMapper.updateBuilding(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }
                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                buildingMapper.insertBuilding(item);
                saveMapping(ENTITY_BUILDING, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_BUILDING, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private void processBiObjects(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        List<BiObject> list = parseList(data, BiObject.class);
        int total = list.size();
        Set<String> processedUuids = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            int roundCount = 0;
            for (BiObject item : list) {
                if (processedUuids.contains(item.getOfflineUuid()))
                    continue;

                Long parentId = 0L;
                if (item.getParentUuid() != null && !item.getParentUuid().isEmpty()) {
                    parentId = uuidMap.get(item.getParentUuid());
                    if (parentId == null)
                        continue;
                }

                try {
                    item.setParentId(parentId);
                    if (item.getBuildingUuid() != null)
                        item.setBuildingId(uuidMap.get(item.getBuildingUuid()));
                    BiObject existing = biObjectMapper.selectByOfflineUuid(item.getOfflineUuid());
                    if (existing != null) {
                        uuidMap.put(item.getOfflineUuid(), existing.getId());
                        item.setId(existing.getId());
                        item.setUpdateBy(loginName);
                        item.setUpdateTime(DateUtils.getNowDate());
                        processedUuids.add(item.getOfflineUuid());
                        roundCount++;
                        if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                            biObjectMapper.deleteBiObjectById(item.getId());
                            continue;
                        }
                        biObjectMapper.updateBiObject(item);
                        result.setSuccessCount(result.getSuccessCount() + 1);
                        continue;
                    }
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        processedUuids.add(item.getOfflineUuid());
                        roundCount++;
                        continue;
                    }

                    if (parentId != 0L) {
                        BiObject parentNode = biObjectMapper.selectBiObjectById(parentId);
                        item.setAncestors(
                                parentNode != null ? parentNode.getAncestors() + "," + parentId : "0," + parentId);
                    } else {
                        item.setAncestors("0");
                    }

                    item.setIsOfflineData(1);
                    item.setCreateBy(loginName);
                    item.setCreateTime(DateUtils.getNowDate());
                    biObjectMapper.insertBiObject(item);

                    saveMapping(ENTITY_OBJECT, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
                    processedUuids.add(item.getOfflineUuid());
                    roundCount++;
                } catch (Exception e) {
                    result.addError(ENTITY_OBJECT, item.getOfflineUuid(), e.getMessage());
                    processedUuids.add(item.getOfflineUuid());
                }
            }
            if (processedUuids.size() == total || roundCount == 0)
                break;
        }
    }

    private void processComponents(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        List<Component> list = parseList(data, Component.class);
        for (Component item : list) {
            try {
                if (item.getObjectUuid() != null)
                    item.setBiObjectId(uuidMap.get(item.getObjectUuid()));
                Component existing = componentMapper.selectByOfflineUuid(item.getOfflineUuid());
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        componentMapper.deleteComponentById(item.getId());
                        continue;
                    }
                    componentMapper.updateComponent(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }
                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                componentMapper.insertComponent(item);
                if (item.getId() != null && item.getBiObjectId() != null) {
                    BiObjectComponent rel = new BiObjectComponent();
                    rel.setComponentId(item.getId());
                    rel.setBiObjectId(item.getBiObjectId());
                    rel.setWeight(new java.math.BigDecimal("1.0"));
                    rel.setCreateBy(loginName);
                    rel.setCreateTime(item.getCreateTime());
                    biObjectComponentMapper.insertBiObjectComponent(rel);
                }
                saveMapping(ENTITY_COMPONENT, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_COMPONENT, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private void processDiseases(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        List<Disease> list = parseList(data, Disease.class);
        for (Disease item : list) {
            try {
                if (item.getBuildingUuid() != null)
                    item.setBuildingId(uuidMap.get(item.getBuildingUuid()));
                if (item.getObjectUuid() != null)
                    item.setBiObjectId(uuidMap.get(item.getObjectUuid()));
                if (item.getComponentUuid() != null)
                    item.setComponentId(uuidMap.get(item.getComponentUuid()));
                Disease existing = diseaseMapper.selectByOfflineUuid(item.getOfflineUuid());
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        diseaseMapper.deleteDiseaseById(item.getId());
                        continue;
                    }
                    diseaseMapper.updateDisease(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }
                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                diseaseMapper.insertDisease(item);
                saveMapping(ENTITY_DISEASE, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_DISEASE, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private void processDiseaseDetails(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result) {
        List<DiseaseDetail> list = parseList(data, DiseaseDetail.class);
        for (DiseaseDetail item : list) {
            try {
                if (item.getDiseaseUuid() != null)
                    item.setDiseaseId(uuidMap.get(item.getDiseaseUuid()));
                DiseaseDetail existing = item.getOfflineUuid() != null ? diseaseDetailMapper.selectByOfflineUuid(item.getOfflineUuid()) : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        diseaseDetailMapper.deleteDiseaseDetailById(item.getId());
                        continue;
                    }
                    diseaseDetailMapper.updateDiseaseDetail(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }
                item.setIsOfflineData(1);
                diseaseDetailMapper.insertDiseaseDetail(item);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.addError(ENTITY_DISEASE_DETAIL, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private void processAttachments(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        List<Attachment> list = parseList(data, Attachment.class);
        for (Attachment item : list) {
            try {
                if (item.getOfflineSubjectUuid() != null)
                    item.setSubjectId(uuidMap.get(item.getOfflineSubjectUuid()));
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    // For attachments, we skip or we can delete if it already exists, assuming offline_deleted implies we skip insertion if it's new.
                    // If we need true deletion for attachments we should check if they exist first. For now skip insertion.
                    continue;
                }
                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                attachmentMapper.insert(item);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.addError(ENTITY_ATTACHMENT, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private void processBuildingsRootIds(Object data, Map<String, Long> uuidMap) {
        List<Building> list = parseList(data, Building.class);
        for (Building item : list) {
            Long rootId = uuidMap.get(item.getRootObjectUuid());
            Long buildingId = uuidMap.get(item.getOfflineUuid());
            if (rootId != null && buildingId != null) {
                Building update = new Building();
                update.setId(buildingId);
                update.setRootObjectId(rootId);
                buildingMapper.updateBuilding(update);
            }
        }
    }

    private void processBiObjectComponents(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName, Set<Long> affectedSpanIds) {
        if (data == null)
            return;
        List<BiObjectComponent> list = parseList(data, BiObjectComponent.class);
        for (BiObjectComponent item : list) {
            try {
                if (item.getComponentUuid() != null)
                    item.setComponentId(uuidMap.get(item.getComponentUuid()));
                if (item.getObjectUuid() != null)
                    item.setBiObjectId(uuidMap.get(item.getObjectUuid()));
                List<BiObjectComponent> existings = biObjectComponentMapper.selectBiObjectComponentList(new BiObjectComponent() {{ setOfflineUuid(item.getOfflineUuid()); }});
                if (!existings.isEmpty()) {
                    BiObjectComponent existing = existings.get(0);
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        biObjectComponentMapper.deleteBiObjectComponentById(item.getId());
                        continue;
                    }
                    biObjectComponentMapper.updateBiObjectComponent(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }

                if (item.getComponentId() == null || item.getBiObjectId() == null)
                    continue;

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                biObjectComponentMapper.insertBiObjectComponent(item);

                affectedSpanIds.add(item.getBiObjectId());
                saveMapping(ENTITY_OBJECT_COMPONENT, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_OBJECT_COMPONENT, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private <T> List<T> parseList(Object data, Class<T> clazz) {
        if (data == null)
            return new ArrayList<>();
        return JSON.parseArray(JSON.toJSONString(data), clazz);
    }

    private void saveMapping(String type, String uuid, Long id, String syncUuid, Map<String, Long> uuidMap,
            SyncResultVo result) {
        if (uuid == null || uuid.isEmpty())
            return;
        uuidMap.put(uuid, id);
        result.addMapping(type, uuid, id);
        result.setSuccessCount(result.getSuccessCount() + 1);
        idMappingMapper
                .insert(IdMapping.builder().entityType(type).offlineUuid(uuid).serverId(id).syncUuid(syncUuid).build());
    }
}
