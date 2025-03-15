package edu.whut.cs.bm.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bm.biz.domain.AlertRulePlan;
import edu.whut.cs.bm.biz.domain.Plan;
import edu.whut.cs.bm.biz.mapper.AlertRulePlanMapper;
import edu.whut.cs.bm.biz.mapper.PlanMapper;
import edu.whut.cs.bm.biz.service.IPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 抢修抢建方案Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-09
 */
@Service
public class PlanServiceImpl implements IPlanService
{
    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private AlertRulePlanMapper alertRulePlanMapper;

    /**
     * 查询抢修抢建方案
     * 
     * @param id 抢修抢建方案ID
     * @return 抢修抢建方案
     */
    @Override
    public Plan selectPlanById(Long id)
    {
        return planMapper.selectPlanById(id);
    }

    /**
     * 查询抢修抢建方案列表
     * 
     * @param plan 抢修抢建方案
     * @return 抢修抢建方案
     */
    @Override
    public List<Plan> selectPlanList(Plan plan)
    {
        return planMapper.selectPlanList(plan);
    }

    /**
     * 新增抢修抢建方案
     * 
     * @param plan 抢修抢建方案
     * @return 结果
     */
    @Override
    public int insertPlan(Plan plan)
    {
        plan.setCreateTime(DateUtils.getNowDate());
        return planMapper.insertPlan(plan);
    }

    /**
     * 修改抢修抢建方案
     * 
     * @param plan 抢修抢建方案
     * @return 结果
     */
    @Override
    public int updatePlan(Plan plan)
    {
        plan.setUpdateTime(DateUtils.getNowDate());
        return planMapper.updatePlan(plan);
    }

    /**
     * 删除抢修抢建方案对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deletePlanByIds(String ids)
    {
        return planMapper.deletePlanByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除抢修抢建方案信息
     * 
     * @param id 抢修抢建方案ID
     * @return 结果
     */
    @Override
    public int deletePlanById(Long id)
    {
        return planMapper.deletePlanById(id);
    }

    /**
     * 以rule为参数，查找所有候选方案，只用于关联方案；一个预警规则可能关联多个抢修抢建方案
     *
     * @param alertRuleId
     * @return
     */
    @Override
    public List<Plan> selectByAlertRule4Assign(Long alertRuleId) {
        Plan queryPlan = new Plan();
        List<Plan> plans = planMapper.selectPlanList(queryPlan);
        AlertRulePlan queryAlertRulePlan = new AlertRulePlan();
        queryAlertRulePlan.setAlertRuleId(alertRuleId);
        List<AlertRulePlan> alertRulePlans = alertRulePlanMapper.selectAlertRulePlanList(queryAlertRulePlan);
        for (Plan plan : plans) {
            for (AlertRulePlan alertRulePlan : alertRulePlans) {
                if (plan.getId().longValue() == alertRulePlan.getPlanId().longValue()) {
                    plan.setFlag(true);
                    break;
                }
            }
        }
        return plans;
    }
}
