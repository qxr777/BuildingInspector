package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.LineBridgeGroup;
import edu.whut.cs.bi.biz.domain.LineReportData;
import lombok.Data;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only matching of Word sections to the existing multi-bridge form keys. */
public final class LineReportWordMatcher {
    public static final long MAX_UPLOAD_BYTES = 300L * 1024 * 1024;
    public static final String PREVIOUS_EVALUATION_KEY = "line-previous-evaluation-scores";
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern HEADING = Pattern.compile("(?:heading|标题)\\s*([1-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("^\\s*\\d+(?:[.．]\\d+)*[.．、]?\\s*");
    private static final Pattern LEVEL = Pattern.compile("(\\d+)\\s*类");
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        field("line-route-overview", "项目概况", "项目概括", "工程概况");
        field("line-component-numbering-rules", "构件编号规则", "构件编号");
        field("overallOverview", "整体概况", "整体概括");
        field("designPoints", "设计要点");
        field("mainMaterial", "主要材料", "主要建筑材料");
        field("technicalAdvice", "技术建议");
    }

    private LineReportWordMatcher() { }

    private static void field(String key, String label, String... aliases) {
        LABELS.put(key, label);
        ALIASES.put(label, key);
        for (String alias : aliases) ALIASES.put(alias, key);
    }

    @Data
    public static class Result {
        private List<Section> sections = new ArrayList<>();
        private List<Target> targets = new ArrayList<>();
        private List<EvaluationTable> evaluationTables = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    /** 从上年报告评定表识别出的一座子桥分数。 */
    @Data
    public static class EvaluationTable {
        private String id;
        /** 表题里的桥名，例如「左幅(钢混组合梁)」。 */
        private String bridgeName;
        /** 表题前面的大桥标题，例如「胡家台大桥」。 */
        private String sourceBridge;
        /** 人工选定的系统子桥名称，导入后按这个名字对应。 */
        private String matchedName;
        private BigDecimal superstructureScore;
        private BigDecimal substructureScore;
        private BigDecimal deckSystemScore;
        private BigDecimal systemScore;
        private Integer systemLevel;
    }

    @Data
    public static class Target {
        private String id;
        private String groupId;
        private String groupName;
        private String key;
        private String label;
        private boolean hasExistingContent;
        private String version;
    }

    @Data
    public static class Section {
        private String id;
        private String sourcePath;
        private String sourceBridge;
        private String key;
        private String label;
        // Half-open body-element range in the original DOCX, excluding the section heading.
        private int start;
        private int end;
        private String targetId;
        private String status;
        private String text;
        private int tableCount;
        private int imageCount;
        private boolean empty;
        private List<String> warnings = new ArrayList<>();
    }

