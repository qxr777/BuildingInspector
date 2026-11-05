package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.enums.AreaIdentifierEnums;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 病害 Excel 尺寸列。分开记录的多条明细：长度、面积相加，宽度取最大。
 * 一条范围记录：长度取均值乘数量，宽度取最大。写入数值单元格。
 */
public final class DiseaseExcelMetrics {

    private static final MathContext AREA_CONTEXT = new MathContext(4, RoundingMode.HALF_UP);
    private static final BigDecimal TWO = new BigDecimal("2");

    private DiseaseExcelMetrics() {
    }

    public static BigDecimal length(Disease disease) {
        List<BigDecimal> values = collect(disease, detail -> firstPresent(
                detail.totalLength(),
                midpoint(detail.getLengthRangeStart(), detail.getLengthRangeEnd())));
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal value : values) {
                total = total.add(value);
            }
            return total;
        }
        int quantity = disease.getQuantity() > 0 ? disease.getQuantity() : 1;
        return values.get(0).multiply(BigDecimal.valueOf(quantity));
    }

    public static BigDecimal width(Disease disease) {
        return maxDetails(disease, detail -> firstPresent(
                detail.getWidth(),
                maxOf(detail.getWidthRangeStart(), detail.getWidthRangeEnd())));
    }

    public static BigDecimal crackWidth(Disease disease) {
        return maxDetails(disease, detail -> firstPresent(
                detail.getCrackWidth(),
                maxOf(detail.getCrackWidthRangeStart(), detail.getCrackWidthRangeEnd())));
    }

    public static BigDecimal heightDepth(Disease disease) {
        return sumDetails(disease, detail -> firstPresent(
                detail.getHeightDepth(),
                midpoint(detail.getHeightDepthRangeStart(), detail.getHeightDepthRangeEnd())));
    }

    public static BigDecimal area(Disease disease) {
        List<DiseaseDetail> details = detailsOf(disease);
        if (details.isEmpty()) {
            return null;
        }
        if (details.size() == 1) {
            DiseaseDetail detail = details.get(0);
            BigDecimal area = rectangleArea(detail);
            if (area == null) {
                return null;
            }
            if (detail.getAreaIdentifier() != null
                    && detail.getAreaIdentifier() == AreaIdentifierEnums.AVERAGE.getCode()) {
                area = area.multiply(BigDecimal.valueOf(disease.getQuantity()));
            }
            return area.round(AREA_CONTEXT);
        }

        BigDecimal total = null;
        for (DiseaseDetail detail : details) {
            BigDecimal part = rectangleArea(detail);
            if (part != null) {
                total = total == null ? part : total.add(part);
            }
        }
        return total == null ? null : total.round(AREA_CONTEXT);
    }

    public static void writeNumber(Row row, int columnIndex, BigDecimal value) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static BigDecimal sumDetails(Disease disease, Function<DiseaseDetail, BigDecimal> getter) {
        BigDecimal total = null;
        for (BigDecimal value : collect(disease, getter)) {
            total = total == null ? value : total.add(value);
        }
        return total;
    }

    private static BigDecimal maxDetails(Disease disease, Function<DiseaseDetail, BigDecimal> getter) {
        BigDecimal max = null;
        for (BigDecimal value : collect(disease, getter)) {
            max = max == null ? value : max.max(value);
        }
        return max;
    }

    private static List<BigDecimal> collect(Disease disease, Function<DiseaseDetail, BigDecimal> getter) {
        List<BigDecimal> values = new ArrayList<>();
        for (DiseaseDetail detail : detailsOf(disease)) {
            BigDecimal value = getter.apply(detail);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static BigDecimal maxOf(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.max(right);
    }

    private static List<DiseaseDetail> detailsOf(Disease disease) {
        if (disease == null || disease.getDiseaseDetails() == null) {
            return Collections.emptyList();
        }
        return disease.getDiseaseDetails();
    }

    private static BigDecimal rectangleArea(DiseaseDetail detail) {
        if (detail.getAreaLength() == null || detail.getAreaWidth() == null) {
            return null;
        }
        return detail.getAreaLength().multiply(detail.getAreaWidth());
    }

    private static BigDecimal firstPresent(BigDecimal exact, BigDecimal rangeMid) {
        return exact != null ? exact : rangeMid;
    }

    private static BigDecimal midpoint(BigDecimal start, BigDecimal end) {
        if (start == null || end == null) {
            return null;
        }
        return start.add(end).divide(TWO, 6, RoundingMode.HALF_UP);
    }
}
