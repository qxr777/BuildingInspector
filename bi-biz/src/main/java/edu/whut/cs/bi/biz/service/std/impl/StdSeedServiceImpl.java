package edu.whut.cs.bi.biz.service.std.impl;

import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.std.StdSchemaService;
import edu.whut.cs.bi.biz.service.std.StdSeedReport;
import edu.whut.cs.bi.biz.service.std.StdSeedScope;
import edu.whut.cs.bi.biz.service.std.StdSeedService;
import edu.whut.cs.bi.biz.service.std.StandardCatalogLoader;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseScale;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseType;
import edu.whut.cs.bi.biz.service.std.model.StdTemplateNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标准种子实现：按自然键（(code,std_version) / nodeCode）幂等 upsert，
 * 对账删除严格限制在本标准版本命名空间内，绝不触碰旧标准 H21-2011 数据。
 */
@Service
public class StdSeedServiceImpl implements StdSeedService {

    private static final Logger log = LoggerFactory.getLogger(StdSeedServiceImpl.class);

    @Resource
    private StandardCatalogLoader catalogLoader;

    @Resource
    private StdSchemaService schemaService;

    @Resource
    private DiseaseTypeMapper diseaseTypeMapper;

    @Resource
    private DiseaseScaleMapper diseaseScaleMapper;

    @Resource
    private BiTemplateObjectMapper templateObjectMapper;

    @Resource
    private TODiseaseTypeMapper todMapper;

    /** 本次执行中的 code→病害类型id。 */
    private final Map<String, Long> diseaseTypeIdMap = new HashMap<>();

    /** nodeCode→id。 */
    private final Map<String, Long> nodeIdMap = new HashMap<>();

    /** nodeCode→ancestors。 */
    private final Map<String, String> ancestorsMap = new HashMap<>();

    /** dryRun 时给待插入节点分配的临时负 id，仅用于父链/计数。 */
    private long nextFakeId = -1L;

    @Override
    public StdSeedReport seed(StdSeedScope scope, boolean dryRun, List<String> bridgeTypes) {
        StdSeedReport report = new StdSeedReport();
        StdCatalog catalog = catalogLoader.getCatalog();
        report.setStdVersion(catalog.getStdVersion());
        report.setDryRun(dryRun);

        Set<String> selectedBridges = resolveBridges(catalog, bridgeTypes);

        try {
            if (scope == StdSeedScope.SCHEMA || scope == StdSeedScope.ALL) {
                report.getDdl().addAll(schemaService.ensureSchema(dryRun));
            }
            if (scope == StdSeedScope.TYPES || scope == StdSeedScope.ALL) {
                seedDiseaseTypes(catalog, dryRun, report);
            }
            if (scope == StdSeedScope.TEMPLATES || scope == StdSeedScope.ALL) {
                seedTemplateNodes(catalog, selectedBridges, dryRun, report);
            }
            if (scope == StdSeedScope.BINDINGS || scope == StdSeedScope.ALL) {
                seedBindings(catalog, selectedBridges, dryRun, report);
            }
        } catch (Exception e) {
            log.error("标准种子执行失败: {}", e.getMessage(), e);
            report.getErrors().add(e.getMessage());
        }

        log.info("标准种子完成 scope={} dryRun={} summary={}", scope, dryRun, report.summary());
        return report;
    }

    private Set<String> resolveBridges(StdCatalog catalog, List<String> bridgeTypes) {
        if (bridgeTypes == null || bridgeTypes.isEmpty()) {
            return catalog.getBridgeTypes().stream()
                    .map(b -> b.getCode())
                    .collect(Collectors.toCollection(HashSet::new));
        }
        Set<String> valid = new HashSet<>();
        for (String code : bridgeTypes) {
            if (code != null && !code.trim().isEmpty()) {
                valid.add(code.trim());
            }
        }
        return valid;
    }

    // ---------- 病害类型 + 标度 ----------

