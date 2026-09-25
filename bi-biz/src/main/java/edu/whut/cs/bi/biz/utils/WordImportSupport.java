package edu.whut.cs.bi.biz.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.domain.ReportData;
import lombok.Data;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import java.util.*;

/** Type 2 values reference an immutable source document, never HTML or plain-text replacements. */
public final class WordImportSupport {
    public static final int TYPE = 2;
    public static final Map<String, String> FIELDS = Map.of(
            "line-route-overview", "项目概况", "line-component-numbering-rules", "构件编号规则",
            "overallOverview", "整体概况", "designPoints", "设计要点", "mainMaterial", "主要材料",
            "technicalAdvice", "技术建议");
    private static final ObjectMapper JSON = new ObjectMapper();
    private WordImportSupport() { }

    @Data
    public static class Reference {
        private Long fileId;
        private int start;
        private int end;
        private String sourceName;
        private String sourcePath;
        private String preview;
    }

    public static boolean isImported(ReportData data) {
        return data != null && Integer.valueOf(TYPE).equals(data.getType()) && FIELDS.containsKey(data.getKey());
    }

    public static String version(ReportData row) {
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(row == null ? "absent"
                : row.getId() + ":" + row.getType() + ":" + row.getValue());
    }

    public static String scopeKey(String groupId, String key) {
        return Objects.toString(groupId, "") + "\u0000" + key;
    }

    public static Reference reference(ReportData data) throws Exception {
        Reference ref = JSON.readValue(data.getValue(), Reference.class);
        if (ref.getFileId() == null || ref.getStart() < 0 || ref.getEnd() <= ref.getStart())
            throw new IllegalArgumentException("Word 导入记录无效：" + data.getKey());
        return ref;
    }

    public static String marker(ReportData data) {
        if (data.getId() == null) throw new IllegalArgumentException("Word 导入记录尚未保存");
        return "__BI_WORD_IMPORT_" + data.getId() + "__";
    }

    /** Replace the existing field region, including overview photo slots, with one late-merge anchor. */
    public static void prepare(XWPFDocument document, ReportData data) {
        String label = FIELDS.get(data.getKey());
        List<IBodyElement> elements = document.getBodyElements();
        XWPFParagraph heading = null;
        for (XWPFParagraph p : document.getParagraphs()) {
            if (label.equals(p.getText().trim()) && headingLevel(document, p) > 0) { heading = p; break; }
        }
        if (heading != null) {
            int begin = document.getPosOfParagraph(heading) + 1;
            int end = begin;
            int level = headingLevel(document, heading);
            while (end < elements.size()) {
                IBodyElement element = elements.get(end);
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph p = (XWPFParagraph) element;
                    int next = headingLevel(document, p);
                    if ((next > 0 && next <= level) || p.getText().contains("${line.bridge-block-end}")) break;
                }
                end++;
            }
            if (end == elements.size()) throw new IllegalStateException("无法确定导入章节结束位置：" + label);
            // A section boundary also controls earlier paragraphs outside this
            // field (e.g. comparison analysis before technical advice). Preserve
            // the final boundary independently of the replaceable merge marker.
            boolean boundaryKept = false;
            for (int i = end - 1; i >= begin; i--) {
                IBodyElement element = document.getBodyElements().get(i);
                if (!boundaryKept && element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr()) {
                        var boundary = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP.Factory.newInstance();
                        boundary.addNewPPr().setSectPr(paragraph.getCTP().getPPr().getSectPr());
                        for (int run = paragraph.getRuns().size() - 1; run >= 0; run--) paragraph.removeRun(run);
                        paragraph.getCTP().setPPr(boundary.getPPr());
                        boundaryKept = true;
                        continue;
                    }
                }
                document.removeBodyElement(i);
            }
            IBodyElement next = document.getBodyElements().get(begin);
            try (XmlCursor cursor = next instanceof XWPFParagraph ? ((XWPFParagraph) next).getCTP().newCursor()
                    : ((XWPFTable) next).getCTTbl().newCursor()) {
                document.insertNewParagraph(cursor).createRun().setText(marker(data));
            }
            return;
        }
        XWPFParagraph p = ReportGenerateTools.findParagraphByPlaceholder(document, "${" + data.getKey() + "}");
        if (p == null) throw new IllegalStateException("模板缺少导入位置：" + label);
        for (int i = p.getRuns().size() - 1; i >= 0; i--) p.removeRun(i);
        p.createRun().setText(marker(data));
    }

    private static int headingLevel(XWPFDocument doc, XWPFParagraph p) {
        if (p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetOutlineLvl()) {
            int value = p.getCTP().getPPr().getOutlineLvl().getVal().intValue();
            return value < 9 ? value + 1 : -1;
        }
        XWPFStyle style = doc.getStyles() == null ? null : doc.getStyles().getStyle(p.getStyle());
        Set<String> visited = new HashSet<>();
        while (style != null && visited.add(style.getStyleId())) {
            if (style.getCTStyle().isSetPPr() && style.getCTStyle().getPPr().isSetOutlineLvl()) {
                int value = style.getCTStyle().getPPr().getOutlineLvl().getVal().intValue();
                return value < 9 ? value + 1 : -1;
            }
            String name = Objects.toString(style.getName(), "");
            if (name.matches("(?i)heading [1-9]")) return Integer.parseInt(name.substring(8));
            style = style.getBasisStyleID() == null ? null : doc.getStyles().getStyle(style.getBasisStyleID());
        }
        return -1;
    }
}
