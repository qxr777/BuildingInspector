package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用结构化病害明细生成报告小结。
 *
 * <p>上部承重构件按结构形式归并成对位置后汇总，其他部位跨位置合并。当前报告输出数量、长度、
 * 宽度、面积、角度和程度（比例换算为百分比，如脱空）；没有这些定量数据的病害只输出数量。</p>
 */
@Service
public class ProgrammaticDiseaseSummaryService {

    private static final String UPPER_BEARING_COMPONENT = "上部承重构件";
    private static final String UPPER_BEARING_STRUCTURE = "上部承重结构";
    private static final Map<String, Map<String, String>> POSITION_MERGE_RULES = createPositionMergeRules();

    /** 来源：结构形式-病害位置分布表(1).xlsx，Sheet1 的合并分组。 */
    private static Map<String, Map<String, String>> createPositionMergeRules() {
        Map<String, Map<String, String>> rules = new LinkedHashMap<>();
        for (String component : List.of("空心板", "实心板", "整体现浇板", "T梁", "I梁", "Π梁", "小箱梁")) {
            Map<String, String> positions = new LinkedHashMap<>();
            mergePositionPair(positions, "腹板", "左腹板", "右腹板");
            mergePositionPair(positions, "腹板", "内腹板", "外腹板");
            boolean flange = List.of("空心板", "T梁", "I梁", "Π梁").contains(component);
            String leftRightWing = flange ? "翼缘板" : "翼板";
            // 表中 T 梁左右位置为翼缘板，内外位置为翼板，保留原有名称区别。
            String innerOuterWing = flange && !"T梁".equals(component) ? "翼缘板" : "翼板";
            mergePositionPair(positions, leftRightWing, "左" + leftRightWing, "右" + leftRightWing);
            mergePositionPair(positions, innerOuterWing, "内" + innerOuterWing, "外" + innerOuterWing);
            if (List.of("T梁", "I梁", "Π梁").contains(component)) {
                mergePositionPair(positions, "马蹄侧面", "马蹄左侧", "马蹄右侧");
                mergePositionPair(positions, "马蹄侧面", "马蹄内侧", "马蹄外侧");
                mergePositionPair(positions, "马蹄斜面", "马蹄左斜面", "马蹄右斜面");
                mergePositionPair(positions, "马蹄斜面", "马蹄内斜面", "马蹄外斜面");
            }
            rules.put(component, Map.copyOf(positions));
        }
        Map<String, String> boxPositions = new LinkedHashMap<>();
        for (String scope : List.of("箱内", "箱外")) {
            mergePositionPair(boxPositions, scope + "腹板", scope + "左腹板", scope + "右腹板");
            mergePositionPair(boxPositions, scope + "腹板", scope + "内腹板", scope + "外腹板");
        }
        mergePositionPair(boxPositions, "箱外翼板", "箱外左翼板", "箱外右翼板");
        mergePositionPair(boxPositions, "箱外翼板", "箱外内翼板", "箱外外翼板");
        for (String component : List.of("箱梁", "节段", "节段（变截面箱梁）")) {
            rules.put(component, Map.copyOf(boxPositions));
        }
        return Map.copyOf(rules);
    }

    private static void mergePositionPair(Map<String, String> positions, String target,
                                          String first, String second) {
        positions.put(first, target);
        positions.put(second, target);
    }
    private static final Pattern SPECIAL_LENGTH_IN_MM = Pattern.compile(
            "(?i)(?:长度|(?<![a-z])L(?:_?\\d+)?)\\s*[=＝:：]?[^，,；;]{0,40}?mm");
    private static final Pattern SPECIAL_AREA_IN_SQUARE_CENTIMETRES = Pattern.compile("(?i)cm(?:²|2|\\^2)");
    private static final Pattern OTHER_CATEGORY = Pattern.compile("^其他(?:[（(].*[）)])?$");
    private static final Pattern TRAILING_DESCRIPTION_PUNCTUATION = Pattern.compile("[\\s，,；;。．.]+$");
    // 只去掉开头的构件编号，多个编号或复杂句子不参与简化。
    private static final Pattern COMPONENT_DESCRIPTION_PREFIX = Pattern.compile(
            "^[A-Za-z0-9_\\-—－、\\s]+[#＃]");
    private static final Pattern SIMPLE_DESCRIPTION_COUNT = Pattern.compile(
            "^([^#＃，,；;。\\d=＝:：%％]+?)\\s*([1-9]\\d*)\\s*([条处个颗联])$");
    private static final Pattern PERCENT_IN_DESCRIPTION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");