    private void seedDiseaseTypes(StdCatalog catalog, boolean dryRun, StdSeedReport report) {
        for (StdDiseaseType stdType : catalog.getDiseaseTypes()) {
            DiseaseType candidate = mapType(stdType, catalog.getStdVersion());
            DiseaseType existing = diseaseTypeMapper.selectByCodeAndStdVersion(
                    stdType.getCode(), catalog.getStdVersion());

            Long typeId;
            if (existing == null) {
                if (!dryRun) {
                    diseaseTypeMapper.insertDiseaseType(candidate);
                }
                typeId = dryRun ? nextFakeId-- : candidate.getId();
                report.getDiseaseTypes().setInserted(report.getDiseaseTypes().getInserted() + 1);
            } else {
                typeId = existing.getId();
                if (!sameType(candidate, existing)) {
                    candidate.setId(typeId);
                    if (!dryRun) {
                        diseaseTypeMapper.updateDiseaseType(candidate);
                    }
                    report.getDiseaseTypes().setUpdated(report.getDiseaseTypes().getUpdated() + 1);
                } else {
                    report.getDiseaseTypes().setUnchanged(report.getDiseaseTypes().getUnchanged() + 1);
                }
            }
            diseaseTypeIdMap.put(stdType.getCode(), typeId);

            seedScales(stdType, catalog.getStdVersion(), typeId, dryRun, report);
        }
    }

    private void seedScales(StdDiseaseType stdType, String stdVersion, Long typeId,
                            boolean dryRun, StdSeedReport report) {
        Map<Integer, DiseaseScale> existingScales = new HashMap<>();
        for (DiseaseScale row : diseaseScaleMapper.selectByTypeCodeAndStdVersion(
                stdType.getCode(), stdVersion)) {
            existingScales.put(row.getScale(), row);
        }

        Set<Integer> desiredScales = new HashSet<>();
        for (StdDiseaseScale stdScale : stdType.getScales()) {
            desiredScales.add(stdScale.getScale());
            DiseaseScale candidate = mapScale(stdType.getCode(), stdVersion, stdScale);
            DiseaseScale existing = existingScales.get(stdScale.getScale());
            if (existing == null) {
                if (!dryRun) {
                    diseaseScaleMapper.insertDiseaseScale(candidate);
                }
                report.getDiseaseScales().setInserted(report.getDiseaseScales().getInserted() + 1);
            } else if (!sameScale(candidate, existing)) {
                candidate.setId(existing.getId());
                if (!dryRun) {
                    diseaseScaleMapper.updateDiseaseScale(candidate);
                }
                report.getDiseaseScales().setUpdated(report.getDiseaseScales().getUpdated() + 1);
            } else {
                report.getDiseaseScales().setUnchanged(report.getDiseaseScales().getUnchanged() + 1);
            }
        }

        for (Map.Entry<Integer, DiseaseScale> entry : existingScales.entrySet()) {
            if (!desiredScales.contains(entry.getKey())) {
                if (!dryRun) {
                    diseaseScaleMapper.deleteDiseaseScaleById(entry.getValue().getId());
                }
                report.getDiseaseScales().setDeleted(report.getDiseaseScales().getDeleted() + 1);
            }
        }
    }

    // ---------- 模板节点 ----------

    private void seedTemplateNodes(StdCatalog catalog, Set<String> selectedBridges,
                                   boolean dryRun, StdSeedReport report) {
        // 取版本下全部节点（含软删），用于 upsert/复活与对账
        Map<String, BiTemplateObject> snapshot = new LinkedHashMap<>();
        for (BiTemplateObject node : templateObjectMapper.selectByStdVersion(catalog.getStdVersion())) {
            snapshot.put(node.getNodeCode(), node);
        }

        Set<String> desiredCodes = new HashSet<>();
        for (StdTemplateNode node : catalog.getNodes()) {
            if (node.getBridgeType() == null || !selectedBridges.contains(node.getBridgeType())) {
                continue;
            }
            desiredCodes.add(node.getNodeCode());

            BiTemplateObject existing = snapshot.get(node.getNodeCode());
            Long parentId = resolveParentId(node, dryRun);
            String ancestors = resolveAncestors(node, parentId);

            BiTemplateObject candidate = mapNode(node, parentId, ancestors, catalog.getStdVersion());

            if (existing == null) {
                Long id;
                if (!dryRun) {
                    templateObjectMapper.insertBiTemplateObject(candidate);
                    id = candidate.getId();
                } else {
                    id = nextFakeId--;
                }
                nodeIdMap.put(node.getNodeCode(), id);
                ancestorsMap.put(node.getNodeCode(), ancestors);
                report.getTemplateNodes().setInserted(report.getTemplateNodes().getInserted() + 1);
            } else {
                Long id = existing.getId();
                boolean revive = !"0".equals(existing.getDelFlag());
                candidate.setId(id);
                if (revive) {
                    candidate.setDelFlag("0");
                }
                nodeIdMap.put(node.getNodeCode(), id);
                ancestorsMap.put(node.getNodeCode(), ancestors);
                if (revive || !sameNode(candidate, existing)) {
                    if (!dryRun) {
                        templateObjectMapper.updateBiTemplateObject(candidate);
                    }
                    report.getTemplateNodes().setUpdated(report.getTemplateNodes().getUpdated() + 1);
                } else {
                    report.getTemplateNodes().setUnchanged(report.getTemplateNodes().getUnchanged() + 1);
                }
            }
        }

        // 对账：仅软删“属于所选桥型命名空间、且已不在目录”的节点
        for (Map.Entry<String, BiTemplateObject> entry : snapshot.entrySet()) {
            String code = entry.getKey();
            if (desiredCodes.contains(code) || inSelectedNamespace(code, selectedBridges) == null) {
                continue;
            }
            if (!"0".equals(entry.getValue().getDelFlag())) {
                continue; // 已软删
            }
            if (!dryRun) {
                templateObjectMapper.deleteBiTemplateObjectById(entry.getValue().getId());
            }
            report.getTemplateNodes().setDeleted(report.getTemplateNodes().getDeleted() + 1);
        }
    }

