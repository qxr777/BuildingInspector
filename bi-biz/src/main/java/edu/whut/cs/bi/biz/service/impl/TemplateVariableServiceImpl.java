package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.TemplateVariableMapper;
import edu.whut.cs.bi.biz.domain.TemplateVariable;
import edu.whut.cs.bi.biz.service.ITemplateVariableService;
import com.ruoyi.common.core.text.Convert;

/**
 * 模板变量Service业务层处理
 * 
 * @author wanzheng
 */
@Service
public class TemplateVariableServiceImpl implements ITemplateVariableService {
    @Autowired
    private TemplateVariableMapper templateVariableMapper;

    /**
     * 查询模板变量
     * 
     * @param id 模板变量ID
     * @return 模板变量
     */
    @Override
    public TemplateVariable selectTemplateVariableById(Long id) {
        return templateVariableMapper.selectTemplateVariableById(id);
    }

    /**
     * 查询模板变量列表
     * 
     * @param templateVariable 模板变量
     * @return 模板变量
     */
    @Override
    public List<TemplateVariable> selectTemplateVariableList(TemplateVariable templateVariable) {
        return templateVariableMapper.selectTemplateVariableList(templateVariable);
    }

    /**
     * 新增模板变量
     * 
     * @param templateVariable 模板变量
     * @return 结果
     */
    @Override
    public int insertTemplateVariable(TemplateVariable templateVariable) {
        return templateVariableMapper.insertTemplateVariable(templateVariable);
    }

    /**
     * 修改模板变量
     * 
     * @param templateVariable 模板变量
     * @return 结果
     */
    @Override
    public int updateTemplateVariable(TemplateVariable templateVariable) {
        return templateVariableMapper.updateTemplateVariable(templateVariable);
    }

    /**
     * 删除模板变量对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteTemplateVariableByIds(String ids) {
        return templateVariableMapper.deleteTemplateVariableByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除模板变量信息
     * 
     * @param id 模板变量ID
     * @return 结果
     */
    @Override
    public int deleteTemplateVariableById(Long id) {
        return templateVariableMapper.deleteTemplateVariableById(id);
    }
    
    /**
     * 根据模板ID查询变量列表
     * 
     * @param reportTemplateId 模板ID
     * @return 变量列表
     */
    @Override
    public List<TemplateVariable> selectTemplateVariablesByTemplateId(Long reportTemplateId) {
        return templateVariableMapper.selectTemplateVariablesByTemplateId(reportTemplateId);
    }
} 