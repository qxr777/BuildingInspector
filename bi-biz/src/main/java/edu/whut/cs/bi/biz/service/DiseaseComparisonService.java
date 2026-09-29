package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.vo.DiseaseComparisonData;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 病害对比数据处理服务
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class DiseaseComparisonService {

    private static final Pattern AREA_MULTIPLIER_PATTERN = Pattern.compile(
            "m(?:²|2)?\\s*[×xX*]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AVERAGE_LENGTH_VALUE_PATTERN = Pattern.compile(
            "(?:(?:长度\\s*)?L\\s*均|平均长度)\\s*[=:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*m",
            Pattern.CASE_INSENSITIVE);

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
                "，当前年份: " + currentYear);

        List<Disease> currentYearDiseases = diseaseMapper.selectDiseaseComponentData(level4ObjectIds, bubuildingId, currentYear);

        long queryTime = System.currentTimeMillis() - startTime;
        log.info("批量查询完成，当前年份病害: " + currentYearDiseases.size() +
                "条，查询耗时: " + queryTime + "ms");

        List<Long> allDiseaseIds = currentYearDiseases.stream()
                .filter(disease -> disease.getId() != null)
                .map(Disease::getId)
                .collect(Collectors.toList());

        List<DiseaseDetail> allDiseaseDetails = new ArrayList<>();
        if (!allDiseaseIds.isEmpty()) {
            allDiseaseDetails = diseaseDetailMapper.selectDiseaseDetailsByDiseaseIds(allDiseaseIds);
        }

        Map<Long, List<DiseaseDetail>> diseaseDetailMap = allDiseaseDetails.stream()
                .collect(Collectors.groupingBy(DiseaseDetail::getDiseaseId));

        for (Disease disease : currentYearDiseases) {
            if (disease.getId() != null) {
                disease.setDiseaseDetails(diseaseDetailMap.getOrDefault(disease.getId(), new ArrayList<>()));
            }
        }

        long detailQueryTime = System.currentTimeMillis() - startTime - queryTime;
        log.info("病害详情查询完成，详情数量: " + allDiseaseDetails.size() +
                "条，详情查询耗时: " + detailQueryTime + "ms");

        Map<Long, Map<Long, List<Disease>>> currentYearGrouped = currentYearDiseases.stream()
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

            for (Long diseaseTypeId : currentYearDiseasesByType.keySet()) {
                List<Disease> typeDiseases = currentYearDiseasesByType.get(diseaseTypeId);
                String diseaseTypeName = getDiseaseTypeNameFromDiseases(typeDiseases);
                if (diseaseTypeName == null) continue;

                List<Disease> lastYearDiseases = filterByTrends(typeDiseases,
                        "稳定", "发展", "已维修", "部分维修", "未找到");
                List<Disease> thisYearDiseases = filterByTrends(typeDiseases,
                        "稳定", "新增", "发展", "部分维修");
                List<Disease> repairedDiseases = filterByTrends(typeDiseases, "已维修");
                List<Disease> developingDiseases = filterByTrends(typeDiseases, "发展");
                List<Disease> partiallyRepairedDiseases = filterByTrends(typeDiseases, "部分维修");
                List<Disease> missingDiseases = filterByTrends(typeDiseases, "未找到");
                List<Disease> addedDiseases = filterByTrends(typeDiseases, "新增");
                if (lastYearDiseases.isEmpty() && thisYearDiseases.isEmpty()) {
                    continue;
                }

                DiseaseComparisonData data = new DiseaseComparisonData();

                data.setCurrentYear(currentYear);
                data.setLastYear(previousYear);

                data.setBridgeName(hierarchy.getBridgeName());
                data.setPosition1(hierarchy.getPosition1());
                data.setPosition2(hierarchy.getPosition2());
                data.setComponent(hierarchy.getComponent());
                data.setDiseaseType(diseaseTypeName);

                data.setRootObjectId(hierarchy.getRootObjectId());
                data.setLevel2ObjectId(hierarchy.getLevel2ObjectId());
                data.setLevel3ObjectId(hierarchy.getLevel3ObjectId());
                data.setLevel4ObjectId(hierarchy.getLevel4ObjectId());
                data.setDiseaseTypeId(diseaseTypeId);

                data.setQuantity2023(sumQuantity(lastYearDiseases));
                data.setSeverity2023(generateSeverityDescription(lastYearDiseases));
                data.setQuantity2024(sumQuantity(thisYearDiseases));
                data.setSeverity2024(generateSeverityDescription(thisYearDiseases));
                data.setDevelopmentStatus(generateDevelopmentStatus(
                        repairedDiseases, developingDiseases, partiallyRepairedDiseases,
                        missingDiseases, addedDiseases));

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
    private String getDiseaseTypeNameFromDiseases(List<Disease> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            return null;
        }
        Disease disease = diseases.get(0);
        if (disease.getDiseaseType() != null) {
            return disease.getDiseaseType().getName();
        }
        return null;
    }

    private static List<Disease> filterByTrends(List<Disease> diseases, String... trends) {
        Set<String> allowed = new HashSet<>(Arrays.asList(trends));
        return diseases.stream()
                .filter(disease -> allowed.contains(trendOf(disease)))
                .collect(Collectors.toList());
    }

    private static String trendOf(Disease disease) {
        if (disease == null || disease.getDevelopmentTrend() == null) {
            return "";
        }
        return disease.getDevelopmentTrend().trim();
    }

    /**
     * 病害数量以数据库 quantity 字段为准，不能用病害记录条数代替。
     */
    static int sumQuantity(List<Disease> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            return 0;
        }
        return diseases.stream()
                .filter(Objects::nonNull)
                .mapToInt(disease -> Math.max(disease.getQuantity(), 0))
                .sum();
    }

    /**
     * 生成病害程度描述
     */
    private String generateSeverityDescription(List<Disease> diseases) {
        if (diseases.isEmpty()) {
            return "/";
        }

        DiseaseAggregateData data = extractAggregateData(diseases);

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

        if (sb.length() == 0) {
            sb.append("/");
        }

        return sb.toString();
    }

    /**
     * 根据单条病害的发展趋势生成变化说明。
     */
    private String generateDevelopmentStatus(List<Disease> repairedDiseases,
                                             List<Disease> developingDiseases,
                                             List<Disease> partiallyRepairedDiseases,
                                             List<Disease> missingDiseases,
                                             List<Disease> addedDiseases) {
        List<String> statusParts = new ArrayList<>();
        appendTrendStatus(statusParts, "修复", repairedDiseases);
        appendTrendStatus(statusParts, "部分维修", partiallyRepairedDiseases);
        appendTrendStatus(statusParts, "未找到", missingDiseases);
        appendTrendStatus(statusParts, "发展", developingDiseases);

        if (addedDiseases != null && !addedDiseases.isEmpty()) {
            StringBuilder added = new StringBuilder("新增")
                    .append(sumQuantity(addedDiseases)).append("条");
            String addedSeverity = generateSeverityDescription(addedDiseases);
            if (addedSeverity != null && !addedSeverity.isEmpty() && !"/".equals(addedSeverity)) {
                added.append("，").append(addedSeverity);
            }
            statusParts.add(added.toString());
        }
        return statusParts.isEmpty() ? "稳定" : String.join("，", statusParts);
    }

    private static void appendTrendStatus(List<String> statusParts, String label, List<Disease> diseases) {
        if (diseases != null && !diseases.isEmpty()) {
            statusParts.add(label + sumQuantity(diseases) + "条");
        }
    }

    /**
     * 提取病害列表的汇总数据（通用方法）
     */
    private DiseaseAggregateData extractAggregateData(List<Disease> diseases) {
        DiseaseAggregateData data = new DiseaseAggregateData();

        if (diseases == null || diseases.isEmpty()) {
            return data;
        }

        for (Disease disease : diseases) {
            List<DiseaseDetail> details = disease.getDiseaseDetails();
            if (details == null || details.isEmpty()) {
                continue;
            }
            addLengthData(data, disease, details);
            addWidthData(data, details);
            addAreaData(data, disease, details);
        }

        return data;
    }

    private static void addLengthData(DiseaseAggregateData data, Disease disease,
                                      List<DiseaseDetail> details) {
        List<Double> exactLengths = new ArrayList<>();
        for (DiseaseDetail detail : details) {
            if (detail.getLength1() != null) exactLengths.add(detail.getLength1().doubleValue());
            if (detail.getLength2() != null) exactLengths.add(detail.getLength2().doubleValue());
            if (detail.getLength3() != null) exactLengths.add(detail.getLength3().doubleValue());
        }
        if (!exactLengths.isEmpty()) {
            double sum = exactLengths.stream().mapToDouble(Double::doubleValue).sum();
            String description = textOf(disease);
            if (isAverageLength(description)) {
                Double statedAverage = statedAverageLength(description);
                sum = (statedAverage == null ? sum / exactLengths.size() : statedAverage)
                        * positiveQuantity(disease);
            } else if (!isTotalLength(description)
                    && exactLengths.size() == 1 && positiveQuantity(disease) > 1) {
                // 一条结构化明细对应多处病害时，该值按每处尺寸处理。
                sum *= positiveQuantity(disease);
            }
            data.totalLength += sum;
            data.hasLengthData = true;
            return;
        }

        for (DiseaseDetail detail : details) {
            if (detail.getLengthRangeStart() == null && detail.getLengthRangeEnd() == null) {
                continue;
            }
            double start = detail.getLengthRangeStart() == null
                    ? detail.getLengthRangeEnd().doubleValue()
                    : detail.getLengthRangeStart().doubleValue();
            double end = detail.getLengthRangeEnd() == null
                    ? start
                    : detail.getLengthRangeEnd().doubleValue();
            data.totalLength += ((start + end) / 2D) * positiveQuantity(disease);
            data.hasLengthData = true;
            return;
        }
    }

    private static void addWidthData(DiseaseAggregateData data, List<DiseaseDetail> details) {
        for (DiseaseDetail detail : details) {
            if (detail.getCrackWidth() != null || detail.getWidth() != null) {
                double width = (detail.getCrackWidth() != null
                        ? detail.getCrackWidth()
                        : detail.getWidth()).doubleValue();
                data.minWidth = Math.min(data.minWidth, width);
                data.maxWidth = Math.max(data.maxWidth, width);
                data.hasWidthData = true;
            }
            if (detail.getCrackWidthRangeStart() != null || detail.getCrackWidthRangeEnd() != null) {
                double start = detail.getCrackWidthRangeStart() == null
                        ? detail.getCrackWidthRangeEnd().doubleValue()
                        : detail.getCrackWidthRangeStart().doubleValue();
                double end = detail.getCrackWidthRangeEnd() == null
                        ? start
                        : detail.getCrackWidthRangeEnd().doubleValue();
                data.minWidth = Math.min(data.minWidth, Math.min(start, end));
                data.maxWidth = Math.max(data.maxWidth, Math.max(start, end));
                data.hasWidthData = true;
            }
        }
    }

    private static void addAreaData(DiseaseAggregateData data, Disease disease,
                                    List<DiseaseDetail> details) {
        List<AreaValue> areas = details.stream()
                .filter(detail -> detail.getAreaLength() != null && detail.getAreaWidth() != null)
                .map(detail -> new AreaValue(
                        detail.getAreaLength().doubleValue() * detail.getAreaWidth().doubleValue(),
                        detail.getAreaIdentifier()))
                .collect(Collectors.toList());
        if (areas.isEmpty()) {
            return;
        }

        double total;
        boolean hasStructuredIdentifier = areas.stream()
                .anyMatch(area -> Objects.equals(area.identifier, 1) || Objects.equals(area.identifier, 2));
        if (hasStructuredIdentifier) {
            double ordinary = areas.stream()
                    .filter(area -> area.identifier == null || area.identifier == 0)
                    .mapToDouble(area -> area.value).sum();
            List<AreaValue> averages = areas.stream()
                    .filter(area -> Objects.equals(area.identifier, 1)).collect(Collectors.toList());
            double average = averages.isEmpty() ? 0 : averages.stream()
                    .mapToDouble(area -> area.value).average().orElse(0) * positiveQuantity(disease);
            double statedTotals = areas.stream()
                    .filter(area -> Objects.equals(area.identifier, 2))
                    .mapToDouble(area -> area.value).sum();
            total = ordinary + average + statedTotals;
        } else {
            String description = textOf(disease);
            double sum = areas.stream().mapToDouble(area -> area.value).sum();
            if (isTotalArea(description) || isIndexedArea(description)) {
                total = sum;
            } else {
                Integer multiplier = explicitAreaMultiplier(description);
                if (multiplier != null) {
                    total = areas.get(0).value * multiplier;
                } else if (isAverageArea(description)) {
                    total = areas.stream().mapToDouble(area -> area.value).average().orElse(0)
                            * positiveQuantity(disease);
                } else if (areas.size() == 1 && positiveQuantity(disease) > 1) {
                    total = sum * positiveQuantity(disease);
                } else {
                    total = sum;
                }
            }
        }
        data.totalArea += total;
        data.hasAreaData = true;
    }

    private static int positiveQuantity(Disease disease) {
        return Math.max(disease == null ? 0 : disease.getQuantity(), 0);
    }

    private static String textOf(Disease disease) {
        return disease == null || disease.getDescription() == null ? "" : disease.getDescription();
    }

    private static boolean isAverageLength(String description) {
        return description.contains("L均") || description.contains("长度均")
                || description.contains("平均长度");
    }

    private static Double statedAverageLength(String description) {
        Matcher matcher = AVERAGE_LENGTH_VALUE_PATTERN.matcher(description);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private static boolean isTotalLength(String description) {
        return description.contains("L总") || description.contains("总长度");
    }

    private static boolean isAverageArea(String description) {
        return description.contains("S均") || description.contains("平均面积");
    }

    private static boolean isTotalArea(String description) {
        return description.contains("S总") || description.contains("总面积");
    }

    private static boolean isIndexedArea(String description) {
        return description.matches("(?s).*S_?\\d+\\s*=.*");
    }

    private static Integer explicitAreaMultiplier(String description) {
        Matcher matcher = AREA_MULTIPLIER_PATTERN.matcher(description);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static final class AreaValue {
        private final double value;
        private final Integer identifier;

        private AreaValue(double value, Integer identifier) {
            this.value = value;
            this.identifier = identifier;
        }
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