    private Long resolveParentId(StdTemplateNode node, boolean dryRun) {
        if (node.getParentCode() == null) {
            return 0L;
        }
        Long id = nodeIdMap.get(node.getParentCode());
        if (id != null) {
            return id;
        }
        // 父节点本次未处理（如单独补某层），回库查询
        return 0L;
    }

    private String resolveAncestors(StdTemplateNode node, Long parentId) {
        if (node.getParentCode() == null) {
            return "0";
        }
        String parentAncestors = ancestorsMap.get(node.getParentCode());
        if (parentAncestors == null) {
            return "0";
        }
        return parentAncestors + "," + parentId;
    }

    /** 若节点码属于所选桥型命名空间，返回该桥型；否则返回 null。 */
    private String inSelectedNamespace(String nodeCode, Set<String> selectedBridges) {
        int dot = nodeCode.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String prefix = nodeCode.substring(0, dot);
        return selectedBridges.contains(prefix) ? prefix : null;
    }

    // ---------- 绑定 ----------

    private void seedBindings(StdCatalog catalog, Set<String> selectedBridges,
                              boolean dryRun, StdSeedReport report) {
        // 补全 id 映射（BINDINGS 单独执行时 nodeIdMap 为空）
        if (nodeIdMap.isEmpty()) {
            for (BiTemplateObject node : templateObjectMapper.selectByStdVersion(catalog.getStdVersion())) {
                nodeIdMap.put(node.getNodeCode(), node.getId());
            }
        }

        for (StdTemplateNode node : catalog.getNodes()) {
            if (!"PART".equals(node.getRole())
                    || node.getBridgeType() == null
                    || !selectedBridges.contains(node.getBridgeType())) {
                continue;
            }
            Long templateObjectId = nodeIdMap.get(node.getNodeCode());
            if (templateObjectId == null || templateObjectId < 0) {
                // dryRun 下待插入的部件：现有绑定视为空，全部拟新增
                templateObjectId = null;
            }

            Set<Long> existing = templateObjectId == null
                    ? new HashSet<>()
                    : new HashSet<>(todMapper.selectDiseaseTypeIdsByTemplateObjectId(templateObjectId));

            List<Long> desired = new ArrayList<>();
            for (String code : node.getDiseaseTypeCodes()) {
                Long id = resolveDiseaseTypeId(catalog, code);
                if (id != null && id > 0) {
                    desired.add(id);
                }
            }

            List<Long> toAdd = desired.stream().filter(id -> !existing.contains(id)).collect(Collectors.toList());
            List<Long> toRemove = existing.stream().filter(id -> !desired.contains(id)).collect(Collectors.toList());

            if (!toAdd.isEmpty()) {
                if (!dryRun && templateObjectId != null) {
                    todMapper.batchInsertBridgeTemplateDiseaseType(templateObjectId, toAdd);
                }
                report.getBindings().setInserted(report.getBindings().getInserted() + toAdd.size());
            }
            if (!toRemove.isEmpty()) {
                if (!dryRun && templateObjectId != null) {
                    todMapper.batchDeleteData(templateObjectId, toRemove);
                }
                report.getBindings().setDeleted(report.getBindings().getDeleted() + toRemove.size());
            }
            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                report.getBindings().setUnchanged(report.getBindings().getUnchanged() + 1);
            }
        }
    }

    private Long resolveDiseaseTypeId(StdCatalog catalog, String code) {
        Long id = diseaseTypeIdMap.get(code);
        if (id != null) {
            return id;
        }
        DiseaseType row = diseaseTypeMapper.selectByCodeAndStdVersion(code, catalog.getStdVersion());
        if (row != null) {
            diseaseTypeIdMap.put(code, row.getId());
            return row.getId();
        }
        return null;
    }

    // ---------- Std → domain 映射 ----------

    /**
     * 病害合并阈值的种子默认值。YAML 不携带该 H21 遗留合并语义；若不显式给值，
     * MySQL 列默认值(10)会让“写入前 null / 回读 10”不一致，导致每次重跑都误判为 update。
     * 这里显式给同值，保证幂等且不依赖库默认（SQLite 等环境也一致）。
     */
    private static final int DEFAULT_MERGE_THRESHOLD = 10;

    private DiseaseType mapType(StdDiseaseType std, String stdVersion) {
        DiseaseType type = new DiseaseType();
        type.setCode(std.getCode());
        type.setName(std.getName());
        type.setMinScale(std.getMinScale());
        type.setMaxScale(std.getMaxScale());
        type.setThreshold(std.getThreshold() != null
                ? std.getThreshold()
                : DEFAULT_MERGE_THRESHOLD);
        type.setSelectColumn(std.getSelectColumn());
        type.setStdVersion(stdVersion);
        type.setStatus("0");
        return type;
    }

    private DiseaseScale mapScale(String typeCode, String stdVersion, StdDiseaseScale std) {
        DiseaseScale scale = new DiseaseScale();
        scale.setTypeCode(typeCode);
        scale.setScale(std.getScale());
        scale.setQualitativeDescription(std.getQualitative());
        scale.setQuantitativeDescription(std.getQuantitative());
        scale.setStdVersion(stdVersion);
        scale.setMetricKey(std.getMetricKey());
        scale.setValueLower(std.getValueLower());
        scale.setValueUpper(std.getValueUpper());
        scale.setUnit(std.getUnit());
        scale.setStatus("0");
        return scale;
    }

    private BiTemplateObject mapNode(StdTemplateNode node, Long parentId, String ancestors,
                                     String stdVersion) {
        BiTemplateObject obj = new BiTemplateObject();
        obj.setNodeCode(node.getNodeCode());
        obj.setName(node.getName());
        obj.setParentId(parentId);
        obj.setAncestors(ancestors);
        obj.setOrderNum(node.getOrderNum());
        obj.setStatus(node.getStatus());
        obj.setGamma(node.getGamma());
        obj.setOmega(node.getOmega());
        obj.setCategoryI(node.getCategoryI());
        obj.setProps(node.getProps());
        obj.setStdVersion(stdVersion);
        return obj;
    }

    // ---------- 字段比较 ----------

    private boolean sameType(DiseaseType a, DiseaseType b) {
        return Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getMinScale(), b.getMinScale())
                && Objects.equals(a.getMaxScale(), b.getMaxScale())
                && Objects.equals(a.getThreshold(), b.getThreshold())
                && Objects.equals(a.getSelectColumn(), b.getSelectColumn());
    }

    private boolean sameScale(DiseaseScale a, DiseaseScale b) {
        return Objects.equals(a.getScale(), b.getScale())
                && Objects.equals(emptyToNull(a.getQualitativeDescription()), emptyToNull(b.getQualitativeDescription()))
                && Objects.equals(emptyToNull(a.getQuantitativeDescription()), emptyToNull(b.getQuantitativeDescription()))
                && Objects.equals(emptyToNull(a.getMetricKey()), emptyToNull(b.getMetricKey()))
                && Objects.equals(emptyToNull(a.getUnit()), emptyToNull(b.getUnit()))
                && decimalEquals(a.getValueLower(), b.getValueLower())
                && decimalEquals(a.getValueUpper(), b.getValueUpper());
    }

    private boolean sameNode(BiTemplateObject a, BiTemplateObject b) {
        return Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getParentId(), b.getParentId())
                && Objects.equals(a.getAncestors(), b.getAncestors())
                && Objects.equals(a.getOrderNum(), b.getOrderNum())
                && Objects.equals(a.getStatus(), b.getStatus())
                && Objects.equals(a.getOmega(), b.getOmega())
                && Objects.equals(a.getProps(), b.getProps())
                && decimalEquals(a.getGamma(), b.getGamma());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static boolean decimalEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }
}
