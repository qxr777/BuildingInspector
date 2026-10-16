package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.vo.DiseaseCauseMatch;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class ReportGenerateToolsSegmentsTest {

    @Test
    void missingSuggestionIsRedAndKeepsPlaceholderFont() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("宋体");
        run.setText("${focusOnDiseases}");

        DiseaseCauseMatch match = new DiseaseCauseMatch();
        match.setCause("混凝土收缩。");
        List<ReportGenerateTools.TextSegment> segments = ReportGenerateTools.causeMatchSegments(match);
        ReportGenerateTools.replaceTextWithSegments(document, "${focusOnDiseases}", segments);

        assertEquals("成因分析：\n混凝土收缩。\n处治建议：\n" + DiseaseCauseMatch.SUGGESTION_PLACEHOLDER,
                paragraph.getText());
        XWPFRun cause = findRun(paragraph, "混凝土收缩。");
        XWPFRun placeholder = findRun(paragraph, DiseaseCauseMatch.SUGGESTION_PLACEHOLDER);
        assertNull(cause.getColor());
        assertEquals("FF0000", placeholder.getColor());
        assertEquals("宋体", placeholder.getFontFamily());
    }

    private static XWPFRun findRun(XWPFParagraph paragraph, String text) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (text.equals(run.getText(0))) {
                return run;
            }
        }
        fail("未找到 run: " + text);
        return null;
    }
}
