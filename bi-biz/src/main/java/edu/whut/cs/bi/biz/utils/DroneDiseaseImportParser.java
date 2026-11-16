package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.enums.AreaIdentifierEnums;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
/**
 * 无人机病害导入。Excel 列出全部定量列；入库时只保留病害类型 selectColumn 命中的列。
 * 一条病害对应一组定量数据。不参与评定时标度记 0。
 */
public final class DroneDiseaseImportParser {

    /** 第1行是填写提示，第2行是表头，第3行起是数据。 */
    public static final int HEADER_ROW_INDEX = 1;
    public static final int FIRST_DATA_ROW_INDEX = 2;

    public static final int COL_COMPONENT3 = 1;
    public static final int COL_COMPONENT4 = 2;
    public static final int COL_POSITION = 3;
    public static final int COL_TYPE = 4;
    public static final int COL_PARTICIPATE = 5;
    public static final int COL_QUANTITY = 6;
    public static final int COL_UNITS = 7;
    public static final int COL_CRACK = 8;
    public static final int COL_LENGTH = 9;
    public static final int COL_CRACK_WIDTH = 10;
    public static final int COL_HEIGHT = 11;
    public static final int COL_AREA_IDENTIFIER = 12;
    public static final int COL_AREA_LENGTH = 13;
    public static final int COL_AREA_WIDTH = 14;
    public static final int COL_DEFORMATION = 15;
    public static final int COL_ANGLE = 16;
    public static final int COL_RATIO = 17;
    public static final int COL_REFERENCE1_NAME = 18;
    public static final int COL_REFERENCE1_START = 19;
    public static final int COL_REFERENCE1_END = 20;
    public static final int COL_REFERENCE2_NAME = 21;
    public static final int COL_REFERENCE2_START = 22;
    public static final int COL_REFERENCE2_END = 23;
    public static final int COL_LEVEL = 24;
    public static final int COL_TREND = 25;
    public static final int COL_NATURE = 26;
    public static final int COL_DESCRIPTION = 27;
    public static final int COL_PHOTO = 28;
    public static final int COL_REMARK = 29;

    /** 第2行表头，用于校验是否上传了无人机病害模板。 */
    public static final String[] EXPECTED_HEADERS = {
            "序号", "构件", "构件2", "缺损位置", "缺损类型", "是否参与评定", "数量", "单位",
            "裂缝特征", "长度(m)", "缝宽(mm)", "高度/深度(m)", "面积标识", "面积长(m)", "面积宽(m)",
            "变形/位移(m)", "角度(度)", "比例(分子/分母)",
            "参考面1名称", "参考面1起点(m)", "参考面1终点(m)",
            "参考面2名称", "参考面2起点(m)", "参考面2终点(m)",
            "评定标度", "发展趋势", "病害性质", "描述", "照片编号", "备注"
    };

    private static final int SCALE_BIT_MASK = 0x3FF;
    private static final Set<String> CRACK_TYPES = Set.of("纵向", "横向", "斜向", "L型", "U型", "网状");
    private static final Set<String> TRENDS = Set.of("新增", "稳定", "发展", "已维修", "部分维修", "未找到", "修补不全");
    private static final Set<String> NATURES = Set.of("结构病害", "非结构病害");

    private DroneDiseaseImportParser() {
    }

