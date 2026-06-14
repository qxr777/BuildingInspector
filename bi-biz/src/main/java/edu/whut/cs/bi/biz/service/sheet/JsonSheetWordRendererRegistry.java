package edu.whut.cs.bi.biz.service.sheet;

import com.ruoyi.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JsonSheetWordRendererRegistry {

    private final Map<String, JsonSheetWordRenderer> rendererMap;

    public JsonSheetWordRendererRegistry(List<JsonSheetWordRenderer> renderers) {
        this.rendererMap = renderers.stream()
                .collect(Collectors.toMap(JsonSheetWordRenderer::sheetType, Function.identity(), (a, b) -> a));
    }

    public boolean supports(String sheetType) {
        return rendererMap.containsKey(sheetType);
    }

    public List<String> supportedTypes() {
        return Collections.unmodifiableList(new ArrayList<>(rendererMap.keySet()));
    }

    public JsonSheetWordRenderer getRequired(String sheetType) {
        JsonSheetWordRenderer renderer = rendererMap.get(sheetType);
        if (renderer == null) {
            throw new ServiceException("该表格类型暂不支持 Word 渲染：" + sheetType);
        }
        return renderer;
    }
}
