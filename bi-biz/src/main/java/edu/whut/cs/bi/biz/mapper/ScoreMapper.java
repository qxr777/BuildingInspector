package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Score;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/4/17 14:32
 * @Description:
 **/
public interface ScoreMapper {
    /**
     * 查询构件得分
     *
     * @param id ID
     * @return 构件得分
     */
    public Score selectScoreById(Long id);

    /**
     * 根据构件id查询构件得分
     *
     * @param id ID
     * @return 构件得分
     */
    public Score selectScoreBycomponentId(Long componentId);

    /**
     * 查询构件得分列表
     *
     * @param score 构件得分
     * @return 构件得分集合
     */
    public List<Score> selectScoreList(Score score);

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
     * 删除构件得分
     *
     * @param id ID
     * @return 结果
     */
    public int deleteScoreById(Long id);

    /**
     * 批量删除构件得分
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteScoreByIds(String[] ids);

    /**
     * 根据条件ID删除所有关联的构件得分
     *
     * @param conditionId 条件ID
     * @return 结果
     */
    public int deleteScoreByConditionId(Long conditionId);

    /**
     * 批量插入构件得分
     *
     * @param scores 构件得分列表
     * @return 结果
     */
    public int batchInsertScores(@Param("list") List<Score> scores);
}