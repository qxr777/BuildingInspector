package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.ReportData;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 报告数据Service接口
 * 
 * @author wanzheng
 */
public interface IReportDataService {
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
     * 批量删除报告数据
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteReportDataByIds(String ids);

    /**
     * 删除报告数据信息
     * 
     * @param id 报告数据ID
     * @return 结果
     */
    public int deleteReportDataById(Long id);
    
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

    /**
     * 导出桥梁卡片
     *
     * @param bid 报告ID
     * @return 结果
     */
    public void exportPropertyWord(Long bid, HttpServletResponse response);


    /**
     * 保存报告数据（先查询是否存在，存在则更新，不存在则新增）
     *
     * @param reportId 报告ID
     * @param dataList 报告数据列表
     * @return 结果
     */
    public int saveReportDataBatch(Long reportId, List<ReportData> dataList);
} 