package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.ReportData;
import java.util.List;

/**
 * 报告数据Mapper接口
 * 
 * @author wanzheng
 */
public interface ReportDataMapper {
    /**
     * 查询报告数据
     * 
     * @param id 报告数据ID
     * @return 报告数据
     */
    public ReportData selectReportDataById(Long id);

    /**
     * 查询报告数据列表
     * 
     * @param reportData 报告数据
     * @return 报告数据集合
     */
    public List<ReportData> selectReportDataList(ReportData reportData);

    /**
     * 新增报告数据
     * 
     * @param reportData 报告数据
     * @return 结果
     */
    public int insertReportData(ReportData reportData);

    /**
     * 修改报告数据
     * 
     * @param reportData 报告数据
     * @return 结果
     */
    public int updateReportData(ReportData reportData);

    /**
     * 删除报告数据
     * 
     * @param id 报告数据ID
     * @return 结果
     */
    public int deleteReportDataById(Long id);

    /**
     * 批量删除报告数据
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteReportDataByIds(String[] ids);
    
    /**
     * 根据报告ID查询报告数据
     * 
     * @param reportId 报告ID
     * @return 报告数据集合
     */
    public List<ReportData> selectReportDataByReportId(Long reportId);
    
    /**
     * 批量新增报告数据
     * 
     * @param list 报告数据列表
     * @return 结果
     */
    public int batchInsertReportData(List<ReportData> list);
    
    /**
     * 根据报告ID删除报告数据
     * 
     * @param reportId 报告ID
     * @return 结果
     */
    public int deleteReportDataByReportId(Long reportId);
} 