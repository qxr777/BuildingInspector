package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.ObjectIndexAlertRule;

/**
 * 关联预警规则Service接口
 * 
 * @author qixin
 * @date 2021-08-14
 */
public interface IObjectIndexAlertRuleService 
{
    /**
     * 查询关联预警规则
     * 
     * @param id 关联预警规则ID
     * @return 关联预警规则
     */
    public ObjectIndexAlertRule selectObjectIndexAlertRuleById(Long id);

    /**
     * 查询关联预警规则列表
     * 
     * @param objectIndexAlertRule 关联预警规则
     * @return 关联预警规则集合
     */
    public List<ObjectIndexAlertRule> selectObjectIndexAlertRuleList(ObjectIndexAlertRule objectIndexAlertRule);

    /**
     * 新增关联预警规则
     * 
     * @param objectIndexAlertRule 关联预警规则
     * @return 结果
     */
    public int insertObjectIndexAlertRule(ObjectIndexAlertRule objectIndexAlertRule);

    /**
     * 修改关联预警规则
     * 
     * @param objectIndexAlertRule 关联预警规则
     * @return 结果
     */
    public int updateObjectIndexAlertRule(ObjectIndexAlertRule objectIndexAlertRule);

    /**
     * 批量删除关联预警规则
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteObjectIndexAlertRuleByIds(String ids);

    /**
     * 删除关联预警规则信息
     * 
     * @param id 关联预警规则ID
     * @return 结果
     */
    public int deleteObjectIndexAlertRuleById(Long id);

    int insertObjectIndexAlertRules(Long objectId, Long indexId, Long[] alertRuleIds);

}
