package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.domain.Disease;

import java.util.List;
import java.util.Map;

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


    Condition selectConditionByBiObjectId(Long biObjectId, Long biEvaluationId);

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
    public Condition calculateCondition(BiObject biObject, Long biEvaluationId, Long projectId);

    /**
     * 在内存中计算部件评定和构件得分，不写数据库。
     *
     * @param biObject 部件
     * @param existing 已有部件评定，没有时为空
     * @param components 部件及其下级构件
     * @param diseasesByComponent 构件病害
     * @return 待保存的评定结果
     */
    PreparedCondition prepareCondition(BiObject biObject, Condition existing, List<Component> components,
                                       Map<Long, List<Disease>> diseasesByComponent);

    /**
     * 保存已算好的部件评定，并按部件替换构件得分。调用方负责事务。
     *
     * @param preparedConditions 待保存的评定结果
     */
    void writePreparedConditions(List<PreparedCondition> preparedConditions);
}