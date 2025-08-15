package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Score;
import edu.whut.cs.bi.biz.mapper.ConditionMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.service.IScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 部件技术状况评分Service业务层处理
 */
@Service
public class ConditionServiceImpl implements IConditionService {
    @Autowired
    private ConditionMapper conditionMapper;

    @Autowired
    private IScoreService scoreService;

    @Autowired
    private ComponentServiceImpl componentService;

    @Autowired
    private IBiObjectService biObjectService;

    @Override
    public Condition selectConditionById(Long id) {
        return conditionMapper.selectConditionById(id);
    }

    @Override
    public List<Condition> selectConditionList(Condition condition) {
        return conditionMapper.selectConditionList(condition);
    }

    @Override
    public List<Condition> selectConditionsByBiEvaluationId(Long biEvaluationId) {
        Condition condition = new Condition();
        condition.setBiEvaluationId(biEvaluationId);
        return selectConditionList(condition);
    }

    @Override
    public Condition selectConditionByBiObjectId(Long biObjectId,Long biEvaluationId) {
        Condition condition = new Condition();
        condition.setBiObjectId(biObjectId);
        condition.setBiEvaluationId(biEvaluationId);
        List<Condition> conditions = selectConditionList(condition);
        return conditions.isEmpty() ? null : conditions.get(0);
    }

    @Override
    public int insertCondition(Condition condition) {
        return conditionMapper.insertCondition(condition);
    }

    @Override
    public int updateCondition(Condition condition) {
        return conditionMapper.updateCondition(condition);
    }

    @Override
    public int deleteConditionByIds(String ids) {
        return conditionMapper.deleteConditionByIds(ids.split(","));
    }

    @Override
    public int deleteConditionById(Long id) {
        return conditionMapper.deleteConditionById(id);
    }

    @Override
    @Transactional
    public Condition calculateCondition(BiObject biObject, Long biEvaluationId, Long projectId) {
        // 检查参数
        if (biObject == null) {
            throw new RuntimeException("计算失败：部件对象为空");
        }

        if (biEvaluationId == null) {
            throw new RuntimeException("计算失败：评定ID为空");
        }

        if (projectId == null) {
            throw new RuntimeException("计算失败：项目ID为空");
        }

        // 1. 获取或创建部件的Condition记录
        Condition condition = selectConditionByBiObjectId(biObject.getId(),biEvaluationId);
        if (condition == null) {
            condition = new Condition();
            condition.setBiObjectId(biObject.getId());
            condition.setBiEvaluationId(biEvaluationId);
            condition.setWeight(biObject.getWeight());
            condition.setCreateBy(ShiroUtils.getLoginName());
            condition.setCreateTime(new Date());
            insertCondition(condition);
        }

        // 2. 获取该部件下所有构件的得分
        List<Component> components = componentService.selectComponentsByBiObjectIdAndChildren(biObject.getId());
        if (components == null) {
            throw new RuntimeException("计算失败：无法获取部件的构件列表");
        }

        List<Score> scores = new ArrayList<>();
        if (components.size() > 0) {
            scores = scoreService.calculateScore(components, condition.getId(), projectId);
        }
        if (scores == null || scores.isEmpty()) {
            // 如果没有构件得分记录，说明没有病害记录，返回满分
            condition.setScore(new BigDecimal("100"));
            condition.setLevel(1);
            // 如果权重为0 则评分直接为0 等级为0
            if (biObject.getWeight() == null || biObject.getWeight().equals(new BigDecimal("0"))) {
                condition.setScore(new BigDecimal("0"));
                condition.setLevel(0);
            }

            condition.setComponentsCount(biObject.getCount());
            condition.setUpdateBy(ShiroUtils.getLoginName());
            condition.setUpdateTime(new Date());
            updateCondition(condition);
            return condition;
        }

        // 获取构件数量（用于查找t值）
        Integer count = biObject.getCount();
        if (count == null || count <= 0) {
            throw new RuntimeException("计算失败：部件 " + biObject.getName() + " 构件数量为0");
        }

        int componentsCount = count;
        condition.setComponentsCount(componentsCount);

        // 4. 计算部件得分
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal lowestScore = new BigDecimal("100");

        for (Score score : scores) {
            BigDecimal currentScore = score.getScore();
            if (currentScore == null) {
                throw new RuntimeException("计算失败：构件得分为空");
            }

            totalScore = totalScore.add(currentScore);
            if (currentScore.compareTo(lowestScore) < 0) {
                lowestScore = currentScore;
            }
        }

        // 如果构件中有得分低于60分的，部件得分取最低分
        if (lowestScore.compareTo(new BigDecimal("60")) < 0 || componentsCount == 1) {
            condition.setScore(lowestScore);
        } else {
            int fullScore = 100 * (componentsCount - scores.size());
            BigDecimal realTotalScore = totalScore.add(BigDecimal.valueOf(fullScore));
            // 5.计算平均分
            BigDecimal averageScore = realTotalScore.divide(new BigDecimal(componentsCount), 4, RoundingMode.HALF_UP);

            // 6.获取t值
            double tValue = Condition.getT(componentsCount);
            if (tValue <= 0) {
                throw new RuntimeException("计算失败：无法获取有效的t值");
            }

            BigDecimal tValueDecimal = new BigDecimal(String.valueOf(tValue));
            // 按照公式计算：DCCI_i = DMCI - (100 - DMCI_min)/t
            BigDecimal adjustment = new BigDecimal("100").subtract(lowestScore)
                    .divide(tValueDecimal, 4, RoundingMode.HALF_UP);
            BigDecimal finalScore = averageScore.subtract(adjustment)
                    .setScale(2, RoundingMode.HALF_UP);

            // 确保得分不小于0
            if (finalScore.compareTo(BigDecimal.ZERO) < 0) {
                finalScore = BigDecimal.ZERO;
            }

            condition.setScore(finalScore);
        }

        // 7. 设置技术状况等级
        condition.setLevel(calculateLevel(condition.getScore()));

        // 8. 更新记录
        condition.setUpdateBy(ShiroUtils.getLoginName());
        condition.setUpdateTime(new Date());
        updateCondition(condition);

        return condition;
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