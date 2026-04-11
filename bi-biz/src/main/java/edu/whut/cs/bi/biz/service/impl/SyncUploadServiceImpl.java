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
 * @date 2026/04/09
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

    private static final String ENTITY_BUILDING = "Building";
    private static final String ENTITY_OBJECT = "BiObject";
    private static final String ENTITY_COMPONENT = "Component";
    private static final String ENTITY_DISEASE = "Disease";
    private static final String ENTITY_DISEASE_DETAIL = "DiseaseDetail";
    private static final String ENTITY_ATTACHMENT = "Attachment";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncResultVo syncUpload(Map<String, Object> dataMap) {
        String syncUuid = (String) dataMap.get("syncUuid");
        if (syncUuid == null)
            syncUuid = UUID.randomUUID().toString();
        log.info("开始处理离线同步请求, UUID: {}", syncUuid);

        SyncResultVo result = new SyncResultVo();

        // 1. 记录同步日志 (不影响主流程)
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

            log.info("阶段 2: 处理 Building...");
            processBuildings(dataMap.get("buildings"), syncUuid, uuidMap, result, loginName);

            log.info("阶段 3: 处理 BiObject...");
            processBiObjects(dataMap.get("objects"), syncUuid, uuidMap, result, loginName);

            log.info("阶段 2.5: 补充处理 Building...");
            processBuildingsRootIds(dataMap.get("buildings"), uuidMap);

            log.info("阶段 4: 处理 Component...");
            processComponents(dataMap.get("components"), syncUuid, uuidMap, result, loginName);

            log.info("阶段 5: 处理 Disease...");
            processDiseases(dataMap.get("diseases"), syncUuid, uuidMap, result, loginName);

            log.info("阶段 6: 处理 DiseaseDetail...");
            processDiseaseDetails(dataMap.get("diseaseDetails"), syncUuid, uuidMap, result);

            log.info("阶段 7: 处理 Attachment...");
            processAttachments(dataMap.get("attachments"), syncUuid, uuidMap, result, loginName);

            syncLogMapper.updateStatus(syncUuid, 1, "同步成功: " + result.getSuccessCount() + " 条记录");
            log.info("离线同步处理完成, UUID: {}", syncUuid);

        } catch (Exception e) {
            log.error("离线同步致命错误, UUID: " + syncUuid, e);
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
                log.debug("同步 Building: {} (OfflineUUID: {})", item.getName(), item.getOfflineUuid());
                Building existing = buildingMapper.selectByOfflineUuid(item.getOfflineUuid());
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
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

        for (int i = 0; i < 10; i++) { // 增加循环层数，应对更复杂的树结构
            int roundCount = 0;
            for (BiObject item : list) {
                if (processedUuids.contains(item.getOfflineUuid()))
                    continue;

                // 检查父节点是否已就绪
                Long parentId = 0L;
                if (item.getParentUuid() != null && !item.getParentUuid().isEmpty()) {
                    parentId = uuidMap.get(item.getParentUuid());
                    if (parentId == null) {
                        IdMapping mapping = idMappingMapper.selectByOfflineUuid(ENTITY_OBJECT, item.getParentUuid());
                        if (mapping != null)
                            parentId = mapping.getServerId();
                    }
                    if (parentId == null)
                        continue; // 父节点还没处理，等下一轮
                }

                try {
                    log.debug("同步 BiObject: {} (OfflineUUID: {})", item.getName(), item.getOfflineUuid());
                    BiObject existing = biObjectMapper.selectByOfflineUuid(item.getOfflineUuid());
                    if (existing != null) {
                        uuidMap.put(item.getOfflineUuid(), existing.getId());
                        processedUuids.add(item.getOfflineUuid());
                        roundCount++;
                        continue;
                    }

                    // 处理关联 ID
                    item.setParentId(parentId);
                    if (item.getBuildingUuid() != null) {
                        item.setBuildingId(uuidMap.get(item.getBuildingUuid()));
                    }

                    // 重新生成祖级列表 (ancestors)，确保云端树形结构路径正确
                    if (parentId == 0L) {
                        item.setAncestors("0");
                    } else {
                        BiObject parentNode = biObjectMapper.selectBiObjectById(parentId);
                        if (parentNode != null) {
                            item.setAncestors(parentNode.getAncestors() + "," + parentId);
                        } else {
                            item.setAncestors("0," + parentId); // 兜底方案
                        }
                    }

                    item.setIsOfflineData(1);
                    item.setCreateBy(loginName);
                    item.setCreateTime(DateUtils.getNowDate());
                    biObjectMapper.insertBiObject(item);

                    // 方案B：如果是顶层节点且关联了桥梁，自动更新桥梁的 root_object_id 和 root_object_uuid
                    // if (item.getParentUuid() == null && item.getBuildingId() != null) {
                    // Building b = new Building();
                    // b.setId(item.getBuildingId());
                    // b.setRootObjectId(item.getId());
                    // b.setRootObjectUuid(item.getOfflineUuid());
                    // buildingMapper.updateBuilding(b);
                    // log.info("已自动更新桥梁 (ID:{}) 的根对象 ID 为 {}, UUID 为 {}", item.getBuildingId(),
                    // item.getId(),
                    // item.getOfflineUuid());
                    // }

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
                log.debug("同步 Component: {} (OfflineUUID: {})", item.getName(), item.getOfflineUuid());
                Component existing = componentMapper.selectByOfflineUuid(item.getOfflineUuid());
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    continue;
                }

                if (item.getObjectUuid() != null) {
                    item.setBiObjectId(uuidMap.get(item.getObjectUuid()));
                }

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                componentMapper.insertComponent(item);

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
                log.debug("同步 Disease: {} (OfflineUUID: {})", item.getDescription(), item.getOfflineUuid());
                Disease existing = diseaseMapper.selectByOfflineUuid(item.getOfflineUuid());
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    continue;
                }

                if (item.getBuildingUuid() != null)
                    item.setBuildingId(uuidMap.get(item.getBuildingUuid()));
                if (item.getObjectUuid() != null)
                    item.setBiObjectId(uuidMap.get(item.getObjectUuid()));
                if (item.getComponentUuid() != null)
                    item.setComponentId(uuidMap.get(item.getComponentUuid()));

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
                log.info("同步 DiseaseDetail (OfflineUUID: {})", item.getOfflineUuid());
                // 增加 DiseaseDetail 幂等校验
                if (item.getOfflineUuid() != null) {
                    DiseaseDetail existing = diseaseDetailMapper.selectByOfflineUuid(item.getOfflineUuid());
                    if (existing != null)
                        continue;
                }

                log.info("同步 DiseaseDetail (OfflineUUID: {}, DiseaseUuid: {})", item.getOfflineUuid(),
                        item.getDiseaseUuid());
                if (item.getDiseaseUuid() != null) {
                    Long serverId = uuidMap.get(item.getDiseaseUuid());
                    log.info("  -> 映射 DiseaseUuid {} 到 ServerId: {}", item.getDiseaseUuid(), serverId);
                    item.setDiseaseId(serverId);
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
                if (item.getOfflineSubjectUuid() != null) {
                    item.setSubjectId(uuidMap.get(item.getOfflineSubjectUuid()));
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
            String rootUuid = item.getRootObjectUuid();
            if (rootUuid != null && !rootUuid.isEmpty()) {
                Long rootId = uuidMap.get(rootUuid);
                if (rootId != null) {
                    // 获取 Building 的 server id
                    Long buildingId = uuidMap.get(item.getOfflineUuid());
                    if (buildingId != null) {
                        Building update = new Building();
                        update.setId(buildingId);
                        update.setRootObjectId(rootId);
                        buildingMapper.updateBuilding(update);
                        log.info("阶段 2.5: 已更新桥梁 {} 的 root_object_id 为 {}", buildingId, rootId);
                    }
                }
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

        IdMapping mapping = IdMapping.builder()
                .entityType(type)
                .offlineUuid(uuid)
                .serverId(id)
                .syncUuid(syncUuid)
                .build();
        idMappingMapper.insert(mapping);
    }
}
