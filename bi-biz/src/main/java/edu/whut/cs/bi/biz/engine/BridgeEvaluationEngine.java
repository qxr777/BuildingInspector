package edu.whut.cs.bi.biz.engine;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObjectComponent;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.mapper.BiEvaluationMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectComponentMapper;
import edu.whut.cs.bi.biz.service.IComponentService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026《公路桥梁技术状况评定标准》计算引擎
 * 核心逻辑：以“跨”为评定单元，基于构件四维指标（EDI, EFI, EAI）进行加权汇总。
 */
@Service
public class BridgeEvaluationEngine {

    @Autowired
    private IBiObjectService biObjectService;

    @Autowired
    private IComponentService componentService;

    @Autowired
    private BiObjectComponentMapper biObjectComponentMapper;

    @Autowired
    private BiEvaluationMapper biEvaluationMapper;

    @Autowired
    private edu.whut.cs.bi.biz.mapper.BiEvalComponentDetailMapper biEvalComponentDetailMapper;

    /**
     * 计算并保存指定单元（桥跨或全桥）的技术状况
     */
    public BiEvaluation evaluate(String targetType, Long targetId, Long taskId) {
        BiEvaluation eval = new BiEvaluation();
        eval.setTargetType(targetType);
        eval.setTargetId(targetId);
        eval.setTaskId(taskId);

        if ("SPAN".equals(targetType)) {
            calculateSpanEvaluation(eval, targetId, taskId);
            // 恢复存储评定结果到 bi_evaluation
            biEvaluationMapper.insertBiEvaluation(eval);
        } else {
            // 全桥评定逻辑
            calculateBridgeEvaluation(eval, targetId, taskId);
            // 恢复存储评定结果到 bi_evaluation
            biEvaluationMapper.insertBiEvaluation(eval);
        }

        return eval;
    }

    /**
     * 计算单跨评分 (2026 新标逻辑)
     */
    private void calculateSpanEvaluation(BiEvaluation eval, Long spanId, Long taskId) {
        // 1. 获取该跨关联的所有构件 (物理归属 + 逻辑关联)
        List<Component> components = componentService.selectComponentsByObjectIdForEval(spanId);

        // 获取多对多关联表的权重细节 (Map 加速查找)
        BiObjectComponent relQuery = new BiObjectComponent();
        relQuery.setBiObjectId(spanId);
        List<BiObjectComponent> rels = biObjectComponentMapper.selectBiObjectComponentList(relQuery);
        Map<Long, BigDecimal> relWeightMap = new HashMap<>();
        if (rels != null) {
            for (BiObjectComponent r : rels) {
                if (r.getWeight() != null) relWeightMap.put(r.getComponentId(), r.getWeight());
            }
        }

        // 2. 将构件按大类分组
        Map<String, List<ComponentWithWeight>> groups = new HashMap<>();
        groups.put("UPPER", new ArrayList<>());
        groups.put("LOWER", new ArrayList<>());
        groups.put("DECKSYSTEM", new ArrayList<>());

        for (Component component : components) {
            // 1. 获取当次任务的评价细节 (从实例隔离表读取 EDI, EFI, EAI)
            edu.whut.cs.bi.biz.domain.BiEvalComponentDetail detailQuery = new edu.whut.cs.bi.biz.domain.BiEvalComponentDetail();
            detailQuery.setTaskId(taskId);
            detailQuery.setSpanId(spanId);
            detailQuery.setComponentId(component.getId());
            List<edu.whut.cs.bi.biz.domain.BiEvalComponentDetail> detailList = biEvalComponentDetailMapper.selectList(detailQuery);
            if (detailList != null && !detailList.isEmpty()) {
                edu.whut.cs.bi.biz.domain.BiEvalComponentDetail d = detailList.get(0);
                component.setEdi(d.getEdi());
                component.setEfi(d.getEfi());
                component.setEai(d.getEai());
            } else {
                component.setEdi(null);
                component.setEfi(null);
                component.setEai(null);
            }

            // 2. 权重处理优先级：关联表权重 > 物理父节点(bi_object)权重 > 默认1.0
            BigDecimal weight = relWeightMap.get(component.getId());
            if (weight == null) {
                BiObject obj = biObjectService.selectBiObjectById(component.getBiObjectId());
                weight = (obj != null && obj.getWeight() != null) ? obj.getWeight() : BigDecimal.ONE;
            }

            String category = getComponentCategory(component.getBiObjectId());
            if (groups.containsKey(category)) {
                groups.get(category).add(new ComponentWithWeight(component, weight));
            }
        }

        // 3. 计算各部件得分 (BPI)
        eval.setSuperstructureScore(calculatePartScore(groups.get("UPPER")));
        eval.setSubstructureScore(calculatePartScore(groups.get("LOWER")));
        eval.setDeckSystemScore(calculatePartScore(groups.get("DECKSYSTEM")));

        // 4. 汇总跨得分 (BTI_span)
        eval.setSystemScore(aggregatePartScores(eval));
    }

    /**
     * 构件大类识别逻辑 (基于 ancestors 路径溯源)
     */
    private String getComponentCategory(Long biObjectId) {
        BiObject obj = biObjectService.selectBiObjectById(biObjectId);
        if (obj == null || obj.getAncestors() == null) return "UNKNOWN";

        String ancestors = obj.getAncestors();
        if (ancestors.contains("上部结构") || ancestors.contains("UPPER")) return "UPPER";
        if (ancestors.contains("下部结构") || ancestors.contains("LOWER")) return "LOWER";
        if (ancestors.contains("桥面系") || ancestors.contains("DECK")) return "DECKSYSTEM";
        
        // 扩展逻辑：ID 溯源
        String[] ids = ancestors.split(",");
        if (ids.length >= 3) {
            try {
                Long partId = Long.parseLong(ids[2].trim());
                BiObject partNode = biObjectService.selectBiObjectById(partId);
                if (partNode != null) {
                    String name = partNode.getName();
                    if (name.contains("上部")) return "UPPER";
                    if (name.contains("下部")) return "LOWER";
                    if (name.contains("桥面")) return "DECKSYSTEM";
                }
            } catch (Exception ignored) {}
        }

        return "UNKNOWN";
    }

