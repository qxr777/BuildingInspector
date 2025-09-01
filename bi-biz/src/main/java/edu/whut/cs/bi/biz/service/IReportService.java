package edu.whut.cs.bi.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Report;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 检测报告Service接口
 *
 * @author wanzheng
 */
public interface IReportService {
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
   * 批量删除检测报告
   *
   * @param ids 需要删除的数据ID
   * @return 结果
   */
  public int deleteReportByIds(String ids);

  /**
   * 删除检测报告信息
   *
   * @param id 检测报告ID
   * @return 结果
   */
  public int deleteReportById(Long id);

  /**
   * 生成报告数据
   *
   * @param reportId 报告ID
   * @return 结果
   */
  public int generateReportData(Long reportId);

  /**
   * 生成报告文档
   *
   * @param reportId 报告ID
   * @param buildingId 建筑物ID
   * @return 生成的文件路径
   */
  public String generateReportDocument(Long reportId, Long buildingId,Long projectId) ;

  /**
   * 调用后台 ai 模型获取报告中的病害 小结。
   * @param diseases
   * @return
   */
  String getDiseaseSummary(List<Disease> diseases) throws JsonProcessingException;

}