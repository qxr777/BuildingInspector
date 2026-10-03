package edu.whut.cs.bi.biz.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 定期检查养护建议字典，来源：定期检查记录表养护建议-新增部件.xlsx，Sheet1 B2:D61。
 * 先匹配部件规则，再匹配 ALL 通用规则；病害名称精确匹配，兼容 Excel 中顿号并列的名称。
 */
public final class RegularInspectionMaintenanceMatcher {
    private static final String ALL_COMPONENTS = "ALL";
    private static final Map<String, ComponentRules> RECOMMENDATIONS = loadRecommendations();

    private RegularInspectionMaintenanceMatcher() {
    }

    public static String match(String componentName, Collection<String> diseaseTypeNames) {
        if (diseaseTypeNames == null || diseaseTypeNames.isEmpty()) {
            return "";
        }
        Set<String> recommendations = new LinkedHashSet<>();
        ComponentRules componentRules = RECOMMENDATIONS.get(normalizeComponent(componentName));
        ComponentRules commonRules = RECOMMENDATIONS.get(ALL_COMPONENTS);
        for (String name : diseaseTypeNames) {
            String key = normalize(name);
            String recommendation = componentRules == null ? null : componentRules.match(key);
            if (recommendation == null && commonRules != null) {
                recommendation = commonRules.match(key);
            }
            if (recommendation != null) {
                recommendations.add(recommendation);
            }
        }
        return String.join("；", recommendations);
    }

    private static Map<String, ComponentRules> loadRecommendations() {
        try (InputStream input = RegularInspectionMaintenanceMatcher.class.getResourceAsStream(
                "/json/regular_inspection_maintenance.json")) {
            if (input == null) {
                throw new IllegalStateException("未找到定期检查养护建议字典");
            }
            JsonNode source = new ObjectMapper().readTree(input);
            if (source == null || !source.isArray() || source.isEmpty()) {
                throw new IllegalStateException("定期检查养护建议字典必须为非空规则数组");
            }
            Map<String, ComponentRules> rules = new LinkedHashMap<>();
            for (JsonNode row : source) {
                String components = requiredText(row, "componentName");
                String diseaseType = normalize(requiredText(row, "diseaseType"));
                String recommendation = requiredText(row, "recommendation");
                for (String component : components.split("、")) {
                    String key = normalizeComponent(component);
                    if (key.isEmpty()) {
                        throw new IllegalStateException("定期检查养护建议字典存在空部件名称");
                    }
                    ComponentRules componentRules = rules.computeIfAbsent(key, ignored -> new ComponentRules());
                    componentRules.add(diseaseType, recommendation);
                }
            }
            return rules;
        } catch (IOException e) {
            throw new IllegalStateException("读取定期检查养护建议字典失败", e);
        }
    }

    private static String requiredText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || normalize(value.asText()).isEmpty()) {
            throw new IllegalStateException("定期检查养护建议字典字段为空或格式错误: " + field);
        }
        return value.asText();
    }

    private static final class ComponentRules {
        private final Map<String, String> exact = new LinkedHashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();

        private void add(String name, String recommendation) {
            putRule(exact, name, recommendation);
            if (name.contains("、")) {
                for (String alias : name.split("、")) {
                    putRule(aliases, alias, recommendation);
                }
            }
        }

        private String match(String name) {
            String recommendation = exact.get(name);
            return recommendation == null ? aliases.get(name) : recommendation;
        }

        private void putRule(Map<String, String> target, String name, String recommendation) {
            String previous = target.putIfAbsent(name, recommendation);
            if (previous != null && !previous.equals(recommendation)) {
                throw new IllegalStateException("同一部件的定期检查养护建议规则冲突: " + name);
            }
        }
    }

    private static String normalizeComponent(String value) {
        // 表格/结构树中的“栏杆、护栏”与 Excel 的“栏杆护栏”是同一部件。
        String name = normalize(value).replace("、", "").replace("，", "").replace(",", "");
        return ALL_COMPONENTS.equalsIgnoreCase(name) ? ALL_COMPONENTS : name;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u3000\\u00a0]+", "")
                .replace('（', '(').replace('）', ')');
    }
}
