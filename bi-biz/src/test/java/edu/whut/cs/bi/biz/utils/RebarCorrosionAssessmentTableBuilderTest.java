package edu.whut.cs.bi.biz.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void ratesTheLowestPotentialByTable543() {
        assertEquals(1, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-29.8")));
        assertEquals(1, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-200")));
        assertEquals(2, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-200.1")));
        assertEquals(2, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-300")));
        assertEquals(3, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-300.1")));
        assertEquals(4, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-500")));
        assertEquals(5, RebarCorrosionAssessmentTableBuilder.corrosionScale(new BigDecimal("-500.1")));
    }
}
