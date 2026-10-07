package edu.whut.cs.bi.biz.service.std;

import edu.whut.cs.bi.biz.mapper.StdSchemaMapper;
import edu.whut.cs.bi.biz.service.std.impl.StdSchemaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StdSchemaServiceImpl} 纯 Mockito 测试：mock information_schema。
 * 已存在的列/索引不产生 DDL；缺失时每条 DDL 只产生并执行一次；dryRun 只规划不执行。
 */
@ExtendWith(MockitoExtension.class)
class StdSchemaServiceTest {

    private static final String DB = "biprod";
    private static final String T_TYPE = "bi_disease_type";
    private static final String T_SCALE = "bi_disease_scale";
    private static final String T_TEMPLATE = "bi_template_object";

    @Mock
    private StdSchemaMapper stdSchemaMapper;

    @InjectMocks
    private StdSchemaServiceImpl schemaService;

    private Map<String, Object> indexRow(String name, int nonUnique, int seq, String column) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index_name", name);
        row.put("non_unique", nonUnique);
        row.put("seq_in_index", seq);
        row.put("column_name", column);
        return row;
    }

    private void stubColumns(List<String> typeCols, List<String> scaleCols, List<String> templateCols) {
        when(stdSchemaMapper.selectCurrentSchema()).thenReturn(DB);
        when(stdSchemaMapper.selectTableColumns(DB, T_TYPE)).thenReturn(typeCols);
        when(stdSchemaMapper.selectTableColumns(DB, T_SCALE)).thenReturn(scaleCols);
        when(stdSchemaMapper.selectTableColumns(DB, T_TEMPLATE)).thenReturn(templateCols);
    }

    @Test
    void 列与复合索引均已存在_零DDL零执行() {
        stubColumns(
                Arrays.asList("id", "code", "name", "std_version"),
                Arrays.asList("id", "type_code", "scale", "std_version",
                        "metric_key", "value_lower", "value_upper", "unit"),
                Arrays.asList("id", "name", "parent_id", "std_version", "node_code"));
        when(stdSchemaMapper.selectIndexColumns(DB, T_TYPE)).thenReturn(Arrays.asList(
                indexRow("uk_disease_type_code_ver", 0, 1, "code"),
                indexRow("uk_disease_type_code_ver", 0, 2, "std_version")));
        when(stdSchemaMapper.selectIndexColumns(DB, T_TEMPLATE)).thenReturn(Arrays.asList(
                indexRow("uk_template_nodecode_ver", 0, 1, "node_code"),
                indexRow("uk_template_nodecode_ver", 0, 2, "std_version")));

        List<String> ddl = schemaService.ensureSchema(false);

        assertTrue(ddl.isEmpty(), "已就绪不应产生 DDL: " + ddl);
        verify(stdSchemaMapper, never()).executeDdl(anyString());
    }

    @Test
    void 缺失列与旧单列索引_补齐DDL各执行一次() {
        stubColumns(
                Arrays.asList("id", "code", "name"),
                Arrays.asList("id", "type_code", "scale"),
                Arrays.asList("id", "name", "parent_id"));
        // 旧的 code 单列唯一索引应被 DROP；模板表无任何唯一索引。
        when(stdSchemaMapper.selectIndexColumns(DB, T_TYPE)).thenReturn(
                Collections.singletonList(indexRow("uk_code", 0, 1, "code")));
        when(stdSchemaMapper.selectIndexColumns(DB, T_TEMPLATE))
                .thenReturn(new ArrayList<>());

        List<String> ddl = schemaService.ensureSchema(false);

        // 类型表3 + 标度表5 + 模板表3 = 11
        assertEquals(11, ddl.size());
        assertEquals(11, ddl.stream().distinct().count(), "DDL 不应重复");
        assertTrue(containsFragment(ddl, "DROP INDEX `uk_code`"));
        // 每条 DDL 实际执行一次
        verify(stdSchemaMapper, org.mockito.Mockito.times(11)).executeDdl(anyString());
    }

    @Test
    void dryRun_规划全部DDL但不执行() {
        stubColumns(
                Arrays.asList("id", "code", "name"),
                Arrays.asList("id", "type_code", "scale"),
                Arrays.asList("id", "name", "parent_id"));
        when(stdSchemaMapper.selectIndexColumns(DB, T_TYPE)).thenReturn(
                Collections.singletonList(indexRow("uk_code", 0, 1, "code")));
        when(stdSchemaMapper.selectIndexColumns(DB, T_TEMPLATE))
                .thenReturn(new ArrayList<>());

        List<String> ddl = schemaService.ensureSchema(true);

        assertEquals(11, ddl.size());
        assertFalse(ddl.isEmpty());
        verify(stdSchemaMapper, never()).executeDdl(anyString());
    }

    private static boolean containsFragment(List<String> ddl, String fragment) {
        return ddl.stream().anyMatch(s -> s.contains(fragment));
    }
}
