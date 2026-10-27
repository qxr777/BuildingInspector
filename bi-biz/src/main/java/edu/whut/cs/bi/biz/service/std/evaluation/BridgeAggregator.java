package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.enums.SingleIndicator;
import edu.whut.cs.bi.biz.service.std.evaluation.model.BridgeAggregate;
import edu.whut.cs.bi.biz.service.std.evaluation.model.UnitEval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全桥聚合（JTG/T 5230—2026 第 3.4.4、3.4.5 条）。
 *
 * <ul>
 *   <li>3.4.4：划分评定单元时（纵向不同结构形式/横向拼宽），以最差评定单元结果为全桥结果。
 *       最差等级相同的单元有多个时，取其中 BCI 最低者（同级取更差）；单评定单元直通。</li>
 *   <li>3.4.5：16 项单项控制任一命中，BCL=5、BCI=min(分层BCI,40)，分层结果留痕。</li>
 * </ul>
 */
@Component
public class BridgeAggregator {

    public static final String MODE_SINGLE_UNIT = "SINGLE_UNIT";
    public static final String MODE_WORST_UNIT = "WORST_UNIT";

    private final EvaluationMath math;

    public BridgeAggregator(EvaluationMath math) {
        this.math = math;
    }

    /**
     * @param units 各评定单元的合成结果（至少 1 个）
     * @param hitIndicatorNos 命中的单项控制编号（服务层已按 hit='1' 过滤；可空）
     */
    public BridgeAggregate aggregate(List<UnitEval> units, List<Integer> hitIndicatorNos) {
        if (units == null || units.isEmpty()) {
            throw new ServiceException("无评定单元结果，无法进行全桥评定");
        }

        BridgeAggregate result = new BridgeAggregate();

        UnitEval worst;
        if (units.size() == 1) {
            result.setCombineMode(MODE_SINGLE_UNIT);
            worst = units.get(0);
        } else {
            // 3.4.4：等级最差（数值最大）；同级取 BCI 最低；再同级取 unitId 较小者保证确定性
            result.setCombineMode(MODE_WORST_UNIT);
            worst = units.stream()
                    .max(Comparator.comparingInt(UnitEval::getBcl)
                            .thenComparing(UnitEval::getBci, Comparator.reverseOrder())
                            .thenComparing(UnitEval::getUnitId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .get();
            result.setWorstUnitId(worst.getUnitId());
        }
        result.setLayeredBcl(worst.getBcl());
        result.setLayeredBci(worst.getBci());

        // 3.4.5 单项控制覆盖
        List<Integer> hits = new ArrayList<>();
        if (hitIndicatorNos != null) {
            for (Integer no : hitIndicatorNos) {
                if (no != null) {
                    SingleIndicator.valueOfNo(no); // 防御：非法序号直接抛错
                    if (!hits.contains(no)) {
                        hits.add(no);
                    }
                }
            }
        }
        Collections.sort(hits);
        result.setHitIndicatorNos(hits);

        result.setBcl(result.getLayeredBcl());
        result.setBci(result.getLayeredBci());
        if (!hits.isEmpty()) {
            result.setSingleControlHit(true);
            result.setSingleControlNos(hits.stream().map(String::valueOf).collect(Collectors.joining(",")));
            result.setBcl(5);
            // 与 BCL=5 一致：不高于 5 类档上限 40
            result.setBci(math.scale1(result.getLayeredBci().min(BigDecimal.valueOf(40))));
        }
        return result;
    }
}