    public String summarize(List<Disease> diseases, String sectionName) {
        if (diseases == null || diseases.isEmpty()) {
            return "未见明显病害。";
        }

        boolean includePosition = UPPER_BEARING_COMPONENT.equals(trim(sectionName))
                || UPPER_BEARING_STRUCTURE.equals(trim(sectionName));
        LinkedHashMap<ScopeKey, LinkedHashMap<CategoryKey, MeasurementAccumulator>> scopes =
                new LinkedHashMap<>();

        for (Disease disease : diseases) {
            if (disease == null) {
                continue;
            }
            String component = valueOrDefault(trim(disease.getBiObjectName()), "其他");
            String position = includePosition ? normalizePosition(disease.getPosition(), component) : null;
            ScopeKey scopeKey = new ScopeKey(component, position);
            String category = normalizeCategory(disease);
            String quantityUnit = normalizeQuantityUnit(disease.getUnits(), category);
            CategoryKey categoryKey = new CategoryKey(category, quantityUnit);

            scopes.computeIfAbsent(scopeKey, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(categoryKey, ignored -> new MeasurementAccumulator())
                    .accept(disease);
        }

        return includePosition ? renderUpperBearing(scopes) : renderGeneral(scopes);
    }

    private String renderUpperBearing(
            LinkedHashMap<ScopeKey, LinkedHashMap<CategoryKey, MeasurementAccumulator>> scopes) {
        LinkedHashMap<String, List<PositionSummary>> byComponent = new LinkedHashMap<>();
        scopes.forEach((scope, categories) -> byComponent
                .computeIfAbsent(scope.component(), ignored -> new ArrayList<>())
                .add(new PositionSummary(scope.position(), categories)));

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<PositionSummary>> componentEntry : byComponent.entrySet()) {
            List<String> positionTexts = new ArrayList<>();
            for (PositionSummary positionSummary : componentEntry.getValue()) {
                String categories = formatCategories(positionSummary.categories());
                String position = displayPosition(positionSummary.position(), componentEntry.getKey());
                positionTexts.add(position + categories);
            }
            lines.add(componentEntry.getKey() + "：" + String.join("；", positionTexts) + "。");
        }
        return joinSummaryLines(lines);
    }