    /**
     * 计算单个部件得分 (BPI) - 构件加权汇总
     */
    private BigDecimal calculatePartScore(List<ComponentWithWeight> components) {
        if (components == null || components.isEmpty()) return new BigDecimal("100.00");

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalWeightedScore = BigDecimal.ZERO;

        for (ComponentWithWeight cw : components) {
            BigDecimal compScore = calculateComponentScore(cw.component);
            BigDecimal weight = cw.weight != null ? cw.weight : BigDecimal.ONE;
            
            totalWeightedScore = totalWeightedScore.add(compScore.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }

        return totalWeight.signum() > 0 ? 
               totalWeightedScore.divide(totalWeight, 2, RoundingMode.HALF_UP) : new BigDecimal("100.00");
    }

    /**
     * 2026标构件级扣分算法 (EDI, EFI, EAI)
     */
    private BigDecimal calculateComponentScore(Component c) {
        // 若全部指标为空，视为无病害
        if (c.getEdi() == null && c.getEfi() == null) {
            return new BigDecimal("100.00");
        }

        double penalty = 0.0;
        
        // 1. EDI (0-3): 缺损状况 (缺损最不利值)
        if (c.getEdi() != null) {
            if (c.getEdi() == 3) penalty = Math.max(penalty, 100.0);
            else if (c.getEdi() == 2) penalty = Math.max(penalty, 55.0);
            else if (c.getEdi() == 1) penalty = Math.max(penalty, 15.0);
        }

        // 2. EFI (0-2): 功能状况, 默认0(正常)
        Integer efi = c.getEfi() != null ? c.getEfi() : 0;
        if (efi == 2) penalty = Math.max(penalty, 40.0);
        else if (efi == 1) penalty = Math.max(penalty, 15.0);

        // 3. EAI (-1 ~ 1): 影响状况修正系数, 默认-1(稳定)
        double modifier = 1.0;
        Integer eai = c.getEai() != null ? c.getEai() : -1;
        if (eai == 1) modifier = 1.25;
        else if (eai == -1) modifier = 0.85;

        double finalScore = Math.max(0.0, 100.0 - penalty * modifier);
        return new BigDecimal(finalScore).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 部件得分汇总 (采用 2026标常见的最不利权重控制逻辑)
     */
    private BigDecimal aggregatePartScores(BiEvaluation eval) {
        // 此处采用标准部件加权公式
        BigDecimal score = eval.getSuperstructureScore().multiply(new BigDecimal("0.4"))
                .add(eval.getSubstructureScore().multiply(new BigDecimal("0.4")))
                .add(eval.getDeckSystemScore().multiply(new BigDecimal("0.2")));
        
        return score.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 全桥评定逻辑 (跨径汇总)
     */
    private void calculateBridgeEvaluation(BiEvaluation eval, Long bridgeId, Long taskId) {
        // 1. 查询该任务下所有已计算的分跨评定结果
        BiEvaluation query = new BiEvaluation();
        query.setTaskId(taskId);
        query.setTargetType("SPAN");
        List<BiEvaluation> spanEvals = biEvaluationMapper.selectBiEvaluationList(query);

        if (spanEvals == null || spanEvals.isEmpty()) {
            eval.setSystemScore(new BigDecimal("100.00"));
            return;
        }

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal minScore = new BigDecimal("100.00");
        
        for (BiEvaluation spanEval : spanEvals) {
            BigDecimal s = spanEval.getSystemScore() != null ? spanEval.getSystemScore() : new BigDecimal("100.00");
            totalScore = totalScore.add(s);
            if (s.compareTo(minScore) < 0) {
                minScore = s;
            }
        }

        BigDecimal avgScore = totalScore.divide(new BigDecimal(spanEvals.size()), 2, RoundingMode.HALF_UP);
        
        BigDecimal finalScore = avgScore;
        if (minScore.compareTo(new BigDecimal("60.00")) < 0) {
            finalScore = minScore; // 触及四类/五类控制项
        }

        eval.setSystemScore(finalScore);
        eval.setTargetType("BRIDGE");
        eval.setTargetId(bridgeId);
        
        eval.setSuperstructureScore(averagePartScore(spanEvals, "SUPER"));
        eval.setSubstructureScore(averagePartScore(spanEvals, "SUB"));
        eval.setDeckSystemScore(averagePartScore(spanEvals, "DECK"));
    }

    private BigDecimal averagePartScore(List<BiEvaluation> evals, String type) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (BiEvaluation e : evals) {
            BigDecimal s = null;
            if ("SUPER".equals(type)) s = e.getSuperstructureScore();
            else if ("SUB".equals(type)) s = e.getSubstructureScore();
            else if ("DECK".equals(type)) s = e.getDeckSystemScore();
            
            if (s != null) {
                total = total.add(s);
                count++;
            }
        }
        return count > 0 ? total.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP) : new BigDecimal("100.00");
    }

    private static class ComponentWithWeight {
        Component component;
        BigDecimal weight;
        ComponentWithWeight(Component c, BigDecimal w) { this.component = c; this.weight = w; }
    }
}
