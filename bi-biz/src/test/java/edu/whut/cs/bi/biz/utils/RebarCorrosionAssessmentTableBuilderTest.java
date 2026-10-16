package edu.whut.cs.bi.biz.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RebarCorrosionAssessmentTableBuilderTest {

    @Test
    void correctsMinimumWhenTemperatureIsOutsideTheReferenceBand() {
        assertArrayEquals(new String[]{"2.7", "-29.8"},
                RebarCorrosionAssessmentTableBuilder.correctMinimum(new BigDecimal("-32.5"), "30"));
        assertArrayEquals(new String[]{"-4.5", "-37.0"},
                RebarCorrosionAssessmentTableBuilder.correctMinimum(new BigDecimal("-32.5"), "12℃"));
    }

    @Test
    void keepsMinimumInsideTheReferenceBandAndSkipsMissingTemperature() {
        assertArrayEquals(new String[]{"0.0", "-32.5"},
                RebarCorrosionAssessmentTableBuilder.correctMinimum(new BigDecimal("-32.5"), "22"));
        assertArrayEquals(new String[]{"", ""},
                RebarCorrosionAssessmentTableBuilder.correctMinimum(new BigDecimal("-32.5"), ""));
    }
}
