package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class LineReportWordImportService {
    @Autowired private IFileMapService files;
    @Autowired private ILineMultiBridgeReportDataService dataService;

    @Data
    public static class Selection {
        private String sourceId;
        private String groupId;
        private String key;
        private String expectedVersion;
        private boolean replaceExisting;
    }

    public int apply(Long reportId, MultipartFile file, LineReportWordMatcher.Result parsed,
                     List<Selection> selections) throws Exception {
        return apply(reportId, file, parsed, selections, null);
    }

    public int apply(Long reportId, MultipartFile file, LineReportWordMatcher.Result parsed,
                     List<Selection> selections, String evaluationMatches) throws Exception {
        boolean hasSections = selections != null && !selections.isEmpty();
        boolean hasEvaluations = parsed != null && parsed.getEvaluationTables() != null
                && !parsed.getEvaluationTables().isEmpty();
        if (!hasSections && !hasEvaluations) {
            throw new IllegalArgumentException("请至少选择一个匹配项，或导入含技术状况评定表的上年报告");
        }
        int count = 0;
        if (hasSections) {
            List<LineReportData> rows = prepare(reportId, parsed, selections, file.getOriginalFilename());
            validateSource(file, rows);
            FileMap uploaded = files.handleFileUpload(file);
            try {
                ObjectMapper json = new ObjectMapper();
                for (LineReportData row : rows) {
                    WordImportSupport.Reference ref = json.readValue(row.getValue(), WordImportSupport.Reference.class);
                    ref.setFileId(uploaded.getId().longValue());
                    row.setValue(json.writeValueAsString(ref));
                }
                Map<String, String> versions = new HashMap<>();
                for (Selection selection : selections) versions.put(WordImportSupport.scopeKey(selection.getGroupId(), selection.getKey()), selection.getExpectedVersion());
                dataService.saveWordImports(reportId, rows, versions);
                count += rows.size();
            } catch (Exception e) {
                try { files.deleteFileMapById(uploaded.getId().longValue()); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }
        List<LineReportWordMatcher.EvaluationTable> chosen = chooseEvaluationTables(parsed, evaluationMatches);
        if (!hasSections && chosen.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个匹配项，或为一张评定表选择系统子桥");
        }
        if (!chosen.isEmpty()) {
            LineReportData scores = new LineReportData();
            scores.setReportId(reportId);
            scores.setKey(LineReportWordMatcher.PREVIOUS_EVALUATION_KEY);
            scores.setType(0);
            scores.setValue(new ObjectMapper().writeValueAsString(chosen));
            count += dataService.saveBatch(reportId, List.of(scores));
        }
        return count;
    }

    private List<LineReportWordMatcher.EvaluationTable> chooseEvaluationTables(LineReportWordMatcher.Result parsed,
                                                                                String evaluationMatches) throws Exception {
        List<LineReportWordMatcher.EvaluationTable> tables = parsed == null || parsed.getEvaluationTables() == null
                ? List.of() : parsed.getEvaluationTables();
        if (evaluationMatches == null || evaluationMatches.isBlank()) {
            return tables;
        }
        List<Map<String, String>> chosen = new ObjectMapper().readValue(evaluationMatches,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() { });
        Map<String, LineReportWordMatcher.EvaluationTable> byId = new HashMap<>();
        for (LineReportWordMatcher.EvaluationTable table : tables) {
            if (table != null && table.getId() != null) {
                byId.put(table.getId(), table);
            }
        }
        List<LineReportWordMatcher.EvaluationTable> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Map<String, String> item : chosen) {
            if (item == null) {
                continue;
            }
            String matched = item.get("matchedName");
            if (matched == null || matched.isBlank()) {
                continue;
            }
            LineReportWordMatcher.EvaluationTable table = byId.get(item.get("id"));
            if (table == null) {
                throw new IllegalArgumentException("评定表已变化，请重新识别");
            }
            if (!used.add(LineReportWordMatcher.normalize(matched))) {
                throw new IllegalArgumentException("多张评定表选到了同一座系统子桥：" + matched);
            }
            table.setMatchedName(matched.trim());
            result.add(table);
        }
        return result;
    }

    protected void validateSource(MultipartFile file, List<LineReportData> rows) throws Exception {
        java.nio.file.Path source = java.nio.file.Files.createTempFile("word-import-check-source-", ".docx");
        java.nio.file.Path target = java.nio.file.Files.createTempFile("word-import-check-target-", ".docx");
        try {
            try (java.io.InputStream input = file.getInputStream()) {
                java.nio.file.Files.copy(input, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            List<WordImportMerger.Item> fragments = new ArrayList<>();
            try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
                doc.createStyles();
                for (int i = 0; i < rows.size(); i++) {
                    WordImportSupport.Reference ref = new ObjectMapper().readValue(rows.get(i).getValue(), WordImportSupport.Reference.class);
                    String marker = "WORD_IMPORT_VALIDATE_" + i;
                    doc.createParagraph().createRun().setText(marker);
                    fragments.add(new WordImportMerger.Item(marker, source, ref.getStart(), ref.getEnd()));
                }
                try (java.io.OutputStream output = java.nio.file.Files.newOutputStream(target)) { doc.write(output); }
            }
            WordImportMerger.merge(target, fragments);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("所选章节无法完整导入：" + e.getMessage(), e);
        } finally {
            java.nio.file.Files.deleteIfExists(source); java.nio.file.Files.deleteIfExists(target);
        }
    }

    public static List<LineReportData> prepare(Long reportId, LineReportWordMatcher.Result parsed,
                                               List<Selection> selections, String filename) throws Exception {
        if (selections == null || selections.isEmpty()) throw new IllegalArgumentException("请至少选择一个匹配项");
        List<LineReportData> rows = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (Selection selection : selections) {
            LineReportWordMatcher.Section source = parsed.getSections().stream()
                    .filter(s -> Objects.equals(selection.getSourceId(), s.getId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("来源章节已变化，请重新识别"));
            LineReportWordMatcher.Target target = parsed.getTargets().stream()
                    .filter(t -> Objects.equals(selection.getGroupId(), t.getGroupId()) && Objects.equals(selection.getKey(), t.getKey()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("目标大桥或填报项已变化，请重新识别"));
            if (source.isEmpty() || source.getKey().startsWith("line-") != target.getKey().startsWith("line-"))
                throw new IllegalArgumentException("空章节或跨报告/大桥范围的匹配无效");
            if (!used.add(target.getId()) || !sources.add(source.getId())) throw new IllegalArgumentException("来源或目标重复，请检查匹配");
            if (!Objects.equals(selection.getExpectedVersion(), target.getVersion())) throw new IllegalArgumentException("填报内容已变化，请重新识别后导入");
            if (target.isHasExistingContent() && !selection.isReplaceExisting()) throw new IllegalArgumentException("目标已有内容，请确认替换或选择忽略");
            WordImportSupport.Reference ref = new WordImportSupport.Reference();
            ref.setStart(source.getStart()); ref.setEnd(source.getEnd()); ref.setSourceName(filename);
            ref.setSourcePath(source.getSourcePath()); ref.setPreview(source.getText());
            LineReportData row = new LineReportData(); row.setReportId(reportId); row.setGroupId(target.getGroupId());
            row.setKey(target.getKey()); row.setType(WordImportSupport.TYPE);
            row.setValue(new ObjectMapper().writeValueAsString(ref)); rows.add(row);
        }
        return rows;
    }
}
