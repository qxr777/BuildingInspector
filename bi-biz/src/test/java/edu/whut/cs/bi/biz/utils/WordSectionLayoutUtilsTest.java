package edu.whut.cs.bi.biz.utils;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.InputStream;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordSectionLayoutUtilsTest {

    @Test
    void followingAnalysisReturnsToPortraitWithoutAnEmptySection() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var size = document.getDocument().getBody().addNewSectPr().addNewPgSz();
            size.setW(BigInteger.valueOf(16838));
            size.setH(BigInteger.valueOf(11906));
            size.setOrient(STPageOrientation.LANDSCAPE);
            var anchor = document.createParagraph();
            anchor.createRun().setText("评定结果");
            var analysis = document.createParagraph();
            analysis.createRun().setText("近年评定结果对比分析");
            var end = WordSectionLayoutUtils.beginLandscapeTableBlock(document, anchor,
                    new WordSectionLayoutUtils.Layouts(null, null));
            assertTrue(WordSectionLayoutUtils.isLandscape(end.getCTP().getPPr().getSectPr()));
            assertFalse(WordSectionLayoutUtils.isLandscape(
                    WordSectionLayoutUtils.findSectionContaining(document, analysis)));
            assertEquals(3, document.getParagraphs().size(), "不能插入额外的空白竖版节");
            assertEquals(3, WordSectionLayoutUtils.listSectPr(document).size());
        }
    }

    @Test
    void repeatedTablesAtSameAnchorKeepBothSidesAndSectionHeaders() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx");
             XWPFDocument document = new XWPFDocument(stream)) {
            var layouts = WordSectionLayoutUtils.snapshot(document);
            XWPFParagraph anchor = document.createParagraph();
            anchor.createRun().setText("评定结果说明");
            document.createParagraph().createRun().setText("后续章节");
            int originalSections = WordSectionLayoutUtils.listSectPr(document).size();
            int originalTables = document.getTables().size();
            for (String side : java.util.List.of("右幅", "左幅")) {
                XWPFParagraph landscape = WordSectionLayoutUtils.beginLandscapeTableBlock(document, anchor, layouts);
                try (var cursor = landscape.getCTP().newCursor()) {
                    var table = document.insertNewTbl(cursor);
                    table.getRow(0).getCell(0).setText(side + "评定表");
                }

                assertTrue(WordSectionLayoutUtils.isLandscape(landscape.getCTP().getPPr().getSectPr()));
                assertEquals(layouts.landscapeDefaultHeaderId(),
                        WordSectionLayoutUtils.defaultHeaderId(landscape.getCTP().getPPr().getSectPr()));
            }
            var output = new java.io.ByteArrayOutputStream();
            document.write(output);
            try (var reopened = new XWPFDocument(new java.io.ByteArrayInputStream(output.toByteArray()))) {
                assertEquals(originalTables + 2, reopened.getTables().size());
                assertEquals(originalSections + 3, WordSectionLayoutUtils.listSectPr(reopened).size());
                XWPFParagraph following = reopened.getParagraphs().stream()
                        .filter(p -> "后续章节".equals(p.getText())).findFirst().orElseThrow();
                assertFalse(WordSectionLayoutUtils.isLandscape(
                        WordSectionLayoutUtils.findSectionContaining(reopened, following)));
                var tables = reopened.getTables();
                assertTrue(tables.get(originalTables).getText().contains("左幅评定表"));
                assertTrue(tables.get(originalTables + 1).getText().contains("右幅评定表"));
                assertEquals(layouts.portraitDefaultHeaderId(),
                        WordSectionLayoutUtils.defaultHeaderId(anchor.getCTP().getPPr().getSectPr()));
            }
        }
    }

    @Test
    void headerTemplateKeepsDistinctLandscapeAndPortraitHeaders() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx");
             XWPFDocument document = new XWPFDocument(stream)) {
            WordSectionLayoutUtils.Layouts layouts = WordSectionLayoutUtils.snapshot(document);
            assertTrue(layouts.hasPortraitHeader());
            assertTrue(layouts.hasLandscapeHeader());
            assertNotEquals(layouts.portraitDefaultHeaderId(), layouts.landscapeDefaultHeaderId(),
                    "横版和竖版必须绑不同的页眉，logo 才能分别贴在两种页宽的右边");
        }
    }

    @Test
    void rebindPutsLandscapeHeaderOnLandscapeSectionAndLeavesEmptyPortraitAlone() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx");
             XWPFDocument document = new XWPFDocument(stream)) {
            WordSectionLayoutUtils.Layouts layouts = WordSectionLayoutUtils.snapshot(document);

            XWPFParagraph landscapeEnd = document.createParagraph();
            CTSectPr landscapeSect = landscapeEnd.getCTP().addNewPPr().addNewSectPr();
            landscapeSect.addNewPgSz().setOrient(STPageOrientation.LANDSCAPE);
            landscapeSect.getPgSz().setW(BigInteger.valueOf(16838));
            landscapeSect.getPgSz().setH(BigInteger.valueOf(11906));

            XWPFParagraph emptyPortrait = document.createParagraph();
            CTSectPr emptyPortraitSect = emptyPortrait.getCTP().addNewPPr().addNewSectPr();
            emptyPortraitSect.addNewPgSz().setW(BigInteger.valueOf(11906));
            emptyPortraitSect.getPgSz().setH(BigInteger.valueOf(16838));

            WordSectionLayoutUtils.rebindHeadersByOrientation(document, layouts);

            assertEquals(layouts.landscapeDefaultHeaderId(),
                    WordSectionLayoutUtils.defaultHeaderId(landscapeSect));
            assertFalse(WordSectionLayoutUtils.hasDefaultHeader(emptyPortraitSect),
                    "本来没有页眉的竖版节不应被补上横版或竖版页眉");
        }
    }

    @Test
    void insertLandscapeSectionUsesLandscapeHeaderFromTemplate() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/word.biz/定期检查_普通桥梁线路多桥服务端模板-页眉.docx");
             XWPFDocument document = new XWPFDocument(stream)) {
            WordSectionLayoutUtils.Layouts layouts = WordSectionLayoutUtils.snapshot(document);
            XWPFParagraph after = document.getParagraphs().get(document.getParagraphs().size() / 2);
            XWPFParagraph landscape = WordSectionLayoutUtils.beginLandscapeTableBlock(document, after, layouts);

            CTSectPr landscapeSect = landscape.getCTP().getPPr().getSectPr();
            CTSectPr portraitSect = after.getCTP().getPPr().getSectPr();
            assertTrue(WordSectionLayoutUtils.isLandscape(landscapeSect));
            assertEquals(layouts.landscapeDefaultHeaderId(),
                    WordSectionLayoutUtils.defaultHeaderId(landscapeSect));
            assertEquals(layouts.portraitDefaultHeaderId(),
                    WordSectionLayoutUtils.defaultHeaderId(portraitSect));
            assertNotEquals(WordSectionLayoutUtils.defaultHeaderId(landscapeSect),
                    WordSectionLayoutUtils.defaultHeaderId(portraitSect));
        }
    }
}
