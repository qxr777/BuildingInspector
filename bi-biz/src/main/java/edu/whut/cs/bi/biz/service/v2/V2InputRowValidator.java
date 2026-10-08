package edu.whut.cs.bi.biz.service.v2;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.domain.enums.V2SingleIndicator;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.std.evaluation.StructureResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 新标评定输入逐行校验器。
 * 与旧网页整批校验的区别：返回错误消息列表（可多条）而不是抛异常，
 * 默认值原地回填，供同步链路逐行 addError 继续处理。
 */
@Component
public class V2InputRowValidator {

    private static final int EVIDENCE_MAX = 500;

    private final BiObjectMapper biObjectMapper;
    private final StructureResolver structureResolver;

    public V2InputRowValidator(BiObjectMapper biObjectMapper,
                               StructureResolver structureResolver) {
        this.biObjectMapper = biObjectMapper;
        this.structureResolver = structureResolver;
    }

    /**
     * 构件人工评定输入校验。
     */
    public List<String> validateComponent(V2ComponentInput row) {
        List<String> errors = new ArrayList<>();
        if (row.getComponentId() == null) {
            errors.add("构件输入缺少 componentId");
        }
        row.setEddi(normalize(errors, "eddi", row.getEddi(), 0, 2));
        row.setEfi(normalize(errors, "efi", row.getEfi(), 0, 2));
        row.setEai(normalize(errors, "eai", row.getEai(), -1, 1));
        if (row.getSafetyAffected() == null) {
            row.setSafetyAffected(0);
        } else if (row.getSafetyAffected() != 0 && row.getSafetyAffected() != 1) {
            errors.add("safety_affected 仅允许 0/1");
        }
        return errors;
    }

    /**
     * 应设未设部件标记校验。spanId 不校验（null=全桥所有跨，合法）。
     */
    public List<String> validateAbsent(V2AbsentMark row, String bridgeType) {
        List<String> errors = new ArrayList<>();
        if (row.getPartId() == null) {
            errors.add("应设未设标记缺少 partId");
            return errors;
        }
        BiObject partNode = biObjectMapper.selectBiObjectById(row.getPartId());
        if (partNode == null || !structureResolver.isTemplatePart(partNode, bridgeType)) {
            errors.add("应设未设部件(partId=" + row.getPartId()
                    + ")不属于桥型 " + bridgeType + " 模板部件");
        }
        return errors;
    }

    /**
     * 16 项单项控制指标标记校验。
     */
    public List<String> validateControl(V2SingleControlMark row) {
        List<String> errors = new ArrayList<>();
        if (row.getIndicatorNo() == null) {
            errors.add("单项控制标记缺少 indicatorNo");
        } else {
            try {
                V2SingleIndicator.valueOfNo(row.getIndicatorNo());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }
        if (row.getHit() == null) {
            row.setHit(0);
        } else if (row.getHit() != 0 && row.getHit() != 1) {
            errors.add("单项控制 hit 仅允许 0/1");
        }
        if (row.getEvidence() != null && row.getEvidence().length() > EVIDENCE_MAX) {
            row.setEvidence(row.getEvidence().substring(0, EVIDENCE_MAX));
        }
        return errors;
    }

    private Integer normalize(List<String> errors, String field, Integer value, int min, int max) {
        if (value == null) {
            return 0;
        }
        if (value < min || value > max) {
            errors.add(field + " 取值越界 [" + min + "," + max + "]: " + value);
        }
        return value;
    }
}
