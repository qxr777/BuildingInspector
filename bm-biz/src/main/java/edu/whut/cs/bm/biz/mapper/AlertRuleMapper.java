package edu.whut.cs.bm.biz.mapper;

import java.util.List;
import edu.whut.cs.bm.biz.domain.AlertRule;

/**
 * 预警规则Mapper接口
 * 
 * @author qixin
 * @date 2021-08-13
 */
public interface AlertRuleMapper 
{
    /**
     * 查询预警规则
     * 
     * @param id 预警规则ID
     * @return 预警规则
     */
    public AlertRule selectAlertRuleById(Long id);

    /**
     * 查询预警规则列表
     * 
     * @param alertRule 预警规则
     * @return 预警规则集合
     */
    public List<AlertRule> selectAlertRuleList(AlertRule alertRule);

    /**
     * 新增预警规则
     * 
     * @param alertRule 预警规则
     * @return 结果
     */
    public int insertAlertRule(AlertRule alertRule);

    /**
     * 修改预警规则
     * 
     * @param alertRule 预警规则
     * @return 结果
     */
    public int updateAlertRule(AlertRule alertRule);

    /**
     * 删除预警规则
     * 
     * @param id 预警规则ID
     * @return 结果
     */
    public int deleteAlertRuleById(Long id);

    /**
     * 批量删除预警规则
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteAlertRuleByIds(String[] ids);
}
