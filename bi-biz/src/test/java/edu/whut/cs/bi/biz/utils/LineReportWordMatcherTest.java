package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.LineBridgeGroup;
import edu.whut.cs.bi.biz.domain.LineReportData;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class LineReportWordMatcherTest {
    @Test
    public void matchesLineAndBridgeFieldsUsingStyleNamesAndStopsAtOtherHeadings() throws Exception {
        try (XWPFDocument doc = document()) {
            heading(doc, "目录中的整体概况", "toc");
            heading(doc, "1. 项目概况", "h1");
            text(doc, "线路介绍");
            doc.createTable(1, 1).getRow(0).getCell(0).setText("桥梁一览表");
            heading(doc, "2. 构件编号及划分原则", "h1");
            heading(doc, "2.1 构件编号规则", "h2");
            text(doc, "编号说明");
            heading(doc, "2.2 部件划分及构件数量", "h2");
            text(doc, "不能混入编号规则");
            heading(doc, "3. 杜家台大桥", "h1");
            heading(doc, "桥梁概况", "h2");
            heading(doc, "整体概况", "h3");
            text(doc, "该桥整体情况");
            heading(doc, "设计要点", "h3");
            text(doc, "设计正文");
            heading(doc, "主要材料", "h3");
            heading(doc, "混凝土", "h4");
            text(doc, "C50");
            heading(doc, "技术标准", "h3");
            text(doc, "不得进入材料");
            LineReportData old = new LineReportData();
            old.setGroupId("g1"); old.setKey("mainMaterial"); old.setValue("旧材料");
            LineReportWordMatcher.Result result = match(doc, List.of(group("g1", "杜家台大桥")), List.of(old));
            assertEquals(5, result.getSections().size());
            assertEquals(6, result.getTargets().size());
            assertEquals(1, result.getSections().get(0).getTableCount());
            assertEquals("编号说明", result.getSections().get(1).getText());
            assertEquals("MATCHED", result.getSections().get(2).getStatus());
            assertEquals("杜家台大桥", result.getSections().get(2).getSourceBridge());
            assertEquals("EXISTING", result.getSections().get(4).getStatus());
            assertEquals("混凝土\nC50", result.getSections().get(4).getText());
            assertTrue(result.getSections().get(4).getEnd() > result.getSections().get(4).getStart());
        }
    }

    @Test
    public void matchesTechnicalAdviceUnderEachBridgeWithoutIncludingNextChapter() throws Exception {
        try (XWPFDocument doc = document()) {
            heading(doc, "3. 杜家台大桥", "h1");
            heading(doc, "3.3 结论与建议", "h2");
            heading(doc, "3.3.1 技术建议", "h3");
            text(doc, "杜家台维修建议");
            heading(doc, "分项措施", "h4");
            text(doc, "修复伸缩缝");
            heading(doc, "3.3.2 其他说明", "h3");
            text(doc, "不能导入技术建议");
            heading(doc, "4. 胡家台大桥", "h1");
            heading(doc, "4.3 结论与建议", "h2");
            heading(doc, "4.3.1 技术建议", "h3");
            text(doc, "胡家台维修建议");
            LineReportWordMatcher.Result result = match(doc,
                    List.of(group("g1", "杜家台大桥"), group("g2", "胡家台大桥")), List.of());
            assertEquals(2, result.getSections().size());
            assertEquals("杜家台维修建议\n分项措施\n修复伸缩缝", result.getSections().get(0).getText());
            assertEquals("胡家台维修建议", result.getSections().get(1).getText());
            for (int i = 0; i < 2; i++) {
                LineReportWordMatcher.Section section = result.getSections().get(i);
                assertEquals("technicalAdvice", section.getKey());
                assertEquals("MATCHED", section.getStatus());
                LineReportWordMatcher.Target target = result.getTargets().stream()
                        .filter(t -> t.getId().equals(section.getTargetId())).findFirst().orElseThrow();
                assertEquals("g" + (i + 1), target.getGroupId());
            }
        }
    }

    @Test
    public void doesNotGuessUnknownOrDuplicateBridgeNames() throws Exception {
        try (XWPFDocument doc = document()) {
            heading(doc, "未登记大桥", "h1");
            heading(doc, "整体概况", "h3"); text(doc, "未知桥");
            heading(doc, "杜家台大桥", "h1");
            heading(doc, "主要材料", "h3"); text(doc, "材料");
            LineReportWordMatcher.Result result = match(doc,
                    List.of(group("g1", "杜家台大桥"), group("g2", "杜家台大桥")), List.of());
            assertEquals("UNMATCHED", result.getSections().get(0).getStatus());
            assertEquals("AMBIGUOUS", result.getSections().get(1).getStatus());
            assertNull(result.getSections().get(1).getTargetId());
        }
    }

    @Test
    public void detectsEmptyAndDuplicateSourcesAndDoesNotTreatTocAsContent() throws Exception {
        try (XWPFDocument doc = document()) {
            heading(doc, "项目概况", "toc");
            heading(doc, "项目概况", "h1"); text(doc, "项目1");
            heading(doc, "项目概况", "h1"); text(doc, "项目2");
            heading(doc, "杜家台大桥", "h1");
            heading(doc, "整体概况", "h3");
            heading(doc, "主要材料", "h3"); text(doc, "正文");
            LineReportWordMatcher.Result result = match(doc, List.of(group("g1", "杜家台大桥")), List.of());
            assertEquals(4, result.getSections().size());
            assertEquals("DUPLICATE", result.getSections().get(0).getStatus());
            assertEquals("DUPLICATE", result.getSections().get(1).getStatus());
            assertEquals("EMPTY", result.getSections().get(2).getStatus());
        }
    }

    @Test
    public void includesImagesAndTablesInsideTheSectionAndKeepsOriginalBytesUntouched() throws Exception {
        try (XWPFDocument doc = document()) {
            heading(doc, "杜家台大桥", "h1");
            heading(doc, "整体概况", "h3");
            XWPFParagraph picture = doc.createParagraph();
            picture.createRun().getCTR().addNewDrawing();
            XWPFTable table = doc.createTable(1, 1);
            table.getRow(0).getCell(0).getParagraphs().get(0).createRun().getCTR().addNewDrawing();
            heading(doc, "设计要点", "h3"); text(doc, "另一节");
            ByteArrayOutputStream output = new ByteArrayOutputStream(); doc.write(output);
            byte[] original = output.toByteArray();
            LineReportWordMatcher.Result result = LineReportWordMatcher.match(new ByteArrayInputStream(original),
                    List.of(group("g1", "杜家台大桥")), List.of());
            assertEquals(2, result.getSections().get(0).getImageCount());
            assertEquals(1, result.getSections().get(0).getTableCount());
            assertFalse(result.getSections().get(0).isEmpty());
            assertEquals("MATCHED", result.getSections().get(0).getStatus());
            try (XWPFDocument reopened = new XWPFDocument(new ByteArrayInputStream(original))) {
                assertEquals(doc.getBodyElements().size(), reopened.getBodyElements().size());
            }
        }
    }

    @Test
    public void recognizesPlainExactAliasesWithoutInventingMissingBridge() throws Exception {
        try (XWPFDocument doc = document()) {
            text(doc, "项目概括"); text(doc, "项目正文");
            text(doc, "构件编号"); text(doc, "规则");
            text(doc, "杜家台大桥");
            text(doc, "整体概括"); text(doc, "概况");
            text(doc, "主要建筑材料"); text(doc, "钢材");
            text(doc, "技术标准"); text(doc, "不属于主要材料");
            LineReportWordMatcher.Result result = match(doc, List.of(group("g1", "杜家台大桥")), List.of());
            assertEquals(4, result.getSections().size());
            assertTrue(result.getSections().stream().allMatch(s -> "MATCHED".equals(s.getStatus())));
            assertEquals("钢材", result.getSections().get(3).getText());
        }
    }

    @Test
    public void readsLargeImageArchivesWithoutChangingGlobalPoiLimits() throws Exception {
        long limit = org.apache.poi.openxml4j.util.ZipSecureFile.getMaxFileCount();
        byte[] zip = xmlArchive("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>项目概况</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>正文</w:t></w:r></w:p></w:body></w:document>", 1100);
        LineReportWordMatcher.Result result = LineReportWordMatcher.match(new ByteArrayInputStream(zip), List.of(), List.of());
        assertEquals(1, result.getSections().size());
        assertEquals("MATCHED", result.getSections().get(0).getStatus());
        assertEquals(limit, org.apache.poi.openxml4j.util.ZipSecureFile.getMaxFileCount());
    }

    @Test
    public void rejectsDoctypeAndDoesNotReadExternalEntities() throws Exception {
        byte[] zip = xmlArchive("<!DOCTYPE document [<!ENTITY secret SYSTEM 'file:///not-to-be-read'>]>"
                + "<document>&secret;</document>", 0);
        assertThrows(IOException.class, () -> LineReportWordMatcher.match(new ByteArrayInputStream(zip), List.of(), List.of()));
    }

    private byte[] xmlArchive(String xml, int images) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            for (int i = 0; i < images; i++) {
                zip.putNextEntry(new ZipEntry("word/media/image" + i + ".png"));
                zip.write(i); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @Test
    public void preservesDigitsInBridgeNames() {
        assertEquals("通顺河2号大桥", LineReportWordMatcher.normalize("5. 通顺河 2 号大桥"));
        assertNotEquals(LineReportWordMatcher.normalize("东荆河1号大桥"), LineReportWordMatcher.normalize("东荆河2号大桥"));
    }

    private XWPFDocument document() {
        XWPFDocument doc = new XWPFDocument();
        XWPFStyles styles = doc.createStyles();
        for (int level = 1; level <= 4; level++) {
            CTStyle style = CTStyle.Factory.newInstance();
            style.setStyleId("h" + level); style.addNewName().setVal("heading " + level);
            style.addNewPPr().addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
            styles.addStyle(new XWPFStyle(style));
        }
        CTStyle toc = CTStyle.Factory.newInstance();
        toc.setStyleId("toc"); toc.addNewName().setVal("toc 1"); styles.addStyle(new XWPFStyle(toc));
        return doc;
    }

    private void text(XWPFDocument doc, String text) { doc.createParagraph().createRun().setText(text); }

    private void heading(XWPFDocument doc, String text, String style) {
        XWPFParagraph paragraph = doc.createParagraph(); paragraph.setStyle(style); paragraph.createRun().setText(text);
    }

    private LineBridgeGroup group(String id, String name) {
        LineBridgeGroup group = new LineBridgeGroup(); group.setId(id); group.setName(name); return group;
    }

    private LineReportWordMatcher.Result match(XWPFDocument doc, List<LineBridgeGroup> groups,
                                               List<LineReportData> existing) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); doc.write(output);
        return LineReportWordMatcher.match(new ByteArrayInputStream(output.toByteArray()), groups, existing);
    }
}
