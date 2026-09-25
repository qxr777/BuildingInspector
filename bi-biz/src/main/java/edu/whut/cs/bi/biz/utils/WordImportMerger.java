package edu.whut.cs.bi.biz.utils;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Late OOXML merge: 正文对齐目标样式集，段落直接格式仍用来源报告。 */
public final class WordImportMerger {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    public record Item(String marker, Path source, int start, int end) { }
    private WordImportMerger() { }

    public static void merge(Path report, List<Item> items) throws Exception {
        if (items.isEmpty()) return;
        Path output = Files.createTempFile(report.getParent(), "word-import-", ".docx");
        try {
            try (ZipFile target = new ZipFile(report.toFile())) {
                Document body = read(target, "word/document.xml");
                Document styles = read(target, "word/styles.xml");
                Document rels = read(target, "word/_rels/document.xml.rels");
                Document types = read(target, "[Content_Types].xml");
                Document numbering = target.getEntry("word/numbering.xml") == null ? empty(W, "w:numbering") : read(target, "word/numbering.xml");
                Map<String, byte[]> added = new LinkedHashMap<>();
                for (Item item : items) {
                    try (ZipFile source = new ZipFile(item.source().toFile())) {
                        insert(body, styles, numbering, rels, types, added, source, item);
                    }
                }
                if (body.getDocumentElement().getTextContent().contains("__BI_WORD_IMPORT_"))
                    throw new IOException("报告仍有未填充的 Word 导入位置");
                if (!nodes(rels, REL, "Relationship").stream().anyMatch(e -> e.getAttribute("Type").equals(R + "/numbering")))
                    relationship(rels, "wi_numbering_" + UUID.randomUUID(), R + "/numbering", "numbering.xml", false);
                contentType(types, "/word/numbering.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml");
                added.put("word/document.xml", bytes(body));
                added.put("word/styles.xml", bytes(styles));
                added.put("word/numbering.xml", bytes(numbering));
                added.put("word/_rels/document.xml.rels", bytes(rels));
                added.put("[Content_Types].xml", bytes(types));
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                    Enumeration<? extends ZipEntry> entries = target.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (added.containsKey(entry.getName())) continue;
                        zip.putNextEntry(new ZipEntry(entry.getName()));
                        try (InputStream input = target.getInputStream(entry)) { input.transferTo(zip); }
                        zip.closeEntry();
                    }
                    for (Map.Entry<String, byte[]> entry : added.entrySet()) {
                        zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
                    }
                }
            }
            Files.move(output, report, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); }
    }

    private static void insert(Document target, Document styles, Document numbering, Document rels, Document types,
                               Map<String, byte[]> added, ZipFile source, Item item) throws Exception {
        Element targetBody = nodes(target, W, "body").get(0);
        List<Element> anchors = children(targetBody).stream()
                .filter(e -> "p".equals(e.getLocalName()) && item.marker().equals(e.getTextContent().trim())).toList();
        if (anchors.size() != 1) throw new IOException("Word 导入位置缺失或重复：" + item.marker());
        Element anchor = anchors.get(0);
        Document sourceDoc = read(source, "word/document.xml");
        List<Element> all = children(nodes(sourceDoc, W, "body").get(0)).stream()
                .filter(e -> W.equals(e.getNamespaceURI())).toList();
        if (item.start() < 0 || item.end() > all.size() || item.start() >= item.end()) throw new IOException("Word 内容范围无效");
        List<Element> fragments = new ArrayList<>();
        for (Element element : all.subList(item.start(), item.end())) {
            if ("sectPr".equals(element.getLocalName())) continue;
            if (!Set.of("p", "tbl", "bookmarkStart", "bookmarkEnd", "proofErr").contains(element.getLocalName()))
                throw new IOException("此章节含暂不支持的 Word 内容：" + element.getLocalName());
            Element copy = (Element) target.importNode(element, true);
            remove(copy, "sectPr"); // report owns page layout, headers and footers
            if (!nodes(copy, W, "footnoteReference").isEmpty() || !nodes(copy, W, "endnoteReference").isEmpty())
                throw new IOException("章节包含脚注或尾注，请先将其转为正文后导入");
            remove(copy, "commentRangeStart"); remove(copy, "commentRangeEnd"); remove(copy, "commentReference");
            // A section's fields may reference source headings outside the copied range. Keep the
            // already displayed runs (including their formatting) instead of recalculating in a new report.
            remove(copy, "instrText"); remove(copy, "fldChar");
            for (Element field : nodes(copy, W, "fldSimple")) {
                Node parent = field.getParentNode();
                while (field.getFirstChild() != null) parent.insertBefore(field.getFirstChild(), field);
                parent.removeChild(field);
            }
            fragments.add(copy);
        }
        String prefix = "WI" + UUID.randomUUID().toString().replace("-", "") + "_";
        Document sourceStyles = source.getEntry("word/styles.xml") == null ? empty(W, "w:styles") : read(source, "word/styles.xml");
        String targetBodyId = bodyStyleId(styles);
        Map<Element, String> bodyParagraphStyles = new IdentityHashMap<>();
        for (Element fragment : fragments) {
            for (Element paragraph : including(fragment, W, "p")) {
                String styleId = val(first(first(paragraph, "pPr"), "pStyle"));
                if (isSourceBodyStyle(sourceStyles, styleId)) bodyParagraphStyles.put(paragraph, styleId);
            }
        }
        Map<String, String> styleIds = new HashMap<>();
        List<Element> copiedStyles = new ArrayList<>();
        for (Element original : nodes(sourceStyles, W, "style")) {
            String id = original.getAttributeNS(W, "styleId");
            if (isBodyParagraphStyle(original)) {
                styleIds.put(id, targetBodyId);
                continue;
            }
            styleIds.put(id, prefix + id);
            Element copy = (Element) styles.importNode(original, true);
            copy.setAttributeNS(W, "w:styleId", prefix + id); copy.removeAttributeNS(W, "default");
            Element styleName = first(copy, "name");
            if (styleName != null) styleName.setAttributeNS(W, "w:val", prefix + styleName.getAttributeNS(W, "val"));
            copiedStyles.add(copy);
        }

        Map<String, String> numIds = new HashMap<>();
        List<Element> copiedNumbering = new ArrayList<>();
        if (source.getEntry("word/numbering.xml") != null) {
            Document sourceNumbering = read(source, "word/numbering.xml");
            Map<String, String> abstractIds = new HashMap<>();
            long nextAbstract = nextId(numbering, "abstractNum", "abstractNumId");
            long nextNum = Math.max(1, nextId(numbering, "num", "numId"));
            for (Element original : nodes(sourceNumbering, W, "abstractNum")) {
                Element copy = (Element) numbering.importNode(original, true);
                String id = String.valueOf(nextAbstract++);
                abstractIds.put(original.getAttributeNS(W, "abstractNumId"), id);
                copy.setAttributeNS(W, "w:abstractNumId", id); copiedNumbering.add(copy);
            }
            for (Element original : nodes(sourceNumbering, W, "num")) {
                Element copy = (Element) numbering.importNode(original, true);
                String id = String.valueOf(nextNum++);
                numIds.put(original.getAttributeNS(W, "numId"), id);
                copy.setAttributeNS(W, "w:numId", id);
                Element abstractId = first(copy, "abstractNumId");
                if (abstractId != null) abstractId.setAttributeNS(W, "w:val", abstractIds.get(abstractId.getAttributeNS(W, "val")));
                copiedNumbering.add(copy);
            }
        }
        for (Element style : copiedStyles) {
            remap(style, styleIds, numIds);
            if ("paragraph".equals(style.getAttributeNS(W, "type")) && first(style, "basedOn") == null) {
                Element based = styles.createElementNS(W, "w:basedOn"); based.setAttributeNS(W, "w:val", targetBodyId);
                Element before = children(style).stream().filter(e -> !Set.of("name", "aliases").contains(e.getLocalName())).findFirst().orElse(null);
                style.insertBefore(based, before);
            }
        }
        for (Element node : copiedNumbering) remap(node, styleIds, numIds);
        for (Element fragment : fragments) remap(fragment, styleIds, numIds);
        flattenBodyFormatting(target, sourceStyles, bodyParagraphStyles, targetBodyId);
        List<Element> allCopies = new ArrayList<>(fragments); allCopies.addAll(copiedStyles); allCopies.addAll(copiedNumbering);
        if (source.getEntry("word/theme/theme1.xml") != null) {
            Document theme = read(source, "word/theme/theme1.xml");
            for (Element copy : allCopies) for (Element fonts : nodes(copy, W, "rFonts")) {
                for (String attr : List.of("ascii", "hAnsi", "eastAsia", "cs")) {
                    String themeAttr = attr.equals("cs") ? "cstheme" : attr + "Theme";
                    String value = fonts.getAttributeNS(W, themeAttr);
                    if (value.isEmpty()) continue;
                    List<Element> families = nodes(theme, A, value.startsWith("major") ? "majorFont" : "minorFont");
                    if (families.isEmpty()) continue;
                    String kind = attr.equals("eastAsia") ? "ea" : attr.equals("cs") ? "cs" : "latin";
                    String face = nodes(families.get(0), A, kind).stream().map(e -> e.getAttribute("typeface")).filter(s -> !s.isEmpty()).findFirst().orElse("");
                    if (face.isEmpty() && attr.equals("eastAsia")) face = nodes(families.get(0), A, "font").stream()
                            .filter(e -> "Hans".equals(e.getAttribute("script"))).map(e -> e.getAttribute("typeface")).findFirst().orElse("");
                    if (!face.isEmpty()) { fonts.setAttributeNS(W, "w:" + attr, face); fonts.removeAttributeNS(W, themeAttr); }
                }
            }
        }
        Map<String, Element> sourceRels = new HashMap<>();
        if (source.getEntry("word/_rels/document.xml.rels") != null)
            for (Element rel : nodes(read(source, "word/_rels/document.xml.rels"), REL, "Relationship")) sourceRels.put(rel.getAttribute("Id"), rel);
        Document sourceTypes = read(source, "[Content_Types].xml");
        Map<String, String> relMap = new HashMap<>();
        for (Element copy : allCopies) {
            // Keep source namespace aliases for VML, grouped drawings and mc:Ignorable values.
            NamedNodeMap namespaces = sourceDoc.getDocumentElement().getAttributes();
            for (int i = 0; i < namespaces.getLength(); i++) {
                Node attr = namespaces.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) copy.setAttributeNS(attr.getNamespaceURI(), attr.getNodeName(), attr.getNodeValue());
            }
            for (Element element : includingAll(copy)) {
                NamedNodeMap attrs = element.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    Node attr = attrs.item(i);
                    if (!R.equals(attr.getNamespaceURI())) continue;
                    String old = attr.getNodeValue();
                    if (!relMap.containsKey(old)) {
                        Element relationship = sourceRels.get(old);
                        if (relationship == null) throw new IOException("来源图片或链接关系缺失：" + old);
                        String type = relationship.getAttribute("Type");
                        boolean external = "External".equals(relationship.getAttribute("TargetMode"));
                        String destination = relationship.getAttribute("Target");
                        if (!type.equals(R + "/image") && !type.equals(R + "/hyperlink")) throw new IOException("此章节包含暂不支持的嵌入对象：" + type);
                        if (external && type.equals(R + "/image")) throw new IOException("请先将外部链接图片嵌入 Word 后重新上传");
                        if (!external) {
                            String path = Path.of("word").resolve(destination).normalize().toString().replace('\\', '/');
                            ZipEntry image = source.getEntry(path);
                            if (image == null || image.getSize() > 50L * 1024 * 1024) throw new IOException("图片缺失或超过大小限制：" + path);
                            String extension = path.substring(path.lastIndexOf('.') + 1);
                            String imageName = "word/media/" + prefix + relMap.size() + "." + extension;
                            try (InputStream in = source.getInputStream(image)) { added.put(imageName, in.readAllBytes()); }
                            destination = imageName.substring(5);
                            contentType(types, "/" + imageName, sourceContentType(sourceTypes, path));
                        }
                        String id = prefix + "r" + relMap.size();
                        relationship(rels, id, type, destination, external); relMap.put(old, id);
                    }
                    attr.setNodeValue(relMap.get(old));
                }
            }
        }
        // Drawing and bookmark IDs must be unique across repeated source chapters.
        long drawingId = Math.max(nextId(target, "docPr", "id"), 100000);
        long bookmarkId = Math.max(nextId(target, "bookmarkStart", "id"), 100000);
        Map<String, String> bookmarkIds = new HashMap<>();
        for (Element fragment : fragments) for (Element e : includingAll(fragment)) {
            if ("docPr".equals(e.getLocalName())) e.setAttribute("id", String.valueOf(drawingId++));
            if (W.equals(e.getNamespaceURI()) && Set.of("bookmarkStart", "bookmarkEnd").contains(e.getLocalName())) {
                String old = e.getAttributeNS(W, "id");
                if (!bookmarkIds.containsKey(old)) bookmarkIds.put(old, String.valueOf(bookmarkId++));
                e.setAttributeNS(W, "w:id", bookmarkIds.get(old));
                if (e.hasAttributeNS(W, "name")) e.setAttributeNS(W, "w:name", prefix + e.getAttributeNS(W, "name"));
            }
            if (W.equals(e.getNamespaceURI()) && "hyperlink".equals(e.getLocalName()) && e.hasAttributeNS(W, "anchor"))
                e.setAttributeNS(W, "w:anchor", prefix + e.getAttributeNS(W, "anchor"));
        }
        for (Element style : copiedStyles) styles.getDocumentElement().appendChild(style);
        // Abstract numbering definitions precede concrete numbering instances.
        for (Element num : copiedNumbering) {
            Element firstNum = nodes(numbering, W, "num").stream().findFirst().orElse(null);
            numbering.getDocumentElement().insertBefore(num, "abstractNum".equals(num.getLocalName()) ? firstNum : null);
        }
        for (Element fragment : fragments) targetBody.insertBefore(fragment, anchor);
        targetBody.removeChild(anchor);
    }

    /**
     * 来源「正文」对上目标样式集的正文，但把来源正文的有效格式写成直接格式，避免变成 WI_ 独立样式。
     */
    private static void flattenBodyFormatting(Document target, Document sourceStyles,
                                              Map<Element, String> bodyParagraphStyles, String targetBodyId) {
        Set<String> skipParagraph = Set.of("pStyle", "rStyle", "numPr", "outlineLvl", "sectPr");
        Set<String> skipRun = Set.of("rStyle");
        for (Map.Entry<Element, String> entry : bodyParagraphStyles.entrySet()) {
            Element paragraph = entry.getKey();
            Element resolvedParagraph = target.createElementNS(W, "w:pPr");
            Element resolvedRun = target.createElementNS(W, "w:rPr");
            collectResolvedBodyProps(sourceStyles, entry.getValue(), resolvedParagraph, resolvedRun, skipParagraph, skipRun);
            Element props = first(paragraph, "pPr");
            if (props == null) {
                props = target.createElementNS(W, "w:pPr");
                paragraph.insertBefore(props, paragraph.getFirstChild());
            }
            fillAbsent(props, resolvedParagraph, skipParagraph);
            Element mark = first(props, "rPr");
            if (mark == null) {
                mark = target.createElementNS(W, "w:rPr");
                props.appendChild(mark);
            }
            fillAbsent(mark, resolvedRun, skipRun);
            for (Element run : nodes(paragraph, W, "r")) {
                Element runProps = first(run, "rPr");
                if (runProps == null) {
                    runProps = target.createElementNS(W, "w:rPr");
                    run.insertBefore(runProps, run.getFirstChild());
                }
                fillAbsent(runProps, resolvedRun, skipRun);
            }
            setNamedVal(props, target, "pStyle", targetBodyId);
        }
    }

    private static void collectResolvedBodyProps(Document sourceStyles, String styleId, Element paragraphProps,
                                                 Element runProps, Set<String> skipParagraph, Set<String> skipRun) {
        Element defaults = first(sourceStyles.getDocumentElement(), "docDefaults");
        overlay(paragraphProps, first(first(defaults, "pPrDefault"), "pPr"), skipParagraph);
        overlay(runProps, first(first(defaults, "rPrDefault"), "rPr"), skipRun);
        for (Element style : bodyStyleChain(sourceStyles, styleId)) {
            overlay(paragraphProps, first(style, "pPr"), skipParagraph);
            overlay(runProps, first(first(style, "pPr"), "rPr"), skipRun);
            overlay(runProps, first(style, "rPr"), skipRun);
        }
    }

    private static List<Element> bodyStyleChain(Document styles, String startId) {
        LinkedList<Element> chain = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        String id = startId == null || startId.isEmpty() ? bodyStyleId(styles) : startId;
        while (!id.isEmpty() && seen.add(id)) {
            Element style = findStyle(styles, id);
            if (style == null) break;
            chain.addFirst(style);
            id = val(first(style, "basedOn"));
        }
        return chain;
    }

    private static boolean isSourceBodyStyle(Document sourceStyles, String styleId) {
        if (styleId == null || styleId.isEmpty()) return true;
        return isBodyParagraphStyle(findStyle(sourceStyles, styleId));
    }

    private static boolean isBodyParagraphStyle(Element style) {
        if (style == null || !"paragraph".equals(style.getAttributeNS(W, "type"))) return false;
        if (Set.of("1", "true", "on").contains(style.getAttributeNS(W, "default"))) return true;
        String name = val(first(style, "name")).trim();
        return "正文".equals(name) || "normal".equals(name.toLowerCase(Locale.ROOT));
    }

    private static String bodyStyleId(Document styles) {
        String named = null;
        for (Element style : nodes(styles, W, "style")) {
            if (!"paragraph".equals(style.getAttributeNS(W, "type"))) continue;
            if (Set.of("1", "true", "on").contains(style.getAttributeNS(W, "default")))
                return style.getAttributeNS(W, "styleId");
            if (named == null && isBodyParagraphStyle(style)) named = style.getAttributeNS(W, "styleId");
        }
        return named != null ? named : "1";
    }

    private static Element findStyle(Document styles, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Element style : nodes(styles, W, "style"))
            if (id.equals(style.getAttributeNS(W, "styleId"))) return style;
        return null;
    }

    private static void setNamedVal(Element parent, Document doc, String name, String value) {
        Element existing = first(parent, name);
        if (existing != null) parent.removeChild(existing);
        Element element = doc.createElementNS(W, "w:" + name);
        element.setAttributeNS(W, "w:val", value);
        parent.insertBefore(element, parent.getFirstChild());
    }

    private static void overlay(Element dest, Element src, Set<String> skip) {
        copyProps(dest, src, skip, true);
    }

    private static void fillAbsent(Element dest, Element src, Set<String> skip) {
        copyProps(dest, src, skip, false);
    }

    private static void copyProps(Element dest, Element src, Set<String> skip, boolean overwrite) {
        if (dest == null || src == null) return;
        // A font name and its theme reference are one cascading property. Do not
        // leave a lower-priority theme reference beside a higher-priority font.
        Set<String> blockedFonts = new HashSet<>();
        if ("rFonts".equals(src.getLocalName())) {
            for (String font : List.of("ascii", "hAnsi", "eastAsia", "cs")) {
                String theme = font.equals("cs") ? "cstheme" : font + "Theme";
                boolean sourceHasFont = src.hasAttributeNS(W, font) || src.hasAttributeNS(W, theme);
                boolean targetHasFont = dest.hasAttributeNS(W, font) || dest.hasAttributeNS(W, theme);
                if (overwrite && sourceHasFont) {
                    dest.removeAttributeNS(W, font);
                    dest.removeAttributeNS(W, theme);
                } else if (!overwrite && targetHasFont) {
                    blockedFonts.add(font);
                    blockedFonts.add(theme);
                }
            }
        }
        NamedNodeMap attrs = src.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String ns = attr.getNamespaceURI();
            String local = attr.getLocalName();
            if (W.equals(ns) && blockedFonts.contains(local)) continue;
            if (!overwrite && dest.hasAttributeNS(ns, local) && !dest.getAttributeNS(ns, local).isEmpty()) continue;
            dest.setAttributeNS(ns, attr.getNodeName(), attr.getNodeValue());
        }
        for (Element child : children(src)) {
            if (!W.equals(child.getNamespaceURI()) || skip.contains(child.getLocalName())) continue;
            Element existing = first(dest, child.getLocalName());
            if (existing == null) dest.appendChild(dest.getOwnerDocument().importNode(child, true));
            else copyProps(existing, child, Set.of(), overwrite);
        }
    }

    private static String val(Element element) {
        return element == null ? "" : element.getAttributeNS(W, "val");
    }

    private static void remap(Element root, Map<String, String> styles, Map<String, String> nums) {
        for (Element e : includingAll(root)) {
            if (!W.equals(e.getNamespaceURI())) continue;
            String old = e.getAttributeNS(W, "val");
            if (Set.of("pStyle", "rStyle", "tblStyle", "basedOn", "next", "link", "styleLink", "numStyleLink").contains(e.getLocalName()) && styles.containsKey(old))
                e.setAttributeNS(W, "w:val", styles.get(old));
            if ("numId".equals(e.getLocalName()) && nums.containsKey(old)) e.setAttributeNS(W, "w:val", nums.get(old));
        }
    }
    private static long nextId(Document doc, String tag, String attr) {
        long max = 0;
        NodeList list = doc.getElementsByTagNameNS("*", tag);
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            try { max = Math.max(max, Long.parseLong(e.hasAttributeNS(W, attr) ? e.getAttributeNS(W, attr) : e.getAttribute(attr))); }
            catch (NumberFormatException ignored) { }
        }
        return max + 1;
    }
    private static String sourceContentType(Document doc, String path) throws IOException {
        for (Element e : nodes(doc, CT, "Override")) if (("/" + path).equals(e.getAttribute("PartName"))) return e.getAttribute("ContentType");
        String extension = path.substring(path.lastIndexOf('.') + 1);
        for (Element e : nodes(doc, CT, "Default")) if (extension.equalsIgnoreCase(e.getAttribute("Extension"))) return e.getAttribute("ContentType");
        throw new IOException("无法确定图片类型：" + path);
    }
    private static void contentType(Document doc, String path, String type) {
        if (nodes(doc, CT, "Override").stream().anyMatch(e -> path.equals(e.getAttribute("PartName")))) return;
        Element e = doc.createElementNS(CT, "Override"); e.setAttribute("PartName", path); e.setAttribute("ContentType", type); doc.getDocumentElement().appendChild(e);
    }
    private static void relationship(Document doc, String id, String type, String target, boolean external) {
        Element e = doc.createElementNS(REL, "Relationship"); e.setAttribute("Id", id); e.setAttribute("Type", type); e.setAttribute("Target", target);
        if (external) e.setAttribute("TargetMode", "External"); doc.getDocumentElement().appendChild(e);
    }
    private static Document read(ZipFile zip, String name) throws IOException { return LineReportWordMatcher.readXml(zip, name); }
    private static Document empty(String ns, String root) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument(); doc.appendChild(doc.createElementNS(ns, root)); return doc;
    }
    private static byte[] bytes(Document doc) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); factory.newTransformer().transform(new DOMSource(doc), new StreamResult(out)); return out.toByteArray();
    }
    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        if (parent != null) for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) result.add((Element) n);
        return result;
    }
    private static Element first(Element parent, String name) { return children(parent).stream().filter(e -> W.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())).findFirst().orElse(null); }
    private static List<Element> nodes(Node root, String ns, String tag) {
        NodeList list = root instanceof Document ? ((Document) root).getElementsByTagNameNS(ns, tag) : ((Element) root).getElementsByTagNameNS(ns, tag);
        List<Element> result = new ArrayList<>(); for (int i = 0; i < list.getLength(); i++) result.add((Element) list.item(i)); return result;
    }
    private static List<Element> including(Element e, String ns, String name) {
        List<Element> list = nodes(e, ns, name); if (ns.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())) list.add(0, e); return list;
    }
    private static List<Element> includingAll(Element e) { List<Element> list = nodes(e, "*", "*"); list.add(0, e); return list; }
    private static void remove(Element e, String name) { for (Element node : nodes(e, W, name)) node.getParentNode().removeChild(node); }
}
