package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.TemplateVariable;
import java.util.List;

/**
 * 模板变量Service接口
 * 
 * @author wanzheng
 */
public interface ITemplateVariableService {
    /**
     * 查询模板变量
     * 
     * @param id 模板变量ID
     * @return 模板变量
     */
    public TemplateVariable selectTemplateVariableById(Long id);

    /**
     * 查询模板变量列表
     * 
     * @param templateVariable 模板变量
     * @return 模板变量集合
     */
    public List<TemplateVariable> selectTemplateVariableList(TemplateVariable templateVariable);

    /**
     * 新增模板变量
     * 
     * @param templateVariable 模板变量
     * @return 结果
     */
    public int insertTemplateVariable(TemplateVariable templateVariable);

    /**
     * 修改模板变量
     * 
     * @param templateVariable 模板变量
     * @return 结果
     */
    public int updateTemplateVariable(TemplateVariable templateVariable);

    /**
     * 批量删除模板变量
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteTemplateVariableByIds(String ids);

    /**
     * 删除模板变量信息
     * 
     * @param id 模板变量ID
     * @return 结果
     */
    public int deleteTemplateVariableById(Long id);
    
    /**
     * 根据模板ID查询变量列表
     * 
     * @param reportTemplateId 模板ID
     * @return 变量列表
     */
    public List<TemplateVariable> selectTemplateVariablesByTemplateId(Long reportTemplateId);
} 