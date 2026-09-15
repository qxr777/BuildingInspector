package edu.whut.cs.bi.biz.service.impl;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LineMultiBridgeCaptionTest {
    @Test
    void copiedBridgeCardKeepsFieldsAndUsesFivePointCaptionStyle() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板.docx");
             XWPFDocument source = new XWPFDocument(stream);
             XWPFDocument target = new XWPFDocument()) {
            XWPFParagraph card = source.getParagraphs().stream()
                    .filter(p -> p.getText().contains("桥梁基本状况卡片"))
                    .findFirst().orElseThrow();
            List<String> fields = fieldInstructions(card);
            assertTrue(fields.stream().anyMatch(s -> s.contains("STYLEREF")));
            assertTrue(fields.stream().anyMatch(s -> s.contains("SEQ")));
            target.createStyles().setStyles(source.getStyle());
            LineMultiBridgeReportServiceImpl service = new LineMultiBridgeReportServiceImpl();
            Method copy = LineMultiBridgeReportServiceImpl.class.getDeclaredMethod(
                    "insertBodyElementCopies", XWPFDocument.class, XWPFParagraph.class,
                    XWPFDocument.class, List.class);
            copy.setAccessible(true);
            XWPFParagraph anchor = target.createParagraph();
            copy.invoke(service, target, anchor, source, List.of(card, card));
            Method format = LineMultiBridgeReportServiceImpl.class.getDeclaredMethod(
                    "applyDocumentHeadingStyles", XWPFDocument.class);
            format.setAccessible(true);
            format.invoke(service, target);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            target.write(bytes);
            try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(bytes.toByteArray()))) {
                List<XWPFParagraph> captions = result.getParagraphs().stream()
                        .filter(p -> p.getText().contains("桥梁基本状况卡片"))
                        .collect(Collectors.toList());
                assertEquals(2, captions.size());
                for (XWPFParagraph caption : captions) {
                    assertEquals("BIAppendixCaption", caption.getStyle());
                    assertEquals(ParagraphAlignment.CENTER, caption.getAlignment());
                    assertEquals(BigInteger.valueOf(9), caption.getCTP().getPPr().getOutlineLvl().getVal());
                    assertEquals(fields, fieldInstructions(caption));
                    assertEquals(card.getText(), caption.getText());
                    for (XWPFRun run : caption.getRuns()) {
                        assertEquals(10.5, run.getFontSizeAsDouble());
                    }
                }
            }
        }
    }

    private List<String> fieldInstructions(XWPFParagraph paragraph) {
        return paragraph.getRuns().stream()
                .flatMap(run -> run.getCTR().getInstrTextList().stream())
                .map(text -> text.getStringValue()).collect(Collectors.toList());
    }
}
