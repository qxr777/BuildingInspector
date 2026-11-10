package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Report;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 检测报告Mapper接口
 * 
 * @author wanzheng
 */
public interface ReportMapper {
    @org.apache.ibatis.annotations.Select("select id from bi_report where id = #{id} for update")
    Long lockForDataUpdate(@Param("id") Long id);
    /**
     * 查询检测报告
     * 
     * @param id 检测报告ID
     * @return 检测报告
     */
    public Report selectReportById(Long id);

    /**
     * 按状态查询报告，按更新时间从早到晚，用于生成排队。
     */
    public List<Report> selectReportsByStatus(@Param("status") Integer status);

    /**
     * 把重启前仍标记为生成中的报告放回排队，不改更新时间，排队顺序保持不变。
     */
    public int requeueInterruptedReport(@Param("id") Long id, @Param("status") Integer status,
                                        @Param("updateBy") String updateBy, @Param("remark") String remark);

    /**
     * 查询检测报告列表
     * 
     * @param report 检测报告
     * @param currentUserId 当前用户ID（用于普通用户权限过滤）
     * @param selectDeptId 部门ID（用于部门管理员权限过滤）
     * @param parentDeptId 父部门ID（用于部门管理员权限过滤）
     * @return 检测报告集合
     */
    public List<Report> selectReportList(@Param("report") Report report, 
                                         @Param("currentUserId") Long currentUserId, 
                                         @Param("selectDeptId") Long selectDeptId, 
                                         @Param("parentDeptId") Long parentDeptId);

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
