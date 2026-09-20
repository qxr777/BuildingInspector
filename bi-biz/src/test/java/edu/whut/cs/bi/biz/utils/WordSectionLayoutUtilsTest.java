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
            CTSectPr current = WordSectionLayoutUtils.findSectionContaining(document, after);
            WordSectionLayoutUtils.closeCurrentSection(after, current);
            XWPFParagraph landscape = WordSectionLayoutUtils.insertLandscapeSectionEnd(document, after, layouts);
            XWPFParagraph portrait = WordSectionLayoutUtils.insertPortraitSectionAfter(document, landscape, layouts);

            CTSectPr landscapeSect = landscape.getCTP().getPPr().getSectPr();
            CTSectPr portraitSect = portrait.getCTP().getPPr().getSectPr();
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
