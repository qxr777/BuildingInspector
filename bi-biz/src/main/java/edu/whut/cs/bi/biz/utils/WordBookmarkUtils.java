package edu.whut.cs.bi.biz.utils;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 书签名与书签 ID 清理。
 *
 * <p>Word 书签名最多 40 个字符、只能含字母数字下划线，超长或非法的名字会被截断丢弃，REF 域随之报「未找到引用源」。
 * 书签起止靠 ID 配对，ID 重复时删除一处内容会让 Word/WPS 把起止配错，REF 取回大段带图正文，导致更新域错乱或卡死。</p>
 */
public final class WordBookmarkUtils {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final QName BOOKMARK_START = new QName(W_NS, "bookmarkStart");
    private static final QName BOOKMARK_END = new QName(W_NS, "bookmarkEnd");
    private static final QName INSTR_TEXT = new QName(W_NS, "instrText");
    private static final QName FLD_SIMPLE = new QName(W_NS, "fldSimple");
    private static final QName HYPERLINK = new QName(W_NS, "hyperlink");
    private static final QName DOC_PR = new QName(WP_NS, "docPr");
    private static final QName W_ID = new QName(W_NS, "id");
    private static final QName W_NAME = new QName(W_NS, "name");
    private static final QName W_INSTR = new QName(W_NS, "instr");
    private static final QName W_ANCHOR = new QName(W_NS, "anchor");
    private static final QName PLAIN_ID = new QName("", "id");

