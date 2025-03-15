package edu.whut.cs.bm.biz.mapper;

import java.util.List;
import edu.whut.cs.bm.biz.domain.Evaluation;

/**
 * 对象健康评估Mapper接口
 * 
 * @author qixin
 * @date 2021-10-31
 */
public interface EvaluationMapper 
{
    /**
     * 查询对象健康评估
     * 
     * @param id 对象健康评估ID
     * @return 对象健康评估
     */
    public Evaluation selectEvaluationById(Long id);

    /**
     * 查询对象健康评估列表
     * 
     * @param evaluation 对象健康评估
     * @return 对象健康评估集合
     */
    public List<Evaluation> selectEvaluationList(Evaluation evaluation);

    /**
     * 新增对象健康评估
     * 
     * @param evaluation 对象健康评估
     * @return 结果
     */
    public int insertEvaluation(Evaluation evaluation);

    /**
     * 修改对象健康评估
     * 
     * @param evaluation 对象健康评估
     * @return 结果
     */
    public int updateEvaluation(Evaluation evaluation);

    /**
     * 删除对象健康评估
     * 
     * @param id 对象健康评估ID
     * @return 结果
     */
    public int deleteEvaluationById(Long id);

    /**
     * 批量删除对象健康评估
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteEvaluationByIds(String[] ids);
}
