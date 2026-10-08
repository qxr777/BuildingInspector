package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ComponentEval;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 构件级评定（JTG/T 5230—2026 第 3.2 节）。
 *
 * <ul>
 *   <li>ESDI = 参与评定的结构性病害标度最大值（0~3）；</li>
 *   <li>EDDI 人工录入；EDI = max(EDDI, ESDI)；</li>
 *   <li>EFI 人工录入；EAI 仅主要构件可取 -1/1，非主要部件强制为 0；</li>
 *   <li>ECI = EDI+EFI+EAI（上限 5、下限 0）；ECL 按表 3.2.6 映射。</li>
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
     * @param componentId 构件ID
     * @param partKey 所属部件 key
     * @param diseases 该构件参与评定的 5230-2026 病害
     * @param input 人工录入（无录入行时为 null）
     * @param mainPart 是否主要部件
     * @param warnings 非阻断告警
     */
    public ComponentEval evaluate(Long componentId, String partKey, List<Disease> diseases,
                                  V2ComponentInput input, boolean mainPart, EvalWarnings warnings) {
        int esdi = 0;
        if (diseases != null) {
            for (Disease disease : diseases) {
                esdi = Math.max(esdi, disease.getLevel());
            }
        }
        esdi = Math.min(3, esdi);

        int eddi = input != null && input.getEddi() != null ? input.getEddi() : 0;
        int efi = input != null && input.getEfi() != null ? input.getEfi() : 0;

        int eai = 0;
        if (mainPart) {
            eai = input != null && input.getEai() != null ? input.getEai() : 0;
        } else if (input != null && input.getEai() != null && input.getEai() != 0) {
            warnings.add("构件(id=" + componentId + ")属于非主要部件，EAI 强制为 0（录入值 "
                    + input.getEai() + "）");
        }

        int edi = Math.max(eddi, esdi);
        int eci = math.clampEci(edi + efi + eai);

        ComponentEval eval = new ComponentEval();
        eval.setComponentId(componentId);
        eval.setPartKey(partKey);
        eval.setEsdi(esdi);
        eval.setEddi(eddi);
        eval.setEdi(edi);
        eval.setEfi(efi);
        eval.setEai(eai);
        eval.setEci(eci);
        eval.setEcl(toEcl(eci));
        return eval;
    }

    /** 表 3.2.6：ECI 0/1→1类，2→2，3→3，4→4，5→5。 */
    private int toEcl(int eci) {
        if (eci <= 1) {
            return 1;
        }
        return eci;
    }
}
