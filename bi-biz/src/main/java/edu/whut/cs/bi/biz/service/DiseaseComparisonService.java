package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.vo.DiseaseComparisonData;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 病害对比数据处理服务
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class DiseaseComparisonService {

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private IProjectService projectService;

    @Autowired
    private DiseaseDetailMapper diseaseDetailMapper;


    /**
     * 生成病害对比数据
     *
     * @param subBridge        建筑物信息
     * @param currentProjectId 当前项目ID
     * @return 对比数据列表
     */
    public List<DiseaseComparisonData> generateComparisonData(BiObject subBridge, Long currentProjectId, Long bubuildingId) {
        // 1. 获取当前项目年份
        Project currentProject = projectService.selectProjectById(currentProjectId);
        if (currentProject == null) {
            return new ArrayList<>();
        }

        int currentYear = currentProject.getYear();
        int previousYear = currentYear - 1;

        // 2. 获取BiObject层级结构
        List<BiObject> allObjects = biObjectMapper.selectChildrenById(subBridge.getId());
        Map<Long, BiObject> objectMap = allObjects.stream()
                .collect(Collectors.toMap(BiObject::getId, obj -> obj));

        objectMap.put(subBridge.getId(), subBridge);
        allObjects.add(subBridge);

        // 4. 获取第四层节点（构件层）
        List<BiObject> level4Objects = findLevel4Objects(allObjects, subBridge.getId());

        // 5. 批量获取病害数据（优化版本）
        List<DiseaseComparisonData> comparisonData = new ArrayList<>();

        if (level4Objects.isEmpty()) {
            return comparisonData;
        }

        long startTime = System.currentTimeMillis();

        // 提取所有第四层节点的ID
        List<Long> level4ObjectIds = level4Objects.stream()
                .map(BiObject::getId)
                .collect(Collectors.toList());

       log.info("开始批量查询病害数据，构件数量: " + level4ObjectIds.size() +
                "，当前年份: " + currentYear + "，上一年份: " + previousYear);

        // 批量查询当前年份和上一年份的病害数据（2次查询替代N*2次查询）
        List<Disease> currentYearDiseases = diseaseMapper.selectDiseaseComponentData(level4ObjectIds, bubuildingId, currentYear);
        List<Disease> previousYearDiseases = diseaseMapper.selectDiseaseComponentData(level4ObjectIds, bubuildingId, previousYear);

        long queryTime = System.currentTimeMillis() - startTime;
        log.info("批量查询完成，当前年份病害: " + currentYearDiseases.size() +
                "条，上一年份病害: " + previousYearDiseases.size() +
                "条，查询耗时: " + queryTime + "ms");

        // 批量查询病害详情
        List<Long> allDiseaseIds = new ArrayList<>();
        allDiseaseIds.addAll(currentYearDiseases.stream()
                .filter(disease -> disease.getId() != null)
                .map(Disease::getId)
                .collect(Collectors.toList()));
        allDiseaseIds.addAll(previousYearDiseases.stream()
                .filter(disease -> disease.getId() != null)
                .map(Disease::getId)
                .collect(Collectors.toList()));

        List<DiseaseDetail> allDiseaseDetails = new ArrayList<>();
        if (!allDiseaseIds.isEmpty()) {
            allDiseaseDetails = diseaseDetailMapper.selectDiseaseDetailsByDiseaseIds(allDiseaseIds);
        }

        // 将病害详情按病害ID分组
        Map<Long, List<DiseaseDetail>> diseaseDetailMap = allDiseaseDetails.stream()
                .collect(Collectors.groupingBy(DiseaseDetail::getDiseaseId));

        // 为当前年份病害设置详情
        for (Disease disease : currentYearDiseases) {
            if (disease.getId() != null) {
                List<DiseaseDetail> diseaseDetails = diseaseDetailMap.getOrDefault(disease.getId(), new ArrayList<>());
                disease.setDiseaseDetails(diseaseDetails);
            }
        }

        // 为上一年份病害设置详情
        for (Disease disease : previousYearDiseases) {
            if (disease.getId() != null) {
                List<DiseaseDetail> diseaseDetails = diseaseDetailMap.getOrDefault(disease.getId(), new ArrayList<>());
                disease.setDiseaseDetails(diseaseDetails);
            }
        }

        long detailQueryTime = System.currentTimeMillis() - startTime - queryTime;
        log.info("病害详情查询完成，详情数量: " + allDiseaseDetails.size() +
                "条，详情查询耗时: " + detailQueryTime + "ms");

        // 按构件ID和病害类型ID分组当前年份数据
        Map<Long, Map<Long, List<Disease>>> currentYearGrouped = currentYearDiseases.stream()
                .filter(disease -> disease.getBiObjectId() != null && disease.getDiseaseTypeId() != null)
                .collect(Collectors.groupingBy(Disease::getBiObjectId,
                        Collectors.groupingBy(Disease::getDiseaseTypeId)));

        // 按构件ID和病害类型ID分组上一年份数据
        Map<Long, Map<Long, List<Disease>>> previousYearGrouped = previousYearDiseases.stream()
                .filter(disease -> disease.getBiObjectId() != null && disease.getDiseaseTypeId() != null)
                .collect(Collectors.groupingBy(Disease::getBiObjectId,
                        Collectors.groupingBy(Disease::getDiseaseTypeId)));

        // 为每个第四层节点生成对比数据
        for (BiObject level4Obj : level4Objects) {
            // 获取层级信息
            HierarchyInfo hierarchy = getHierarchyInfo(level4Obj, objectMap);
            if (hierarchy == null) continue;

            Long componentId = level4Obj.getId();

            // 获取该构件的病害数据
            Map<Long, List<Disease>> currentYearDiseasesByType = currentYearGrouped.getOrDefault(componentId, new HashMap<>());
            Map<Long, List<Disease>> previousYearDiseasesByType = previousYearGrouped.getOrDefault(componentId, new HashMap<>());

            // 获取所有病害类型ID
            Set<Long> allDiseaseTypeIds = new HashSet<>();
            allDiseaseTypeIds.addAll(currentYearDiseasesByType.keySet());
            allDiseaseTypeIds.addAll(previousYearDiseasesByType.keySet());

            // 为每种病害类型创建对比数据
            for (Long diseaseTypeId : allDiseaseTypeIds) {
                // 从病害数据中获取病害类型名称
                String diseaseTypeName = getDiseaseTypeNameFromDiseases(diseaseTypeId,
                        currentYearDiseasesByType, previousYearDiseasesByType);
                if (diseaseTypeName == null) continue;

                DiseaseComparisonData data = new DiseaseComparisonData();

                data.setCurrentYear(currentYear);
                data.setLastYear(previousYear);

                // 设置层级信息
                data.setBridgeName(hierarchy.getBridgeName());
                data.setPosition1(hierarchy.getPosition1());
                data.setPosition2(hierarchy.getPosition2());
                data.setComponent(hierarchy.getComponent());
                data.setDiseaseType(diseaseTypeName);

                // 设置层级ID
                data.setRootObjectId(hierarchy.getRootObjectId());
                data.setLevel2ObjectId(hierarchy.getLevel2ObjectId());
                data.setLevel3ObjectId(hierarchy.getLevel3ObjectId());
                data.setLevel4ObjectId(hierarchy.getLevel4ObjectId());
                data.setDiseaseTypeId(diseaseTypeId);

                // 处理当前年份数据
                List<Disease> currentDiseases = currentYearDiseasesByType.getOrDefault(diseaseTypeId, new ArrayList<>());
                data.setQuantity2024(currentDiseases.size());
                data.setSeverity2024(generateSeverityDescription(currentDiseases));

                // 处理上一年份数据
                List<Disease> previousDiseases = previousYearDiseasesByType.getOrDefault(diseaseTypeId, new ArrayList<>());
                data.setQuantity2023(previousDiseases.size());
                data.setSeverity2023(generateSeverityDescription(previousDiseases));

                // 生成发展情况描述
                data.setDevelopmentStatus(generateDevelopmentStatus(previousDiseases, currentDiseases));

                comparisonData.add(data);
            }
        }

        // 6. 计算合并信息
        calculateMergeInfo(comparisonData);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("病害对比数据生成完成，共生成: " + comparisonData.size() +
                "条对比记录，总耗时: " + totalTime + "ms");

        return comparisonData;
    }

    /**
     * 查找第四层节点
     */
    private List<BiObject> findLevel4Objects(List<BiObject> allObjects, Long rootObjectId) {
        // 构建父子关系映射
        Map<Long, List<BiObject>> childrenMap = allObjects.stream()
                .collect(Collectors.groupingBy(BiObject::getParentId));

        List<BiObject> level4Objects = new ArrayList<>();

        // 遍历根节点的子节点（第二层）
        List<BiObject> level2Objects = childrenMap.getOrDefault(rootObjectId, new ArrayList<>());
        for (BiObject level2 : level2Objects) {
            // 遍历第二层的子节点（第三层）
            List<BiObject> level3Objects = childrenMap.getOrDefault(level2.getId(), new ArrayList<>());
            for (BiObject level3 : level3Objects) {
                // 获取第三层的子节点（第四层）
                List<BiObject> level4 = childrenMap.getOrDefault(level3.getId(), new ArrayList<>());
                level4Objects.addAll(level4);
            }
        }

        return level4Objects;
    }

    /**
     * 获取层级信息
     */
    private HierarchyInfo getHierarchyInfo(BiObject level4Obj, Map<Long, BiObject> objectMap) {
        BiObject level3Obj = objectMap.get(level4Obj.getParentId());
        if (level3Obj == null) return null;

        BiObject level2Obj = objectMap.get(level3Obj.getParentId());
        if (level2Obj == null) return null;

        BiObject rootObj = objectMap.get(level2Obj.getParentId());
        if (rootObj == null) return null;

        HierarchyInfo info = new HierarchyInfo();
        info.setBridgeName(rootObj.getName());
        info.setPosition1(level2Obj.getName());
        info.setPosition2(level3Obj.getName());
        info.setComponent(level4Obj.getName());
        info.setRootObjectId(rootObj.getId());
        info.setLevel2ObjectId(level2Obj.getId());
        info.setLevel3ObjectId(level3Obj.getId());
        info.setLevel4ObjectId(level4Obj.getId());

        return info;
    }



    /**
     * 从病害数据中获取病害类型名称
     */
    private String getDiseaseTypeNameFromDiseases(Long diseaseTypeId,
                                                  Map<Long, List<Disease>> currentYearDiseases,
                                                  Map<Long, List<Disease>> previousYearDiseases) {
        // 先从当前年份的病害中查找
        List<Disease> currentDiseases = currentYearDiseases.get(diseaseTypeId);
        if (currentDiseases != null && !currentDiseases.isEmpty()) {
            Disease disease = currentDiseases.get(0);
            if (disease.getDiseaseType() != null) {
                return disease.getDiseaseType().getName();
            }
        }

        // 如果当前年份没有，从上一年份的病害中查找
        List<Disease> previousDiseases = previousYearDiseases.get(diseaseTypeId);
        if (previousDiseases != null && !previousDiseases.isEmpty()) {
            Disease disease = previousDiseases.get(0);
            if (disease.getDiseaseType() != null) {
                return disease.getDiseaseType().getName();
            }
        }

        return null;
    }

    /**
     * 生成病害程度描述
     */
    private String generateSeverityDescription(List<Disease> diseases) {
        if (diseases.isEmpty()) {
            return "/";
        }

        // 使用通用的数据汇总方法
        DiseaseAggregateData data = extractAggregateData(diseases);

        // 生成汇总描述
        StringBuilder sb = new StringBuilder();

        if (data.hasLengthData && data.totalLength > 0) {
            sb.append(String.format("总长度%.2fm", data.totalLength));
        }

        if (data.hasWidthData) {
            if (sb.length() > 0) sb.append("，");
            if (data.minWidth == data.maxWidth) {
                sb.append(String.format("宽度%.2fmm", data.maxWidth));
            } else {
                sb.append(String.format("宽度介于%.2f-%.2fmm之间", data.minWidth, data.maxWidth));
            }
        }

        if (data.hasAreaData && data.totalArea > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append(String.format("面积%.4f㎡", data.totalArea));
        }

        // 如果没有结构化数据，返回默认描述
        if (sb.length() == 0) {
            sb.append("/");
        }

        return sb.toString();
    }

    /**
     * 生成发展情况描述
     */
    private String generateDevelopmentStatus(List<Disease> previousDiseases, List<Disease> currentDiseases) {
        int previousCount = previousDiseases.size();
        int currentCount = currentDiseases.size();

        // 提取前一年的汇总数据
        DiseaseAggregateData previousData = extractAggregateData(previousDiseases);

        // 提取当前年的汇总数据
        DiseaseAggregateData currentData = extractAggregateData(currentDiseases);

        StringBuilder status = new StringBuilder();

        int quantityChange = currentCount - previousCount;

        if (quantityChange > 0) {
            // 新增病害
            status.append("新增").append(quantityChange).append("条");

            // 计算长度、宽度、面积的变化
            double lengthChange = currentData.totalLength - previousData.totalLength;
            double widthChange = currentData.maxWidth - previousData.maxWidth;
            double areaChange = currentData.totalArea - previousData.totalArea;

            if (lengthChange > 0) {
                status.append("，长度增加").append(String.format("%.2f", lengthChange)).append("m");
            }
            if (widthChange > 0) {
                status.append("，宽度增加").append(String.format("%.2f", widthChange)).append("mm");
            }
            if (areaChange > 0) {
                status.append("，面积增加").append(String.format("%.4f", areaChange)).append("㎡");
            }

        } else if (quantityChange < 0) {
            // 减少病害（修复）
            status.append("修复").append(Math.abs(quantityChange)).append("条");

            // 计算长度、宽度、面积的修复量
            double lengthReduced = previousData.totalLength - currentData.totalLength;
            double widthReduced = previousData.maxWidth - currentData.maxWidth; // 宽度看最大值的变化
            double areaReduced = previousData.totalArea - currentData.totalArea;

            if (lengthReduced > 0) {
                status.append("，长度修复").append(String.format("%.2f", lengthReduced)).append("m");
            }
            if (widthReduced > 0) {
                status.append("，宽度修复").append(String.format("%.2f", widthReduced)).append("mm");
            }
            if (areaReduced > 0) {
                status.append("，面积修复").append(String.format("%.4f", areaReduced)).append("㎡");
            }

        } else {
            // 数量没有变化，但可能有程度变化
            double lengthChange = currentData.totalLength - previousData.totalLength;
            double widthChange = currentData.maxWidth - previousData.maxWidth;
            double areaChange = currentData.totalArea - previousData.totalArea;

            if (Math.abs(lengthChange) > 0.01 || Math.abs(widthChange) > 0.01 || Math.abs(areaChange) > 0.0001) {
                status.append("数量稳定");
                if (lengthChange > 0) {
                    status.append("，长度增加").append(String.format("%.2f", lengthChange)).append("m");
                } else if (lengthChange < 0) {
                    status.append("，长度减少").append(String.format("%.2f", Math.abs(lengthChange))).append("m");
                }
                if (widthChange > 0) {
                    status.append("，宽度增加").append(String.format("%.2f", widthChange)).append("mm");
                } else if (widthChange < 0) {
                    status.append("，宽度减少").append(String.format("%.2f", Math.abs(widthChange))).append("mm");
                }
                if (areaChange > 0) {
                    status.append("，面积增加").append(String.format("%.4f", areaChange)).append("㎡");
                } else if (areaChange < 0) {
                    status.append("，面积减少").append(String.format("%.4f", Math.abs(areaChange))).append("㎡");
                }
            } else {
                status.append("稳定");
            }
        }

        return status.toString();
    }

    /**
     * 提取病害列表的汇总数据（通用方法）
     */
    private DiseaseAggregateData extractAggregateData(List<Disease> diseases) {
        DiseaseAggregateData data = new DiseaseAggregateData();

        if (diseases == null || diseases.isEmpty()) {
            return data;
        }

        // 遍历每个病害
        for (Disease disease : diseases) {
            List<DiseaseDetail> details = disease.getDiseaseDetails();
            if (details == null || details.isEmpty()) {
                continue;
            }

            // 遍历每个病害的详情
            for (DiseaseDetail detail : details) {
                // 汇总长度
                if (detail.getLength1() != null) {
                    data.totalLength += detail.getLength1().doubleValue();
                    data.hasLengthData = true;
                }

                // 统计宽度范围
                if (detail.getCrackWidth() != null) {
                    double width = detail.getCrackWidth().doubleValue();
                    data.minWidth = Math.min(data.minWidth, width);
                    data.maxWidth = Math.max(data.maxWidth, width);
                    data.hasWidthData = true;
                }

                // 计算面积
                if (detail.getAreaLength() != null && detail.getAreaWidth() != null) {
                    double area = detail.getAreaLength().doubleValue() * detail.getAreaWidth().doubleValue();
                    data.totalArea += area;
                    data.hasAreaData = true;
                }

                // 统计长度范围 宽度范围
                if (detail.getLengthRangeStart() != null || detail.getLengthRangeEnd() != null || detail.getCrackWidthRangeStart() != null || detail.getCrackWidthRangeEnd() != null) {
                    double lengthRangeStart = detail.getLengthRangeStart() == null ? 0 : detail.getLengthRangeStart().doubleValue();
                    double lengthRangeEnd = detail.getLengthRangeEnd() == null ? 0 : detail.getLengthRangeEnd().doubleValue();
                    data.totalLength += ((lengthRangeEnd - lengthRangeStart) / 2) * disease.getQuantity();
                    data.hasLengthData = true;
                    double widthMin = detail.getCrackWidthRangeStart() == null ? 0 : detail.getCrackWidthRangeStart().doubleValue();
                    double widthMax = detail.getCrackWidthRangeEnd() == null ? 0 : detail.getCrackWidthRangeEnd().doubleValue();
                    data.minWidth = Math.min(data.minWidth, widthMin);
                    data.maxWidth = Math.max(data.maxWidth, widthMax);
                    data.hasWidthData = true;
                    break;
                }
            }
        }

        return data;
    }

    /**
     * 病害汇总数据内部类
     */
    private static class DiseaseAggregateData {
        double totalLength = 0;
        double minWidth = Double.MAX_VALUE;
        double maxWidth = 0;
        double totalArea = 0;
        boolean hasLengthData = false;
        boolean hasWidthData = false;
        boolean hasAreaData = false;
    }

    /**
     * 计算合并信息
     */
    private void calculateMergeInfo(List<DiseaseComparisonData> data) {
        if (data.isEmpty()) return;

        // 按层级分组统计
        Map<String, List<DiseaseComparisonData>> bridgeGroups = data.stream()
                .collect(Collectors.groupingBy(DiseaseComparisonData::getBridgeName, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<DiseaseComparisonData>> bridgeEntry : bridgeGroups.entrySet()) {
            List<DiseaseComparisonData> bridgeItems = bridgeEntry.getValue();

            // 设置桥梁级别合并信息
            if (!bridgeItems.isEmpty()) {
                bridgeItems.get(0).setFirstInBridge(true);
                bridgeItems.get(0).setBridgeRowSpan(bridgeItems.size());
            }

            // 按部位1分组
            Map<String, List<DiseaseComparisonData>> pos1Groups = bridgeItems.stream()
                    .collect(Collectors.groupingBy(DiseaseComparisonData::getPosition1, LinkedHashMap::new, Collectors.toList()));

            for (Map.Entry<String, List<DiseaseComparisonData>> pos1Entry : pos1Groups.entrySet()) {
                List<DiseaseComparisonData> pos1Items = pos1Entry.getValue();

                if (!pos1Items.isEmpty()) {
                    pos1Items.get(0).setFirstInPosition1(true);
                    pos1Items.get(0).setPosition1RowSpan(pos1Items.size());
                }

                // 按部位2分组
                Map<String, List<DiseaseComparisonData>> pos2Groups = pos1Items.stream()
                        .collect(Collectors.groupingBy(DiseaseComparisonData::getPosition2, LinkedHashMap::new, Collectors.toList()));

                for (Map.Entry<String, List<DiseaseComparisonData>> pos2Entry : pos2Groups.entrySet()) {
                    List<DiseaseComparisonData> pos2Items = pos2Entry.getValue();

                    if (!pos2Items.isEmpty()) {
                        pos2Items.get(0).setFirstInPosition2(true);
                        pos2Items.get(0).setPosition2RowSpan(pos2Items.size());
                    }

                    // 按构件分组
                    Map<String, List<DiseaseComparisonData>> componentGroups = pos2Items.stream()
                            .collect(Collectors.groupingBy(DiseaseComparisonData::getComponent, LinkedHashMap::new, Collectors.toList()));

                    for (Map.Entry<String, List<DiseaseComparisonData>> componentEntry : componentGroups.entrySet()) {
                        List<DiseaseComparisonData> componentItems = componentEntry.getValue();

                        if (!componentItems.isEmpty()) {
                            componentItems.get(0).setFirstInComponent(true);
                            componentItems.get(0).setComponentRowSpan(componentItems.size());
                        }
                    }
                }
            }
        }
    }

    /**
     * 层级信息内部类
     */
    private static class HierarchyInfo {
        private String bridgeName;
        private String position1;
        private String position2;
        private String component;
        private Long rootObjectId;
        private Long level2ObjectId;
        private Long level3ObjectId;
        private Long level4ObjectId;

        // Getters and Setters
        public String getBridgeName() {
            return bridgeName;
        }

        public void setBridgeName(String bridgeName) {
            this.bridgeName = bridgeName;
        }

        public String getPosition1() {
            return position1;
        }

        public void setPosition1(String position1) {
            this.position1 = position1;
        }

        public String getPosition2() {
            return position2;
        }

        public void setPosition2(String position2) {
            this.position2 = position2;
        }

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

        public Long getRootObjectId() {
            return rootObjectId;
        }

        public void setRootObjectId(Long rootObjectId) {
            this.rootObjectId = rootObjectId;
        }

        public Long getLevel2ObjectId() {
            return level2ObjectId;
        }

        public void setLevel2ObjectId(Long level2ObjectId) {
            this.level2ObjectId = level2ObjectId;
        }

        public Long getLevel3ObjectId() {
            return level3ObjectId;
        }

        public void setLevel3ObjectId(Long level3ObjectId) {
            this.level3ObjectId = level3ObjectId;
        }

        public Long getLevel4ObjectId() {
            return level4ObjectId;
        }

        public void setLevel4ObjectId(Long level4ObjectId) {
            this.level4ObjectId = level4ObjectId;
        }
    }


}