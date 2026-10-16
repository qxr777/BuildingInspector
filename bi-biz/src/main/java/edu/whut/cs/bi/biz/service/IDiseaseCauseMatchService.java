package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.dto.CauseMatchQuery;
import edu.whut.cs.bi.biz.domain.vo.DiseaseCauseMatch;

import java.util.List;

/**
 * 病害成因/处治建议匹配：程序按模板、部位、病害名称缩小知识库范围，大模型只从候选中选最接近的条目。
 */
public interface IDiseaseCauseMatchService {

    DiseaseCauseMatch match(CauseMatchQuery query);

    /**
     * @param disease 需包含 biObjectId、type（或 diseaseType），可选 biObjectName、crackType、position、description
     * @param level   截取处治建议所用的评定标度，可为 null
     */
    DiseaseCauseMatch match(Disease disease, Integer level);

    /**
     * 并发匹配多条病害，结果顺序与入参一致；单条异常记为 FAILED，不影响其他条。
     */
    List<DiseaseCauseMatch> matchAll(List<Disease> diseases, List<Integer> levels);
}
