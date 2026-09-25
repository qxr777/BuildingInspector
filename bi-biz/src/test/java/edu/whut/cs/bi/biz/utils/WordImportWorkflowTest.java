package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.service.impl.LineReportWordImportService;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class WordImportWorkflowTest {
    @TempDir Path temp;

    @Test
    void replacesFieldAndPreservesParagraphTableImageStylesAndNumbering() throws Exception {
        Path source = temp.resolve("source.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            CTStyle normal = CTStyle.Factory.newInstance(); normal.setStyleId("Normal");
            normal.setType(STStyleType.PARAGRAPH); normal.setDefault(true);
            normal.addNewName().setVal("Normal"); normal.addNewRPr().addNewRFonts().setEastAsia("楷体");
            doc.createStyles().addStyle(new XWPFStyle(normal));
            doc.createParagraph().createRun().setText("项目概况");
            XWPFParagraph p = doc.createParagraph(); p.setStyle("Normal"); p.setIndentationFirstLine(480);
            XWPFRun run = p.createRun(); run.setText("原格式项目概况"); run.setBold(true); run.setColor("123456"); run.setFontSize(18);
            XWPFNumbering nums = doc.createNumbering();
            CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance(); abstractNum.setAbstractNumId(BigInteger.ZERO);
            CTLvl lvl = abstractNum.addNewLvl(); lvl.setIlvl(BigInteger.ZERO); lvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
            lvl.addNewLvlText().setVal("%1."); lvl.addNewStart().setVal(BigInteger.ONE);
            BigInteger abstractId = nums.addAbstractNum(new XWPFAbstractNum(abstractNum));
            p.setNumID(nums.addNum(abstractId));
            XWPFTable table = doc.createTable(1, 1); table.getRow(0).getCell(0).setText("来源表格"); table.setWidth(4200);
            byte[] image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=");
            doc.createParagraph().createRun().addPicture(new ByteArrayInputStream(image), Document.PICTURE_TYPE_PNG, "image.png", 200000, 100000);
            XWPFParagraph caption = doc.createParagraph(); caption.createRun().setText("图");
            caption.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            caption.createRun().getCTR().addNewInstrText().setStringValue(" STYLEREF 1 ");
            caption.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            caption.createRun().setText("3.1");
            caption.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            doc.createParagraph().createRun().setText("构件编号"); doc.createParagraph().createRun().setText("编号正文");
            try (OutputStream out = Files.newOutputStream(source)) { doc.write(out); }
        }
        byte[] original = Files.readAllBytes(source);
        LineReportWordMatcher.Result parsed;
        try (InputStream in = Files.newInputStream(source)) { parsed = LineReportWordMatcher.match(in, List.of(), List.of()); }
        Path target = temp.resolve("target.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            CTStyle targetNormal = CTStyle.Factory.newInstance();
            targetNormal.setStyleId("1");
            targetNormal.setType(STStyleType.PARAGRAPH);
            targetNormal.setDefault(true);
            targetNormal.addNewName().setVal("Normal");
            doc.createStyles().addStyle(new XWPFStyle(targetNormal));
            doc.createParagraph().createRun().setText("目标标题");
            doc.createParagraph().createRun().setText("MARKER");
            doc.createParagraph().createRun().setText("后续章节保持");
            try (OutputStream out = Files.newOutputStream(target)) { doc.write(out); }
        }
        var section = parsed.getSections().get(0);
        WordImportMerger.merge(target, List.of(new WordImportMerger.Item("MARKER", source, section.getStart(), section.getEnd())));
        assertArrayEquals(original, Files.readAllBytes(source));
        try (XWPFDocument result = new XWPFDocument(Files.newInputStream(target))) {
            XWPFParagraph p = result.getParagraphs().stream().filter(x -> x.getText().equals("原格式项目概况")).findFirst().orElseThrow();
            assertEquals(480, p.getIndentationFirstLine()); assertTrue(p.getRuns().get(0).isBold());
            assertEquals("123456", p.getRuns().get(0).getColor()); assertEquals(18, p.getRuns().get(0).getFontSize());
            assertEquals("1", p.getStyle());
            assertEquals("楷体", p.getRuns().get(0).getCTR().getRPr().getRFontsArray(0).getEastAsia());
            assertEquals("Normal", result.getStyles().getStyle("1").getName());
            assertNotNull(result.getNumbering().getNum(p.getNumID()));
            assertEquals("来源表格", result.getTables().get(0).getText().trim());
            assertEquals(1, result.getAllPictures().size());
            assertTrue(result.getParagraphs().stream().anyMatch(x -> x.getText().equals("图3.1")));
            assertFalse(result.getDocument().xmlText().contains("STYLEREF"));
            assertTrue(result.getParagraphs().stream().anyMatch(x -> x.getText().equals("后续章节保持")));
            assertFalse(result.getParagraphs().stream().anyMatch(x -> x.getText().contains("编号正文") || x.getText().contains("MARKER")));
        }
    }

    @Test
    void chineseBodyStyleMapsToTemplateNormalAndKeepsSourceLook() throws Exception {
        Path source = temp.resolve("source-body.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            CTStyle body = CTStyle.Factory.newInstance();
            body.setStyleId("a");
            body.setType(STStyleType.PARAGRAPH);
            body.setDefault(true);
            body.addNewName().setVal("正文");
            CTRPr bodyRun = body.addNewRPr();
            bodyRun.addNewRFonts().setEastAsia("仿宋");
            bodyRun.addNewSz().setVal(BigInteger.valueOf(28));
            var sourceStyles = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles.Factory.newInstance();
            sourceStyles.addNewDocDefaults().addNewRPrDefault().addNewRPr().addNewRFonts()
                    .setEastAsiaTheme(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTheme.MINOR_EAST_ASIA);
            sourceStyles.addNewStyle().set(body);
            doc.createStyles().setStyles(sourceStyles);
            doc.createParagraph().createRun().setText("项目概况");
            XWPFParagraph p = doc.createParagraph();
            p.setStyle("a");
            p.createRun().setText("来源正文");
            XWPFRun explicitFont = p.createRun();
            explicitFont.setText("直接指定宋体");
            explicitFont.getCTR().addNewRPr().addNewRFonts().setEastAsia("宋体");
            XWPFRun themeFont = p.createRun();
            themeFont.setText("显式主题字体");
            themeFont.getCTR().addNewRPr().addNewRFonts().setEastAsiaTheme(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STTheme.MAJOR_EAST_ASIA);
            try (OutputStream out = Files.newOutputStream(source)) { doc.write(out); }
        }
        LineReportWordMatcher.Result parsed;
        try (InputStream in = Files.newInputStream(source)) {
            parsed = LineReportWordMatcher.match(in, List.of(), List.of());
        }
        Path target = temp.resolve("target-template.docx");
        try (XWPFDocument doc = new XWPFDocument(getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板.docx"))) {
            doc.createParagraph().createRun().setText("MARKER");
            try (OutputStream out = Files.newOutputStream(target)) { doc.write(out); }
        }
        WordImportMerger.merge(target, List.of(new WordImportMerger.Item(
                "MARKER", source, parsed.getSections().get(0).getStart(), parsed.getSections().get(0).getEnd())));
        try (XWPFDocument result = new XWPFDocument(Files.newInputStream(target))) {
            XWPFParagraph p = result.getParagraphs().stream()
                    .filter(x -> x.getText().contains("来源正文")).findFirst().orElseThrow();
            assertEquals("1", p.getStyle());
            assertEquals("仿宋", p.getRuns().get(0).getCTR().getRPr().getRFontsArray(0).getEastAsia());
            assertFalse(p.getRuns().get(0).getCTR().getRPr().getRFontsArray(0).isSetEastAsiaTheme());
            assertEquals("宋体", p.getRuns().get(1).getCTR().getRPr().getRFontsArray(0).getEastAsia());
            assertFalse(p.getRuns().get(1).getCTR().getRPr().getRFontsArray(0).isSetEastAsiaTheme());
            assertFalse(p.getRuns().get(2).getCTR().getRPr().getRFontsArray(0).isSetEastAsia());
            assertEquals(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTheme.MAJOR_EAST_ASIA,
                    p.getRuns().get(2).getCTR().getRPr().getRFontsArray(0).getEastAsiaTheme());
            assertEquals(28, ((Number) p.getRuns().get(0).getCTR().getRPr().getSzArray(0).getVal()).intValue());
        }
    }

    @Test
    void existingTemplateCanPrepareAllSixFieldsAndRemovesOldOverviewPhotos() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(getClass().getResourceAsStream("/word.biz/定期检查_普通桥梁线路多桥服务端模板.docx"))) {
            long id = 100;
            for (String key : List.of("line-route-overview", "line-component-numbering-rules", "overallOverview", "designPoints", "mainMaterial", "technicalAdvice")) {
                ReportData row = new ReportData(); row.setId(id++); row.setKey(key); row.setType(2);
                WordImportSupport.prepare(doc, row);
                assertEquals(1, doc.getParagraphs().stream().filter(p -> p.getText().equals(WordImportSupport.marker(row))).count());
            }
            String xml = doc.getDocument().xmlText();
            assertFalse(xml.contains("%{leftFront}"));
            assertFalse(xml.contains("${line-component-numbering-rules}"));
            assertTrue(xml.contains("${line.bridge-block-end}"));
        }
    }

    @Test
    void technicalAdviceImportPreservesPortraitBoundaryInHeaderTemplate() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx"))) {
            XWPFParagraph analysis = doc.getParagraphs().stream()
                    .filter(p -> p.getText().contains("${comparativeAnalysisOfEvaluationResults}"))
                    .findFirst().orElseThrow();
            var before = WordSectionLayoutUtils.findSectionContaining(doc, analysis);
            var originalSize = (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz) before.getPgSz().copy();
            String originalHeader = WordSectionLayoutUtils.defaultHeaderId(before);
            assertFalse(WordSectionLayoutUtils.isLandscape(before));
            int sectionCount = WordSectionLayoutUtils.listSectPr(doc).size();
            ReportData row = new ReportData();
            row.setId(999L); row.setKey("technicalAdvice"); row.setType(2);
            WordImportSupport.prepare(doc, row);
            var after = WordSectionLayoutUtils.findSectionContaining(doc, analysis);
            assertEquals(originalSize.getW(), after.getPgSz().getW());
            assertEquals(originalSize.getH(), after.getPgSz().getH());
            assertEquals(originalHeader, WordSectionLayoutUtils.defaultHeaderId(after));
            assertEquals(sectionCount, WordSectionLayoutUtils.listSectPr(doc).size());
            // Late merge removes the marker. The independent boundary must survive it.
            XWPFParagraph marker = doc.getParagraphs().stream()
                    .filter(p -> WordImportSupport.marker(row).equals(p.getText())).findFirst().orElseThrow();
            doc.removeBodyElement(doc.getPosOfParagraph(marker));
            assertFalse(WordSectionLayoutUtils.isLandscape(
                    WordSectionLayoutUtils.findSectionContaining(doc, analysis)));
        }
    }

    @Test
    void validatesTargetsVersionsAndExplicitOverwriteBeforeUpload() throws Exception {
        LineReportWordMatcher.Result parsed = new LineReportWordMatcher.Result();
        LineReportWordMatcher.Section section = new LineReportWordMatcher.Section();
        section.setId("s1"); section.setStart(2); section.setEnd(5); section.setKey("mainMaterial"); section.setText("正文");
        parsed.getSections().add(section);
        LineReportWordMatcher.Target target = new LineReportWordMatcher.Target();
        target.setId("t1"); target.setGroupId("g1"); target.setKey("mainMaterial"); target.setVersion("current"); target.setHasExistingContent(true);
        parsed.getTargets().add(target);
        LineReportWordImportService.Selection choice = new LineReportWordImportService.Selection();
        choice.setSourceId("s1"); choice.setGroupId("g1"); choice.setKey("mainMaterial"); choice.setExpectedVersion("old");
        assertThrows(IllegalArgumentException.class, () -> LineReportWordImportService.prepare(1L, parsed, List.of(choice), "来源.docx"));
        choice.setExpectedVersion("current");
        assertThrows(IllegalArgumentException.class, () -> LineReportWordImportService.prepare(1L, parsed, List.of(choice), "来源.docx"));
        choice.setReplaceExisting(true);
        List<LineReportData> rows = LineReportWordImportService.prepare(1L, parsed, List.of(choice), "来源.docx");
        assertEquals(2, rows.get(0).getType()); assertEquals("g1", rows.get(0).getGroupId());
        assertThrows(IllegalArgumentException.class, () -> LineReportWordImportService.prepare(1L, parsed, List.of(choice, choice), "来源.docx"));
        choice.setGroupId("other-report-bridge");
        assertThrows(IllegalArgumentException.class, () -> LineReportWordImportService.prepare(1L, parsed, List.of(choice), "来源.docx"));
    }
}
