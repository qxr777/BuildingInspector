package edu.whut.cs.bi.biz.utils;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHdrFtrRef;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 按纸张方向保留模板里的横版 / 竖版页眉。
 *
 * <p>Word 页眉跟分节走。模板把 logo 钉在右页边，横版和竖版页宽不同，
 * 所以两套页眉必须分开。生成时如果新建分节却不拷 headerReference，
 * 横版页会落到竖版 logo 位置上。</p>
 */
public final class WordSectionLayoutUtils {

    private static final BigInteger PORTRAIT_WIDTH = BigInteger.valueOf(11906);
    private static final BigInteger PORTRAIT_HEIGHT = BigInteger.valueOf(16838);
    private static final BigInteger LANDSCAPE_WIDTH = BigInteger.valueOf(16838);
    private static final BigInteger LANDSCAPE_HEIGHT = BigInteger.valueOf(11906);

    private WordSectionLayoutUtils() {
    }

    public record Layouts(CTSectPr portrait, CTSectPr landscape) {
        public boolean hasPortraitHeader() {
            return hasDefaultHeader(portrait);
        }

        public boolean hasLandscapeHeader() {
            return hasDefaultHeader(landscape);
        }

        public String portraitDefaultHeaderId() {
            return defaultHeaderId(portrait);
        }

        public String landscapeDefaultHeaderId() {
            return defaultHeaderId(landscape);
        }
    }

    public static Layouts snapshot(XWPFDocument document) {
        CTSectPr portrait = null;
        CTSectPr landscape = null;
        for (CTSectPr sect : listSectPr(document)) {
            if (isTitlePage(sect) || !hasDefaultHeader(sect)) {
                continue;
            }
            if (isLandscape(sect)) {
                if (landscape == null) {
                    landscape = copySectPr(sect);
                }
            } else if (portrait == null) {
                portrait = copySectPr(sect);
            }
        }
        return new Layouts(portrait, landscape);
    }

    /**
     * 横版节一律用横版页眉；已经带了页眉的竖版节改回竖版页眉。
     * 封面、签字表这类本来没有内容页眉的竖版节不改。
     */
    public static void rebindHeadersByOrientation(XWPFDocument document, Layouts layouts) {
        if (document == null || layouts == null) {
            return;
        }
        for (CTSectPr sect : listSectPr(document)) {
            if (isTitlePage(sect)) {
                continue;
            }
            if (isLandscape(sect)) {
                if (layouts.landscape() != null) {
                    copyHeaderFooterRefs(layouts.landscape(), sect);
                }
            } else if (hasAnyHeader(sect) && layouts.portrait() != null) {
                copyHeaderFooterRefs(layouts.portrait(), sect);
            }
        }
    }

    public static void closeCurrentSection(XWPFParagraph paragraph, CTSectPr currentSection) {
        if (paragraph == null) {
            return;
        }
        CTP ctP = paragraph.getCTP();
        CTPPr pPr = ctP.isSetPPr() ? ctP.getPPr() : ctP.addNewPPr();
        if (pPr.isSetSectPr()) {
            pPr.unsetSectPr();
        }
        CTSectPr sectPr = pPr.addNewSectPr();
        copyPageSetup(currentSection, sectPr);
        ensureSectionType(sectPr, STSectionMark.NEXT_PAGE);
    }

    public static XWPFParagraph insertLandscapeSectionEnd(XWPFDocument document, XWPFParagraph after,
                                                          Layouts layouts) {
        XmlCursor cursor = after.getCTP().newCursor();
        cursor.toEndToken();
        cursor.toNextToken();
        XWPFParagraph paragraph = document.insertNewParagraph(cursor);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTSectPr sectPr = pPr.addNewSectPr();
        if (layouts != null && layouts.landscape() != null) {
            copyPageSetup(layouts.landscape(), sectPr);
        } else {
            applyFallbackLandscapePage(sectPr);
        }
        return paragraph;
    }

    public static XWPFParagraph insertPortraitSectionAfter(XWPFDocument document, XWPFParagraph landscapeParagraph,
                                                           Layouts layouts) {
        XmlCursor cursor = landscapeParagraph.getCTP().newCursor();
        cursor.toEndToken();
        cursor.toNextToken();
        XWPFParagraph paragraph = document.insertNewParagraph(cursor);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTSectPr sectPr = pPr.addNewSectPr();
        if (layouts != null && layouts.portrait() != null) {
            copyPageSetup(layouts.portrait(), sectPr);
        } else {
            applyFallbackPortraitPage(sectPr);
        }
        ensureSectionType(sectPr, STSectionMark.CONTINUOUS);
        return paragraph;
    }

    public static CTSectPr findSectionContaining(XWPFDocument document, XWPFParagraph paragraph) {
        if (document == null || paragraph == null) {
            return null;
        }
        int start = document.getPosOfParagraph(paragraph);
        List<IBodyElement> elements = document.getBodyElements();
        if (start < 0) {
            start = 0;
        }
        for (int i = start; i < elements.size(); i++) {
            IBodyElement element = elements.get(i);
            if (!(element instanceof XWPFParagraph)) {
                continue;
            }
            CTPPr pPr = ((XWPFParagraph) element).getCTP().getPPr();
            if (pPr != null && pPr.isSetSectPr()) {
                return pPr.getSectPr();
            }
        }
        CTBody body = document.getDocument().getBody();
        return body.isSetSectPr() ? body.getSectPr() : null;
    }