    public static Result match(InputStream input, List<LineBridgeGroup> groups,
                               List<LineReportData> existing) throws IOException {
        // Matching needs only body/styles XML, not thousands of image bytes. Keep original DOCX untouched.
        Path temporary = Files.createTempFile("line-report-match-", ".docx");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                int length;
                while ((length = input.read(buffer)) != -1) {
                    total += length;
                    if (total > MAX_UPLOAD_BYTES) throw new IOException("Word 文件超过 300MB");
                    output.write(buffer, 0, length);
                }
            }
            validateArchive(temporary);
            try (ZipFile zip = new ZipFile(temporary.toFile())) {
                Document document = readXml(zip, "word/document.xml");
                Map<String, Element> styles = new HashMap<>();
                if (zip.getEntry("word/styles.xml") != null) {
                    Document styleDocument = readXml(zip, "word/styles.xml");
                    for (Element style : children(styleDocument.getDocumentElement(), "style"))
                        styles.put(style.getAttributeNS(W, "styleId"), style);
                }
                return matchDocument(document, styles, groups, existing);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Document readXml(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        int limit = 64 * 1024 * 1024;
        if (entry == null || entry.getSize() > limit) throw new IOException("Word 正文过大或缺失");
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] xml = input.readNBytes(limit + 1);
            if (xml.length > limit) throw new IOException("Word XML 超过解析限制");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "256");
            return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("无法读取 Word XML", e);
        }
    }

    private static void validateArchive(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            long expanded = 0;
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                expanded += Math.max(0, entry.getSize());
                if (++count > 5000 || entry.getSize() < 0 || expanded > 600L * 1024 * 1024)
                    throw new IOException("Word 文件内容过多，请拆分后上传");
            }
            if (zip.getEntry("word/document.xml") == null) throw new IOException("缺少 Word 正文");
        }
    }

    private static Result matchDocument(Document document, Map<String, Element> styles, List<LineBridgeGroup> groups,
                                         List<LineReportData> existing) {
        Result result = new Result();
        addTargets(result, groups, existing);
        {
            List<Element> body = children(child(document.getDocumentElement(), "body"), null);
            TreeMap<Integer, String> path = new TreeMap<>();
            Section active = null;
            int activeLevel = 10;
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < body.size(); i++) {
                Element element = body.get(i);
                // TOCs in content controls are not report body sections.
                if ("sdt".equals(element.getLocalName())) continue;
                Element paragraph = "p".equals(element.getLocalName()) ? element : null;
                if (paragraph != null && isToc(styles, paragraph)) continue;
                String title = paragraph == null ? "" : normalize(wordText(paragraph));
                int level = paragraph == null ? -1 : headingLevel(styles, paragraph);
                String key = ALIASES.get(title);
                // Plain exact field labels are a fallback; numeric IDs alone are never heading levels.
                if (level < 0 && key != null) level = key.startsWith("line-") ? 1 : 3;
                if (level < 0 && paragraph != null && plainBridgeTitle(title, groups)) level = 1;
                if (level < 0 && Set.of("技术标准", "历史检测情况", "历史维修情况").contains(title)) level = 3;
                if (level < 0 && Set.of("桥梁概况", "外观检测结果", "部件划分及构件数量", "结论与建议").contains(title)) level = 2;
                if (level > 0) {
                    if (active != null && level <= activeLevel) {
                        finish(active, text, i);
                        active = null;
                    }
                    path.tailMap(level, true).clear();
                    path.put(level, title);
                    if (key != null && active == null) {
                        active = new Section();
                        active.setId("section-" + i);
                        active.setSourcePath(String.join(" → ", path.values()));
                        active.setSourceBridge(findBridge(path.headMap(level, false), groups));
                        active.setKey(key);
                        active.setLabel(LABELS.get(key));
                        active.setStart(i + 1);
                        activeLevel = level;
                        text = new StringBuilder();
                        result.getSections().add(active);
                        continue;
                    }
                }
                if (active != null) {
                    if (paragraph != null) {
                        append(text, wordText(paragraph));
                        active.setImageCount(active.getImageCount() + images(paragraph));
                    } else if ("tbl".equals(element.getLocalName())) {
                        active.setTableCount(active.getTableCount() + 1);
                        for (Element row : children(element, "tr")) {
                            append(text, children(row, "tc").stream().map(LineReportWordMatcher::wordText)
                                    .collect(Collectors.joining(" | ")));
                        }
                        active.setImageCount(active.getImageCount() + images(element));
                    }
                }
            }
            if (active != null) finish(active, text, body.size());
        }
        result.setEvaluationTables(extractEvaluationTables(
                children(child(document.getDocumentElement(), "body"), null), styles));
        for (Section section : result.getSections()) resolve(section, result.getTargets());
        Map<String, Long> counts = result.getSections().stream().filter(s -> s.getTargetId() != null && !s.isEmpty())
                .collect(Collectors.groupingBy(Section::getTargetId, Collectors.counting()));
        for (Section section : result.getSections()) {
            if (section.getTargetId() != null && counts.getOrDefault(section.getTargetId(), 0L) > 1) {
                section.setStatus("DUPLICATE");
                section.getWarnings().add("多个来源章节匹配到同一填报项，请选择其中一项。");
            }
        }
        if (result.getSections().isEmpty() && result.getEvaluationTables().isEmpty()) {
            result.getWarnings().add("未找到支持的正文标题，请检查 Word 标题或将标题设置为标题样式。");
        }
        if (!result.getEvaluationTables().isEmpty()) {
            result.getWarnings().add("识别到 " + result.getEvaluationTables().size()
                    + " 张技术状况评定表。请在下方为每张表选择系统中的子桥，未选择的不导入。");
        }
        result.getWarnings().add("请确认目标后点击“确认导入”。预览仅核对内容，导入生成使用原 Word 的段落、表格和图片格式。");
        return result;
    }

    static List<EvaluationTable> extractEvaluationTables(List<Element> body, Map<String, Element> styles) {
        List<EvaluationTable> tables = new ArrayList<>();
        if (body == null) {
            return tables;
        }
        String sourceBridge = null;
        for (int i = 0; i < body.size(); i++) {
            Element element = body.get(i);
            if ("sdt".equals(element.getLocalName())) {
                continue;
            }
            if ("p".equals(element.getLocalName()) && (styles == null || !isToc(styles, element))) {
                String title = normalize(wordText(element));
                int level = styles == null ? -1 : headingLevel(styles, element);
                if (isParentBridgeHeading(title) && ((level > 0 && level <= 3) || (level < 0 && title.length() <= 24))) {
                    sourceBridge = title;
                }
            }
            if (!"tbl".equals(element.getLocalName())) {
                continue;
            }
            String caption = nearbyCaption(body, i);
            if (caption == null) {
                continue;
            }
            EvaluationTable table = parseEvaluationTable(element, captionBridgeName(caption));
            if (table == null) {
                continue;
            }
            table.setId("eval-" + tables.size());
            table.setSourceBridge(sourceBridge);
            tables.add(table);
        }
        return tables;
    }

    /** 章节标题里的大桥名，不含左幅、右幅。 */
    static boolean isParentBridgeHeading(String title) {
        if (title == null || title.isEmpty() || title.length() > 40) {
            return false;
        }
        if (title.contains("技术状况") || title.contains("评定") || title.contains("左幅")
                || title.contains("右幅") || title.contains("幅")) {
            return false;
        }
        return title.matches(".+(?:特大桥|大桥|中桥|小桥|高架桥|匝道桥)");
    }

    private static String nearbyCaption(List<Element> body, int tableIndex) {
        for (int i = tableIndex - 1; i >= 0 && i >= tableIndex - 3; i--) {
            Element element = body.get(i);
            if ("tbl".equals(element.getLocalName())) {
                break;
            }
            if (!"p".equals(element.getLocalName())) {
                continue;
            }
            String text = wordText(element);
            if (isEvaluationCaption(text)) {
                return text;
            }
            if (!text.trim().isEmpty()) {
                break;
            }
        }
        for (int i = tableIndex + 1; i < body.size() && i <= tableIndex + 2; i++) {
            Element element = body.get(i);
            if ("tbl".equals(element.getLocalName())) {
                break;
            }
            if (!"p".equals(element.getLocalName())) {
                continue;
            }
            String text = wordText(element);
            if (isEvaluationCaption(text)) {
                return text;
            }
            if (!text.trim().isEmpty()) {
                break;
            }
        }
        return null;
    }

    private static boolean isEvaluationCaption(String text) {
        if (text == null) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        return compact.contains("技术状况评定表") && !compact.contains("对比");
    }

    static String captionBridgeName(String caption) {
        String text = normalize(caption);
        text = text.replaceFirst("^表(?:\\d+(?:[-—－.．]\\d+)*)?", "");
        text = text.replaceFirst("^[-—－.．]+", "");
        text = text.replace("桥梁技术状况评定表", "");
        text = text.replace("技术状况评定表", "");
        return text;
    }

    private static EvaluationTable parseEvaluationTable(Element tbl, String bridgeName) {
        List<List<String>> rows = tableGrid(tbl);
        if (rows.isEmpty()) {
            return null;
        }
        EvaluationTable table = new EvaluationTable();
        table.setBridgeName(bridgeName == null || bridgeName.isEmpty() ? null : bridgeName);
        table.setSuperstructureScore(scoreAfterLabel(rows, "SPCI"));
        table.setSubstructureScore(scoreAfterLabel(rows, "SBCI"));
        table.setDeckSystemScore(scoreAfterLabel(rows, "BDCI"));
        fillOverall(rows, table);
        if (table.getSuperstructureScore() == null && table.getSubstructureScore() == null
                && table.getDeckSystemScore() == null && table.getSystemScore() == null
                && table.getSystemLevel() == null) {
            return null;
        }
        return table;
    }

    private static List<List<String>> tableGrid(Element tbl) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : children(tbl, "tr")) {
            List<String> row = new ArrayList<>();
            for (Element tc : children(tr, "tc")) {
                row.add(wordText(tc).replace('\u3000', ' ').trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal scoreAfterLabel(List<List<String>> rows, String label) {
        String want = label.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (!want.equals(row.get(i).replaceAll("\\s+", "").toUpperCase(Locale.ROOT))) {
                    continue;
                }
                for (int j = i + 1; j < row.size(); j++) {
                    BigDecimal score = parseScore(row.get(j));
                    if (score != null) {
                        return score;
                    }
                }
            }
        }
        return null;
    }

    private static void fillOverall(List<List<String>> rows, EvaluationTable table) {
        for (List<String> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            String joined = String.join("", row);
            if (joined.contains("部位") || joined.contains("桥梁分项") || joined.contains("权重标准")
                    || joined.contains("评价部件")) {
                continue;
            }
            Integer level = null;
            BigDecimal score = null;
            for (int i = row.size() - 1; i >= 0; i--) {
                if (level == null) {
                    level = parseLevel(row.get(i));
                    continue;
                }
                score = parseScore(row.get(i));
                if (score != null) {
                    break;
                }
            }
            table.setSystemScore(score);
            table.setSystemLevel(level);
            return;
        }
    }

    private static BigDecimal parseScore(String cell) {
        if (cell == null) {
            return null;
        }
        String text = cell.replaceAll("\\s+", "").replace(",", "");
        if (text.isEmpty() || "/".equals(text) || text.contains("类")) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseLevel(String cell) {
        if (cell == null) {
            return null;
        }
        Matcher matcher = LEVEL.matcher(cell);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    public static EvaluationTable matchEvaluation(String bridgeName, List<EvaluationTable> tables) {
        if (bridgeName == null || tables == null || tables.isEmpty()) {
            return null;
        }
        String key = normalize(bridgeName);
        if (key.isEmpty()) {
            return null;
        }
        EvaluationTable exact = tables.stream()
                .filter(table -> table != null && key.equals(normalize(table.getBridgeName())))
                .findFirst().orElse(null);
        if (exact != null) {
            return exact;
        }
        List<EvaluationTable> contains = tables.stream()
                .filter(table -> table != null && table.getBridgeName() != null)
                .filter(table -> {
                    String name = normalize(table.getBridgeName());
                    return !name.isEmpty() && (key.contains(name) || name.contains(key));
                })
                .collect(Collectors.toList());
        return contains.size() == 1 ? contains.get(0) : null;
    }

    private static void addTargets(Result result, List<LineBridgeGroup> groups, List<LineReportData> existing) {
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            if (entry.getKey().startsWith("line-")) {
                addTarget(result, null, "整份报告", entry, existing);
            } else {
                for (LineBridgeGroup group : groups) {
                    if (group != null && group.getId() != null)
                        addTarget(result, group.getId(), group.getName(), entry, existing);
                }
            }
        }
    }

    private static void addTarget(Result result, String groupId, String name, Map.Entry<String, String> entry,
                                  List<LineReportData> existing) {
        Target target = new Target();
        target.setId("target-" + result.getTargets().size());
        target.setGroupId(groupId);
        target.setGroupName(name);
        target.setKey(entry.getKey());
        target.setLabel(entry.getValue());
        LineReportData stored = existing.stream().filter(row -> row.getTaskId() == null
                && Objects.equals(blankToNull(row.getGroupId()), groupId) && entry.getKey().equals(row.getKey()))
                .findFirst().orElse(null);
        target.setVersion(WordImportSupport.version(stored));
        target.setHasExistingContent(existing.stream().anyMatch(row -> row.getTaskId() == null
                && Objects.equals(blankToNull(row.getGroupId()), groupId)
                && (entry.getKey().equals(row.getKey()) || ("line-route-overview".equals(entry.getKey())
                && "line-project-overview".equals(row.getKey())))
                && row.getValue() != null && !row.getValue().trim().isEmpty()));
        result.getTargets().add(target);
    }

    private static void resolve(Section section, List<Target> targets) {
        if (section.isEmpty()) {
            section.setStatus("EMPTY");
            return;
        }
        List<Target> matches = targets.stream().filter(t -> t.getKey().equals(section.getKey()))
                .filter(t -> t.getGroupId() == null ? section.getSourceBridge() == null
                        : section.getSourceBridge() != null
                        && normalize(t.getGroupName()).equals(normalize(section.getSourceBridge())))
                .collect(Collectors.toList());
        if (matches.size() == 1) {
            Target target = matches.get(0);
            section.setTargetId(target.getId());
            section.setStatus(target.isHasExistingContent() ? "EXISTING" : "MATCHED");
        } else {
            section.setStatus(matches.size() > 1 ? "AMBIGUOUS" : "UNMATCHED");
        }
        if ("overallOverview".equals(section.getKey()) && section.getImageCount() > 0)
            section.getWarnings().add("整体概况含图片。后续整段生成时需与页面独立照片项互斥，避免重复。");
    }

    private static void finish(Section section, StringBuilder text, int end) {
        section.setEnd(end);
        section.setText(text.toString().trim());
        section.setEmpty(section.getText().isEmpty() && section.getImageCount() == 0 && section.getTableCount() == 0);
    }

    private static void append(StringBuilder text, String value) {
        if (value != null && !value.trim().isEmpty()) text.append(value).append('\n');
    }

    private static String findBridge(SortedMap<Integer, String> path, List<LineBridgeGroup> groups) {
        List<String> values = new ArrayList<>(path.values());
        Collections.reverse(values);
        for (String value : values) if (plainBridgeTitle(value, groups)) return value;
        return null;
    }

    private static boolean plainBridgeTitle(String title, List<LineBridgeGroup> groups) {
        if (title.isEmpty() || title.length() > 80) return false;
        if (groups.stream().anyMatch(g -> g != null && normalize(g.getName()).equals(title))) return true;
        return title.matches("[^。；：:]+(?:大桥|特大桥|中桥|小桥|高架桥|匝道桥)(?:[（(][^）)]*[）)])?");
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String text = value.replace('\u3000', ' ').trim();
        text = text.replaceFirst("^第[一二三四五六七八九十百零〇\\d]+章\\s*", "");
        return NUMBER.matcher(text).replaceFirst("").replaceAll("\\s+", "")
                .replace('（', '(').replace('）', ')');
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static boolean isToc(Map<String, Element> styles, Element paragraph) {
        String id = val(child(child(paragraph, "pPr"), "pStyle"));
        Element style = styles.get(id);
        String name = val(child(style, "name")).toLowerCase(Locale.ROOT);
        return name.startsWith("toc") || name.startsWith("目录")
                || (id != null && id.toLowerCase(Locale.ROOT).startsWith("toc"));
    }

    private static int headingLevel(Map<String, Element> styles, Element paragraph) {
        Element properties = child(paragraph, "pPr");
        Element outline = child(properties, "outlineLvl");
        if (outline != null) return outlineLevel(outline);
        String id = val(child(properties, "pStyle"));
        Set<String> visited = new HashSet<>();
        while (!id.isEmpty() && visited.add(id)) {
            Element style = styles.get(id);
            if (style == null) break;
            outline = child(child(style, "pPr"), "outlineLvl");
            if (outline != null) return outlineLevel(outline);
            Matcher matcher = HEADING.matcher(val(child(style, "name")));
            if (matcher.matches()) return Integer.parseInt(matcher.group(1));
            id = val(child(style, "basedOn"));
        }
        return -1;
    }

    private static int outlineLevel(Element outline) {
        try {
            int level = Integer.parseInt(val(outline));
            return level >= 0 && level < 9 ? level + 1 : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    private static String val(Element element) {
        return element == null ? "" : element.getAttributeNS(W, "val");
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element && W.equals(node.getNamespaceURI())
                    && (name == null || name.equals(node.getLocalName()))) result.add((Element) node);
        return result;
    }

    private static Element child(Element parent, String name) {
        List<Element> elements = children(parent, name);
        return elements.isEmpty() ? null : elements.get(0);
    }

    private static String wordText(Node node) {
        if (W.equals(node.getNamespaceURI())) {
            if ("t".equals(node.getLocalName())) return node.getTextContent();
            if ("tab".equals(node.getLocalName())) return "\t";
            if ("br".equals(node.getLocalName())) return "\n";
        }
        StringBuilder text = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) text.append(wordText(child));
        return text.toString();
    }

    private static int images(Node node) {
        if (W.equals(node.getNamespaceURI()) && ("drawing".equals(node.getLocalName()) || "pict".equals(node.getLocalName()))) return 1;
        int count = 0;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) count += images(child);
        return count;
    }
}
