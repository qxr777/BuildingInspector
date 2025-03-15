package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.AlertRule;
import edu.whut.cs.bm.biz.domain.IndexData;
import edu.whut.cs.bm.biz.vo.CheckVo;

/**
 * 预警规则Service接口
 *
 * @author qixin
 * @date 2021-08-13
 */
public interface IAlertRuleService
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
     * 批量删除预警规则
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteAlertRuleByIds(String ids);

    /**
     * 删除预警规则信息
     *
     * @param id 预警规则ID
     * @return 结果
     */
    public int deleteAlertRuleById(Long id);

    /**
     * 以object和index为参数，查找所有预警规则
     * @param objectId
     * @param indexId
     * @return
     */
    List<AlertRule> selectByObjectIndex(Long objectId, Long indexId);

    /**
     * 以object和index为参数，查找所有候选预警规则，只用于关联预警规则；一个对象指标可能关联多个预警规则
     * @param objectId
     * @param indexId
     * @return
     */
    List<AlertRule> selectByObjectIndex4Assign(Long objectId, Long indexId);

    /**
     * 检查监测数据是否需要预警
     * @param indexData
     * @return  最严重预警级别
     */
    CheckVo check(IndexData indexData);

    /**
     * 预警规则关联若干个抢修抢建方案
     * @param alertRuleId
     * @param planIds
     * @return
     */
    int insertAlertRulePlans(Long alertRuleId, Long[] planIds);

}