    private static final int MAX_NAME_LENGTH = 40;
    private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]{0,39}");
    private static final Pattern FIELD_TARGET = Pattern.compile("\\b(REF|PAGEREF|NOTEREF)(\\s+)([^\\s\\\\\"]+)");
    private static final Pattern HYPERLINK_TARGET = Pattern.compile("(\\\\l\\s+\")([^\"]+)(\")");

    private WordBookmarkUtils() {
    }

    /**
     * 收集文档正文已有的书签名。
     */
    public static Set<String> bookmarkNames(XWPFDocument document) {
        Set<String> names = new HashSet<>();
        for (XmlCursor cursor : elements(document.getDocument(), BOOKMARK_START)) {
            String name = cursor.getAttributeText(W_NAME);
            if (name != null) {
                names.add(name);
            }
            cursor.dispose();
        }
        return names;
    }

    /**
     * 把这些正文元素中定义的书签改成合法且不与 taken 冲突的名字，并同步改写元素内部的 REF、PAGEREF、超链接锚点。
     * 用于把一座桥的章节并入总报告前，避免各桥同名书签互相覆盖。
     */
    public static void renameBookmarks(List<IBodyElement> elements, Set<String> taken) {
        List<XmlObject> roots = new ArrayList<>();
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph) {
                roots.add(((XWPFParagraph) element).getCTP());
            } else if (element instanceof XWPFTable) {
                roots.add(((XWPFTable) element).getCTTbl());
            }
        }
        renameBookmarksIn(roots, taken);
    }

    /**
     * 导出前整理整份文档：书签名合法、书签 ID 唯一且起止成对、图片 docPr ID 唯一。
     */
    public static void normalize(XWPFDocument document) {
        List<XmlObject> parts = new ArrayList<>();
        parts.add(document.getDocument());
        for (XWPFHeader header : document.getHeaderList()) {
            parts.add(header._getHdrFtr());
        }
        for (XWPFFooter footer : document.getFooterList()) {
            parts.add(footer._getHdrFtr());
        }
        renameBookmarksIn(List.of(document.getDocument()), new HashSet<>());
        renumberBookmarkIds(parts);
        renumberDrawingIds(parts);
    }

    private static void renameBookmarksIn(List<XmlObject> roots, Set<String> taken) {
        Map<String, String> renamed = new HashMap<>();
        for (XmlObject root : roots) {
            for (XmlCursor cursor : elements(root, BOOKMARK_START)) {
                String name = cursor.getAttributeText(W_NAME);
                if (name == null) {
                    cursor.dispose();
                    continue;
                }
                if (VALID_NAME.matcher(name).matches() && taken.add(name)) {
                    cursor.dispose();
                    continue;
                }
                String newName = uniqueName(name, taken);
                cursor.setAttributeText(W_NAME, newName);
                cursor.dispose();
                // 同一作用域内重名时，引用仍指向第一处；只有第一处被改名才需要改引用。
                renamed.putIfAbsent(name, newName);
            }
        }
        if (renamed.isEmpty()) {
            return;
        }
        for (XmlObject root : roots) {
            for (XmlCursor cursor : elements(root, INSTR_TEXT)) {
                String text = cursor.getTextValue();
                String updated = rewriteInstruction(text, renamed);
                if (!updated.equals(text)) {
                    cursor.setTextValue(updated);
                }
                cursor.dispose();
            }
            for (XmlCursor cursor : elements(root, FLD_SIMPLE)) {
                String instr = cursor.getAttributeText(W_INSTR);
                if (instr != null) {
                    String updated = rewriteInstruction(instr, renamed);
                    if (!updated.equals(instr)) {
                        cursor.setAttributeText(W_INSTR, updated);
                    }
                }
                cursor.dispose();
            }
            for (XmlCursor cursor : elements(root, HYPERLINK)) {
                String anchor = cursor.getAttributeText(W_ANCHOR);
                if (anchor != null && renamed.containsKey(anchor)) {
                    cursor.setAttributeText(W_ANCHOR, renamed.get(anchor));
                }
                cursor.dispose();
            }
        }
    }

    private static String rewriteInstruction(String instruction, Map<String, String> renamed) {
        if (instruction == null || instruction.isEmpty()) {
            return instruction == null ? "" : instruction;
        }
        String result = replaceGroup(FIELD_TARGET, instruction, 3, renamed);
        return replaceGroup(HYPERLINK_TARGET, result, 2, renamed);
    }

    private static String replaceGroup(Pattern pattern, String text, int group, Map<String, String> renamed) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String target = matcher.group(group);
            String replacement = renamed.get(target);
            if (replacement == null) {
                continue;
            }
            out.append(text, last, matcher.start(group)).append(replacement);
            last = matcher.end(group);
        }
        return last == 0 ? text : out.append(text.substring(last)).toString();
    }

    private static String uniqueName(String original, Set<String> taken) {
        String base = original.replaceAll("[^\\p{L}\\p{N}_]", "");
        if (base.isEmpty() || !(Character.isLetter(base.charAt(0)) || base.charAt(0) == '_')) {
            base = "BM_" + base;
        }
        for (int i = 1; ; i++) {
            String suffix = "_" + i;
            String head = base.length() + suffix.length() > MAX_NAME_LENGTH
                    ? base.substring(0, MAX_NAME_LENGTH - suffix.length())
                    : base;
            String candidate = head + suffix;
            if (taken.add(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * 按文档顺序重排书签 ID；同一旧 ID 有多处时，终点与最近一个未闭合的同 ID 起点配对。
     * 找不到起点的终点删除；没有终点的起点（模板书签的终点被移到了起点前面）就地补一个终点。
     */
    private static void renumberBookmarkIds(List<XmlObject> parts) {
        long nextId = 1;
        List<XmlCursor> orphanEnds = new ArrayList<>();
        List<XmlCursor> unclosedStarts = new ArrayList<>();
        for (XmlObject part : parts) {
            Map<String, Deque<XmlCursor>> open = new HashMap<>();
            XmlCursor cursor = part.newCursor();
            XmlCursor end = part.newCursor();
            try {
                end.toEndToken();
                while (!cursor.toNextToken().isNone() && cursor.comparePosition(end) < 0) {
                    if (!cursor.isStart()) {
                        continue;
                    }
                    QName name = cursor.getName();
                    if (BOOKMARK_START.equals(name)) {
                        String oldId = String.valueOf(cursor.getAttributeText(W_ID));
                        cursor.setAttributeText(W_ID, String.valueOf(nextId++));
                        open.computeIfAbsent(oldId, k -> new ArrayDeque<>()).push(cursor.newCursor());
                    } else if (BOOKMARK_END.equals(name)) {
                        Deque<XmlCursor> starts = open.get(String.valueOf(cursor.getAttributeText(W_ID)));
                        if (starts == null || starts.isEmpty()) {
                            orphanEnds.add(cursor.newCursor());
                        } else {
                            XmlCursor start = starts.pop();
                            cursor.setAttributeText(W_ID, start.getAttributeText(W_ID));
                            start.dispose();
                        }
                    }
                }
            } finally {
                cursor.dispose();
                end.dispose();
            }
            open.values().forEach(unclosedStarts::addAll);
        }
        for (XmlCursor orphan : orphanEnds) {
            orphan.removeXml();
            orphan.dispose();
        }
        for (XmlCursor start : unclosedStarts) {
            String id = start.getAttributeText(W_ID);
            start.toEndToken();
            start.toNextToken();
            start.beginElement(BOOKMARK_END);
            start.insertAttributeWithValue(W_ID, id);
            start.dispose();
        }
    }

    private static void renumberDrawingIds(List<XmlObject> parts) {
        long nextId = 1;
        for (XmlObject part : parts) {
            for (XmlCursor cursor : elements(part, DOC_PR)) {
                cursor.setAttributeText(PLAIN_ID, String.valueOf(nextId++));
                cursor.dispose();
            }
        }
    }

    /** 返回 root（含自身）下所有指定元素的游标，调用方负责 dispose。 */
    private static List<XmlCursor> elements(XmlObject root, QName elementName) {
        List<XmlCursor> result = new ArrayList<>();
        XmlCursor cursor = root.newCursor();
        XmlCursor end = root.newCursor();
        try {
            end.toEndToken();
            do {
                if (cursor.isStart() && elementName.equals(cursor.getName())) {
                    result.add(cursor.newCursor());
                }
            } while (!cursor.toNextToken().isNone() && cursor.comparePosition(end) < 0);
        } finally {
            cursor.dispose();
            end.dispose();
        }
        return result;
    }
}
