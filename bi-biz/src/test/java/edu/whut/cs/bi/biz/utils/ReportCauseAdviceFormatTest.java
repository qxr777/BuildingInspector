package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.vo.DiseaseCauseMatch;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportCauseAdviceFormatTest {

    @Test
    public void singleCauseStaysOnTheLabelLine() {
        List<List<ReportGenerateTools.TextSegment>> paragraphs = ReportGenerateTools.causeAnalysisParagraphs(
                2, "伸缩缝装置橡胶条破损", "长期重复位移导致橡胶条磨损。");

        assertEquals("（2）伸缩缝装置橡胶条破损", text(paragraphs.get(0)));
        assertEquals("成因分析：1）长期重复位移导致橡胶条磨损。", text(paragraphs.get(1)));
    }

    @Test
    public void numberedCauseLinesBecomeSeparatePoints() {
        List<List<ReportGenerateTools.TextSegment>> paragraphs = ReportGenerateTools.causeAnalysisParagraphs(
                1, "T梁剥落", "1)钢筋暴露导致剥落\n2)保护层厚度不足。");

        assertEquals("（1）T梁剥落", text(paragraphs.get(0)));
        assertEquals("成因分析：", text(paragraphs.get(1)));
        assertEquals("1）钢筋暴露导致剥落；", text(paragraphs.get(2)));
        assertEquals("2）保护层厚度不足。", text(paragraphs.get(3)));
    }

    @Test
    public void missingCauseIsHighlighted() {
        List<List<ReportGenerateTools.TextSegment>> paragraphs =
                ReportGenerateTools.causeAnalysisParagraphs(3, "台身混凝土腐蚀", "  ");

        assertEquals("成因分析：", paragraphs.get(1).get(0).text());
        assertEquals(DiseaseCauseMatch.CAUSE_PLACEHOLDER, paragraphs.get(1).get(1).text());
        assertTrue(paragraphs.get(1).get(1).highlight());
    }

    @Test
    public void identicalSuggestionsMergeIntoOneItem() {
        List<List<ReportGenerateTools.TextSegment>> paragraphs = ReportGenerateTools.technicalAdviceParagraphs(Arrays.asList(
                "对裂缝进行表面封闭。",
                "对裂缝进行表面封闭",
                "更换伸缩缝橡胶条",
                null,
                DiseaseCauseMatch.SUGGESTION_PLACEHOLDER), Arrays.asList(
                null, null, null, "空心板剥落", "台身渗水"));

        assertEquals(4, paragraphs.size());
        assertEquals("1）对裂缝进行表面封闭；", text(paragraphs.get(0)));
        assertEquals("2）更换伸缩缝橡胶条；", text(paragraphs.get(1)));
        assertEquals("3）", paragraphs.get(2).get(0).text());
        assertEquals("需补充处治建议（空心板剥落）；", paragraphs.get(2).get(1).text());
        assertTrue(paragraphs.get(2).get(1).highlight());
        assertEquals("4）", paragraphs.get(3).get(0).text());
        assertEquals("需补充处治建议（台身渗水）。", paragraphs.get(3).get(1).text());
        assertTrue(paragraphs.get(3).get(1).highlight());
    }

    @Test
    public void bodyParagraphUsesSongtiSymbolsAndTimesDigits() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("${focus}");
            ReportGenerateTools.writeBodyParagraphs(document, "${focus}",
                    ReportGenerateTools.causeAnalysisParagraphs(1, "T梁剥落", "保护层不足"));

            XWPFParagraph title = document.getParagraphs().get(0);
            assertEquals("（1）T梁剥落", title.getText());
            assertEquals(ParagraphAlignment.BOTH, title.getAlignment());
            assertEquals(BigInteger.valueOf(480), title.getCTP().getPPr().getInd().getFirstLine());
            assertEquals(BigInteger.valueOf(360), title.getCTP().getPPr().getSpacing().getLine());

            boolean digitIsTimes = false;
            boolean symbolIsSongti = false;
            for (XWPFRun run : title.getRuns()) {
                CTFonts fonts = run.getCTR().getRPr().getRFontsArray(0);
                assertEquals(BigInteger.valueOf(24), run.getCTR().getRPr().getSzArray(0).getVal());
                if ("1".equals(run.text())) {
                    assertEquals("Times New Roman", fonts.getAscii());
                    assertEquals("Times New Roman", fonts.getEastAsia());
                    digitIsTimes = true;
                }
                if (run.text().contains("）")) {
                    assertEquals("宋体", fonts.getEastAsia());
                    symbolIsSongti = true;
                }
            }
            assertTrue(digitIsTimes);
            assertTrue(symbolIsSongti);
            assertEquals("成因分析：1）保护层不足。", document.getParagraphs().get(1).getText());
        }
    }

    private static String text(List<ReportGenerateTools.TextSegment> runs) {
        StringBuilder builder = new StringBuilder();
        for (ReportGenerateTools.TextSegment run : runs) {
            builder.append(run.text());
        }
        return builder.toString();
    }
}
