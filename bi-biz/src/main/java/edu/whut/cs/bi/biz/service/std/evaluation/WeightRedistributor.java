package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 附录 B.1.3 权重再分配。
 *
 * <p>以部件在全桥各跨的实际设置情况为准：</p>
 * <ul>
 *   <li>部件全桥所有跨均未设置、且无应设未设标记 → 全桥无须设置：
 *       其 ω 在同一结构层内按比例并入保留部件 ω′=ω·100/Σ_R ω，γ 不变，全桥统一；</li>
 *   <li>部件仅部分跨设置 → 不再分配，缺失跨 CI=0，各跨统一按 ω′ 计；</li>
 *   <li>经标记的应设未设部件 → 保留原始 ω、不进 R、不参与再分配。</li>
 * </ul>
 */
@Component
public class WeightRedistributor {

    private static final int EFFECTIVE_SCALE = 4;

    /**
     * 原地改写各部件 omegaEffective。
     *
     * @param unit 解析后的评定单元
     * @param presentSpanPartKeys 实际设有构件的 (spanId, partKey) 集合
     * @param warnings 非阻断告警
     */
    public void applyB13(ResolvedUnit unit, Set<String> presentSpanPartKeys, EvalWarnings warnings) {
        List<String> partKeys = unit.allPartKeys();

        // 部件分类：absent（存在应设未设标记）/ presentSpans（实际设置的跨）
        Map<String, Boolean> absentAny = new LinkedHashMap<>();
        Map<String, Set<Long>> presentSpans = new LinkedHashMap<>();
        for (String key : partKeys) {
            absentAny.put(key, false);
            presentSpans.put(key, new HashSet<>());
        }
        for (ResolvedSpan span : unit.getSpans()) {
            for (ResolvedPart part : span.getParts()) {
                if (part.isAbsent()) {
                    absentAny.put(part.getPartKey(), true);
                } else if (presentSpanPartKeys.contains(key(span.getSpanId(), part.getPartKey()))) {
                    presentSpans.get(part.getPartKey()).add(span.getSpanId());
                }
            }
        }

        // 全桥无须设置的部件
        Set<String> unnecessary = new HashSet<>();
        for (String key : partKeys) {
            if (presentSpans.get(key).isEmpty() && !absentAny.get(key)) {
                unnecessary.add(key);
                warnings.add("部件 " + key + " 全桥各跨均未设置且无应设未设标记，按 B.1.3 判为全桥无须设置");
            }
        }

        // 按结构层分组做比例再分配（结构层信息取模板部件）
        Map<String, List<String>> partsByLayer = new LinkedHashMap<>();
        for (String key : partKeys) {
            ResolvedPart template = unit.findPart(key);
            partsByLayer.computeIfAbsent(template.getLayerKey(), k -> new java.util.ArrayList<>()).add(key);
        }

        for (Map.Entry<String, List<String>> layerEntry : partsByLayer.entrySet()) {
            String layerKey = layerEntry.getKey();
            List<String> layerParts = layerEntry.getValue();

            // R = 层内保留部件（非全桥无须设置）
            List<String> retained = new java.util.ArrayList<>();
            for (String key : layerParts) {
                if (!unnecessary.contains(key)) {
                    retained.add(key);
                }
            }

            int sumR = 0;
            boolean hasAbsent = false;
            for (String key : retained) {
                ResolvedPart template = unit.findPart(key);
                if (absentAny.get(key)) {
                    hasAbsent = true;
                } else {
                    sumR += template.getOmega();
                }
            }

            if (sumR == 0) {
                if (retained.isEmpty() || !hasAbsent) {
                    throw new ServiceException(
                            "结构层 " + layerKey + " 权重再分配后保留集合为空，无法评定，请检查结构或补应设未设标记");
                }
                // 层内仅剩应设未设部件：权重原样保留，不做再分配
                for (ResolvedSpan span : unit.getSpans()) {
                    for (ResolvedPart part : span.getParts()) {
                        if (layerKey.equals(part.getLayerKey()) && retained.contains(part.getPartKey())) {
                            part.setOmegaEffective(effective(part.getOmega()));
                        }
                    }
                }
                warnings.add("结构层 " + layerKey + " 内仅有应设未设部件，权重原样保留");
                continue;
            }

            // ω′ 按 key 统一计算
            Map<String, BigDecimal> redistributed = new HashMap<>();
            for (String key : retained) {
                ResolvedPart template = unit.findPart(key);
                if (absentAny.get(key)) {
                    // 应设未设：保留原始权重
                    redistributed.put(key, effective(template.getOmega()));
                } else {
                    BigDecimal prime = BigDecimal.valueOf(template.getOmega())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(sumR), EFFECTIVE_SCALE, RoundingMode.HALF_UP);
                    redistributed.put(key, prime);
                }
            }
            for (String key : unnecessary) {
                if (layerParts.contains(key)) {
                    redistributed.put(key, BigDecimal.ZERO.setScale(EFFECTIVE_SCALE, RoundingMode.HALF_UP));
                }
            }

            for (ResolvedSpan span : unit.getSpans()) {
                for (ResolvedPart part : span.getParts()) {
                    if (layerKey.equals(part.getLayerKey())) {
                        BigDecimal omega = redistributed.get(part.getPartKey());
                        if (omega != null) {
                            part.setOmegaEffective(omega);
                        }
                    }
                }
            }
        }
    }

    /** 组装 presentSpanPartKeys 集合元素。 */
    public static String key(Long spanId, String partKey) {
        return spanId + "|" + partKey;
    }

    private static BigDecimal effective(int omega) {
        return BigDecimal.valueOf(omega).setScale(EFFECTIVE_SCALE, RoundingMode.HALF_UP);
    }
}
