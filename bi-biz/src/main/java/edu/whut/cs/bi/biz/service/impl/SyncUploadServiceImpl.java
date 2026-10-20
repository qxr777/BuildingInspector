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
    private SpanComponentPartMapper spanComponentPartMapper;
    @Autowired
    private ElementMapper elementMapper;
    @Autowired
    private SpanElementPartMapper spanElementPartMapper;

    private static final String ENTITY_BUILDING = "Building";
    private static final String ENTITY_OBJECT = "BiObject";
    private static final String ENTITY_COMPONENT = "Component";
    private static final String ENTITY_ELEMENT = "Element";
    private static final String ENTITY_DISEASE = "Disease";
    private static final String ENTITY_DISEASE_DETAIL = "DiseaseDetail";
    private static final String ENTITY_ATTACHMENT = "Attachment";
    private static final String ENTITY_SPAN_COMPONENT_PART = "SpanComponentPart";
    private static final String ENTITY_SPAN_ELEMENT_PART = "SpanElementPart";

    /** bi_attachment.type：1 普通病害照片（缺省兜底），6 桥梁正/侧立面照片，8 部件当前照片 */
    private static final int ATTACHMENT_TYPE_DISEASE = 1;
    private static final int ATTACHMENT_TYPE_BUILDING = 6;
    private static final int ATTACHMENT_TYPE_PART = 8;

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

            log.info("处理 Element...");
            processElements(dataMap.get("elements"), syncUuid, uuidMap, result, loginName);

            log.info("处理 Disease...");
            processDiseases(dataMap.get("diseases"), syncUuid, uuidMap, result, loginName);

            log.info("处理 DiseaseDetail...");
            processDiseaseDetails(dataMap.get("diseaseDetails"), syncUuid, uuidMap, result);

            log.info("处理 Attachment...");
            processAttachments(dataMap.get("attachments"), syncUuid, uuidMap, result, loginName);

            log.info("处理 SpanComponentPart...");
            processSpanComponentParts(dataMap.get("spanComponentParts"), syncUuid, uuidMap, result, loginName);

            log.info("处理 SpanElementPart...");
            processSpanElementParts(dataMap.get("spanElementParts"), syncUuid, uuidMap, result, loginName);

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
                    parentId = resolveBiObjectId(item.getParentUuid(), uuidMap);
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

    /**
     * 新链构件（bi_element）：object_uuid 反查部件节点(PART)服务端 ID。
     * 构件不绑服务端 element_id 到病害——病害侧只存 element_uuid 字符串。
     */
    private void processElements(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        if (data == null)
            return;
        List<Element> list = parseList(data, Element.class);
        for (Element item : list) {
            try {
                if (item.getObjectUuid() != null)
                    item.setBiObjectId(resolveBiObjectId(item.getObjectUuid(), uuidMap));
                Element existing = item.getOfflineUuid() != null
                        ? elementMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        elementMapper.deleteElementById(item.getId());
                        continue;
                    }
                    elementMapper.updateElement(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }
                item.setDelFlag("0");
                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                elementMapper.insertElement(item);
                saveMapping(ENTITY_ELEMENT, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_ELEMENT, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    private void processDiseases(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        List<Disease> list = parseList(data, Disease.class);
        for (Disease item : list) {
            try {
                if (item.getBuildingUuid() != null)
                    item.setBuildingId(resolveBuildingId(item.getBuildingUuid(), uuidMap));
                if (item.getObjectUuid() != null)
                    item.setBiObjectId(resolveBiObjectId(item.getObjectUuid(), uuidMap));
                if (item.getComponentUuid() != null) {
                    Long componentId = uuidMap.get(item.getComponentUuid());
                    if (componentId == null) {
                        Component existingComponent = componentMapper.selectByOfflineUuid(item.getComponentUuid());
                        if (existingComponent != null) {
                            componentId = existingComponent.getId();
                            uuidMap.put(item.getComponentUuid(), componentId);
                        }
                    }
                    item.setComponentId(componentId);
                }
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

    /**
     * 通道A附件：App 先逐文件调 /api/v2/sync/attachment 预上传拿到 minioId，
     * 再在同步 JSON 的 attachments[] 中提交元数据。
     * 按 type 路由挂载主体（1/7/null 病害照片→病害，6 正/侧立面→桥梁，8 部件当前照片→PART），
     * 本批映射优先、未命中回查云端；按附件自身 offline_uuid 幂等 upsert；
     * offlineDeleted=1 物理删除（表无 del_flag）；minioId 缺失或主体反查失败计入逐行错误。
     * 同步完成后按 bi_attachment 实际行数重算受影响病害的 attachment_count。
     */
    private void processAttachments(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        if (data == null)
            return;
        List<Attachment> list = parseList(data, Attachment.class);
        Set<Long> touchedDiseaseIds = new HashSet<>();
        for (Attachment item : list) {
            try {
                Attachment existing = item.getOfflineUuid() != null
                        ? attachmentMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;

                // 删除优先：附件表为物理删除，按自身 UUID 命中即删，无需再解析挂载主体
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    if (existing != null) {
                        if (isDiseaseAttachment(existing.getType()) && existing.getSubjectId() != null) {
                            touchedDiseaseIds.add(existing.getSubjectId());
                        }
                        attachmentMapper.deleteById(existing.getId());
                    }
                    // 删除不存在的记录视为幂等成功（与其他实体删除分支一致，不计 successCount）
                    continue;
                }

                // 挂载主体路由：本批 uuidMap → 云端回查
                Long subjectId = resolveAttachmentSubject(item, uuidMap);
                if (subjectId == null) {
                    result.addError(ENTITY_ATTACHMENT, item.getOfflineUuid(),
                            "挂载主体反查失败(offlineSubjectUuid=" + item.getOfflineSubjectUuid()
                                    + ",type=" + item.getType() + ")，附件未落库");
                    continue;
                }
                item.setSubjectId(subjectId);

                // minio_id 为 NOT NULL：App 必须先预上传；缺失直接判错，避免底层 SQL 异常
                if (item.getMinioId() == null) {
                    result.addError(ENTITY_ATTACHMENT, item.getOfflineUuid(),
                            "缺少 minioId（文件未预上传或预上传失败），附件未落库");
                    continue;
                }
                // name/type 列均 NOT NULL，旧版 App 可能省略，按普通病害照片兜底
                if (item.getName() == null || item.getName().isEmpty()) {
                    item.setName("disease_" + item.getMinioId() + ".jpg");
                }
                if (item.getType() == null) {
                    item.setType(ATTACHMENT_TYPE_DISEASE);
                }

                if (existing != null) {
                    // 主体类型/归属发生变化时，旧主体若是病害也要重算
                    if (isDiseaseAttachment(existing.getType()) && existing.getSubjectId() != null) {
                        touchedDiseaseIds.add(existing.getSubjectId());
                    }
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    attachmentMapper.update(item);
                } else {
                    item.setIsOfflineData(1);
                    item.setOfflineDeleted(0);
                    item.setCreateBy(loginName);
                    item.setCreateTime(DateUtils.getNowDate());
                    attachmentMapper.insert(item);
                }
                if (isDiseaseAttachment(item.getType())) {
                    touchedDiseaseIds.add(subjectId);
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.addError(ENTITY_ATTACHMENT, item.getOfflineUuid(), e.getMessage());
            }
        }
        if (!touchedDiseaseIds.isEmpty()) {
            diseaseMapper.recountAttachmentCount(new ArrayList<>(touchedDiseaseIds));
        }
    }

    /**
     * 附件挂载主体路由：type=6 桥梁(正/侧立面照片)、8 部件(PART 当前照片)，
     * 其余(null/1/7 病害照片)走病害。解析顺序同其他实体：本批 uuidMap 优先，未命中回查云端。
     * offlineSubjectUuid 缺失时兼容直接携带服务端 subject_id 的载荷。
     */
    private Long resolveAttachmentSubject(Attachment item, Map<String, Long> uuidMap) {
        String subjectUuid = item.getOfflineSubjectUuid();
        if (subjectUuid == null || subjectUuid.isEmpty()) {
            return item.getSubjectId();
        }
        Integer type = item.getType();
        if (type != null) {
            if (type == ATTACHMENT_TYPE_BUILDING) {
                return resolveBuildingId(subjectUuid, uuidMap);
            }
            if (type == ATTACHMENT_TYPE_PART) {
                return resolveBiObjectId(subjectUuid, uuidMap);
            }
        }
        Long id = uuidMap.get(subjectUuid);
        if (id != null) {
            return id;
        }
        Disease disease = diseaseMapper.selectByOfflineUuid(subjectUuid);
        if (disease != null) {
            uuidMap.put(subjectUuid, disease.getId());
            return disease.getId();
        }
        return null;
    }

    /** 病害类附件：普通病害图片(1)、病害ad图片(7)及旧版缺省(null)。 */
    private boolean isDiseaseAttachment(Integer type) {
        return type == null || type == 1 || type == 7;
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
     * 新链锚定（bi_span_element_part）：element/span(SPAN)/part(PART) 三 UUID 反查服务端 ID，
     * 三者都已同步才落库；表无 del_flag 列，删除为物理删除。自然唯一键 (element,span,part)。
     */
    private void processSpanElementParts(Object data, String syncUuid, Map<String, Long> uuidMap, SyncResultVo result,
            String loginName) {
        if (data == null)
            return;
        List<SpanElementPart> list = parseList(data, SpanElementPart.class);
        for (SpanElementPart item : list) {
            try {
                if (item.getElementUuid() != null)
                    item.setElementId(resolveElementId(item.getElementUuid(), uuidMap));
                if (item.getSpanUuid() != null)
                    item.setSpanId(resolveBiObjectId(item.getSpanUuid(), uuidMap));
                if (item.getPartUuid() != null)
                    item.setPartId(resolveBiObjectId(item.getPartUuid(), uuidMap));

                SpanElementPart existing = item.getOfflineUuid() != null
                        ? spanElementPartMapper.selectByOfflineUuid(item.getOfflineUuid())
                        : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        spanElementPartMapper.deleteSpanElementPartById(item.getId());
                        continue;
                    }
                    spanElementPartMapper.updateSpanElementPart(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }

                // 锚定三要素缺失则不落库（构件/桥跨/部件必须都已同步）
                if (item.getElementId() == null || item.getSpanId() == null || item.getPartId() == null) {
                    result.addError(ENTITY_SPAN_ELEMENT_PART, item.getOfflineUuid(),
                            "element/span/part 反查失败，存在未同步实体");
                    continue;
                }

                item.setIsOfflineData(1);
                item.setCreateBy(loginName);
                item.setCreateTime(DateUtils.getNowDate());
                spanElementPartMapper.insertSpanElementPart(item);
                saveMapping(ENTITY_SPAN_ELEMENT_PART, item.getOfflineUuid(), item.getId(), syncUuid, uuidMap, result);
            } catch (Exception e) {
                result.addError(ENTITY_SPAN_ELEMENT_PART, item.getOfflineUuid(), e.getMessage());
            }
        }
    }

    /**
     * bi_object UUID 反查：本批映射优先；未命中回查云端既有节点。
     * App 在下载到的 UNIT 下增量建跨时不会重传 UNIT，父节点可能只存在于云端。
     */
    private Long resolveBiObjectId(String uuid, Map<String, Long> uuidMap) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        Long id = uuidMap.get(uuid);
        if (id != null) {
            return id;
        }
        BiObject existing = biObjectMapper.selectByOfflineUuid(uuid);
        if (existing != null) {
            uuidMap.put(uuid, existing.getId());
            return existing.getId();
        }
        return null;
    }

    /** building UUID 反查，语义同 {@link #resolveBiObjectId}。 */
    private Long resolveBuildingId(String uuid, Map<String, Long> uuidMap) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        Long id = uuidMap.get(uuid);
        if (id != null) {
            return id;
        }
        Building existing = buildingMapper.selectByOfflineUuid(uuid);
        if (existing != null) {
            uuidMap.put(uuid, existing.getId());
            return existing.getId();
        }
        return null;
    }

    /** element UUID 反查（bi_element 为新链表，del_flag='0' 才命中）。 */
    private Long resolveElementId(String uuid, Map<String, Long> uuidMap) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        Long id = uuidMap.get(uuid);
        if (id != null) {
            return id;
        }
        Element existing = elementMapper.selectByOfflineUuid(uuid);
        if (existing != null) {
            uuidMap.put(uuid, existing.getId());
            return existing.getId();
        }
        return null;
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