    /**
     * 校验表头是否为无人机病害模板。返回错误文案列表，空表示通过。
     */
    public static List<String> validateHeaders(List<String> actualHeaders) {
        List<String> errors = new ArrayList<>();
        if (actualHeaders == null || actualHeaders.isEmpty()) {
            errors.add("未找到表头，请下载并使用「无人机病害导入模板」");
            return errors;
        }
        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            String expected = EXPECTED_HEADERS[i];
            String actual = i < actualHeaders.size() ? normalizeHeader(actualHeaders.get(i)) : "";
            if (!expected.equals(actual)) {
                errors.add("表头第" + (i + 1) + "列应为「" + expected + "」，实际是「"
                        + (actual.isEmpty() ? "空" : actual) + "」。请重新下载无人机病害导入模板");
                break;
            }
        }
        return errors;
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('\u00A0', ' ');
    }

    public static Output parse(Input input) {
        List<String> errors = new ArrayList<>();
        DiseaseType diseaseType = input.diseaseType;
        int mask = diseaseType.getSelectColumn() == null ? 0 : (diseaseType.getSelectColumn() & SCALE_BIT_MASK);
        int threshold = diseaseType.getThreshold() == null || diseaseType.getThreshold() <= 0 ? 10 : diseaseType.getThreshold();

        String participate = normalize(input.participate);
        String participateAssess = null;
        if ("是".equals(participate)) {
            participateAssess = "1";
        } else if ("否".equals(participate)) {
            participateAssess = "0";
        } else {
            errors.add(prefix(input) + "是否参与评定只能填“是”或“否”");
        }

        Integer quantity = parsePositiveInt(input.quantity, prefix(input) + "数量", errors);
        boolean rangeMode = quantity != null && quantity >= threshold;

        Integer level = null;
        if ("0".equals(participateAssess)) {
            if (!isBlank(input.level)) {
                errors.add(prefix(input) + "不参与评定时评定标度留空");
            }
            level = 0;
        } else if ("1".equals(participateAssess)) {
            level = parseScale(input, diseaseType, errors);
        }

        String units = normalize(input.units);
        if (isBlank(units)) {
            units = bit(mask, 0) ? "条" : "处";
        } else if (!"条".equals(units) && !"处".equals(units)) {
            errors.add(prefix(input) + "单位只能填“条”或“处”");
        }

        String trend = normalize(input.developmentTrend);
        if (isBlank(trend)) {
            errors.add(prefix(input) + "发展趋势不能为空");
        } else if (!TRENDS.contains(trend)) {
            errors.add(prefix(input) + "发展趋势不在可选范围内");
        }

        String nature = normalize(input.nature);
        if (isBlank(nature)) {
            errors.add(prefix(input) + "病害性质不能为空");
        } else if (!NATURES.contains(nature)) {
            errors.add(prefix(input) + "病害性质只能是结构病害或非结构病害");
        }

        String description = normalize(input.description);
        if (isBlank(description)) {
            errors.add(prefix(input) + "描述不能为空");
        }

        DiseaseDetail detail = new DiseaseDetail();
        String crackType = null;
        if (bit(mask, 0)) {
            crackType = readCrack(input, errors);
        }
        if (bit(mask, 1)) {
            readLength(input, detail, rangeMode, crackType, errors);
        }
        if (bit(mask, 2)) {
            readDecimalField(input.crackWidth, "缝宽", rangeMode, prefix(input), errors,
                    detail::setCrackWidth, detail::setCrackWidthRangeStart, detail::setCrackWidthRangeEnd);
        }
        if (bit(mask, 3)) {
            readDecimalField(input.heightDepth, "高度/深度", rangeMode, prefix(input), errors,
                    detail::setHeightDepth, detail::setHeightDepthRangeStart, detail::setHeightDepthRangeEnd);
        }
        if (bit(mask, 4)) {
            readArea(input, detail, errors);
        }
        if (bit(mask, 5)) {
            readDecimalField(input.deformation, "变形/位移", rangeMode, prefix(input), errors,
                    detail::setDeformation, detail::setDeformationRangeStart, detail::setDeformationRangeEnd);
        }
        if (bit(mask, 6)) {
            readAngle(input, detail, rangeMode, errors);
        }
        if (bit(mask, 7)) {
            readRatio(input, detail, errors);
        }
        if (bit(mask, 8)) {
            readReference(input.reference1Name, input.reference1Start, input.reference1End, "参考面1",
                    prefix(input), errors,
                    detail::setReference1Location, detail::setReference1LocationStart, detail::setReference1LocationEnd);
        }
        if (bit(mask, 9)) {
            readReference(input.reference2Name, input.reference2Start, input.reference2End, "参考面2",
                    prefix(input), errors,
                    detail::setReference2Location, detail::setReference2LocationStart, detail::setReference2LocationEnd);
        }

        Output output = new Output();
        output.errors = errors;
        output.detail = detail;
        output.crackType = crackType;
        output.participateAssess = participateAssess;
        output.level = level == null ? 0 : level;
        output.quantity = quantity == null ? 0 : quantity;
        output.units = units;
        output.developmentTrend = trend;
        output.nature = nature;
        output.description = description;
        output.remark = normalize(input.remark);
        output.photo = normalize(input.photo);
        return output;
    }

    private static String readCrack(Input input, List<String> errors) {
        String value = normalize(input.crackFeature);
        if (isBlank(value)) {
            return null;
        }
        if (!CRACK_TYPES.contains(value)) {
            errors.add(prefix(input) + "裂缝特征只能是纵向、横向、斜向、L型、U型、网状");
            return null;
        }
        return value;
    }

    private static void readLength(Input input, DiseaseDetail detail, boolean rangeMode, String crackType, List<String> errors) {
        String value = normalize(input.length);
        if (isBlank(value)) {
            return;
        }
        if (rangeMode && value.contains("~")) {
            BigDecimal[] range = splitRange(value, prefix(input) + "长度", errors);
            if (range != null) {
                detail.setLengthRangeStart(range[0]);
                detail.setLengthRangeEnd(range[1]);
            }
            return;
        }
        String[] parts = value.split("\\+");
        int expected = expectedLengthParts(crackType);
        if (parts.length != expected) {
            errors.add(prefix(input) + lengthPartMessage(crackType));
            return;
        }
        BigDecimal[] lengths = new BigDecimal[parts.length];
        for (int i = 0; i < parts.length; i++) {
            lengths[i] = decimal(parts[i], prefix(input) + "长度", errors);
        }
        if (lengths[0] != null) {
            detail.setLength1(lengths[0]);
        }
        if (lengths.length > 1 && lengths[1] != null) {
            detail.setLength2(lengths[1]);
        }
        if (lengths.length > 2 && lengths[2] != null) {
            detail.setLength3(lengths[2]);
        }
    }

    private static int expectedLengthParts(String crackType) {
        if ("L型".equals(crackType)) {
            return 2;
        }
        if ("U型".equals(crackType)) {
            return 3;
        }
        return 1;
    }

    private static String lengthPartMessage(String crackType) {
        if ("L型".equals(crackType)) {
            return "L型长度要写两段，用+连接，例如 1.2+0.8";
        }
        if ("U型".equals(crackType)) {
            return "U型长度要写三段，用+连接，例如 1.2+0.8+0.4";
        }
        return "长度填一个数，例如 0.50";
    }

    private static void readArea(Input input, DiseaseDetail detail, List<String> errors) {
        String lengthRaw = normalize(input.areaLength);
        String widthRaw = normalize(input.areaWidth);
        if (isBlank(lengthRaw) && isBlank(widthRaw) && isBlank(input.areaIdentifier)) {
            return;
        }
        if (isBlank(lengthRaw) || isBlank(widthRaw)) {
            errors.add(prefix(input) + "面积要同时填写长和宽");
            return;
        }
        BigDecimal length = decimal(lengthRaw, prefix(input) + "面积长", errors);
        BigDecimal width = decimal(widthRaw, prefix(input) + "面积宽", errors);
        Integer identifier = parseAreaIdentifier(input.areaIdentifier, errors, input.rowNumber);
        if (length != null && width != null && identifier != null) {
            detail.setAreaLength(length);
            detail.setAreaWidth(width);
            detail.setAreaIdentifier(identifier);
        }
    }

    private static Integer parseAreaIdentifier(String raw, List<String> errors, int rowNumber) {
        String value = normalize(raw);
        if (isBlank(value) || "普通".equals(value) || "0".equals(value)) {
            return AreaIdentifierEnums.COMMON.getCode();
        }
        if ("平均".equals(value) || "均".equals(value) || "1".equals(value)) {
            return AreaIdentifierEnums.AVERAGE.getCode();
        }
        if ("总计".equals(value) || "总".equals(value) || "2".equals(value)) {
            return AreaIdentifierEnums.COUNT.getCode();
        }
        errors.add("第" + rowNumber + "行：面积标识只能是普通、平均、总计");
        return null;
    }

    private static void readAngle(Input input, DiseaseDetail detail, boolean rangeMode, List<String> errors) {
        String value = normalize(input.angle);
        if (isBlank(value)) {
            return;
        }
        if (rangeMode && value.contains("~")) {
            BigDecimal[] range = splitRange(value, prefix(input) + "角度", errors);
            if (range != null) {
                detail.setAngleRangeStart(range[0]);
                detail.setAngleRangeEnd(range[1]);
            }
            return;
        }
        Integer angle = wholeNumber(value, prefix(input) + "角度", errors);
        if (angle != null) {
            detail.setAngle(angle);
        }
    }

    private static void readRatio(Input input, DiseaseDetail detail, List<String> errors) {
        String value = normalize(input.ratio).replace('／', '/');
        if (isBlank(value)) {
            return;
        }
        String[] parts = value.split("/");
        if (parts.length != 2) {
            errors.add(prefix(input) + "比例写分子/分母，例如 20/100");
            return;
        }
        Integer numerator = wholeNumber(parts[0], prefix(input) + "比例分子", errors);
        Integer denominator = wholeNumber(parts[1], prefix(input) + "比例分母", errors);
        if (numerator != null && denominator != null) {
            detail.setNumeratorRatio(numerator);
            detail.setDenominatorRatio(denominator);
        }
    }

    /**
     * 与网页一致：参考面名称、起点、终点分三列。名称和起点通常一起填，终点可空。
     */
    private static void readReference(String nameRaw, String startRaw, String endRaw, String label, String rowPrefix,
                                      List<String> errors,
                                      java.util.function.Consumer<String> location,
                                      java.util.function.Consumer<BigDecimal> start,
                                      java.util.function.Consumer<BigDecimal> end) {
        String name = normalize(nameRaw);
        String startText = normalize(startRaw);
        String endText = normalize(endRaw);
        if (isBlank(name) && isBlank(startText) && isBlank(endText)) {
            return;
        }
        if (isBlank(name)) {
            errors.add(rowPrefix + label + "名称不能为空");
            return;
        }
        if (isBlank(startText)) {
            errors.add(rowPrefix + label + "起点不能为空");
            return;
        }
        BigDecimal startValue = decimal(startText, rowPrefix + label + "起点", errors);
        BigDecimal endValue = null;
        if (!isBlank(endText)) {
            endValue = decimal(endText, rowPrefix + label + "终点", errors);
        }
        if (startValue == null || (!isBlank(endText) && endValue == null)) {
            return;
        }
        location.accept(name);
        start.accept(startValue);
        if (endValue != null) {
            end.accept(endValue);
        }
    }

    private static void readDecimalField(String raw, String label, boolean rangeMode, String rowPrefix,
                                         List<String> errors, java.util.function.Consumer<BigDecimal> point,
                                         java.util.function.Consumer<BigDecimal> start,
                                         java.util.function.Consumer<BigDecimal> end) {
        String value = normalize(raw);
        if (isBlank(value)) {
            return;
        }
        if (rangeMode && value.contains("~")) {
            BigDecimal[] range = splitRange(value, rowPrefix + label, errors);
            if (range != null) {
                start.accept(range[0]);
                end.accept(range[1]);
            }
            return;
        }
        if (!rangeMode && value.contains("~")) {
            errors.add(rowPrefix + label + "未达合并阈值，填确定值");
            return;
        }
        BigDecimal number = decimal(value, rowPrefix + label, errors);
        if (number != null) {
            point.accept(number);
        }
    }

    private static Integer parseScale(Input input, DiseaseType diseaseType, List<String> errors) {
        if (isBlank(input.level)) {
            errors.add(prefix(input) + "参与评定时评定标度不能为空");
            return null;
        }
        Integer level = wholeNumber(input.level, prefix(input) + "评定标度", errors);
        if (level == null) {
            return null;
        }
        int min = diseaseType.getMinScale() == null ? 1 : diseaseType.getMinScale();
        int max = diseaseType.getMaxScale() == null ? 5 : diseaseType.getMaxScale();
        if (level < min || level > max) {
            errors.add(prefix(input) + "评定标度要在" + min + "到" + max + "之间");
            return null;
        }
        return level;
    }

    private static Integer parsePositiveInt(String raw, String label, List<String> errors) {
        if (isBlank(raw)) {
            errors.add(label + "不能为空");
            return null;
        }
        Integer value = wholeNumber(raw, label, errors);
        if (value != null && value <= 0) {
            errors.add(label + "要大于0");
            return null;
        }
        return value;
    }

    private static BigDecimal[] splitRange(String raw, String label, List<String> errors) {
        String value = normalize(raw).replace('～', '~');
        String[] parts = value.split("~");
        if (parts.length != 2) {
            errors.add(label + "达到合并阈值，写成范围，例如 0.50~1.20");
            return null;
        }
        BigDecimal start = decimal(parts[0], label, errors);
        BigDecimal end = decimal(parts[1], label, errors);
        if (start == null || end == null) {
            return null;
        }
        return new BigDecimal[]{start, end};
    }

    private static BigDecimal decimal(String raw, String label, List<String> errors) {
        String value = normalize(raw);
        if (isBlank(value)) {
            errors.add(label + "不能为空");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            errors.add(label + "不是数字");
            return null;
        }
    }

    private static Integer wholeNumber(String raw, String label, List<String> errors) {
        BigDecimal number = decimal(raw, label, errors);
        if (number == null) {
            return null;
        }
        try {
            return number.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            errors.add(label + "要填整数");
            return null;
        }
    }

    private static boolean bit(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }

    private static String prefix(Input input) {
        return "第" + input.rowNumber + "行：";
    }

    private static boolean isBlank(String value) {
        String text = normalize(value);
        return text.isEmpty() || "/".equals(text);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('～', '~').replace('＋', '+').replace('：', ':');
    }

    public static final class Input {
        public int rowNumber;
        public String participate;
        public String quantity;
        public String units;
        public String level;
        public String crackFeature;
        public String length;
        public String crackWidth;
        public String heightDepth;
        public String areaIdentifier;
        public String areaLength;
        public String areaWidth;
        public String deformation;
        public String angle;
        public String ratio;
        public String reference1Name;
        public String reference1Start;
        public String reference1End;
        public String reference2Name;
        public String reference2Start;
        public String reference2End;
        public String developmentTrend;
        public String nature;
        public String description;
        public String photo;
        public String remark;
        public DiseaseType diseaseType;
    }

    public static final class Output {
        public List<String> errors;
        public DiseaseDetail detail;
        public String crackType;
        public String participateAssess;
        public int level;
        public int quantity;
        public String units;
        public String developmentTrend;
        public String nature;
        public String description;
        public String photo;
        public String remark;
    }
}
