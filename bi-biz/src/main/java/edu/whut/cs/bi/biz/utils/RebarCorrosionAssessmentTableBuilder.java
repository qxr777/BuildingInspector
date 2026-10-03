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
 * 修正值、修正后最小值、评定标度暂不计算。</p>
 */
public final class RebarCorrosionAssessmentTableBuilder {

    public static final int VALUE_COUNT = 20;
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
            String minText, String temperatureText) {
            this.bridgeName = bridgeName;
            this.location = location;
            this.values = values;
            this.minText = minText;
            this.temperatureText = temperatureText;
            this.correctionText = "";
            this.correctedMinText = "";
            this.scaleText = "";
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
            rows.add(new Row(bridge, location, values, formatNumber(min), temperature));
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
