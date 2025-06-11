package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Condition;

import java.util.List;

/**
 * 部件评定Service接口
 */
public interface IConditionService {
    /**
     * 查询部件评定
     *
     * @param id 部件评定ID
     * @return 部件评定
     */
    public Condition selectConditionById(Long id);

    /**
     * 查询部件评定列表
     *
     * @param condition 部件评定
     * @return 部件评定集合
     */
    public List<Condition> selectConditionList(Condition condition);

    /**
     * 查询桥幅评定下的部件评定列表
     *
     * @param biEvaluationId 桥幅评定ID
     * @return 部件评定集合
     */
    public List<Condition> selectConditionsByBiEvaluationId(Long biEvaluationId);


    Condition selectConditionByBiObjectId(Long biObjectId);

    /**
     * 新增部件评定
     *
     * @param condition 部件评定
     * @return 结果
     */
    public int insertCondition(Condition condition);

    /**
     * 修改部件评定
     *
     * @param condition 部件评定
     * @return 结果
     */
    public int updateCondition(Condition condition);

    /**
     * 批量删除部件评定
     *
     * @param ids 需要删除的部件评定ID
     * @return 结果
     */
    public int deleteConditionByIds(String ids);

    /**
     * 删除部件评定信息
     *
     * @param id 部件评定ID
     * @return 结果
     */
    public int deleteConditionById(Long id);

    /**
     * 计算部件评定
     *
     * @param biObject       部件ID
     * @param biEvaluationId 桥幅评定ID
     * @return 部件评定
     */
    public Condition calculateCondition(BiObject biObject, Long biEvaluationId,Long projectId);
}