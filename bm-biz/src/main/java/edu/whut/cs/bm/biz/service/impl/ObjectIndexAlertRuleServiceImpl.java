package edu.whut.cs.bm.biz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bm.biz.mapper.ObjectIndexAlertRuleMapper;
import edu.whut.cs.bm.biz.domain.ObjectIndexAlertRule;
import edu.whut.cs.bm.biz.service.IObjectIndexAlertRuleService;
import com.ruoyi.common.core.text.Convert;

/**
 * 关联预警规则Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-14
 */
@Service
public class ObjectIndexAlertRuleServiceImpl implements IObjectIndexAlertRuleService 
{
    @Autowired
    private ObjectIndexAlertRuleMapper objectIndexAlertRuleMapper;

    /**
     * 查询关联预警规则
     * 
     * @param id 关联预警规则ID
     * @return 关联预警规则
     */
    @Override
    public ObjectIndexAlertRule selectObjectIndexAlertRuleById(Long id)
    {
        return objectIndexAlertRuleMapper.selectObjectIndexAlertRuleById(id);
    }

    /**
     * 查询关联预警规则列表
     * 
     * @param objectIndexAlertRule 关联预警规则
     * @return 关联预警规则
     */
    @Override
    public List<ObjectIndexAlertRule> selectObjectIndexAlertRuleList(ObjectIndexAlertRule objectIndexAlertRule)
    {
        return objectIndexAlertRuleMapper.selectObjectIndexAlertRuleList(objectIndexAlertRule);
    }

    /**
     * 新增关联预警规则
     * 
     * @param objectIndexAlertRule 关联预警规则
     * @return 结果
     */
    @Override
    public int insertObjectIndexAlertRule(ObjectIndexAlertRule objectIndexAlertRule)
    {
        return objectIndexAlertRuleMapper.insertObjectIndexAlertRule(objectIndexAlertRule);
    }

    /**
     * 修改关联预警规则
     * 
     * @param objectIndexAlertRule 关联预警规则
     * @return 结果
     */
    @Override
    public int updateObjectIndexAlertRule(ObjectIndexAlertRule objectIndexAlertRule)
    {
        return objectIndexAlertRuleMapper.updateObjectIndexAlertRule(objectIndexAlertRule);
    }

    /**
     * 删除关联预警规则对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteObjectIndexAlertRuleByIds(String ids)
    {
        return objectIndexAlertRuleMapper.deleteObjectIndexAlertRuleByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除关联预警规则信息
     * 
     * @param id 关联预警规则ID
     * @return 结果
     */
    @Override
    public int deleteObjectIndexAlertRuleById(Long id)
    {
        return objectIndexAlertRuleMapper.deleteObjectIndexAlertRuleById(id);
    }

    /**
     * 关联对象指标与预警规则
     * @param objectId
     * @param indexId
     * @param alertRuleIds
     * @return
     */
    @Override
    public int insertObjectIndexAlertRules(Long objectId, Long indexId, Long[] alertRuleIds) {
        objectIndexAlertRuleMapper.deleteByObjectIdAndIndexId(objectId, indexId);
        ObjectIndexAlertRule objectIndexAlertRule;
        for(Long alertRuleId : alertRuleIds) {
            objectIndexAlertRule = new ObjectIndexAlertRule();
            objectIndexAlertRule.setObjectId(objectId);
            objectIndexAlertRule.setIndexId(indexId);
            objectIndexAlertRule.setAlertRuleId(alertRuleId);
            this.insertObjectIndexAlertRule(objectIndexAlertRule);
        }
        return alertRuleIds.length;
    }
}
