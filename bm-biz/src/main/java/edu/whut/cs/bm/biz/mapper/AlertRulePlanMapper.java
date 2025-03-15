package edu.whut.cs.bm.biz.mapper;

import java.util.List;
import edu.whut.cs.bm.biz.domain.AlertRulePlan;
import org.apache.ibatis.annotations.Param;

/**
 * 预警规则与预案关联Mapper接口
 * 
 * @author qixin
 * @date 2021-10-08
 */
public interface AlertRulePlanMapper 
{
    /**
     * 查询预警规则与预案关联
     * 
     * @param id 预警规则与预案关联ID
     * @return 预警规则与预案关联
     */
    public AlertRulePlan selectAlertRulePlanById(Long id);

    /**
     * 查询预警规则与预案关联列表
     * 
     * @param alertRulePlan 预警规则与预案关联
     * @return 预警规则与预案关联集合
     */
    public List<AlertRulePlan> selectAlertRulePlanList(AlertRulePlan alertRulePlan);

    /**
     * 新增预警规则与预案关联
     * 
     * @param alertRulePlan 预警规则与预案关联
     * @return 结果
     */
    public int insertAlertRulePlan(AlertRulePlan alertRulePlan);

    /**
     * 修改预警规则与预案关联
     * 
     * @param alertRulePlan 预警规则与预案关联
     * @return 结果
     */
    public int updateAlertRulePlan(AlertRulePlan alertRulePlan);

    /**
     * 删除预警规则与预案关联
     * 
     * @param id 预警规则与预案关联ID
     * @return 结果
     */
    public int deleteAlertRulePlanById(Long id);

    /**
     * 批量删除预警规则与预案关联
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteAlertRulePlanByIds(String[] ids);

    /**
     * 批量删除指定预警规则的所有关联关系
     * @param alertRuleId
     * @return
     */
    int deleteByAlertRuleId(Long alertRuleId);
}
