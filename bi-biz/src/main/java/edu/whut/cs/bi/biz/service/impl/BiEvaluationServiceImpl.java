package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.BiEvaluationMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IComponentService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.service.IScoreService;
import edu.whut.cs.bi.biz.service.PreparedCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private IComponentService componentService;

    @Autowired
    private IScoreService scoreService;

    @Autowired
    private TaskServiceImpl taskService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    /**
     * 计算桥幅得分。读和计算不占数据库事务，整桥算完后一次写入。
     */
    @Override
    public BiEvaluation calculateBiEvaluation(Long taskId, Long rootObjectId) {
        if (taskId == null || rootObjectId == null) {
            throw new RuntimeException("计算失败：任务ID或根对象ID为空");
        }

        String lockName = "bi_eval_" + taskId;
        Connection connection = null;
        boolean locked = false;
        try {
            connection = dataSource.getConnection();
            Boolean lockResult = tryLock(connection, lockName);
            if (lockResult == null) {
                throw new RuntimeException("计算失败：无法锁定评定任务");
            }
            if (!lockResult) {
                throw new RuntimeException("计算失败：该任务正在评定，请稍后再试");
            }
            locked = true;

            TransactionTemplate readTemplate = new TransactionTemplate(transactionManager);
            readTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            return readTemplate.execute(status -> calculateBiEvaluationLocked(taskId, rootObjectId));
        } catch (SQLException e) {
            throw new RuntimeException("计算失败：无法锁定评定任务", e);
        } finally {
            if (connection != null) {
                try {
                    if (locked) {
                        releaseLock(connection, lockName);
                    }
                } finally {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // 连接关闭失败时，连接池会丢弃异常连接
                    }
                }
            }
        }
    }

    private BiEvaluation calculateBiEvaluationLocked(Long taskId, Long rootObjectId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("计算失败：未找到任务信息");
        }

        Long projectId = task.getProjectId();
        if (projectId == null) {
            throw new RuntimeException("计算失败：任务未关联项目");
        }

        BiEvaluation loadedEvaluation = selectBiEvaluationByTaskId(taskId);
        final BiEvaluation biEvaluation;
        if (loadedEvaluation == null) {
            biEvaluation = new BiEvaluation();
            biEvaluation.setTaskId(taskId);
            biEvaluation.setCreateBy(ShiroUtils.getLoginName());
            biEvaluation.setCreateTime(new Date());
        } else {
            biEvaluation = loadedEvaluation;
        }

        List<BiObject> secondLevelObjects = biObjectService.selectDirectChildrenByParentId(rootObjectId);
        if (secondLevelObjects == null || secondLevelObjects.isEmpty()) {
            throw new RuntimeException("计算失败：未找到桥梁结构的子节点");
        }

        List<PartWork> parts = new ArrayList<>();
        List<Component> allComponents = new ArrayList<>();
        for (BiObject object : secondLevelObjects) {
            if (object == null) {
                throw new RuntimeException("计算失败：子节点对象为空");
            }
            if (object.getWeight() == null) {
                throw new RuntimeException("计算失败：" + object.getName() + "的权重为空");
            }
            List<BiObject> leafNodes = biObjectService.selectDirectChildrenByParentId(object.getId());
            if (leafNodes == null || leafNodes.isEmpty()) {
                throw new RuntimeException("计算失败：" + object.getName() + "下没有子节点");
            }
            List<LeafWork> leaves = new ArrayList<>();
            for (BiObject leaf : leafNodes) {
                if (leaf == null) {
                    continue;
                }
                List<Component> components = componentService.selectComponentsByBiObjectIdAndChildren(leaf.getId());
                if (components == null) {
                    throw new RuntimeException("计算失败：无法获取部件的构件列表");
                }
                leaves.add(new LeafWork(leaf, components));
                allComponents.addAll(components);
            }
            parts.add(new PartWork(object, leaves));
        }

        Map<Long, List<Disease>> diseasesByComponent = scoreService.loadParticipatingDiseases(projectId, allComponents);
        Map<Long, Condition> existingByObjectId = loadExistingConditions(biEvaluation.getId());
        PartWeights weights = new PartWeights();
        List<PreparedCondition> preparedConditions = new ArrayList<>();
        for (PartWork part : parts) {
            calculatePartScore(part, biEvaluation, projectId, weights, existingByObjectId, diseasesByComponent, preparedConditions);
        }
        calculateOverallScore(biEvaluation, weights);

        biEvaluation.setUpdateBy(ShiroUtils.getLoginName());
        biEvaluation.setUpdateTime(new Date());
        new TransactionTemplate(transactionManager).execute(status -> {
            saveEvaluation(biEvaluation, preparedConditions);
            return null;
        });
        return biEvaluation;
    }

    private Map<Long, Condition> loadExistingConditions(Long biEvaluationId) {
        Map<Long, Condition> existingByObjectId = new HashMap<>();
        if (biEvaluationId == null) {
            return existingByObjectId;
        }
        List<Condition> existingConditions = conditionService.selectConditionsByBiEvaluationId(biEvaluationId);
        if (existingConditions == null) {
            return existingByObjectId;
        }
        for (Condition existing : existingConditions) {
            if (existing != null && existing.getBiObjectId() != null && !existingByObjectId.containsKey(existing.getBiObjectId())) {
                existingByObjectId.put(existing.getBiObjectId(), existing);
            }
        }
        return existingByObjectId;
    }

    private void saveEvaluation(BiEvaluation biEvaluation, List<PreparedCondition> preparedConditions) {
        if (biEvaluation.getId() == null) {
            insertBiEvaluation(biEvaluation);
        } else {
            updateBiEvaluation(biEvaluation);
        }
        if (biEvaluation.getId() == null) {
            throw new RuntimeException("计算失败：评定记录保存失败");
        }
        for (PreparedCondition prepared : preparedConditions) {
            if (prepared != null && prepared.getCondition() != null) {
                prepared.getCondition().setBiEvaluationId(biEvaluation.getId());
            }
        }
        conditionService.writePreparedConditions(preparedConditions);
    }

    /**
     * 计算部分（上部结构、下部结构、桥面系统）得分
     */
    private void calculatePartScore(PartWork partWork, BiEvaluation biEvaluation, Long projectId, PartWeights weights,
                                    Map<Long, Condition> existingByObjectId, Map<Long, List<Disease>> diseasesByComponent,
                                    List<PreparedCondition> preparedConditions) {
        if (partWork == null || partWork.part == null) {
            throw new RuntimeException("计算失败：部件对象为空");
        }
        BiObject part = partWork.part;
        if (biEvaluation == null) {
            throw new RuntimeException("计算失败：评定记录为空或ID为空");
        }
        if (projectId == null) {
            throw new RuntimeException("计算失败：项目ID为空");
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        Integer worstLevel = 1;
        boolean hasValidCondition = false;

        for (LeafWork leafWork : partWork.leaves) {
            if (leafWork == null || leafWork.leaf == null) {
                continue;
            }
            BiObject leaf = leafWork.leaf;
            PreparedCondition prepared = conditionService.prepareCondition(
                    leaf, existingByObjectId.get(leaf.getId()), leafWork.components, diseasesByComponent);
            preparedConditions.add(prepared);
            Condition condition = prepared.getCondition();
            if (condition != null) {
                hasValidCondition = true;
                BigDecimal weight = leaf.getWeight() != null ? leaf.getWeight() : BigDecimal.ZERO;
                totalWeight = totalWeight.add(weight);
                if (condition.getScore() == null) {
                    throw new RuntimeException("计算失败：" + leaf.getName() + "的评分为空");
                }
                weightedScore = weightedScore.add(condition.getScore().multiply(weight));
                if (condition.getLevel() > worstLevel) {
                    worstLevel = condition.getLevel();
                }
            }
        }

        if (!hasValidCondition) {
            throw new RuntimeException("计算失败：" + part.getName() + "下没有有效的评定数据");
        }

        BigDecimal finalScore = totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
                weightedScore.divide(totalWeight, 2, RoundingMode.HALF_UP) : new BigDecimal("100");
        finalScore = finalScore.setScale(1, RoundingMode.HALF_UP);
        int level = calculateLevel(finalScore);

        if (part.getName() == null) {
            throw new RuntimeException("计算失败：部件名称为空");
        }

        switch (part.getName()) {
            case "上部结构":
                biEvaluation.setSuperstructureScore(finalScore);
                biEvaluation.setSuperstructureLevel(level);
                weights.superWeight = part.getWeight();
                break;
            case "下部结构":
                biEvaluation.setSubstructureScore(finalScore);
                biEvaluation.setSubstructureLevel(level);
                weights.subWeight = part.getWeight();
                break;
            case "桥面系":
                biEvaluation.setDeckSystemScore(finalScore);
                biEvaluation.setDeckSystemLevel(level);
                weights.deckWeight = part.getWeight();
                break;
            default:
                break;
        }
    }

    /**
     * 计算桥梁总体技术状况得分
     */
    private void calculateOverallScore(BiEvaluation biEvaluation, PartWeights weights) {
        if (biEvaluation == null) {
            throw new RuntimeException("计算失败：评定记录为空");
        }

        BigDecimal superScore = biEvaluation.getSuperstructureScore();
        BigDecimal subScore = biEvaluation.getSubstructureScore();
        BigDecimal deckScore = biEvaluation.getDeckSystemScore();

        if (superScore == null || subScore == null || deckScore == null) {
            throw new RuntimeException("计算失败：上部结构、下部结构或桥面系统的得分为空");
        }

        if (weights.superWeight == null || weights.subWeight == null || weights.deckWeight == null) {
            throw new RuntimeException("计算失败：数据异常，权重数据为空");
        }

        BigDecimal totalWeight = weights.superWeight.add(weights.subWeight).add(weights.deckWeight);
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw new RuntimeException("计算失败：权重之和不等于1，当前总和为" + totalWeight);
        }

        BigDecimal systemScore = superScore.multiply(weights.superWeight)
                .add(subScore.multiply(weights.subWeight))
                .add(deckScore.multiply(weights.deckWeight))
                .setScale(1, RoundingMode.HALF_UP);

        biEvaluation.setSystemScore(systemScore);

        Integer superLevel = biEvaluation.getSuperstructureLevel();
        Integer subLevel = biEvaluation.getSubstructureLevel();
        Integer deckLevel = biEvaluation.getDeckSystemLevel();

        if (superLevel == null || subLevel == null || deckLevel == null) {
            throw new RuntimeException("计算失败：上部结构、下部结构或桥面系统的等级为空");
        }

        int worstLevel = Math.max(Math.max(superLevel, subLevel), deckLevel);
        biEvaluation.setWorstPartLevel(worstLevel);

        biEvaluation.setSystemLevel(calculateLevel(systemScore));
        //当上部结构和下部结构技术状况等级为3类、桥面系技术状况等级为4类，且桥梁总体技术状况评分为40<D<60时，桥梁总体技术状况等级应评定为3类。
        if (biEvaluation.getSuperstructureLevel() == 3 && biEvaluation.getSubstructureLevel() == 3 && biEvaluation.getDeckSystemLevel() == 4 && biEvaluation.getSystemLevel() == 4) {
            biEvaluation.setSystemLevel(3);
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

    private Boolean tryLock(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Object value = resultSet.getObject(1);
                if (value == null) {
                    return null;
                }
                return ((Number) value).intValue() == 1;
            }
        }
    }

    private void releaseLock(Connection connection, String lockName) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.execute();
        } catch (SQLException ex) {
            try {
                connection.abort(Runnable::run);
            } catch (SQLException ignored) {
                // 释放失败时中止连接，避免锁留在连接池里的会话上
            }
        }
    }

    private static final class PartWeights {
        private BigDecimal superWeight;
        private BigDecimal subWeight;
        private BigDecimal deckWeight;
    }

    private static final class PartWork {
        private final BiObject part;
        private final List<LeafWork> leaves;

        private PartWork(BiObject part, List<LeafWork> leaves) {
            this.part = part;
            this.leaves = leaves;
        }
    }

    private static final class LeafWork {
        private final BiObject leaf;
        private final List<Component> components;

        private LeafWork(BiObject leaf, List<Component> components) {
            this.leaf = leaf;
            this.components = components;
        }
    }
}