    private String renderGeneral(
            LinkedHashMap<ScopeKey, LinkedHashMap<CategoryKey, MeasurementAccumulator>> scopes) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<ScopeKey, LinkedHashMap<CategoryKey, MeasurementAccumulator>> entry : scopes.entrySet()) {
            lines.add(entry.getKey().component() + "：" + formatCategories(entry.getValue()) + "。");
        }
        return joinSummaryLines(lines);
    }

    /** 只有一条小结时不写 1），多条才编号。 */
    private String joinSummaryLines(List<String> lines) {
        if (lines.size() == 1) {
            return lines.get(0);
        }
        List<String> numbered = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            numbered.add((i + 1) + "）" + lines.get(i));
        }
        return String.join("\n", numbered);
    }

    private String formatCategories(LinkedHashMap<CategoryKey, MeasurementAccumulator> categories) {
        List<Map.Entry<CategoryKey, MeasurementAccumulator>> ordered = new ArrayList<>(categories.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> isCrackCategory(entry.getKey().name()) ? 0 : 1));
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<CategoryKey, MeasurementAccumulator> entry : ordered) {
            clauses.add(entry.getValue().format(entry.getKey()));
        }
        return String.join("；", clauses);
    }

    private static boolean isCrackCategory(String category) {
        return hasText(category) && (category.contains("裂缝") || category.contains("裂纹"));
    }

    private String displayPosition(String position, String component) {
        if (!hasText(position) || Objects.equals(position, component)) {
            return "";
        }
        return position;
    }

    private String normalizeCategory(Disease disease) {
        String type = disease.getDiseaseType() == null ? null : trim(disease.getDiseaseType().getName());
        if (!hasText(type)) {
            type = trim(disease.getType());
            int separator = type.lastIndexOf('#');
            if (separator >= 0) {
                type = trim(type.substring(separator + 1));
            }
        }
        type = valueOrDefault(type, "其他病害");

        String crackType = trim(disease.getCrackType());
        if (type.contains("裂缝") && hasText(crackType)) {
            return crackType + "裂缝";
        }
        return type;
    }

    private String normalizeQuantityUnit(String unit, String category) {
        if (category.contains("网状")) {
            return "处";
        }
        String normalized = trim(unit);
        if (hasText(normalized) && normalized.matches("[条处个颗联]")) {
            return normalized;
        }
        return category.contains("裂缝") ? "条" : "处";
    }

    private String normalizePosition(String position, String component) {
        String normalized = trim(position);
        int separator = normalized.lastIndexOf('#');
        normalized = separator >= 0 ? trim(normalized.substring(separator + 1)) : normalized;
        // 仅匹配表内结构和位置，不全局删除左右内外，避免混淆箱内/箱外等位置。
        return POSITION_MERGE_RULES.getOrDefault(component, Map.of()).getOrDefault(normalized, normalized);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record ScopeKey(String component, String position) {
    }

    private record CategoryKey(String name, String unit) {
    }

    private record PositionSummary(
            String position,
            LinkedHashMap<CategoryKey, MeasurementAccumulator> categories) {
    }

    private static final class MeasurementAccumulator {
        private long quantity;
        private BigDecimal lengthMinimum = BigDecimal.ZERO;
        private BigDecimal lengthMaximum = BigDecimal.ZERO;
        private boolean hasLength;
        private BigDecimal widthMinimum;
        private BigDecimal widthMaximum;
        private BigDecimal totalArea = BigDecimal.ZERO;
        private boolean hasArea;
        private BigDecimal angleMinimum;
        private BigDecimal angleMaximum;
        private BigDecimal ratioMinimum;
        private BigDecimal ratioMaximum;
        private final List<DescriptionEntry> descriptions = new ArrayList<>();

        void accept(Disease disease) {
            String description = normalizeDescription(disease.getDescription());
            List<DiseaseDetail> details = disease.getDiseaseDetails() == null
                    ? List.of() : disease.getDiseaseDetails();
            long effectiveQuantity = disease.getQuantity() > 0
                    ? disease.getQuantity() : Math.max(1, details.size());
            quantity += effectiveQuantity;
            if (hasText(description)) {
                descriptions.add(new DescriptionEntry(description, effectiveQuantity));
            }

            boolean lengthInMillimetres = isLengthInMillimetres(disease.getDescription());
            boolean areaInSquareCentimetres = isAreaInSquareCentimetres(disease.getDescription());
            boolean lengthRangeAdded = false;
            boolean averageAreaAdded = false;
            boolean totalAreaAdded = false;

            for (DiseaseDetail detail : details) {
                if (detail == null) {
                    continue;
                }

                boolean completeLengthRange = detail.getLengthRangeStart() != null
                        && detail.getLengthRangeEnd() != null;
                if (completeLengthRange && !lengthRangeAdded) {
                    BigDecimal start = normalizeLength(detail.getLengthRangeStart(), lengthInMillimetres);
                    BigDecimal end = normalizeLength(detail.getLengthRangeEnd(), lengthInMillimetres);
                    if (start.compareTo(end) <= 0) {
                        BigDecimal count = BigDecimal.valueOf(effectiveQuantity);
                        addLength(start.multiply(count), end.multiply(count));
                        lengthRangeAdded = true;
                    }
                } else if (!completeLengthRange) {
                    BigDecimal exactLength = sumLengthParts(detail, lengthInMillimetres);
                    if (exactLength.signum() > 0) {
                        addLength(exactLength, exactLength);
                    }
                }

                BigDecimal width = detail.getCrackWidth() != null
                        ? detail.getCrackWidth() : detail.getWidth();
                addWidth(width);
                addWidth(detail.getCrackWidthRangeStart());
                addWidth(detail.getCrackWidthRangeEnd());

                Integer areaIdentifier = detail.getAreaIdentifier();
                boolean averageArea = Objects.equals(areaIdentifier, 1);
                boolean countedArea = Objects.equals(areaIdentifier, 2);
                if ((!averageArea || !averageAreaAdded) && (!countedArea || !totalAreaAdded)) {
                    BigDecimal area = calculateArea(detail, areaInSquareCentimetres);
                    if (area != null && area.signum() >= 0) {
                        if (averageArea) {
                            area = area.multiply(BigDecimal.valueOf(effectiveQuantity));
                            averageAreaAdded = true;
                        } else if (countedArea) {
                            totalAreaAdded = true;
                        }
                        totalArea = totalArea.add(area);
                        hasArea = true;
                    }
                }

                addAngle(detail.getAngle() == null ? null : BigDecimal.valueOf(detail.getAngle()));
                addAngle(detail.getAngleRangeStart());
                addAngle(detail.getAngleRangeEnd());
                addRatio(calculateRatioPercent(detail));
            }
            addPercentsFromDescription(description);
        }

        private void addLength(BigDecimal minimum, BigDecimal maximum) {
            lengthMinimum = lengthMinimum.add(minimum);
            lengthMaximum = lengthMaximum.add(maximum);
            hasLength = true;
        }

        private void addWidth(BigDecimal width) {
            if (width == null || width.signum() < 0) {
                return;
            }
            widthMinimum = widthMinimum == null ? width : widthMinimum.min(width);
            widthMaximum = widthMaximum == null ? width : widthMaximum.max(width);
        }

        private void addAngle(BigDecimal angle) {
            if (angle == null) {
                return;
            }
            angleMinimum = angleMinimum == null ? angle : angleMinimum.min(angle);
            angleMaximum = angleMaximum == null ? angle : angleMaximum.max(angle);
        }

        private void addRatio(BigDecimal ratio) {
            if (ratio == null || ratio.signum() < 0) {
                return;
            }
            ratioMinimum = ratioMinimum == null ? ratio : ratioMinimum.min(ratio);
            ratioMaximum = ratioMaximum == null ? ratio : ratioMaximum.max(ratio);
        }

        private void addPercentsFromDescription(String description) {
            if (!hasText(description)) {
                return;
            }
            Matcher matcher = PERCENT_IN_DESCRIPTION.matcher(description);
            while (matcher.find()) {
                addRatio(new BigDecimal(matcher.group(1)));
            }
        }

        String format(CategoryKey category) {
            StringBuilder text = new StringBuilder(category.name())
                    .append(quantity)
                    .append(category.unit());

            if (hasLength) {
                text.append('，').append(quantity > 1 ? "总长度" : "长度");
                appendNumberOrRange(text, lengthMinimum, lengthMaximum, "m", false);
            }
            if (hasArea) {
                text.append('，').append(quantity > 1 ? "总面积" : "面积")
                        .append(formatNumber(totalArea)).append("m²");
            }
            if (widthMinimum != null) {
                text.append("，宽度");
                appendWidth(text, widthMinimum, widthMaximum);
            }
            if (angleMinimum != null) {
                text.append("，角度");
                appendNumberOrRange(text, angleMinimum, angleMaximum, "°", true);
            }
            if (ratioMinimum != null) {
                text.append("，程度");
                appendNumberOrRange(text, ratioMinimum, ratioMaximum, "%", true);
            }
            if (OTHER_CATEGORY.matcher(category.name()).matches() && !descriptions.isEmpty()) {
                text.append("，其中").append(summarizeOtherDescriptions(category.unit()));
            }
            return text.toString();
        }

        private record DescriptionEntry(String text, long quantity) {
        }

        private record DescriptionPart(CategoryKey key, String original) {
        }

        private String summarizeOtherDescriptions(String quantityUnit) {
            Map<CategoryKey, BigDecimal> counts = new LinkedHashMap<>();
            List<DescriptionPart> ordered = new ArrayList<>();
            for (DescriptionEntry entry : descriptions) {
                String body = COMPONENT_DESCRIPTION_PREFIX.matcher(entry.text()).replaceFirst("").trim();
                Matcher matcher = SIMPLE_DESCRIPTION_COUNT.matcher(body);
                // 数量、单位必须和主表一致；无法可靠解析时保留原文。
                if (!matcher.matches()
                        || !matcher.group(3).equals(quantityUnit)
                        || new BigDecimal(matcher.group(2)).compareTo(BigDecimal.valueOf(entry.quantity())) != 0) {
                    ordered.add(new DescriptionPart(null, entry.text()));
                    continue;
                }
                CategoryKey key = new CategoryKey(matcher.group(1).trim(), matcher.group(3));
                if (!counts.containsKey(key)) {
                    ordered.add(new DescriptionPart(key, null));
                }
                counts.merge(key, BigDecimal.valueOf(entry.quantity()), BigDecimal::add);
            }
            List<String> result = new ArrayList<>();
            for (DescriptionPart part : ordered) {
                CategoryKey key = part.key();
                result.add(key == null ? part.original()
                        : key.name() + counts.get(key).toPlainString() + key.unit());
            }
            return String.join("、", result);
        }

        private static String normalizeDescription(String description) {
            String normalized = trim(description);
            return TRAILING_DESCRIPTION_PUNCTUATION.matcher(normalized).replaceAll("");
        }

        private void appendWidth(StringBuilder text, BigDecimal minimum, BigDecimal maximum) {
            if (minimum.compareTo(maximum) == 0) {
                text.append("为").append(formatWidth(minimum)).append("mm");
            } else {
                text.append("介于").append(formatWidth(minimum))
                        .append('～').append(formatWidth(maximum)).append("mm");
            }
        }

        private void appendNumberOrRange(
                StringBuilder text, BigDecimal minimum, BigDecimal maximum, String unit, boolean useValueMarker) {
            if (minimum.compareTo(maximum) == 0) {
                if (useValueMarker) {
                    text.append("为");
                }
                text.append(formatNumber(minimum)).append(unit);
            } else {
                text.append("介于").append(formatNumber(minimum))
                        .append('～').append(formatNumber(maximum)).append(unit);
            }
        }

        private static BigDecimal sumLengthParts(DiseaseDetail detail, boolean millimetres) {
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal value : List.of(
                    valueOrZero(detail.getLength1()),
                    valueOrZero(detail.getLength2()),
                    valueOrZero(detail.getLength3()))) {
                if (value.signum() > 0) {
                    total = total.add(normalizeLength(value, millimetres));
                }
            }
            return total;
        }

        private static BigDecimal calculateRatioPercent(DiseaseDetail detail) {
            Integer numerator = detail.getNumeratorRatio();
            Integer denominator = detail.getDenominatorRatio();
            if (numerator == null) {
                return null;
            }
            if (denominator == null || denominator == 0) {
                return BigDecimal.valueOf(numerator);
            }
            return BigDecimal.valueOf(numerator)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        }

        private static BigDecimal calculateArea(DiseaseDetail detail, boolean squareCentimetres) {
            if (detail.getAreaLength() == null || detail.getAreaWidth() == null) {
                return null;
            }
            BigDecimal area = detail.getAreaLength().multiply(detail.getAreaWidth());
            return squareCentimetres ? area.movePointLeft(4) : area;
        }

        private static BigDecimal normalizeLength(BigDecimal value, boolean millimetres) {
            return millimetres ? value.movePointLeft(3) : value;
        }

        private static BigDecimal valueOrZero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        private static boolean isLengthInMillimetres(String description) {
            return hasText(description) && SPECIAL_LENGTH_IN_MM.matcher(description).find();
        }

        private static boolean isAreaInSquareCentimetres(String description) {
            return hasText(description)
                    && SPECIAL_AREA_IN_SQUARE_CENTIMETRES.matcher(description.toLowerCase(Locale.ROOT)).find();
        }

        /** 宽度固定保留两位小数，例如 0.10mm。 */
        private static String formatWidth(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        private static String formatNumber(BigDecimal value) {
            BigDecimal normalized = value;
            if (normalized.scale() > 4) {
                normalized = normalized.setScale(4, RoundingMode.HALF_UP);
            }
            normalized = normalized.stripTrailingZeros();
            return normalized.signum() == 0 ? "0" : normalized.toPlainString();
        }
    }
}
