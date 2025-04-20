package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.mapper.BiEvaluationMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.service.IScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * 桥梁技术状况评定Service业务层处理
 */
@Service
public class BiEvaluationServiceImpl implements IBiEvaluationService {
    @Autowired
    private BiEvaluationMapper biEvaluationMapper;

    @Autowired
    private IBiObjectService biObjectService;

    @Autowired
    private IConditionService conditionService;

    @Autowired
    private IScoreService scoreService;

    private static BigDecimal superWeight;
    private static BigDecimal subWeight;
    private static BigDecimal deckWeight;

    @Override
    public BiEvaluation selectBiEvaluationById(Long id) {
        return biEvaluationMapper.selectBiEvaluationById(id);
    }

    @Override
    public List<BiEvaluation> selectBiEvaluationList(BiEvaluation biEvaluation) {
        return biEvaluationMapper.selectBiEvaluationList(biEvaluation);
    }

    @Override
    public BiEvaluation selectBiEvaluationByTaskId(Long taskId) {
        BiEvaluation biEvaluation = new BiEvaluation();
        biEvaluation.setTaskId(taskId);
        List<BiEvaluation> evaluations = selectBiEvaluationList(biEvaluation);
        return evaluations.isEmpty() ? null : evaluations.get(0);
    }

    @Override
    public int insertBiEvaluation(BiEvaluation biEvaluation) {
        return biEvaluationMapper.insertBiEvaluation(biEvaluation);
    }

    @Override
    public int updateBiEvaluation(BiEvaluation biEvaluation) {
        return biEvaluationMapper.updateBiEvaluation(biEvaluation);
    }

    @Override
    public int deleteBiEvaluationByIds(String ids) {
        return biEvaluationMapper.deleteBiEvaluationByIds(ids.split(","));
    }

    @Override
    public int deleteBiEvaluationById(Long id) {
        return biEvaluationMapper.deleteBiEvaluationById(id);
    }

    @Override
    @Transactional
    /**
     * 计算桥幅得分
     */
    public BiEvaluation calculateBiEvaluation(Long taskId, Long rootObjectId) {
        // 1. 获取或创建评定记录
        BiEvaluation biEvaluation = selectBiEvaluationByTaskId(taskId);
        if (biEvaluation == null) {
            biEvaluation = new BiEvaluation();
            biEvaluation.setTaskId(taskId);
            biEvaluation.setCreateBy(ShiroUtils.getLoginName());
            biEvaluation.setCreateTime(new Date());
            insertBiEvaluation(biEvaluation);
        }

        // 2. 获取所有第二层的节点
        List<BiObject> secondLevelObject = biObjectService.selectDirectChildrenByParentId(rootObjectId);
        for (int i = 0; i < secondLevelObject.size(); i++) {
            BiObject object = secondLevelObject.get(i);
            if (object.getWeight() != null) {
                calculatePartScore(object, biEvaluation);
            }
        }

        // 3. 计算桥梁总体技术状况得分
        calculateOverallScore(biEvaluation);

        // 4. 更新评定记录
        biEvaluation.setUpdateBy(ShiroUtils.getLoginName());
        biEvaluation.setUpdateTime(new Date());
        updateBiEvaluation(biEvaluation);

        return biEvaluation;
    }

    /**
     * 计算部分（上部结构、下部结构、桥面系统）得分
     */
    private void calculatePartScore(BiObject part, BiEvaluation biEvaluation) {
        // 获取该部分下所有孩子节点
        List<BiObject> leafNodes = biObjectService.selectDirectChildrenByParentId(part.getId());

        // 计算加权平均分
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        Integer worstLevel = 1;

        for (BiObject leaf : leafNodes) {
            // 计算部件得分
            Condition condition = conditionService.calculateCondition(leaf, biEvaluation.getId());
            if (condition != null) {
                BigDecimal weight = leaf.getWeight() != null ? leaf.getWeight() : BigDecimal.ZERO;
                totalWeight = totalWeight.add(weight);
                weightedScore = weightedScore.add(condition.getScore().multiply(weight));

                // 更新最差等级
                if (condition.getLevel() > worstLevel) {
                    worstLevel = condition.getLevel();
                }
            }
        }

        // 计算最终得分
        BigDecimal finalScore = totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
                weightedScore.divide(totalWeight, 2, RoundingMode.HALF_UP) : new BigDecimal("100");
        int level = calculateLevel(finalScore);
        // 设置得分和等级
        switch (part.getName()) {
            case "上部结构":
                biEvaluation.setSuperstructureScore(finalScore);
                biEvaluation.setSuperstructureLevel(level);
                superWeight = part.getWeight();
                break;
            case "下部结构":
                biEvaluation.setSubstructureScore(finalScore);
                biEvaluation.setSubstructureLevel(level);
                subWeight = part.getWeight();
                break;
            case "桥面系":
                biEvaluation.setDeckSystemScore(finalScore);
                biEvaluation.setDeckSystemLevel(level);
                deckWeight = part.getWeight();
                break;
        }
    }

    /**
     * 计算桥梁总体技术状况得分
     */
    private void calculateOverallScore(BiEvaluation biEvaluation) {
        // 1. 计算系统总分
        BigDecimal superScore = biEvaluation.getSuperstructureScore();
        BigDecimal subScore = biEvaluation.getSubstructureScore();
        BigDecimal deckScore = biEvaluation.getDeckSystemScore();

        BigDecimal systemScore = superScore.multiply(superWeight)
                .add(subScore.multiply(subWeight))
                .add(deckScore.multiply(deckWeight));

        biEvaluation.setSystemScore(systemScore);

        // 2. 确定最差部位等级
        int worstLevel = Math.max(
                Math.max(
                        biEvaluation.getSuperstructureLevel(),
                        biEvaluation.getSubstructureLevel()
                ),
                biEvaluation.getDeckSystemLevel()
        );

        biEvaluation.setWorstPartLevel(worstLevel);

        // 3. 设置系统等级（取决于系统得分）
        biEvaluation.setSystemLevel(calculateLevel(systemScore));
        //当上部结构和下部结构技术状况等级为3类、桥面系技术状况等级为4类，且桥梁总体技术状况评分为40<D<60时，桥梁总体技术状况等级应评定为3类。
        if (biEvaluation.getSuperstructureLevel() == 3 && biEvaluation.getSubstructureLevel() == 3 && biEvaluation.getDeckSystemLevel() == 4 && biEvaluation.getSystemLevel() == 4) {
            biEvaluation.setSystemLevel(3);
        }

        // 4. 设置总体等级（取最差等级）
        //biEvaluation.setLevel(worstLevel);
    }

    /**
     * 根据得分计算技术状况等级
     */
    private Integer calculateLevel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("95")) >= 0) {
            return 1;
        } else if (score.compareTo(new BigDecimal("80")) >= 0) {
            return 2;
        } else if (score.compareTo(new BigDecimal("60")) >= 0) {
            return 3;
        } else if (score.compareTo(new BigDecimal("40")) >= 0) {
            return 4;
        } else {
            return 5;
        }
    }
}