package edu.whut.cs.bi.biz.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineMultiBridgeCarbonizationPlaceholderTest {

    @Test
    void headerTemplateHasCarbonizationPlaceholdersAfterHeading() throws Exception {
        assertPlaceholdersAfterHeading("/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx");
    }

    @Test
    void plainTemplateHasCarbonizationPlaceholdersAfterHeading() throws Exception {
        assertPlaceholdersAfterHeading("/word.biz/定期检查_普通桥梁线路多桥服务端模板.docx");
    }

    @Test
    void emptyDataReplacesPlaceholdersInsteadOfToc() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx");
             XWPFDocument document = new XWPFDocument(stream)) {
            LineMultiBridgeReportServiceImpl service = new LineMultiBridgeReportServiceImpl();
            Method method = LineMultiBridgeReportServiceImpl.class.getDeclaredMethod(
                    "handleCarbonizationAssessment", XWPFDocument.class, List.class, AtomicInteger.class);
            method.setAccessible(true);
            method.invoke(service, document, List.of(), new AtomicInteger(1));

            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.toList());
            int toc = indexContaining(texts, "3.5.");
            int heading = lastIndexOf(texts, "碳化深度及锈蚀电位状况");
            int carbonTitle = indexContaining(texts, "（1）碳化状况检测评定结果");
            int corrosionTitle = indexContaining(texts, "（2）锈蚀电位状况检测评定结果");

            assertTrue(toc >= 0);
            assertTrue(heading > toc);
            assertEquals(heading + 1, carbonTitle);
            assertEquals(heading + 3, corrosionTitle);
            assertFalse(texts.get(toc + 1).contains("碳化状况检测评定结果"));
            assertTrue(texts.stream().noneMatch(text -> text.contains("${carbonizationAssessment}")));
            assertTrue(texts.stream().noneMatch(text -> text.contains("${rebarCorrosionAssessment}")));
        }
    }

    private void assertPlaceholdersAfterHeading(String resource) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(resource);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.toList());
            int heading = lastIndexOf(texts, "碳化深度及锈蚀电位状况");
            assertTrue(heading >= 0);
            assertEquals("${carbonizationAssessment}", texts.get(heading + 1).trim());
            assertFalse(texts.get(heading + 2).contains("${rebarCorrosionAssessment}"));
        }
    }

    private static int lastIndexOf(List<String> texts, String expected) {
        for (int i = texts.size() - 1; i >= 0; i--) {
            if (expected.equals(texts.get(i).trim())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexContaining(List<String> texts, String expected) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i) != null && texts.get(i).contains(expected)) {
                return i;
            }
        }
        return -1;
    }
}
