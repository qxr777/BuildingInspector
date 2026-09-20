package edu.whut.cs.bi.biz.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从碳化深度、保护层检测记录 JSON 汇总「混凝土构件碳化状况评定表」行。
 *
 * <p>桥梁名称用大桥下的子桥名；构件名称取该子桥碳化深度检测记录表。
 * 平均碳化深度取该构件各测点「均值」再平均；保护层厚度取该测区 10 根钢筋「均值」再平均。
 * 评定标度暂不计算。</p>
 */
public final class CarbonizationAssessmentTableBuilder {

    private CarbonizationAssessmentTableBuilder() {
    }

    public static final class Row {
        public final String bridgeName;
        public final String componentName;
        public final String carbonAverageText;
        public final String coverAverageText;
        public final String kcText;
        public final String scaleText;

        Row(String bridgeName, String componentName, String carbonAverageText,
            String coverAverageText, String kcText) {
            this.bridgeName = bridgeName;
            this.componentName = componentName;
            this.carbonAverageText = carbonAverageText;
            this.coverAverageText = coverAverageText;
            this.kcText = kcText;
            this.scaleText = "";
        }
    }

    public static List<Row> build(String subBridgeName, String carbonJson, String coverJson) {
        List<CoverRecord> covers = parseCoverRecords(coverJson);
        LinkedHashMap<String, List<BigDecimal>> carbonByComponent = parseCarbonAverages(carbonJson);
        List<Row> rows = new ArrayList<>();
        String bridge = subBridgeName == null ? "" : subBridgeName.trim();
        for (Map.Entry<String, List<BigDecimal>> entry : carbonByComponent.entrySet()) {
            BigDecimal carbon = average(entry.getValue());
            if (carbon == null) {
                continue;
            }
            BigDecimal cover = matchCoverAverage(entry.getKey(), covers);
            String kc = "";
            if (cover != null && cover.compareTo(BigDecimal.ZERO) != 0) {
                kc = formatNumber(carbon.divide(cover, 6, RoundingMode.HALF_UP), 2, 3);
            }
            rows.add(new Row(
                    bridge,
                    entry.getKey(),
                    formatNumber(carbon, 1, 2),
                    cover == null ? "" : formatNumber(cover, 1, 2),
                    kc));
        }
        return rows;
    }

    private static LinkedHashMap<String, List<BigDecimal>> parseCarbonAverages(String json) {
        LinkedHashMap<String, List<BigDecimal>> result = new LinkedHashMap<>();
        for (JSONObject record : allRecords(json)) {
            String component = text(record, "componentName");
            if (component.isEmpty()) {
                continue;
            }
            BigDecimal average = firstNumber(record, "average");
            if (average == null) {
                average = average(List.of(
                        firstNumber(record, "value1"),
                        firstNumber(record, "value2"),
                        firstNumber(record, "value3")));
            }
            if (average == null) {
                continue;
            }
            result.computeIfAbsent(component, ignored -> new ArrayList<>()).add(average);
        }
        return result;
    }

    private static List<CoverRecord> parseCoverRecords(String json) {
        List<CoverRecord> records = new ArrayList<>();
        for (JSONObject record : allRecords(json)) {
            String component = text(record, "componentName");
            String position = firstText(record, "testPosition", "point", "serialNumber");
            List<BigDecimal> averages = new ArrayList<>();
            JSONArray items = record.getJSONArray("rebarCoverItems");
            if (items == null || items.isEmpty()) {
                items = record.getJSONArray("rebars");
            }
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    BigDecimal average = firstNumber(item, "coverThicknessAverage", "average");
                    if (average != null) {
                        averages.add(average);
                    }
                }
            }
            if (averages.isEmpty()) {
                BigDecimal legacy = firstNumber(record, "coverThicknessAverage", "average");
                if (legacy != null) {
                    averages.add(legacy);
                }
            }
            BigDecimal cover = average(averages);
            if (component.isEmpty() || cover == null) {
                continue;
            }
            records.add(new CoverRecord(component, position, cover));
        }
        return records;
    }

    private static BigDecimal matchCoverAverage(String carbonComponent, List<CoverRecord> covers) {
        String carbonKey = normalize(carbonComponent);
        CoverRecord best = null;
        int bestScore = 0;
        for (CoverRecord cover : covers) {
            int score = coverMatchScore(carbonKey, cover);
            if (score > bestScore) {
                bestScore = score;
                best = cover;
            }
        }
        return best == null ? null : best.average;
    }

    private static int coverMatchScore(String carbonKey, CoverRecord cover) {
        String name = normalize(cover.componentName);
        String position = normalize(cover.position);
        String combined = name + position;
        if (!combined.isEmpty() && carbonKey.equals(combined)) {
            return 300 + combined.length();
        }
        if (!name.isEmpty() && !position.isEmpty()
                && carbonKey.startsWith(name) && carbonKey.endsWith(position)) {
            return 200 + combined.length();
        }
        if (!name.isEmpty() && carbonKey.equals(name)) {
            return 100 + name.length();
        }
        return 0;
    }

    private static List<JSONObject> allRecords(String json) {
        List<JSONObject> records = new ArrayList<>();
        for (PageRecords page : pagesOf(json)) {
            records.addAll(page.records);
        }
        return records;
    }

    private static List<PageRecords> pagesOf(String json) {
        List<PageRecords> pages = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return pages;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (Exception ignored) {
            return pages;
        }
        if (root == null) {
            return pages;
        }
        JSONArray pageArray = root.getJSONArray("pages");
        if (pageArray == null) {
            pages.add(new PageRecords(collectRecords(root.getJSONArray("records"))));
            return pages;
        }
        for (int i = 0; i < pageArray.size(); i++) {
            JSONObject page = pageArray.getJSONObject(i);
            if (page == null) {
                continue;
            }
            pages.add(new PageRecords(collectRecords(page.getJSONArray("records"))));
        }
        return pages;
    }

    private static List<JSONObject> collectRecords(JSONArray array) {
        List<JSONObject> records = new ArrayList<>();
        if (array == null) {
            return records;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject record = array.getJSONObject(i);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> present = values.stream().filter(value -> value != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : present) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(present.size()), 6, RoundingMode.HALF_UP);
    }

    static String formatNumber(BigDecimal value, int minScale, int maxScale) {
        if (value == null) {
            return "";
        }
        BigDecimal rounded = value.setScale(maxScale, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < minScale) {
            rounded = rounded.setScale(minScale, RoundingMode.UNNECESSARY);
        }
        return rounded.toPlainString();
    }

    private static BigDecimal firstNumber(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            BigDecimal number = parseNumber(object.get(key));
            if (number != null) {
                return number;
            }
        }
        return null;
    }

    private static BigDecimal parseNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim()
                .replace("mm", "")
                .replace("MM", "")
                .replace(" ", "")
                .replace("　", "");
        if (text.isEmpty() || "/".equals(text) || "-".equals(text) || "—".equals(text) || "－".equals(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String firstText(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = text(object, key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JSONObject object, String key) {
        if (object == null || key == null || !object.containsKey(key) || object.get(key) == null) {
            return "";
        }
        return String.valueOf(object.get(key)).trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s　\\-_—－]", "");
    }

    private static final class PageRecords {
        final List<JSONObject> records;

        PageRecords(List<JSONObject> records) {
            this.records = records;
        }
    }

    private static final class CoverRecord {
        final String componentName;
        final String position;
        final BigDecimal average;

        CoverRecord(String componentName, String position, BigDecimal average) {
            this.componentName = componentName;
            this.position = position == null ? "" : position;
            this.average = average;
        }
    }
}
