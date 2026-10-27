package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 构件级评定（JTG/T 5230—2026 第 3.2 节）。
 *
 * <ul>
 *   <li>ESDI = 参与评定的结构性病害标度最大值（0~3），人工覆盖优先（3.2.3）；</li>
 *   <li>EDDI 人工录入（第 4 章，缺省 0）；EDI = max(EDDI, ESDI)；</li>
 *   <li>应设未设幻影构件（3.2.2-5）：EDI 直接取 3；</li>
 *   <li>EFI 人工录入（第 14 章）；EAI 仅主要构件可取 -1/1，非主要部件强制为 0（3.2.5）；</li>
 *   <li>ECI = EDI+EFI+EAI（截断 [0,5]）；ECL 按表 3.2.6 映射。</li>
 * </ul>
 */
@Component
public class ComponentEvaluator {

    private final EvaluationMath math;

    public ComponentEvaluator(EvaluationMath math) {
        this.math = math;
    }

    /**
     * 评定单个构件。
     *
     * @param input 构件评定输入（真实构件或应设未设幻影构件）
     * @param warnings 非阻断告警（非主要部件 EAI 被强制归 0 等）
     */
    public ComponentEval evaluate(ComponentInput input, EvalWarnings warnings) {
        ComponentEval eval = new ComponentEval();
        eval.setUnitId(input.getUnitId());
        eval.setSpanId(input.getSpanId());
        eval.setPartId(input.getPartId());
        eval.setPartKey(input.getPartKey());
        eval.setLayerKey(input.getLayerKey());
        eval.setElementId(input.getElementId());
        eval.setElementUuid(input.getElementUuid());
        eval.setElementName(input.getElementName());
        eval.setMainPart(input.isMainPart());
        eval.setShared(input.isShared());
        eval.setAbsent(input.isAbsent());
        eval.setSafetyAffected(Boolean.TRUE.equals(input.getSafetyAffected()));

        if (input.isAbsent()) {
            // 3.2.2-5 应设未设部件：EDI=3，无分项病害/功能/影响判定
            eval.setEddi(0);
            eval.setEsdi(0);
            eval.setEdi(3);
            eval.setEfi(0);
            eval.setEai(0);
            eval.setEddiSource(ComponentEval.SOURCE_ABSENT);
            eval.setEsdiSource(ComponentEval.SOURCE_ABSENT);
        } else {
            // ESDI：人工覆盖（3.2.3）优先于结构性病害自动 max
            int esdiAuto = maxStructuralScale(input.getStructuralScales());
            if (input.getEsdiOverride() != null) {
                eval.setEsdi(input.getEsdiOverride());
                eval.setEsdiSource(ComponentEval.SOURCE_MANUAL);
            } else {
                eval.setEsdi(esdiAuto);
                eval.setEsdiSource(ComponentEval.SOURCE_AUTO);
            }

            // EDDI：一期人工录入（第 4 章材料耐久字典自动判定为后批钩子）
            int eddi = input.getEddiManual() != null ? input.getEddiManual() : 0;
            eval.setEddi(eddi);
            eval.setEddiSource(input.getEddiManual() != null
                    ? ComponentEval.SOURCE_MANUAL : ComponentEval.SOURCE_AUTO);

            eval.setEdi(Math.max(eddi, eval.getEsdi()));

            // EFI：人工录入（第 14 章），缺省 0
            eval.setEfi(input.getEfiManual() != null ? input.getEfiManual() : 0);

            // EAI：仅主要构件可取非 0（3.2.5）
            int eai = 0;
            Integer inputEai = input.getEaiManual();
            if (input.isMainPart()) {
                eai = inputEai != null ? inputEai : 0;
            } else if (inputEai != null && inputEai != 0) {
                warnings.add("构件(uuid=" + input.getElementUuid() + ")属于非主要部件，EAI 强制为 0（录入值 "
                        + inputEai + "）");
            }
            eval.setEai(eai);
        }

        int eci = math.clampEci(eval.getEdi() + eval.getEfi() + eval.getEai());
        eval.setEci(eci);
        eval.setEcl(toEcl(eci));
        return eval;
    }

    private int maxStructuralScale(List<Integer> scales) {
        if (scales == null || scales.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (Integer scale : scales) {
            if (scale != null) {
                max = Math.max(max, scale);
            }
        }
        return Math.min(3, max);
    }

    /** 表 3.2.6：ECI 0/1→1类，2→2，3→3，4→4，5→5。 */
    private int toEcl(int eci) {
        if (eci <= 1) {
            return 1;
        }
        return eci;
    }
}
