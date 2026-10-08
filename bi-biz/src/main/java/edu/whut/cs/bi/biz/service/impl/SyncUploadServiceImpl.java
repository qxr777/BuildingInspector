package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import edu.whut.cs.bi.biz.service.v2.V2InputRowValidator;
import edu.whut.cs.bi.biz.service.v2.V2TaskContextResolver;
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
    private SpanComponentPartMapper spanComponentPartMapper;
    @Autowired
    private V2ComponentInputMapper v2ComponentInputMapper;
    @Autowired
    private V2AbsentMarkMapper v2AbsentMarkMapper;
    @Autowired
    private V2SingleControlMarkMapper v2SingleControlMarkMapper;
    @Autowired
    private V2InputRowValidator v2InputRowValidator;
    @Autowired
    private V2TaskContextResolver v2TaskContextResolver;

    private static final String ENTITY_BUILDING = "Building";
    private static final String ENTITY_OBJECT = "BiObject";
    private static final String ENTITY_COMPONENT = "Component";
    private static final String ENTITY_DISEASE = "Disease";
    private static final String ENTITY_DISEASE_DETAIL = "DiseaseDetail";
    private static final String ENTITY_ATTACHMENT = "Attachment";
    private static final String ENTITY_SPAN_COMPONENT_PART = "SpanComponentPart";
    private static final String ENTITY_COMPONENT_INPUT = "V2ComponentInput";
    private static final String ENTITY_ABSENT_MARK = "V2AbsentMark";
    private static final String ENTITY_SINGLE_CONTROL_MARK = "V2SingleControlMark";

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

            log.info("处理 SpanComponentPart...");
            processSpanComponentParts(dataMap.get("spanComponentParts"), syncUuid, uuidMap, result, loginName);

            log.info("处理 V2 ComponentInput...");
            processComponentInputs(dataMap.get("componentInputs"), syncUuid, uuidMap, result, loginName);

            log.info("处理 V2 AbsentMark...");
            processAbsentMarks(dataMap.get("absentMarks"), syncUuid, uuidMap, result, loginName);

            log.info("处理 V2 SingleControlMark...");
            processSingleControlMarks(dataMap.get("singleControlMarks"), syncUuid, uuidMap, result, loginName);

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
                if (item.getParentUuid() != null && !item.getParentUuid().isEmpty()
                        && !"0".equals(item.getParentUuid())) {
                    parentId = uuidMap.get(item.getParentUuid());
                    if (parentId == null)
                        continue;
                }

                try {
                    item.setParentId(parentId);
                    // v1.6 拍板：bi_object 不加 building_id/building_uuid，object→building 归属
                    // 靠 bi_building.root_object_id 单向定位，采集节点随父链挂到 UNIT 根节点下即可
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

    private void processSpanComponentParts(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        if (data == null)
            return;
        List<SpanComponentPart> list = parseList(data, SpanComponentPart.class);
        for (SpanComponentPart item : list) {
            try {
                // 反查 serverId：component / span / part 三者都必须已落库
                if (item.getComponentUuid() != null)
                    item.setComponentId(uuidMap.get(item.getComponentUuid()));
                if (item.getSpanUuid() != null)
                    item.setSpanId(uuidMap.get(item.getSpanUuid()));
                if (item.getPartUuid() != null)
                    item.setPartId(uuidMap.get(item.getPartUuid()));

                SpanComponentPart existing = item.getOfflineUuid() != null
                        ? spanComponentPartMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        spanComponentPartMapper.deleteSpanComponentPartById(item.getId());
                        continue;
                    }
                    spanComponentPartMapper.updateSpanComponentPart(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }

                // 锚定三要素缺失则不落库（构件/桥跨/部件必须都已同步）
                if (item.getComponentId() == null || item.getSpanId() == null || item.getPartId() == null) {
                    result.addError(ENTITY_SPAN_COMPONENT_PART, item.getOfflineUuid(),
                            "component/span/part 反查失败，存在未同步实体");
                    continue;
                }

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                spanComponentPartMapper.insertSpanComponentPart(item);
                saveMapping(ENTITY_SPAN_COMPONENT_PART, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_SPAN_COMPONENT_PART, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    /**
     * 构件人工评定输入（EDDI/EFI/EAI/安全影响），App 按构件录入
     */
    private void processComponentInputs(Object data, String syncUuid, Map<String, Long> uuidMap,
            SyncResultVo result, String loginName) {
        if (data == null)
            return;
        List<V2ComponentInput> list = parseList(data, V2ComponentInput.class);
        Set<Long> knownTasks = new HashSet<>();
        Set<String> dedupKeys = new HashSet<>();
        for (V2ComponentInput item : list) {
            try {
                requireKnownTask(item.getTaskId(), knownTasks);

                if (item.getComponentUuid() != null)
                    item.setComponentId(uuidMap.get(item.getComponentUuid()));

                List<String> errors = v2InputRowValidator.validateComponent(item);
                if (!errors.isEmpty()) {
                    result.addError(ENTITY_COMPONENT_INPUT, item.getOfflineUuid(), String.join("; ", errors));
                    continue;
                }

                String dedupKey = item.getTaskId() + ":" + item.getComponentId();
                if (!dedupKeys.add(dedupKey)) {
                    result.addError(ENTITY_COMPONENT_INPUT, item.getOfflineUuid(), "同批次构件输入重复: " + dedupKey);
                    continue;
                }

                V2ComponentInput existing = item.getOfflineUuid() != null
                        ? v2ComponentInputMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        v2ComponentInputMapper.deleteById(item.getId());
                        continue;
                    }
                    v2ComponentInputMapper.update(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                v2ComponentInputMapper.insert(item);
                saveMapping(ENTITY_COMPONENT_INPUT, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_COMPONENT_INPUT, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    /**
     * 应设未设部件标记。spanUuid 为 null 表示全桥所有跨，spanId 保持 null 合法
     */
    private void processAbsentMarks(Object data, String syncUuid, Map<String, Long> uuidMap,
            SyncResultVo result, String loginName) {
        if (data == null)
            return;
        List<V2AbsentMark> list = parseList(data, V2AbsentMark.class);
        Set<Long> knownTasks = new HashSet<>();
        Map<Long, String> bridgeTypeCache = new HashMap<>();
        Set<String> dedupKeys = new HashSet<>();
        for (V2AbsentMark item : list) {
            try {
                requireKnownTask(item.getTaskId(), knownTasks);
                String bridgeType = bridgeTypeCache.computeIfAbsent(item.getTaskId(),
                        v2TaskContextResolver::bridgeTypeOf);

                if (item.getPartUuid() != null)
                    item.setPartId(uuidMap.get(item.getPartUuid()));
                if (item.getSpanUuid() == null) {
                    // null = 全桥所有跨；不得按反查失败处理
                    item.setSpanId(null);
                } else {
                    item.setSpanId(uuidMap.get(item.getSpanUuid()));
                    if (item.getSpanId() == null) {
                        result.addError(ENTITY_ABSENT_MARK, item.getOfflineUuid(),
                                "spanUuid 反查失败，桥跨未同步: " + item.getSpanUuid());
                        continue;
                    }
                }

                List<String> errors = v2InputRowValidator.validateAbsent(item, bridgeType);
                if (!errors.isEmpty()) {
                    result.addError(ENTITY_ABSENT_MARK, item.getOfflineUuid(), String.join("; ", errors));
                    continue;
                }

                String dedupKey = item.getTaskId() + ":" + item.getSpanId() + "|" + item.getPartId();
                if (!dedupKeys.add(dedupKey)) {
                    result.addError(ENTITY_ABSENT_MARK, item.getOfflineUuid(), "同批次应设未设标记重复: " + dedupKey);
                    continue;
                }

                V2AbsentMark existing = item.getOfflineUuid() != null
                        ? v2AbsentMarkMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        v2AbsentMarkMapper.deleteById(item.getId());
                        continue;
                    }
                    v2AbsentMarkMapper.update(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                v2AbsentMarkMapper.insert(item);
                saveMapping(ENTITY_ABSENT_MARK, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_ABSENT_MARK, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    /**
     * 16 项单项控制指标标记。componentUuid/spanUuid 均可空
     */
    private void processSingleControlMarks(Object data, String syncUuid, Map<String, Long> uuidMap,
            SyncResultVo result, String loginName) {
        if (data == null)
            return;
        List<V2SingleControlMark> list = parseList(data, V2SingleControlMark.class);
        Set<Long> knownTasks = new HashSet<>();
        Set<String> dedupKeys = new HashSet<>();
        for (V2SingleControlMark item : list) {
            try {
                requireKnownTask(item.getTaskId(), knownTasks);

                if (item.getComponentUuid() == null) {
                    item.setComponentId(null);
                } else {
                    item.setComponentId(uuidMap.get(item.getComponentUuid()));
                    if (item.getComponentId() == null) {
                        result.addError(ENTITY_SINGLE_CONTROL_MARK, item.getOfflineUuid(),
                                "componentUuid 反查失败，构件未同步: " + item.getComponentUuid());
                        continue;
                    }
                }
                if (item.getSpanUuid() == null) {
                    item.setSpanId(null);
                } else {
                    item.setSpanId(uuidMap.get(item.getSpanUuid()));
                    if (item.getSpanId() == null) {
                        result.addError(ENTITY_SINGLE_CONTROL_MARK, item.getOfflineUuid(),
                                "spanUuid 反查失败，桥跨未同步: " + item.getSpanUuid());
                        continue;
                    }
                }

                List<String> errors = v2InputRowValidator.validateControl(item);
                if (!errors.isEmpty()) {
                    result.addError(ENTITY_SINGLE_CONTROL_MARK, item.getOfflineUuid(), String.join("; ", errors));
                    continue;
                }

                String dedupKey = item.getTaskId() + ":" + item.getIndicatorNo();
                if (!dedupKeys.add(dedupKey)) {
                    result.addError(ENTITY_SINGLE_CONTROL_MARK, item.getOfflineUuid(),
                            "同批次单项控制标记重复: " + dedupKey);
                    continue;
                }

                V2SingleControlMark existing = item.getOfflineUuid() != null
                        ? v2SingleControlMarkMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        v2SingleControlMarkMapper.deleteById(item.getId());
                        continue;
                    }
                    v2SingleControlMarkMapper.update(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                v2SingleControlMarkMapper.insert(item);
                saveMapping(ENTITY_SINGLE_CONTROL_MARK, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_SINGLE_CONTROL_MARK, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    /**
     * 校验任务为 5230 任务；同批次内同 taskId 只查一次
     */
    private void requireKnownTask(Long taskId, Set<Long> cache) {
        if (!cache.contains(taskId)) {
            v2TaskContextResolver.require5230Task(taskId);
            cache.add(taskId);
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
        // addMapping 内部已经对 successCount +1，这里避免重复累加
        result.addMapping(type, uuid, id);
        idMappingMapper
                .insert(IdMapping.builder().entityType(type).offlineUuid(uuid).serverId(id).syncUuid(syncUuid).build());
    }
}
