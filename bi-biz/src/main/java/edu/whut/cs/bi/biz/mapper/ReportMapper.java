package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Report;
import java.util.List;

/**
 * 检测报告Mapper接口
 * 
 * @author wanzheng
 */
public interface ReportMapper {
    /**
     * 查询检测报告
     * 
     * @param id 检测报告ID
     * @return 检测报告
     */
    public Report selectReportById(Long id);

    /**
     * 查询检测报告列表
     * 
     * @param report 检测报告
     * @return 检测报告集合
     */
    public List<Report> selectReportList(Report report);

    /**
     * 新增检测报告
     * 
     * @param report 检测报告
     * @return 结果
     */
    public int insertReport(Report report);

    /**
     * 修改检测报告
     * 
     * @param report 检测报告
     * @return 结果
     */
    public int updateReport(Report report);

    /**
     * 删除检测报告
     * 
     * @param id 检测报告ID
     * @return 结果
     */
    public int deleteReportById(Long id);

    /**
     * 批量删除检测报告
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteReportByIds(String[] ids);
} 