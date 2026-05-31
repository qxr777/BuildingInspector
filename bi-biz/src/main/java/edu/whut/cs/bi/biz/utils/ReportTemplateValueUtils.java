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
    private static final Map<String, List<String>> PROPERTY_ALIASES = new LinkedHashMap<>();
    private static final Set<String> SKIPPED_BI_OBJECT_NAMES = Set.of("附属设施", "其他");

    static {
        PLACEHOLDER_ALIASES.put("${client-unit}", Arrays.asList("${委托单位}"));
        PLACEHOLDER_ALIASES.put("${engineering-name}", Arrays.asList("${工程名称}"));
        PLACEHOLDER_ALIASES.put("${project-name}", Arrays.asList("${检测项目}"));
        PLACEHOLDER_ALIASES.put("${detection-category}", Arrays.asList("${检测类别}"));

        PROPERTY_ALIASES.put("路线等级", Arrays.asList("路线等级", "道路等级", "公路等级"));
        PROPERTY_ALIASES.put("桥位桩号", Arrays.asList("桥位桩号", "中心桩号", "桥梁桩号"));
        PROPERTY_ALIASES.put("设计荷载", Arrays.asList("设计荷载", "荷载等级"));
        PROPERTY_ALIASES.put("设计单位", Arrays.asList("设计单位"));
        PROPERTY_ALIASES.put("施工单位", Arrays.asList("施工单位"));
        PROPERTY_ALIASES.put("监理单位", Arrays.asList("监理单位"));
        PROPERTY_ALIASES.put("业主单位", Arrays.asList("业主单位", "建设单位", "管养单位"));
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

        for (Map.Entry<String, List<String>> entry : PROPERTY_ALIASES.entrySet()) {
            String targetName = entry.getKey();
            if (hasText(valueOf(propertyMap.get(targetName)))) {
                continue;
            }
            String value = firstValue(propertyMap, entry.getValue());
            if (!hasText(value)) {
                continue;
            }
            Property target = propertyMap.get(targetName);
            if (target == null) {
                target = new Property();
                target.setName(targetName);
                properties.add(target);
                propertyMap.put(targetName, target);
            }
            target.setValue(value);
        }
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
            if (hasText(value)) {
                return value;
            }
        }
        return "";
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
