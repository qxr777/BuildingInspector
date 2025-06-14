package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Score;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.ScoreMapper;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IComponentService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.service.IDiseaseService;
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
 * 构件得分Service业务层处理
 */
@Service
public class ScoreServiceImpl implements IScoreService {
    @Autowired
    private ScoreMapper scoreMapper;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Override
    public Score selectScoreById(Long id) {
        return scoreMapper.selectScoreById(id);
    }

    @Override
    public List<Score> selectScoreList(Score score) {
        return scoreMapper.selectScoreList(score);
    }

    @Override
    public List<Score> selectScoresByConditionId(Long conditionId) {
        Score score = new Score();
        score.setConditionId(conditionId);
        return selectScoreList(score);
    }

    @Override
    public int insertScore(Score score) {
        return scoreMapper.insertScore(score);
    }

    @Override
    public int updateScore(Score score) {
        return scoreMapper.updateScore(score);
    }

    @Override
    public int deleteScoreByIds(String ids) {
        return scoreMapper.deleteScoreByIds(ids.split(","));
    }

    @Override
    public int deleteScoreById(Long id) {
        return scoreMapper.deleteScoreById(id);
    }

    @Override
    @Transactional
    public List<Score> calculateScore(List<Component> components, Long conditionId,Long projectId) {
        List<Score> allScores = new ArrayList<>();
        if (components != null && !components.isEmpty()) {
            List<Score> componentScores = new ArrayList<>();
            for (Component component : components) {
                // 获取构件的病害记录
                Disease queryDisease = new Disease();
                queryDisease.setComponentId(component.getId());
                queryDisease.setParticipateAssess("0");
                queryDisease.setProjectId(projectId);
                List<Disease> diseases = diseaseMapper.selectDiseaseList(queryDisease);;
                // 只处理有病害记录的构件
                if (diseases != null && !diseases.isEmpty()) {
                    Score score = calculateComponentScore(component, diseases, conditionId);
                    if (score != null) {
                        updateScore(score);
                        componentScores.add(score);
                    }
                }
            }

            // 如果有构件得分，添加到总列表中
            if (!componentScores.isEmpty()) {
                allScores.addAll(componentScores);
            }
        }
        return allScores;
    }

    /**
     * 计算单个构件的得分
     */
    private Score calculateComponentScore(Component component, List<Disease> diseases, Long conditionId) {
        // 初始化得分为100
        BigDecimal finalScore = new BigDecimal("100");
        // 按照病害记录的创建时间排序，确保按照发现顺序处理
        diseases.sort((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()));

        // 计算扣分
        BigDecimal totalDeduction = BigDecimal.ZERO;
        for (int i = 0; i < diseases.size(); i++) {
            Disease disease = diseases.get(i);
            // 获取病害类型的最大等级和当前等级
            Integer maxScale = disease.getDiseaseType().getMaxScale();
            Integer currentLevel = disease.getLevel();

            // 获取扣分值
            BigDecimal deduction = getDeductionValue(maxScale, currentLevel);

            // 如果单项扣分为100，则直接判定为0分
            if (deduction.compareTo(new BigDecimal("100")) == 0) {
                totalDeduction = new BigDecimal("100");
                break;
            }

            // 根据公式计算扣分
            if (i == 0) {
                // 当x=1时：U₁ = DP₁
                totalDeduction = deduction;
            } else {
                // 当x≥2时：Uₓ = (DP/100√x) × (100-∑U)
                // 1. 计算1/√x
                double sqrtX = 1.0 / Math.sqrt(i + 1);
                BigDecimal coefficient = new BigDecimal(String.valueOf(sqrtX));

                // 2. 计算DP/100
                BigDecimal normalizedDeduction = deduction.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                // 3. 计算(DP/100√x)
                BigDecimal adjustedDeduction = normalizedDeduction.multiply(coefficient);

                // 4. 计算(100-∑U)
                BigDecimal remainingScore = new BigDecimal("100").subtract(totalDeduction);

                // 5. 计算最终扣分
                BigDecimal currentDeduction = adjustedDeduction.multiply(remainingScore);

                // 6. 累加总扣分
                totalDeduction = totalDeduction.add(currentDeduction);
            }
        }

        // 计算最终得分
        finalScore = finalScore.subtract(totalDeduction);

        // 确保分数不小于0
        if (finalScore.compareTo(BigDecimal.ZERO) < 0) {
            finalScore = BigDecimal.ZERO;
        }

        // 创建得分对象
        Score score = scoreMapper.selectScoreBycomponentId(component.getId());
        if(score==null) {
            score = new Score();
            score.setCreateBy(ShiroUtils.getLoginName());
            score.setCreateTime(new Date());
            insertScore(score);
        }
        score.setScore(finalScore);
        score.setComponentId(component.getId());
        score.setConditionId(conditionId);
        score.setUpdateBy(ShiroUtils.getLoginName());
        score.setUpdateTime(new Date());

        return score;
    }

    /**
     * 根据病害类型最大等级和当前等级获取扣分值
     */
    private BigDecimal getDeductionValue(Integer maxScale, Integer currentLevel) {
        // 根据表4.1.1实现扣分逻辑
        int deduction = 0;

        switch (maxScale) {
            case 3:
                switch (currentLevel) {
                    case 1:
                        deduction = 0;
                        break;
                    case 2:
                        deduction = 20;
                        break;
                    case 3:
                        deduction = 35;
                        break;
                }
                break;
            case 4:
                switch (currentLevel) {
                    case 1:
                        deduction = 0;
                        break;
                    case 2:
                        deduction = 25;
                        break;
                    case 3:
                        deduction = 40;
                        break;
                    case 4:
                        deduction = 50;
                        break;
                }
                break;
            case 5:
                switch (currentLevel) {
                    case 1:
                        deduction = 0;
                        break;
                    case 2:
                        deduction = 35;
                        break;
                    case 3:
                        deduction = 45;
                        break;
                    case 4:
                        deduction = 60;
                        break;
                    case 5:
                        deduction = 100;
                        break;
                }
                break;
        }

        return new BigDecimal(deduction);
    }
}