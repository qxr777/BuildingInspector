package edu.whut.cs.bm.biz.mapper;

import java.util.List;
import edu.whut.cs.bm.biz.domain.ObjectIndexAlertRule;
import org.apache.ibatis.annotations.Param;

/**
 * 关联预警规则Mapper接口
 * 
 * @author qixin
 * @date 2021-08-14
 */
public interface ObjectIndexAlertRuleMapper 
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
     * 删除关联预警规则
     * 
     * @param id 关联预警规则ID
     * @return 结果
     */
    public int deleteObjectIndexAlertRuleById(Long id);

    /**
     * 批量删除关联预警规则
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteObjectIndexAlertRuleByIds(String[] ids);

    int deleteByObjectIdAndIndexId(@Param("objectId")Long objectId, @Param("indexId")Long indexId);
}
