package edu.whut.cs.bm.biz.service;

import java.util.List;

import edu.whut.cs.bm.biz.domain.AlertRule;
import edu.whut.cs.bm.biz.domain.Plan;

/**
 * 抢修抢建方案Service接口
 * 
 * @author qixin
 * @date 2021-08-09
 */
public interface IPlanService 
{
    /**
     * 查询抢修抢建方案
     * 
     * @param id 抢修抢建方案ID
     * @return 抢修抢建方案
     */
    public Plan selectPlanById(Long id);

    /**
     * 查询抢修抢建方案列表
     * 
     * @param plan 抢修抢建方案
     * @return 抢修抢建方案集合
     */
    public List<Plan> selectPlanList(Plan plan);

    /**
     * 新增抢修抢建方案
     * 
     * @param plan 抢修抢建方案
     * @return 结果
     */
    public int insertPlan(Plan plan);

    /**
     * 修改抢修抢建方案
     * 
     * @param plan 抢修抢建方案
     * @return 结果
     */
    public int updatePlan(Plan plan);

    /**
     * 批量删除抢修抢建方案
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deletePlanByIds(String ids);

    /**
     * 删除抢修抢建方案信息
     * 
     * @param id 抢修抢建方案ID
     * @return 结果
     */
    public int deletePlanById(Long id);

    /**
     * 以rule为参数，查找所有候选方案，只用于关联方案；一个预警规则可能关联多个抢修抢建方案
     * @param alertRuleId
     * @return
     */
    List<Plan> selectByAlertRule4Assign(Long alertRuleId);
}
