package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Score;
import edu.whut.cs.bi.biz.mapper.ConditionMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.service.IScoreService;
import edu.whut.cs.bi.biz.service.PreparedCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final int SCORE_INSERT_BATCH = 200;

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
    public Condition calculateCondition(BiObject biObject, Long biEvaluationId, Long projectId) {
        if (biObject == null) {
            throw new RuntimeException("计算失败：部件对象为空");
        }
        if (biEvaluationId == null) {
            throw new RuntimeException("计算失败：评定ID为空");
        }
        if (projectId == null) {
            throw new RuntimeException("计算失败：项目ID为空");
        }

        Condition existing = selectConditionByBiObjectId(biObject.getId(), biEvaluationId);
        List<Component> components = componentService.selectComponentsByBiObjectIdAndChildren(biObject.getId());
        if (components == null) {
            throw new RuntimeException("计算失败：无法获取部件的构件列表");
        }
        Map<Long, List<Disease>> diseasesByComponent = scoreService.loadParticipatingDiseases(projectId, components);
        PreparedCondition prepared = prepareCondition(biObject, existing, components, diseasesByComponent);
        prepared.getCondition().setBiEvaluationId(biEvaluationId);
        new TransactionTemplate(transactionManager).execute(status -> {
            writePreparedConditions(Collections.singletonList(prepared));
            return null;
        });
        return prepared.getCondition();
    }

    @Override
    public PreparedCondition prepareCondition(BiObject biObject, Condition existing, List<Component> components,
                                              Map<Long, List<Disease>> diseasesByComponent) {
        if (biObject == null) {
            throw new RuntimeException("计算失败：部件对象为空");
        }
        if (components == null) {
            throw new RuntimeException("计算失败：无法获取部件的构件列表");
        }

        Condition condition = existing;
        if (condition == null) {
            condition = new Condition();
            condition.setBiObjectId(biObject.getId());
            condition.setWeight(biObject.getWeight());
            condition.setCreateBy(ShiroUtils.getLoginName());
            condition.setCreateTime(new Date());
        }

        boolean replaceScores = !components.isEmpty();
        List<Score> scores = replaceScores
                ? scoreService.calculateScore(components, diseasesByComponent)
                : new ArrayList<>();
        if (scores == null) {
            scores = new ArrayList<>();
        }

        if (scores.isEmpty() || biObject.getWeight() == null || biObject.getWeight().compareTo(BigDecimal.ZERO) == 0) {
            condition.setScore(new BigDecimal("100"));
            condition.setLevel(1);
            if (biObject.getWeight() == null || biObject.getWeight().compareTo(BigDecimal.ZERO) == 0) {
                condition.setScore(new BigDecimal("0"));
                condition.setLevel(0);
            }
            condition.setComponentsCount(biObject.getCount() == null ? 0 : biObject.getCount());
            condition.setUpdateBy(ShiroUtils.getLoginName());
            condition.setUpdateTime(new Date());
            return new PreparedCondition(condition, scores, replaceScores);
        }

        Integer count = biObject.getCount();
        if (count == null || count <= 0) {
            throw new RuntimeException("计算失败：部件 " + biObject.getName() + " 构件数量为0");
        }
        int componentsCount = count;
        condition.setComponentsCount(componentsCount);

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

        if (lowestScore.compareTo(new BigDecimal("40")) < 0 || componentsCount == 1) {
            condition.setScore(lowestScore);
        } else {
            int fullScore = 100 * (componentsCount - scores.size());
            BigDecimal realTotalScore = totalScore.add(BigDecimal.valueOf(fullScore));
            BigDecimal averageScore = realTotalScore.divide(new BigDecimal(componentsCount), 4, RoundingMode.HALF_UP);
            double tValue = Condition.getT(componentsCount);
            if (tValue <= 0) {
                throw new RuntimeException("计算失败：无法获取有效的t值");
            }
            BigDecimal tValueDecimal = new BigDecimal(String.valueOf(tValue));
            BigDecimal adjustment = new BigDecimal("100").subtract(lowestScore)
                    .divide(tValueDecimal, 4, RoundingMode.HALF_UP);
            BigDecimal finalScore = averageScore.subtract(adjustment)
                    .setScale(2, RoundingMode.HALF_UP);
            if (finalScore.compareTo(BigDecimal.ZERO) < 0) {
                finalScore = BigDecimal.ZERO;
            }
            condition.setScore(finalScore);
        }

        BigDecimal displayedScore = condition.getScore().setScale(1, RoundingMode.HALF_UP);
        condition.setScore(displayedScore);
        condition.setLevel(calculateLevel(displayedScore));
        condition.setUpdateBy(ShiroUtils.getLoginName());
        condition.setUpdateTime(new Date());
        return new PreparedCondition(condition, scores, true);
    }

    @Override
    public void writePreparedConditions(List<PreparedCondition> preparedConditions) {
        if (preparedConditions == null || preparedConditions.isEmpty()) {
            return;
        }
        for (PreparedCondition prepared : preparedConditions) {
            if (prepared == null || prepared.getCondition() == null) {
                continue;
            }
            Condition condition = prepared.getCondition();
            if (condition.getId() == null) {
                insertCondition(condition);
            } else {
                updateCondition(condition);
            }
            if (condition.getId() == null) {
                throw new RuntimeException("计算失败：部件评定保存失败");
            }
            if (!prepared.isReplaceScores()) {
                continue;
            }
            scoreService.deleteScoreByConditionId(condition.getId());
            List<Score> scores = prepared.getScores();
            if (scores == null || scores.isEmpty()) {
                continue;
            }
            for (Score score : scores) {
                score.setConditionId(condition.getId());
            }
            for (int start = 0; start < scores.size(); start += SCORE_INSERT_BATCH) {
                int end = Math.min(start + SCORE_INSERT_BATCH, scores.size());
                scoreService.batchInsertScores(scores.subList(start, end));
            }
        }
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