package edu.whut.cs.bi.biz.service.std;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.service.std.model.StdCatalog;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseScale;
import edu.whut.cs.bi.biz.service.std.model.StdDiseaseType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把权威目录中的病害类型/标度生成 App 离线字典 {@code disease_scale.json} 同构内容。
 * 新 5230-2026 标准的离线字典只由本类产出（单一出处），不依赖任何手工维护的静态文件。
 *
 * <p>输出形如：{@code [{"type_code": "...", "type_content": [{"scale":1,
 * "qualitative_description": ..., "quantitative_description": ..., "status":0}]}]}，
 * 字段命名与既有 H21 {@code json/disease_scale.json} 保持一致，便于 App 沿用同一解析结构。</p>
 */
@Component
public class StdDiseaseScaleJsonWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] writeDiseaseScaleJson(StdCatalog catalog) {
        List<Map<String, Object>> root = new ArrayList<>();
        for (StdDiseaseType type : catalog.getDiseaseTypes()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type_code", type.getCode());

            List<Map<String, Object>> contents = new ArrayList<>();
            for (StdDiseaseScale scale : type.getScales()) {
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("scale", scale.getScale());
                // 正式稿表格单元格只给一条判定描述，照录到 quantitative_description。
                content.put("qualitative_description", scale.getQualitative());
                content.put("quantitative_description", scale.getQuantitative());
                content.put("status", 0);
                contents.add(content);
            }
            entry.put("type_content", contents);
            root.add(entry);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (Exception e) {
            throw new IllegalStateException("生成 " + catalog.getStdVersion() + " 病害标度 JSON 失败", e);
        }
    }
}