    public static List<CTSectPr> listSectPr(XWPFDocument document) {
        List<CTSectPr> result = new ArrayList<>();
        if (document == null) {
            return result;
        }
        CTBody body = document.getDocument().getBody();
        for (CTP paragraph : body.getPArray()) {
            if (paragraph.isSetPPr() && paragraph.getPPr().isSetSectPr()) {
                result.add(paragraph.getPPr().getSectPr());
            }
        }
        if (body.isSetSectPr()) {
            result.add(body.getSectPr());
        }
        return result;
    }

    public static boolean isLandscape(CTSectPr sect) {
        if (sect == null || !sect.isSetPgSz()) {
            return false;
        }
        CTPageSz pageSize = sect.getPgSz();
        if (pageSize.getOrient() == STPageOrientation.LANDSCAPE) {
            return true;
        }
        BigInteger width = toBigInteger(pageSize.getW());
        BigInteger height = toBigInteger(pageSize.getH());
        return width != null && height != null && width.compareTo(height) > 0;
    }

    private static BigInteger toBigInteger(Object value) {
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value == null) {
            return null;
        }
        return new BigInteger(value.toString());
    }

    public static boolean hasDefaultHeader(CTSectPr sect) {
        return defaultHeaderId(sect) != null;
    }

    public static String defaultHeaderId(CTSectPr sect) {
        if (sect == null) {
            return null;
        }
        for (int i = 0; i < sect.sizeOfHeaderReferenceArray(); i++) {
            CTHdrFtrRef ref = sect.getHeaderReferenceArray(i);
            if (ref.getType() == STHdrFtr.DEFAULT && ref.getId() != null && !ref.getId().isBlank()) {
                return ref.getId();
            }
        }
        return null;
    }

    static boolean hasAnyHeader(CTSectPr sect) {
        return sect != null && sect.sizeOfHeaderReferenceArray() > 0;
    }

    static boolean isTitlePage(CTSectPr sect) {
        return sect != null && sect.isSetTitlePg();
    }

    static void copyPageSetup(CTSectPr source, CTSectPr target) {
        if (source == null || target == null) {
            return;
        }
        if (source.isSetPgSz()) {
            target.setPgSz((CTPageSz) source.getPgSz().copy());
        }
        if (source.isSetPgMar()) {
            target.setPgMar((CTPageMar) source.getPgMar().copy());
        }
        copyHeaderFooterRefs(source, target);
    }

    static void copyHeaderFooterRefs(CTSectPr source, CTSectPr target) {
        while (target.sizeOfHeaderReferenceArray() > 0) {
            target.removeHeaderReference(0);
        }
        while (target.sizeOfFooterReferenceArray() > 0) {
            target.removeFooterReference(0);
        }
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.sizeOfHeaderReferenceArray(); i++) {
            CTHdrFtrRef src = source.getHeaderReferenceArray(i);
            CTHdrFtrRef dest = target.addNewHeaderReference();
            dest.setType(src.getType());
            dest.setId(src.getId());
        }
        for (int i = 0; i < source.sizeOfFooterReferenceArray(); i++) {
            CTHdrFtrRef src = source.getFooterReferenceArray(i);
            CTHdrFtrRef dest = target.addNewFooterReference();
            dest.setType(src.getType());
            dest.setId(src.getId());
        }
    }

    private static CTSectPr copySectPr(CTSectPr source) {
        return source == null ? null : (CTSectPr) source.copy();
    }

    private static void ensureSectionType(CTSectPr sectPr, STSectionMark.Enum type) {
        if (sectPr.isSetType()) {
            sectPr.getType().setVal(type);
        } else {
            sectPr.addNewType().setVal(type);
        }
    }

    private static void applyFallbackLandscapePage(CTSectPr sectPr) {
        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setOrient(STPageOrientation.LANDSCAPE);
        pageSize.setW(LANDSCAPE_WIDTH);
        pageSize.setH(LANDSCAPE_HEIGHT);
        CTPageMar margin = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margin.setTop(BigInteger.valueOf(1797));
        margin.setBottom(BigInteger.valueOf(1797));
        margin.setLeft(BigInteger.valueOf(1440));
        margin.setRight(BigInteger.valueOf(1440));
    }

    private static void applyFallbackPortraitPage(CTSectPr sectPr) {
        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setOrient(STPageOrientation.PORTRAIT);
        pageSize.setW(PORTRAIT_WIDTH);
        pageSize.setH(PORTRAIT_HEIGHT);
        CTPageMar margin = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margin.setTop(BigInteger.valueOf(1440));
        margin.setBottom(BigInteger.valueOf(1440));
        margin.setLeft(BigInteger.valueOf(1797));
        margin.setRight(BigInteger.valueOf(1797));
    }
}
