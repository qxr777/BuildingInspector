package edu.whut.cs.bi.biz.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 病害成因/处治建议知识库，来源：桥梁病害成因分析与处治建议修订V3.xlsx（由 scripts/disease_cause_kb_from_excel.py 转换）。
 * <p>先按桥梁模板、结构和构件名称路由到 Excel 的 sheet/类型/部件，再按病害名称二元字重合度排序截断，
 * 结果作为大模型的候选范围；成因和处治建议原文只从这里取，不由大模型生成。</p>
 */
public final class DiseaseCauseKnowledgeBase {
    public static final int MAX_CANDIDATES = 30;
    private static final String KB_RESOURCE = "/json/disease_cause_kb.json";
    private static final String ROUTING_RESOURCE = "/json/disease_cause_routing.json";
    private static final Pattern LEVEL_MARKER = Pattern.compile(
            "标度\\s*(\\d)\\s*(?:[~～\\-－—至、,，]\\s*(\\d))?\\s*[：:]");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsHan}A-Za-z]+");
    private static volatile DiseaseCauseKnowledgeBase defaultInstance;

    private final List<Entry> entries;
    private final Map<String, Entry> entriesById;
    private final Map<String, String> templateGroups;
    private final Map<String, List<String>> typeSynonyms;
    private final List<Rule> rules;

    private DiseaseCauseKnowledgeBase(List<Entry> entries, Map<String, String> templateGroups,
                                      Map<String, List<String>> typeSynonyms, List<Rule> rules) {
        this.entries = entries;
        this.templateGroups = templateGroups;
        this.typeSynonyms = typeSynonyms;
        this.rules = rules;
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (byId.put(entry.id(), entry) != null) {
                throw new IllegalStateException("病害成因知识库存在重复 id: " + entry.id());
            }
        }
        this.entriesById = byId;
    }

    public static DiseaseCauseKnowledgeBase getDefault() {
        DiseaseCauseKnowledgeBase instance = defaultInstance;
        if (instance == null) {
            synchronized (DiseaseCauseKnowledgeBase.class) {
                instance = defaultInstance;
                if (instance == null) {
                    try (InputStream kb = DiseaseCauseKnowledgeBase.class.getResourceAsStream(KB_RESOURCE);
                         InputStream routing = DiseaseCauseKnowledgeBase.class.getResourceAsStream(ROUTING_RESOURCE)) {
                        if (kb == null || routing == null) {
                            throw new IllegalStateException("未找到病害成因知识库或路由配置");
                        }
                        instance = load(kb, routing);
                    } catch (IOException e) {
                        throw new IllegalStateException("读取病害成因知识库失败", e);
                    }
                    defaultInstance = instance;
                }
            }
        }
        return instance;
    }

    static DiseaseCauseKnowledgeBase load(InputStream kbInput, InputStream routingInput) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode kb = mapper.readTree(kbInput);
        JsonNode routing = mapper.readTree(routingInput);

        List<Entry> entries = new ArrayList<>();
        for (JsonNode node : kb.path("entries")) {
            entries.add(new Entry(text(node, "id"), text(node, "sheet"), text(node, "structure"),
                    text(node, "category"), text(node, "component"), text(node, "defectClass"),
                    text(node, "defectType"), text(node, "defectName"), text(node, "condition"),
                    text(node, "cause"), text(node, "suggestion")));
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("病害成因知识库为空");
        }
        Set<String> sheets = new HashSet<>();
        entries.forEach(entry -> sheets.add(entry.sheet()));

        Map<String, String> templateGroups = new LinkedHashMap<>();
        routing.path("templateGroups").fields().forEachRemaining(group -> {
            for (String template : strings(group.getValue())) {
                templateGroups.put(normalize(template), group.getKey());
            }
        });

        Map<String, List<String>> typeSynonyms = new LinkedHashMap<>();
        routing.path("typeSynonyms").fields().forEachRemaining(synonym ->
                typeSynonyms.put(synonym.getKey(), strings(synonym.getValue())));

        List<Rule> rules = new ArrayList<>();
        for (JsonNode node : routing.path("rules")) {
            List<Target> targets = new ArrayList<>();
            for (JsonNode target : node.path("targets")) {
                String sheet = text(target, "sheet");
                if (!sheets.contains(sheet)) {
                    throw new IllegalStateException("病害成因路由引用了不存在的 sheet: " + sheet);
                }
                targets.add(new Target(sheet, normalizeAll(strings(target.path("categories"))),
                        normalizeAll(strings(target.path("components"))),
                        normalizeAll(strings(target.path("excludeComponents")))));
            }
            if (targets.isEmpty()) {
                throw new IllegalStateException("病害成因路由规则缺少 targets");
            }
            Set<String> groups = new HashSet<>(strings(node.path("groups")));
            for (String group : groups) {
                if (!templateGroups.containsValue(group)) {
                    throw new IllegalStateException("病害成因路由引用了不存在的模板分组: " + group);
                }
            }
            rules.add(new Rule(text(node, "structure"), groups,
                    new HashSet<>(normalizeAll(strings(node.path("templates")))),
                    normalizeAll(strings(node.path("parts"))),
                    normalizeAll(strings(node.path("names"))), targets));
        }
        return new DiseaseCauseKnowledgeBase(List.copyOf(entries), templateGroups, typeSynonyms, List.copyOf(rules));
    }

    public boolean supportsTemplate(String templateName) {
        return templateGroups.containsKey(normalize(templateName));
    }

    public Entry findById(String id) {
        return id == null ? null : entriesById.get(id);
    }

    /**
     * 返回本次病害的候选条目：路由范围内与病害名称有字面重合的条目，按重合度降序，最多 {@link #MAX_CANDIDATES} 条。
     */
    public List<Entry> candidates(Query query) {
        String group = templateGroups.get(normalize(query.templateName()));
        if (group == null) {
            return List.of();
        }
        Rule rule = selectRule(query, group);
        if (rule == null) {
            return List.of();
        }
        String queryText = queryText(query.diseaseType(), query.crackType());
        StringBuilder expanded = new StringBuilder(queryText);
        typeSynonyms.forEach((keyword, synonyms) -> {
            if (queryText.contains(keyword)) {
                synonyms.forEach(synonym -> expanded.append('、').append(synonym));
            }
        });
        Set<String> queryGrams = bigrams(expanded.toString());
        if (queryGrams.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (Entry entry : entries) {
            if (rule.targets().stream().noneMatch(target -> target.accepts(entry))) {
                continue;
            }
            Set<String> entryGrams = bigrams(entry.defectType() + "、" + entry.defectName() + "、" + entry.condition());
            long overlap = queryGrams.stream().filter(entryGrams::contains).count();
            if (overlap > 0) {
                scored.add(new Scored(entry, (double) overlap / queryGrams.size()));
            }
        }
        // 稳定排序：同分时保留 Excel 原顺序，便于大模型按部件对照。
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream().limit(MAX_CANDIDATES).map(Scored::entry).toList();
    }

    private Rule selectRule(Query query, String group) {
        String structure = normalize(query.structureName());
        String template = normalize(query.templateName());
        String part = normalize(query.partName());
        List<String> names = normalizeAll(query.objectNames());
        if (names.isEmpty() && !part.isEmpty()) {
            names = List.of(part);
        }
        for (Rule rule : rules) {
            if (!normalize(rule.structure()).equals(structure)) {
                continue;
            }
            if (!rule.groups().isEmpty() && !rule.groups().contains(group)) {
                continue;
            }
            if (!rule.templates().isEmpty() && !rule.templates().contains(template)) {
                continue;
            }
            if (!rule.parts().isEmpty() && !containsAny(part, rule.parts())) {
                continue;
            }
            if (rule.names().isEmpty() || names.stream().anyMatch(name -> containsAny(name, rule.names()))) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 按评定标度截取处治建议。Excel 写法如“标度1：…\n标度2~3：…”；无标度分段、标度为空或分段无法覆盖时返回全文，
     * 标度高于所有分段时取最高一段。
     */
    public static String suggestionForLevel(String suggestion, Integer level) {
        if (suggestion == null || suggestion.isBlank() || level == null || level <= 0) {
            return suggestion;
        }
        Matcher matcher = LEVEL_MARKER.matcher(suggestion);
        List<int[]> ranges = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find()) {
            int low = Integer.parseInt(matcher.group(1));
            int high = matcher.group(2) == null ? low : Integer.parseInt(matcher.group(2));
            ranges.add(new int[]{Math.min(low, high), Math.max(low, high)});
            starts.add(matcher.start());
            ends.add(matcher.end());
        }
        if (ranges.isEmpty() || !suggestion.substring(0, starts.get(0)).isBlank()) {
            return suggestion;
        }
        int highest = -1;
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i)[0] <= level && level <= ranges.get(i)[1]) {
                return segment(suggestion, ends, starts, i);
            }
            if (highest < 0 || ranges.get(i)[1] >= ranges.get(highest)[1]) {
                highest = i;
            }
        }
        return level > ranges.get(highest)[1] ? segment(suggestion, ends, starts, highest) : suggestion;
    }

    private static String segment(String text, List<Integer> ends, List<Integer> starts, int index) {
        int end = index + 1 < starts.size() ? starts.get(index + 1) : text.length();
        return text.substring(ends.get(index), end).trim();
    }

    /** 病害类型去掉“5.1.1-2-3#”编码；裂缝类补上裂缝方向（crack_type 默认值会出现在非裂缝病害上，所以只对裂缝补）。 */
    static String queryText(String diseaseType, String crackType) {
        String type = diseaseType == null ? "" : diseaseType.substring(diseaseType.lastIndexOf('#') + 1).trim();
        if (crackType != null && !crackType.isBlank() && type.contains("裂缝") && !type.contains(crackType.trim())) {
            return crackType.trim() + type;
        }
        return type;
    }

    static Set<String> bigrams(String text) {
        Set<String> grams = new LinkedHashSet<>();
        if (text == null) {
            return grams;
        }
        // 钢结构条目写“裂纹”，系统病害多写“裂缝”。
        String normalized = text.replace("裂纹", "裂缝");
        for (String part : NON_WORD.split(normalized)) {
            if (part.length() == 1) {
                grams.add(part);
            }
            for (int i = 0; i + 1 < part.length(); i++) {
                grams.add(part.substring(i, i + 2));
            }
        }
        return grams;
    }

    private static boolean containsAny(String value, Collection<String> keywords) {
        return !value.isEmpty() && keywords.stream().anyMatch(value::contains);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private static List<String> normalizeAll(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(DiseaseCauseKnowledgeBase::normalize)
                .filter(value -> !value.isEmpty()).toList();
    }

    static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u3000\\u00a0]+", "")
                .replace('（', '(').replace('）', ')');
    }

    /**
     * 路由规则中 parts 只匹配 partName，names 只匹配 objectNames；objectNames 为空时以 partName 代替。
     *
     * @param partName    结构下一级（如“上部承重构件”“桥墩”“主拱圈”）
     * @param objectNames 部件以下各级构件名称及病害的 biObjectName，“其他”节点需由调用方剔除
     */
    public record Query(String templateName, String structureName, String partName, List<String> objectNames,
                        String diseaseType, String crackType) {
    }

    public record Entry(String id, String sheet, String structure, String category, String component,
                        String defectClass, String defectType, String defectName, String condition,
                        String cause, String suggestion) {
    }

    private record Rule(String structure, Set<String> groups, Set<String> templates, List<String> parts,
                        List<String> names, List<Target> targets) {
    }

    private record Target(String sheet, List<String> categories, List<String> components,
                          List<String> excludeComponents) {
        boolean accepts(Entry entry) {
            if (!sheet.equals(entry.sheet())) {
                return false;
            }
            String category = normalize(entry.category());
            String component = normalize(entry.component());
            return (categories.isEmpty() || containsAny(category, categories))
                    && (components.isEmpty() || containsAny(component, components))
                    && !containsAny(component, excludeComponents);
        }
    }

    private record Scored(Entry entry, double score) {
    }
}
