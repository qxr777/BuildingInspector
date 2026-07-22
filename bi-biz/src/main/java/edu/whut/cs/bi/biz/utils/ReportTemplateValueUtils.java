package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.ReportData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Report template value normalization shared by combined bridge export code.
 */
public final class ReportTemplateValueUtils {

    private static final Map<String, List<String>> PLACEHOLDER_ALIASES = new LinkedHashMap<>();
    private static final List<List<String>> PROPERTY_ALIAS_GROUPS = new ArrayList<>();
    private static final Set<String> SKIPPED_BI_OBJECT_NAMES = Set.of("附属设施", "其他");

    static {
        PLACEHOLDER_ALIASES.put("${client-unit}", Arrays.asList("${委托单位}"));
        PLACEHOLDER_ALIASES.put("${engineering-name}", Arrays.asList("${工程名称}"));
        PLACEHOLDER_ALIASES.put("${project-name}", Arrays.asList("${检测项目}"));
        PLACEHOLDER_ALIASES.put("${detection-category}", Arrays.asList("${检测类别}"));

        addPropertyAliasGroup("路线技术等级", "路线等级", "道路等级", "公路等级");
        addPropertyAliasGroup("桥梁代码", "桥梁编号");
        addPropertyAliasGroup("桥梁中心桩号", "桥位桩号", "中心桩号", "桥梁桩号");
        addPropertyAliasGroup("被跨越道路（通道）名称", "被跨越道路名称", "跨越地物名称");
        addPropertyAliasGroup("被跨越道路（通道）桩号", "被跨越道路桩号", "跨越地物桩号");
        addPropertyAliasGroup("设计荷载等级", "设计荷载", "荷载等级");
        addPropertyAliasGroup("建成通车日期", "建成时间", "建成日期", "修建年度");
        addPropertyAliasGroup("设计单位名称", "设计单位");
        addPropertyAliasGroup("施工单位名称", "施工单位");
        addPropertyAliasGroup("监理单位名称", "监理单位");
        addPropertyAliasGroup("建设单位名称", "建设单位", "业主单位");

        addPropertyAliasGroup("桥梁全长", "桥梁全长(m)", "桥梁全长（m）");
        addPropertyAliasGroup("桥梁全宽", "桥面总宽(m)", "桥面总宽（m）", "桥面总宽");
        addPropertyAliasGroup("车行道宽", "车道宽度", "车道宽度(m)", "车道宽度（m）");
        addPropertyAliasGroup("人行道宽度", "人行道宽度(m)", "人行道宽度（m）");
        addPropertyAliasGroup("护栏或防撞墙高度", "护栏或防撞墙高度(m)", "护栏或防撞墙高度（m）");
        addPropertyAliasGroup("中央分隔带宽度", "中央分隔带宽度(m)", "中央分隔带宽度（m）");
        addPropertyAliasGroup("桥面标准净空", "桥面标准净空(m)", "桥面标准净空（m）");
        addPropertyAliasGroup("桥面实际净空", "桥面实际净空(m)", "桥面实际净空（m）");
        addPropertyAliasGroup("桥下实际净空", "桥下实际净空(m)", "桥下实际净空（m）");
        addPropertyAliasGroup("引道总宽", "引道总宽(m)", "引道总宽（m）");
        addPropertyAliasGroup("历史洪水位(米)", "历史洪水位", "历史洪水位(m)", "历史洪水位（米）");
        addPropertyAliasGroup("抗震等级", "设计地震动峰值加速度系数");
        addPropertyAliasGroup("桥面标高", "桥面高程", "桥面高程(m)", "桥面高程（m）");

        addPropertyAliasGroup("桥型", "结构体系");
        addPropertyAliasGroup("桥面铺装类型", "桥面铺装");
        addPropertyAliasGroup("伸缩缝类型", "伸缩缝");
        addPropertyAliasGroup("护栏位置分布", "栏杆、护栏");
        addPropertyAliasGroup("附属设施", "照明、标志");
        addPropertyAliasGroup("桥台形式", "桥台");
        addPropertyAliasGroup("桥墩形式", "桥墩");
        addPropertyAliasGroup("支座类型", "支座");
        addPropertyAliasGroup("抗洪防护工程类型", "调治构造物");

        addPropertyAliasGroup("最近评定日期", "评定时间1");
        addPropertyAliasGroup("桥梁技术状况", "评定结果1");
        addPropertyAliasGroup("上一次处治对策", "处治对策1");
    }

    private ReportTemplateValueUtils() {
    }

