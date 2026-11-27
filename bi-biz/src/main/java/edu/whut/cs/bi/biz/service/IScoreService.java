package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Score;

import java.util.List;
import java.util.Map;

/**
 * 构件得分Service接口
 */
public interface IScoreService {
    /**
     * 查询构件得分
     *
     * @param id 构件得分ID
     * @return 构件得分
     */
    public Score selectScoreById(Long id);

    /**
     * 查询构件得分列表
     *
     * @param score 构件得分
     * @return 构件得分集合
     */
    public List<Score> selectScoreList(Score score);

    /**
     * 查询部件评定下的构件得分列表
     *
     * @param conditionId 部件评定ID
     * @return 构件得分集合
     */
    public List<Score> selectScoresByConditionId(Long conditionId);

    /**
     * 新增构件得分
     *
     * @param score 构件得分
     * @return 结果
     */
    public int insertScore(Score score);

    /**
     * 修改构件得分
     *
     * @param score 构件得分
     * @return 结果
     */
    public int updateScore(Score score);

    /**
     * 批量删除构件得分
     *
     * @param ids 需要删除的构件得分ID
     * @return 结果
     */
    public int deleteScoreByIds(String ids);

    /**
     * 删除构件得分信息
     *
     * @param id 构件得分ID
     * @return 结果
     */
    public int deleteScoreById(Long id);

    /**
     * 按构件批量读取参与评定的病害。
     *
     * @param projectId 项目ID
     * @param components 构件列表
     * @return 构件ID对应的病害
     */
    public Map<Long, List<Disease>> loadParticipatingDiseases(Long projectId, List<Component> components);

    /**
     * 按已读取的病害计算构件得分，不写数据库。
     *
     * @param components 构件列表
     * @param diseasesByComponent 构件病害
     * @return 构件得分
     */
    public List<Score> calculateScore(List<Component> components, Map<Long, List<Disease>> diseasesByComponent);

    /**
     * 根据条件ID删除所有关联的构件得分
     *
     * @param conditionId 条件ID
     * @return 结果
     */
    public int deleteScoreByConditionId(Long conditionId);

    /**
     * 批量新增构件得分
     *
     * @param scores 构件得分列表
     * @return 结果
     */
    public int batchInsertScores(List<Score> scores);
}