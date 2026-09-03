package edu.whut.cs.bi.biz.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 多桥报告中的「大桥」分组。
 *
 * <p>一份报告里可以选多座桥梁任务，再把其中若干座合成一座大桥。
 * 列表顺序就是大桥顺序，{@code taskIds} 顺序就是该大桥下子桥顺序。</p>
 */
@Data
public class LineBridgeGroup {
    public static final String DATA_KEY = "line-bridge-groups";

    public static String soloId(Long taskId) {
        return "solo-" + taskId;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 前端生成的分组标识，仅用于填报页编辑。 */
    private String id;

    /** 合成后的大桥名称，例如「东分块 3#高架桥」。 */
    private String name;

    /** 该大桥下的子桥任务 ID，顺序即报告中的子桥顺序。 */
    private List<Long> taskIds;

    public static List<LineBridgeGroup> parseList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<LineBridgeGroup> groups = OBJECT_MAPPER.readValue(
                    json, new TypeReference<List<LineBridgeGroup>>() {
                    });
            return groups == null ? new ArrayList<>() : groups;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