    public static Map<String, String> buildPlaceholderValues(List<ReportData> reportDataList,
                                                             Map<String, String> defaultValues) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> userKeys = new LinkedHashSet<>();
        if (defaultValues != null) {
            defaultValues.forEach((key, value) -> putIfHasText(values, key, value));
        }
        if (reportDataList != null) {
            for (ReportData data : reportDataList) {
                if (data == null || !hasText(data.getKey()) || !hasText(data.getValue())) {
                    continue;
                }
                String key = normalizePlaceholder(data.getKey());
                values.put(key, data.getValue());
                userKeys.add(key);
            }
        }
        addPlaceholderAliases(values, userKeys);
        return values;
    }

    public static void addAliasProperties(List<Property> properties) {
        if (properties == null) {
            return;
        }
        Map<String, Property> propertyMap = properties.stream()
                .filter(Objects::nonNull)
                .filter(property -> hasText(property.getName()))
                .collect(Collectors.toMap(Property::getName, property -> property, (left, right) -> left, LinkedHashMap::new));

        for (List<String> aliases : PROPERTY_ALIAS_GROUPS) {
            String value = firstValue(propertyMap, aliases);
            if (!hasText(value)) {
                continue;
            }
            for (String alias : aliases) {
                putPropertyIfMissing(properties, propertyMap, alias, value);
            }
        }

        addDerivedProperties(properties, propertyMap);
    }

    public static List<BiObject> sortedReportChildren(Collection<BiObject> allNodes, Long parentId) {
        if (allNodes == null) {
            return new ArrayList<>();
        }
        return allNodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> Objects.equals(parentId, node.getParentId()))
                .filter(node -> !shouldSkipBiObject(node))
                .sorted(Comparator
                        .comparing(BiObject::getOrderNum, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BiObject::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(BiObject::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    public static boolean shouldSkipBiObject(BiObject node) {
        return node == null || SKIPPED_BI_OBJECT_NAMES.contains(node.getName());
    }

    public static String buildDiseaseSummary(List<Disease> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            return "未见明显病害。";
        }
        return diseases.stream()
                .filter(Objects::nonNull)
                .map(ReportTemplateValueUtils::describeDisease)
                .filter(ReportTemplateValueUtils::hasText)
                .distinct()
                .collect(Collectors.joining("；"));
    }

    public static int findNearestFollowingTableIndex(List<String> bodyElementTypes, int startIndex, int maxLookAhead) {
        if (bodyElementTypes == null || startIndex < 0 || maxLookAhead <= 0) {
            return -1;
        }
        int end = Math.min(bodyElementTypes.size(), startIndex + maxLookAhead + 1);
        for (int i = startIndex + 1; i < end; i++) {
            if ("tbl".equals(bodyElementTypes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void addPlaceholderAliases(Map<String, String> values, Set<String> userKeys) {
        Map<String, String> snapshot = new LinkedHashMap<>(values);
        for (Map.Entry<String, List<String>> entry : PLACEHOLDER_ALIASES.entrySet()) {
            String canonical = entry.getKey();
            String canonicalValue = snapshot.get(canonical);
            boolean canonicalFromUser = userKeys.contains(canonical);
            for (String alias : entry.getValue()) {
                String aliasValue = snapshot.get(alias);
                boolean aliasFromUser = userKeys.contains(alias);
                if (aliasFromUser && hasText(aliasValue) && !canonicalFromUser) {
                    values.put(canonical, aliasValue);
                    values.put(alias, aliasValue);
                    canonicalValue = aliasValue;
                } else if (hasText(canonicalValue)) {
                    values.put(alias, canonicalValue);
                } else if (hasText(aliasValue)) {
                    values.put(canonical, aliasValue);
                    canonicalValue = aliasValue;
                }
            }
        }
    }

    private static String describeDisease(Disease disease) {
        StringBuilder builder = new StringBuilder();
        append(builder, disease.getPosition());
        append(builder, diseaseTypeName(disease.getType()));
        if (disease.getQuantity() > 0) {
            builder.append(disease.getQuantity());
            if (hasText(disease.getUnits())) {
                builder.append(disease.getUnits());
            }
        }
        append(builder, disease.getDescription());
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (!hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("，");
        }
        builder.append(value.trim());
    }

    private static String diseaseTypeName(String type) {
        if (!hasText(type)) {
            return "";
        }
        int index = type.lastIndexOf('#');
        return index >= 0 && index + 1 < type.length() ? type.substring(index + 1) : type;
    }

    private static String firstValue(Map<String, Property> propertyMap, List<String> names) {
        for (String name : names) {
            String value = valueOf(propertyMap.get(name));
            if (isMeaningful(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static void addPropertyAliasGroup(String... names) {
        PROPERTY_ALIAS_GROUPS.add(Arrays.asList(names));
    }

    private static void addDerivedProperties(List<Property> properties, Map<String, Property> propertyMap) {
        putDerivedPropertyIfMissing(properties, propertyMap, "功能类型",
                firstMeaningfulValue(propertyMap, "功能类型", "是否公铁两用桥梁"));
        putDerivedPropertyIfMissing(properties, propertyMap, "管养单位",
                combineLabeledValues(propertyMap,
                        "管理", "管理单位名称",
                        "养护", "养护单位名称"));
        putDerivedPropertyIfMissing(properties, propertyMap, "桥下通航等级及标准净空",
                combineLabeledValues(propertyMap,
                        "等级", "通航等级（内河）",
                        "净空", "桥下通航标准净空"));
        putDerivedPropertyIfMissing(properties, propertyMap, "引道线形或曲线半径",
                combineLabeledValues(propertyMap,
                        "线形", "引道线形",
                        "半径", "引道曲线半径"));
        putDerivedPropertyIfMissing(properties, propertyMap, "设计洪水频率及其水位",
                combineLabeledValues(propertyMap,
                        "频率", "设计洪水频率",
                        "水位", "设计洪水位"));
        putDerivedPropertyIfMissing(properties, propertyMap, "主梁",
                joinValues(propertyMap, "主桥上部构造结构形式", "主桥上部构造材料"));
        putDerivedPropertyIfMissing(properties, propertyMap, "基础",
                joinValues(propertyMap, "基础类型", "基础材料"));
        putDerivedPropertyIfMissing(properties, propertyMap, "桥梁防撞设施",
                combineLabeledValues(propertyMap,
                        "墩台", "墩台防撞设施类型",
                        "船撞", "防船撞设施类型"));
        putDerivedPropertyIfMissing(properties, propertyMap, "航标及排水系统",
                combineLabeledValues(propertyMap,
                        "航标", "航标类型",
                        "排水", "排水类型"));
        putDerivedPropertyIfMissing(properties, propertyMap, "跨径组合", buildSpanCombination(propertyMap));
    }

    private static void putPropertyIfMissing(List<Property> properties,
                                             Map<String, Property> propertyMap,
                                             String name,
                                             String value) {
        Property property = propertyMap.get(name);
        if (isMeaningful(valueOf(property))) {
            return;
        }
        if (property == null) {
            property = new Property();
            property.setName(name);
            properties.add(property);
            propertyMap.put(name, property);
        }
        property.setValue(value);
    }

    private static void putDerivedPropertyIfMissing(List<Property> properties,
                                                    Map<String, Property> propertyMap,
                                                    String name,
                                                    String value) {
        Property property = propertyMap.get(name);
        if (isMeaningful(valueOf(property)) || !isMeaningful(value)) {
            return;
        }
        if (property == null) {
            property = new Property();
            property.setName(name);
            properties.add(property);
            propertyMap.put(name, property);
        }
        property.setValue(value);
    }

    private static String combineLabeledValues(Map<String, Property> propertyMap,
                                               String firstLabel,
                                               String firstName,
                                               String secondLabel,
                                               String secondName) {
        String first = meaningfulValue(propertyMap, firstName);
        String second = meaningfulValue(propertyMap, secondName);
        if (!hasText(first)) {
            return hasText(second) ? secondLabel + "：" + second : "";
        }
        if (!hasText(second)) {
            return firstLabel + "：" + first;
        }
        if (first.equals(second)) {
            return first;
        }
        return firstLabel + "：" + first + "；" + secondLabel + "：" + second;
    }

    private static String joinValues(Map<String, Property> propertyMap, String... names) {
        return Arrays.stream(names)
                .map(name -> meaningfulValue(propertyMap, name))
                .filter(ReportTemplateValueUtils::hasText)
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private static String buildSpanCombination(Map<String, Property> propertyMap) {
        String left = normalizeSpan(meaningfulValue(propertyMap, "左幅"));
        String right = normalizeSpan(meaningfulValue(propertyMap, "右幅"));
        if (!hasText(left)) {
            return right;
        }
        if (!hasText(right) || left.equals(right)) {
            return left;
        }
        return "左幅：" + left + "；右幅：" + right;
    }

    private static String normalizeSpan(String value) {
        return hasText(value) ? value.replace('*', '×') : "";
    }

    private static String firstMeaningfulValue(Map<String, Property> propertyMap, String... names) {
        for (String name : names) {
            String value = meaningfulValue(propertyMap, name);
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String meaningfulValue(Map<String, Property> propertyMap, String name) {
        String value = valueOf(propertyMap.get(name));
        return isMeaningful(value) ? value.trim() : "";
    }

    private static boolean isMeaningful(String value) {
        if (!hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return !"/".equals(trimmed)
                && !"-".equals(trimmed)
                && !"null".equalsIgnoreCase(trimmed)
                && !"<null>".equalsIgnoreCase(trimmed);
    }

    private static String valueOf(Property property) {
        return property == null ? null : property.getValue();
    }

    private static void putIfHasText(Map<String, String> values, String key, String value) {
        if (hasText(key) && hasText(value)) {
            values.put(normalizePlaceholder(key), value);
        }
    }

    private static String normalizePlaceholder(String key) {
        String trimmed = key.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed;
        }
        return "${" + trimmed + "}";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
