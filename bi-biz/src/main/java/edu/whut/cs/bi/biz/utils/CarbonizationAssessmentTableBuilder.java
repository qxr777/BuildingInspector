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
 * 各测点的 3 次碳化读数求平均后按 0.5 mm 五成双修约，再对同构件测点求平均并按同规则修约。
 * 缺少完整读数的历史记录使用已保存的均值修约；保护层厚度取该测区 10 根钢筋「均值」再平均，
 * 四舍五入保留 1 位小数。比值 Kc＝平均碳化深度／保护层厚度平均值，四舍五入保留 2 位小数。
 * 评定标度按《公路桥梁技术状况评定标准》表 5.7.3：Kc＜0.5 为 1，[0.5,1.0) 为 2，
 * [1.0,1.5) 为 3，[1.5,2.0) 为 4，≥2.0 为 5。表下结论按本表各标度的构件占比生成。</p>
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
            String coverAverageText, String kcText, String scaleText) {
            this.bridgeName = bridgeName;
            this.componentName = componentName;
            this.carbonAverageText = carbonAverageText;
            this.coverAverageText = coverAverageText;
            this.kcText = kcText;
            this.scaleText = scaleText == null ? "" : scaleText;
        }
    }

    public static List<Row> build(String subBridgeName, String carbonJson, String coverJson) {
        List<CoverRecord> covers = parseCoverRecords(coverJson);
        LinkedHashMap<String, List<BigDecimal>> carbonByComponent = parseCarbonAverages(carbonJson);
        List<Row> rows = new ArrayList<>();
        String bridge = subBridgeName == null ? "" : subBridgeName.trim();
        for (Map.Entry<String, List<BigDecimal>> entry : carbonByComponent.entrySet()) {
            BigDecimal carbon = carbonAverageAtHalfStep(entry.getValue());
            if (carbon == null) {
                continue;
            }
            BigDecimal cover = matchCoverAverage(entry.getKey(), covers);
            String coverText = "";
            String kcText = "";
            String scaleText = "";
            if (cover != null) {
                BigDecimal coverRounded = cover.setScale(1, RoundingMode.HALF_UP);
                coverText = coverRounded.toPlainString();
                if (coverRounded.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal kc = carbon.divide(coverRounded, 6, RoundingMode.HALF_UP)
                            .setScale(2, RoundingMode.HALF_UP);
                    kcText = kc.toPlainString();
                    scaleText = String.valueOf(carbonizationScale(kc));
                }
            }
            rows.add(new Row(
                    bridge,
                    entry.getKey(),
                    carbon.setScale(1, RoundingMode.UNNECESSARY).toPlainString(),
                    coverText,
                    kcText,
                    scaleText));
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
            BigDecimal value1 = firstNumber(record, "value1");
            BigDecimal value2 = firstNumber(record, "value2");
            BigDecimal value3 = firstNumber(record, "value3");
            BigDecimal average;
            if (value1 != null && value2 != null && value3 != null) {
                // 原始读数优先，避免已保存均值的预先取整影响五成双边界。
                average = carbonAverageAtHalfStep(List.of(value1, value2, value3));
            } else {
                BigDecimal savedAverage = firstNumber(record, "average");
                average = savedAverage == null ? null : carbonAverageAtHalfStep(List.of(savedAverage));
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

    private static BigDecimal carbonAverageAtHalfStep(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal two = BigDecimal.valueOf(2);
        // (sum / count) × 2 直接修约到整数，避免先计算有限小数平均值引入二次修约误差。
        return sum.multiply(two).divide(BigDecimal.valueOf(values.size()), 0, RoundingMode.HALF_EVEN)
                .divide(two);
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

    /** 表 5.7.3：按已修约到两位小数的 Kc 取混凝土碳化评定标度。 */
    static int carbonizationScale(BigDecimal kc) {
        if (kc.compareTo(new BigDecimal("0.5")) < 0) {
            return 1;
        }
        if (kc.compareTo(BigDecimal.ONE) < 0) {
            return 2;
        }
        if (kc.compareTo(new BigDecimal("1.5")) < 0) {
            return 3;
        }
        if (kc.compareTo(new BigDecimal("2.0")) < 0) {
            return 4;
        }
        return 5;
    }

    /**
     * 本张碳化评定表的结论。标度全为 1 时用固定句；出现 2 及以上时按标度分组，
     * 占比为本表该标度行数除以已评出行数。
     */
    public static String conclusion(List<Row> rows) {
        List<Row> rated = new ArrayList<>();
        if (rows != null) {
            for (Row row : rows) {
                if (row != null && scaleOf(row) > 0) {
                    rated.add(row);
                }
            }
        }
        if (rated.isEmpty()) {
            return "";
        }
        boolean allOne = true;
        for (Row row : rated) {
            if (scaleOf(row) != 1) {
                allOne = false;
                break;
            }
        }
        if (allOne) {
            return "从测试结果看，所测试构件评定标度均为1，表明所测试构件的碳化深度对结构钢筋耐久性的影响不显著。";
        }
        boolean qualifyBridge = rated.stream().map(row -> row.bridgeName == null ? "" : row.bridgeName.trim())
                .filter(name -> !name.isEmpty()).distinct().count() > 1;
        Map<Integer, List<Row>> byScale = new LinkedHashMap<>();
        for (Row row : rated) {
            byScale.computeIfAbsent(scaleOf(row), ignored -> new ArrayList<>()).add(row);
        }
        StringBuilder text = new StringBuilder("从测试结果看，");
        boolean first = true;
        for (int scale = 2; scale <= 5; scale++) {
            List<Row> group = byScale.get(scale);
            if (group == null || group.isEmpty()) {
                continue;
            }
            if (!first) {
                text.append('；');
            }
            first = false;
            text.append("所抽检的").append(joinComponents(group, qualifyBridge))
                    .append("评定标度为").append(scale)
                    .append("，占比").append(percentText(group.size(), rated.size()))
                    .append("%，表明").append(scaleMeaning(scale));
        }
        List<Row> scaleOne = byScale.get(1);
        if (scaleOne != null && !scaleOne.isEmpty()) {
            text.append("；其余构件评定标度为1，占比")
                    .append(percentText(scaleOne.size(), rated.size()))
                    .append("%，表明此类测试构件碳化深度未超过混凝土保护层厚度的1/2，测试构件的碳化深度对结构钢筋耐久性的影响不显著。");
        } else {
            text.append('。');
        }
        return text.toString();
    }

    private static int scaleOf(Row row) {
        if (row == null || row.scaleText == null || row.scaleText.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(row.scaleText.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String joinComponents(List<Row> rows, boolean qualifyBridge) {
        List<String> names = new ArrayList<>();
        for (Row row : rows) {
            String component = row.componentName == null ? "" : row.componentName.trim();
            String bridge = row.bridgeName == null ? "" : row.bridgeName.trim();
            String name;
            if (component.isEmpty()) {
                name = bridge.isEmpty() ? "该构件" : bridge;
            } else if (qualifyBridge && !bridge.isEmpty() && !component.contains(bridge)) {
                name = bridge + component;
            } else {
                name = component;
            }
            names.add(name);
        }
        return String.join("、", names);
    }

    private static String scaleMeaning(int scale) {
        return switch (scale) {
            case 2 -> "此类测试构件碳化深度超过了混凝土保护层厚度的1/2";
            case 3 -> "此类测试构件碳化深度超过了混凝土保护层厚度";
            case 4 -> "此类测试构件碳化深度超过了混凝土保护层厚度的1.5倍";
            case 5 -> "此类测试构件碳化深度超过了混凝土保护层厚度的2倍";
            default -> "此类测试构件碳化深度未超过混凝土保护层厚度的1/2";
        };
    }

    private static String percentText(int count, int total) {
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
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
