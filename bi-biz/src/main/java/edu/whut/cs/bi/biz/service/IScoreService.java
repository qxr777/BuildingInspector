package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Score;

import java.util.List;

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
     * 计算构件得分
     *
     * @param components  构件ID
     * @param conditionId 部件评定ID
     * @return 构件得分
     */
    public List<Score> calculateScore(List<Component> components, Long conditionId,Long projectId);
}