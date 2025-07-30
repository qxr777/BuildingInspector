package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.ReportTemplate;
import java.util.List;

/**
 * 报告模板Mapper接口
 * 
 * @author wanzheng
 */
public interface ReportTemplateMapper {
    /**
     * 查询报告模板
     * 
     * @param id 报告模板ID
     * @return 报告模板
     */
    public ReportTemplate selectReportTemplateById(Long id);

    /**
     * 查询报告模板列表
     * 
     * @param reportTemplate 报告模板
     * @return 报告模板集合
     */
    public List<ReportTemplate> selectReportTemplateList(ReportTemplate reportTemplate);

    /**
     * 新增报告模板
     * 
     * @param reportTemplate 报告模板
     * @return 结果
     */
    public int insertReportTemplate(ReportTemplate reportTemplate);

    /**
     * 修改报告模板
     * 
     * @param reportTemplate 报告模板
     * @return 结果
     */
    public int updateReportTemplate(ReportTemplate reportTemplate);

    /**
     * 删除报告模板
     * 
     * @param id 报告模板ID
     * @return 结果
     */
    public int deleteReportTemplateById(Long id);

    /**
     * 批量删除报告模板
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteReportTemplateByIds(String[] ids);
} 