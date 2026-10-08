package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.dto.V2InputSaveDto;

import java.util.Map;

/**
 * 新标 JTG/T 5230—2026 三级桥梁评定服务。
 *
 * <p>覆盖评定结构预览、人工评定输入只读查询、三级计算与结果查询。
 * 人工评定输入由检测员在 App 端录入、随 /api/v2/sync/upload 打包提交，网页端不提供写入。
 * 与旧标 H21-2011 评定链路完全独立，按任务 std_version 路由。</p>
 */
public interface IV2EvaluationService {

    /**
     * 预览评定单元解析结构（部件 key、ω、γ 解析来源与告警），不落任何数据。
     *
     * @param taskId 任务ID
     * @return {structure: ResolvedUnit, warnings: List}
     */
    Map<String, Object> previewTree(Long taskId);

    /**
     * 查询任务当前的全部人工评定输入（App 同步入库，只读）。
     */
    V2InputSaveDto getInputs(Long taskId);

    /**
     * 执行三级评定并全量重算落库（构件→桥跨→全桥）。
     */
    void calculate(Long taskId);

    /**
     * 查询全桥评定结果（全桥行 + 各跨 + 桥跨部件明细 + 构件评定）。
     *
     * @return {bridge, spans, spanPartDetails, componentEvaluations}
     */
    Map<String, Object> getResult(Long taskId);
}
