package edu.whut.cs.bi.biz.service.std.impl;

import edu.whut.cs.bi.biz.mapper.StdSchemaMapper;
import edu.whut.cs.bi.biz.service.std.StdSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 通过 information_schema 自省，幂等地补齐标准版本隔离所需列与唯一索引。
 * 仅在管理员触发种子时运行，不在启动时执行。
 */
@Service
public class StdSchemaServiceImpl implements StdSchemaService {

    private static final Logger log = LoggerFactory.getLogger(StdSchemaServiceImpl.class);

    private static final String TABLE_TYPE = "bi_disease_type";
    private static final String TABLE_SCALE = "bi_disease_scale";
    private static final String TABLE_TEMPLATE = "bi_template_object";

    private static final String UK_TYPE_CODE_VER = "uk_disease_type_code_ver";
    private static final String UK_TEMPLATE_NODE_VER = "uk_template_nodecode_ver";

    @Resource
    private StdSchemaMapper stdSchemaMapper;

    @Override
    public List<String> ensureSchema(boolean dryRun) {
        String schema = stdSchemaMapper.selectCurrentSchema();
        List<String> ddl = new ArrayList<>();

        planTypeTable(schema, ddl);
        planScaleTable(schema, ddl);
        planTemplateTable(schema, ddl);

        if (!dryRun) {
            for (String stmt : ddl) {
                stdSchemaMapper.executeDdl(stmt);
            }
        }
        log.info("标准 schema {} 共 {} 条 DDL{}", schema, ddl.size(), dryRun ? "（dryRun）" : "并已执行");
        return ddl;
    }

    private void planTypeTable(String schema, List<String> ddl) {
        Set<String> columns = columnsOf(schema, TABLE_TYPE);
        addColumn(ddl, columns, TABLE_TYPE,
                "std_version varchar(32) NOT NULL DEFAULT 'H21-2011'", "std_version");

        Map<String, List<String>> uniqueIndexes = uniqueIndexes(schema, TABLE_TYPE);

        // 去掉仅作用于 code 的单列唯一索引，改由 (code, std_version) 保证
        List<String> stale = uniqueIndexes.entrySet().stream()
                .filter(e -> e.getValue().size() == 1 && e.getValue().get(0).equals("code"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        for (String indexName : stale) {
            ddl.add("ALTER TABLE " + TABLE_TYPE + " DROP INDEX `" + indexName + "`");
        }

        if (!hasIndexOn(uniqueIndexes, "code", "std_version")) {
            ddl.add("ALTER TABLE " + TABLE_TYPE
                    + " ADD UNIQUE KEY " + UK_TYPE_CODE_VER + " (code, std_version)");
        }
    }

    private void planScaleTable(String schema, List<String> ddl) {
        Set<String> columns = columnsOf(schema, TABLE_SCALE);
        addColumn(ddl, columns, TABLE_SCALE,
                "std_version varchar(32) NOT NULL DEFAULT 'H21-2011'", "std_version");
        addColumn(ddl, columns, TABLE_SCALE,
                "metric_key varchar(32) NULL", "metric_key");
        addColumn(ddl, columns, TABLE_SCALE,
                "value_lower decimal(12,3) NULL", "value_lower");
        addColumn(ddl, columns, TABLE_SCALE,
                "value_upper decimal(12,3) NULL", "value_upper");
        addColumn(ddl, columns, TABLE_SCALE,
                "unit varchar(16) NULL", "unit");
    }

    private void planTemplateTable(String schema, List<String> ddl) {
        Set<String> columns = columnsOf(schema, TABLE_TEMPLATE);
        addColumn(ddl, columns, TABLE_TEMPLATE,
                "std_version varchar(32) NOT NULL DEFAULT 'H21-2011'", "std_version");
        addColumn(ddl, columns, TABLE_TEMPLATE,
                "node_code varchar(64) NULL", "node_code");

        Map<String, List<String>> uniqueIndexes = uniqueIndexes(schema, TABLE_TEMPLATE);
        if (!hasIndexOn(uniqueIndexes, "node_code", "std_version")) {
            ddl.add("ALTER TABLE " + TABLE_TEMPLATE
                    + " ADD UNIQUE KEY " + UK_TEMPLATE_NODE_VER + " (node_code, std_version)");
        }
    }

    private Set<String> columnsOf(String schema, String table) {
        return stdSchemaMapper.selectTableColumns(schema, table).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private void addColumn(List<String> ddl, Set<String> existing, String table,
                           String columnDefinition, String columnName) {
        if (!existing.contains(columnName.toLowerCase())) {
            ddl.add("ALTER TABLE " + table + " ADD COLUMN " + columnDefinition);
        }
    }

    /**
     * 取唯一索引（non_unique=0）及其按 seq 排列的列；排除主键 PRIMARY。
     */
    private Map<String, List<String>> uniqueIndexes(String schema, String table) {
        Map<String, TreeMap<Integer, String>> byIndex = new LinkedHashMap<>();
        for (Map<String, Object> row : stdSchemaMapper.selectIndexColumns(schema, table)) {
            String indexName = lc(row, "index_name");
            boolean unique = ((Number) lcObj(row, "non_unique")).intValue() == 0;
            if (!unique || "PRIMARY".equalsIgnoreCase(indexName)) {
                continue;
            }
            int seq = ((Number) lcObj(row, "seq_in_index")).intValue();
            String column = lc(row, "column_name");
            byIndex.computeIfAbsent(indexName, k -> new TreeMap<>()).put(seq, column);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        byIndex.forEach((name, ordered) -> result.put(name, new ArrayList<>(ordered.values())));
        return result;
    }

    private boolean hasIndexOn(Map<String, List<String>> uniqueIndexes, String... columns) {
        List<String> expected = List.of(columns);
        return uniqueIndexes.values().stream().anyMatch(expected::equals);
    }

    private static String lc(Map<String, Object> row, String key) {
        Object value = lcObj(row, key);
        return value == null ? null : value.toString();
    }

    private static Object lcObj(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
