package edu.whut.cs.bi.biz.service.std.evaluation;

import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.enums.SingleIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 5230 人工判定录入逐行校验器（sync/upload 与直连接口共用）。
 *
 * <p>只做行内值域校验，返回错误消息列表（可多条）而不抛异常，默认值原地回填，
 * 供同步链路逐行 addError 继续处理。任务/构件/部件的结构归属不在此处校验：
 * 同步行以 UUID/partKey 锚定，结构解析发生在 calculate 阶段。</p>
 */
@Component
public class AssessInputRowValidator {

    private static final int EVIDENCE_MAX = 500;

    /** 构件级人工判定：EDDI 0/1/2、ESDI 覆盖 0~3、EFI 0/1/2、EAI -1/0/1。 */
    public List<String> validateManualInput(AssessManualInput row) {
        List<String> errors = new ArrayList<>();
        if (row.getTaskId() == null) {
            errors.add("构件人工判定缺少 taskId");
        }
        if (row.getElementUuid() == null || row.getElementUuid().isEmpty()) {
            errors.add("构件人工判定缺少 elementUuid");
        }
        row.setEddi(checkRange("eddi", row.getEddi(), 0, 2, errors));
        row.setEsdiOverride(checkRange("esdiOverride", row.getEsdiOverride(), 0, 3, errors));
        row.setEfi(checkRange("efi", row.getEfi(), 0, 2, errors));
        row.setEai(checkRange("eai", row.getEai(), -1, 1, errors));
        if (row.getSafetyAffected() == null) {
            row.setSafetyAffected(0);
        } else if (row.getSafetyAffected() != 0 && row.getSafetyAffected() != 1) {
            errors.add("safetyAffected 仅允许 0/1");
        }
        row.setEvidence(truncate(row.getEvidence(), errors));
        return errors;
    }

    /** 应设未设标记：partKey 必填；unit/span 可空（空=全桥各单元/该单元各跨）。 */
    public List<String> validateAbsent(AssessAbsentMark row) {
        List<String> errors = new ArrayList<>();
        if (row.getTaskId() == null) {
            errors.add("应设未设标记缺少 taskId");
        }
        if (row.getPartKey() == null || row.getPartKey().isEmpty()) {
            errors.add("应设未设标记缺少 partKey");
        }
        if (row.getLayerKey() != null && !row.getLayerKey().isEmpty()
                && !List.of("deck", "superstructure", "substructure", "ancillary").contains(row.getLayerKey())) {
            errors.add("layerKey 非法: " + row.getLayerKey());
        }
        row.setEvidence(truncate(row.getEvidence(), errors));
        return errors;
    }

    /** 16 项单项控制：indicatorNo 1~16，hit 0/1。 */
    public List<String> validateSingleControl(AssessSingleControlMark row) {
        List<String> errors = new ArrayList<>();
        if (row.getTaskId() == null) {
            errors.add("单项控制标记缺少 taskId");
        }
        if (row.getIndicatorNo() == null) {
            errors.add("单项控制标记缺少 indicatorNo");
        } else {
            try {
                SingleIndicator.valueOfNo(row.getIndicatorNo());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }
        if (row.getHit() == null) {
            row.setHit(1);
        } else if (row.getHit() != 0 && row.getHit() != 1) {
            errors.add("单项控制 hit 仅允许 0/1");
        }
        row.setEvidence(truncate(row.getEvidence(), errors));
        return errors;
    }

    private Integer checkRange(String field, Integer value, int min, int max, List<String> errors) {
        if (value == null) {
            return null; // 未录入：EDDI/EFI 由引擎按 0，ESDI 覆盖缺省走自动
        }
        if (value < min || value > max) {
            errors.add(field + " 取值越界 [" + min + "," + max + "]: " + value);
        }
        return value;
    }

    private String truncate(String value, List<String> errors) {
        if (value != null && value.length() > EVIDENCE_MAX) {
            errors.add("evidence 超过 " + EVIDENCE_MAX + " 字已截断");
            return value.substring(0, EVIDENCE_MAX);
        }
        return value;
    }
}
