package edu.whut.cs.bi.biz.service.impl;

import java.util.List;

import com.ruoyi.common.utils.ShiroUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.ReportTemplateMapper;
import edu.whut.cs.bi.biz.domain.ReportTemplate;
import edu.whut.cs.bi.biz.service.IReportTemplateService;
import com.ruoyi.common.core.text.Convert;

/**
 * 报告模板Service业务层处理
 *
 * @author wanzheng
 */
@Service
public class ReportTemplateServiceImpl implements IReportTemplateService {
    @Autowired
    private ReportTemplateMapper reportTemplateMapper;

    /**
     * 查询报告模板
     *
     * @param id 报告模板ID
     * @return 报告模板
     */
    @Override
    public ReportTemplate selectReportTemplateById(Long id) {
        return reportTemplateMapper.selectReportTemplateById(id);
    }

    /**
     * 查询报告模板列表
     *
     * @param reportTemplate 报告模板
     * @return 报告模板
     */
    @Override
    public List<ReportTemplate> selectReportTemplateList(ReportTemplate reportTemplate) {
        return reportTemplateMapper.selectReportTemplateList(reportTemplate);
    }

    /**
     * 新增报告模板
     *
     * @param reportTemplate 报告模板
     * @return 结果
     */
    @Override
    public int insertReportTemplate(ReportTemplate reportTemplate) {
        reportTemplate.setCreateBy(ShiroUtils.getLoginName());
        // 设置默认版本为1
        if (reportTemplate.getVersion() == null) {
            reportTemplate.setVersion(1);
        }
        return reportTemplateMapper.insertReportTemplate(reportTemplate);
    }

    /**
     * 修改报告模板
     *
     * @param reportTemplate 报告模板
     * @return 结果
     */
    @Override
    public int updateReportTemplate(ReportTemplate reportTemplate) {
        reportTemplate.setUpdateBy(ShiroUtils.getLoginName());
        // 获取当前版本
        ReportTemplate oldTemplate = reportTemplateMapper.selectReportTemplateById(reportTemplate.getId());
        if (oldTemplate != null && oldTemplate.getVersion() != null) {
            // 版本号自动加1
            reportTemplate.setVersion(oldTemplate.getVersion() + 1);
        } else {
            // 如果没有版本号，设为1
            reportTemplate.setVersion(1);
        }
        return reportTemplateMapper.updateReportTemplate(reportTemplate);
    }

    /**
     * 删除报告模板对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteReportTemplateByIds(String ids) {
        return reportTemplateMapper.deleteReportTemplateByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除报告模板信息
     *
     * @param id 报告模板ID
     * @return 结果
     */
    @Override
    public int deleteReportTemplateById(Long id) {
        return reportTemplateMapper.deleteReportTemplateById(id);
    }
} 