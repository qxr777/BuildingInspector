package edu.whut.cs.bi.biz.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportGenerationTimeoutTest {

    @Test
    public void nullUpdateTimeIsTimedOut() {
        assertTrue(ReportServiceImpl.generationTimedOut(null, 1_000L, ReportServiceImpl.REPORT_GENERATION_TIMEOUT_MILLIS));
    }

    @Test
    public void exactlyFiveHoursIsTimedOut() {
        long now = 1_700_000_000_000L;
        Date started = new Date(now - ReportServiceImpl.REPORT_GENERATION_TIMEOUT_MILLIS);
        assertTrue(ReportServiceImpl.generationTimedOut(started, now, ReportServiceImpl.REPORT_GENERATION_TIMEOUT_MILLIS));
    }

    @Test
    public void youngerThanFiveHoursKeepsGenerating() {
        long now = 1_700_000_000_000L;
        Date started = new Date(now - ReportServiceImpl.REPORT_GENERATION_TIMEOUT_MILLIS + 1);
        assertFalse(ReportServiceImpl.generationTimedOut(started, now, ReportServiceImpl.REPORT_GENERATION_TIMEOUT_MILLIS));
    }
}
