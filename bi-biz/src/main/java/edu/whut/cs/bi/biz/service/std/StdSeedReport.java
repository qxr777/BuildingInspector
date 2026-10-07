package edu.whut.cs.bi.biz.service.std;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 种子执行报告。
 */
@Data
public class StdSeedReport {

    /** 单类对象的变更计数。 */
    @Data
    public static class Counter {
        private int inserted;
        private int updated;
        private int unchanged;
        private int deleted;

        public int totalChanged() {
            return inserted + updated + deleted;
        }
    }

    private String stdVersion;

    private boolean dryRun;

    /** 实际执行的 DDL（dryRun 时为“将执行”） */
    private List<String> ddl = new ArrayList<>();

    private Counter diseaseTypes = new Counter();

    private Counter diseaseScales = new Counter();

    private Counter templateNodes = new Counter();

    private Counter bindings = new Counter();

    private List<String> notes = new ArrayList<>();

    private List<String> errors = new ArrayList<>();

    /** 是否完全没有变更。 */
    public boolean isClean() {
        return diseaseTypes.totalChanged() == 0
                && diseaseScales.totalChanged() == 0
                && templateNodes.totalChanged() == 0
                && bindings.totalChanged() == 0
                && ddl.isEmpty();
    }

    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stdVersion", stdVersion);
        map.put("dryRun", dryRun);
        map.put("ddl", ddl.size());
        map.put("diseaseTypes", counts(diseaseTypes));
        map.put("diseaseScales", counts(diseaseScales));
        map.put("templateNodes", counts(templateNodes));
        map.put("bindings", counts(bindings));
        map.put("errors", errors.size());
        return map;
    }

    private Map<String, Integer> counts(Counter c) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("inserted", c.inserted);
        m.put("updated", c.updated);
        m.put("unchanged", c.unchanged);
        m.put("deleted", c.deleted);
        return m;
    }
}
