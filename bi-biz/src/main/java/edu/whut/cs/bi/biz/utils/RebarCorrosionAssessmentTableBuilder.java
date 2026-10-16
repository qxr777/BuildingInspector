package edu.whut.cs.bi.biz.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 从钢筋锈蚀电位检测记录 JSON 汇总「钢筋锈蚀电位评定表」行。
 *
 * <p>桥梁名称用大桥下的子桥名；测区位置取该子桥锈蚀表「构件名称」；
 * 20 个测点电位和温度也取自该表。最小值取 20 个有效测值中的最小者。
 * 温度修正系数 k 固定为 0.9 mV/℃。T≥27℃ 时修正值 = 0.9×(T−27)，
 * T≤17℃ 时修正值 = 0.9×(T−17)，其间修正值为 0。修正后最小值 = 最小值 + 修正值。
 * 评定标度按表 5.4.3，取修正后最小值；没有温度时取最小值。</p>
 */
public final class RebarCorrosionAssessmentTableBuilder {

    public static final int VALUE_COUNT = 20;
    private static final BigDecimal CORRECTION_COEFFICIENT = new BigDecimal("0.9");
    private static final BigDecimal HOT_THRESHOLD = new BigDecimal("27");
    private static final BigDecimal COLD_THRESHOLD = new BigDecimal("17");
    public static final int VALUE_COLUMNS = 5;
    public static final int VALUE_ROWS = 4;

    private RebarCorrosionAssessmentTableBuilder() {
    }

    public static final class Row {
        public final String bridgeName;
        public final String location;
        public final List<String> values;
        public final String minText;
        public final String temperatureText;
        public final String correctionText;
        public final String correctedMinText;
        public final String scaleText;

        Row(String bridgeName, String location, List<String> values,
            String minText, String temperatureText, String correctionText, String correctedMinText,
            String scaleText) {
            this.bridgeName = bridgeName;
            this.location = location;
            this.values = values;
            this.minText = minText;
            this.temperatureText = temperatureText;
            this.correctionText = correctionText;
            this.correctedMinText = correctedMinText;
            this.scaleText = scaleText;
        }
    }

    public static List<Row> build(String subBridgeName, String corrosionJson) {
        List<Row> rows = new ArrayList<>();
        String bridge = subBridgeName == null ? "" : subBridgeName.trim();
        for (JSONObject record : allRecords(corrosionJson)) {
            String location = text(record, "componentName");
            if (isBlankName(location)) {
                continue;
            }
            List<String> values = new ArrayList<>(VALUE_COUNT);
            BigDecimal min = null;
            for (int i = 1; i <= VALUE_COUNT; i++) {
                BigDecimal number = firstNumber(record, "potentialValue" + i, "value" + i);
                values.add(number == null ? "" : formatNumber(number));
                if (number != null && (min == null || number.compareTo(min) < 0)) {
                    min = number;
                }
            }
            if (min == null) {
                continue;
            }
            String temperature = firstText(record, "temperature");
            if (isBlankName(temperature)) {
                temperature = "";
            }
            String[] correction = correctMinimum(min, temperature);
            BigDecimal rated = correction[1].isEmpty() ? min : new BigDecimal(correction[1]);
            rows.add(new Row(bridge, location, values, formatNumber(min), temperature,
                    correction[0], correction[1], String.valueOf(corrosionScale(rated))));
        }
        return rows;
    }

    private static List<JSONObject> allRecords(String json) {
        List<JSONObject> records = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return records;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (Exception ignored) {
            return records;
        }
        if (root == null) {
            return records;
        }
        JSONArray pages = root.getJSONArray("pages");
        if (pages == null) {
            collectRecords(root.getJSONArray("records"), records);
            return records;
        }
        for (int i = 0; i < pages.size(); i++) {
            JSONObject page = pages.getJSONObject(i);
            if (page != null) {
                collectRecords(page.getJSONArray("records"), records);
            }
        }
        return records;
    }

    private static void collectRecords(JSONArray array, List<JSONObject> records) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject record = array.getJSONObject(i);
            if (record != null) {
                records.add(record);
            }
        }
    }

    /**
     * 按 7.3.8 计算。没有温度时两列留空；17℃ 与 27℃ 之间修正值为 0。
     */
    static String[] correctMinimum(BigDecimal minimum, String temperatureText) {
        BigDecimal temperature = parseTemperature(temperatureText);
        if (minimum == null || temperature == null) {
            return new String[]{"", ""};
        }
        BigDecimal delta;
        if (temperature.compareTo(HOT_THRESHOLD) >= 0) {
            delta = CORRECTION_COEFFICIENT.multiply(temperature.subtract(HOT_THRESHOLD));
        } else if (temperature.compareTo(COLD_THRESHOLD) <= 0) {
            delta = CORRECTION_COEFFICIENT.multiply(temperature.subtract(COLD_THRESHOLD));
        } else {
            delta = BigDecimal.ZERO;
        }
        return new String[]{formatNumber(delta), formatNumber(minimum.add(delta))};
    }

    /**
     * 表 5.4.3：按测区最低电位取评定标度。≥−200 为 1，(−200, −300] 为 2，
     * (−300, −400] 为 3，(−400, −500] 为 4，&lt;−500 为 5。
     */
    static int corrosionScale(BigDecimal potential) {
        BigDecimal rated = potential.setScale(1, RoundingMode.HALF_UP);
        if (rated.compareTo(new BigDecimal("-200")) >= 0) {
            return 1;
        }
        if (rated.compareTo(new BigDecimal("-300")) >= 0) {
            return 2;
        }
        if (rated.compareTo(new BigDecimal("-400")) >= 0) {
            return 3;
        }
        if (rated.compareTo(new BigDecimal("-500")) >= 0) {
            return 4;
        }
        return 5;
    }

    private static BigDecimal parseTemperature(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim()
                .replace("℃", "")
                .replace("°C", "")
                .replace("°c", "")
                .replace("C", "")
                .replace("c", "");
        return parseNumber(normalized);
    }

    private static String formatNumber(BigDecimal value) {
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < 1) {
            rounded = rounded.setScale(1, RoundingMode.UNNECESSARY);
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
                .replace("mV", "")
                .replace("mv", "")
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

    private static boolean isBlankName(String value) {
        return value == null || value.isEmpty()
                || "-".equals(value) || "—".equals(value) || "－".equals(value);
    }
}
