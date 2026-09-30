package edu.whut.cs.bi.biz.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.domain.Disease;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 多桥重点病害逐条选择格式：{"version":2,"tasks":{"taskId":["diseaseId"]}}。 */
public final class FocusDiseaseSelection {
    private FocusDiseaseSelection() { }

    /** 返回 null 表示旧的构件/类型选择；空 Map 表示新版明确未选病害。 */
    public static Map<Long, Set<Long>> parseRecordIds(String json) throws IOException {
        if (json == null || json.isBlank()) return null;
        JsonNode root = new ObjectMapper().readTree(json);
        if (root == null || !root.isObject() || !root.has("version")) return null;
        if (root.path("version").asInt() != 2 || !root.path("tasks").isObject()) {
            throw new IllegalArgumentException("重点病害选择数据格式不正确");
        }
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        var tasks = root.get("tasks").fields();
        while (tasks.hasNext()) {
            var task = tasks.next();
            long taskId = positiveId(task.getKey());
            if (!task.getValue().isArray()) throw new IllegalArgumentException("病害 ID 必须为列表");
            Set<Long> ids = new LinkedHashSet<>();
            for (JsonNode id : task.getValue()) {
                if (!id.isTextual() && !id.isIntegralNumber()) {
                    throw new IllegalArgumentException("病害 ID 格式不正确");
                }
                ids.add(positiveId(id.asText()));
            }
            result.put(taskId, ids);
        }
        return result;
    }

    /** 从当前桥梁、任务查询结果中取选中的记录，保持选择顺序且不扩大到同类型病害。 */
    public static List<Disease> selectedRecords(Map<Long, Set<Long>> selection, Long taskId,
                                                 List<Disease> taskRecords) {
        Map<Long, Disease> byId = new LinkedHashMap<>();
        if (taskRecords != null) {
            for (Disease disease : taskRecords) {
                if (disease != null && taskId.equals(disease.getTaskId())) byId.put(disease.getId(), disease);
            }
        }
        List<Disease> selected = new ArrayList<>();
        for (Long id : selection.getOrDefault(taskId, Set.of())) {
            Disease disease = byId.get(id);
            if (disease != null) selected.add(disease);
        }
        return selected;
    }

    private static long positiveId(String value) {
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException("病害或任务 ID 必须为正整数");
        return id;
    }
}
