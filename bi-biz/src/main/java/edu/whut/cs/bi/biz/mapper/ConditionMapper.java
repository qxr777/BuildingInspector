package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Condition;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/4/17 14:31
 * @Description:
 **/
public interface ConditionMapper {
    /**
     * 查询部件技术状况评分
     *
     * @param id ID
     * @return 部件技术状况评分
     */
    public Condition selectConditionById(Long id);

    /**
     * 查询部件技术状况评分列表
     *
     * @param condition 部件技术状况评分
     * @return 部件技术状况评分集合
     */
    public List<Condition> selectConditionList(Condition condition);

    /**
     * 新增部件技术状况评分
     *
     * @param condition 部件技术状况评分
     * @return 结果
     */
    public int insertCondition(Condition condition);

    /**
     * 修改部件技术状况评分
     *
     * @param condition 部件技术状况评分
     * @return 结果
     */
    public int updateCondition(Condition condition);

    /**
     * 删除部件技术状况评分
     *
     * @param id ID
     * @return 结果
     */
    public int deleteConditionById(Long id);

    /**
     * 批量删除部件技术状况评分
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteConditionByIds(String[] ids);
}