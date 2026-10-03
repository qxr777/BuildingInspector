package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Report;
import edu.whut.cs.bi.biz.domain.ReportTemplate;
import edu.whut.cs.bi.biz.domain.Task;

import java.util.List;

/**
 * 定期检查多桥报告Service接口。
 *
 * <p>多桥报告参照一级单桥报告实现，但自成一套：模板只加载一次，桥梁章节先在独立
 * 文档副本中生成，再按网页选择顺序插回主文档，与单桥报告的生成链路互不影响。</p>
 *
 * @author wanzheng
 */
public interface ILineMultiBridgeReportService {
    /**
     * 判断报告模板是否为多桥定期检查模板。
     */
    boolean isMultiBridgeTemplate(ReportTemplate template);

    /**
     * 按网页选择顺序排列任务，报告中的桥梁章节顺序以此为准。
     */
    List<Task> orderTasksBySelection(List<Long> taskIds, List<Task> tasks);

    /**
     * 生成前校验所选任务。
     *
     * @return 校验通过返回 null，否则返回给用户的错误提示
     */
    String validateTasks(Report report, List<Task> tasks);

    /**
     * 生成多桥定期检查报告。
     *
     * @return 生成的报告文件ID；失败时抛出异常
     */
    String generateReportDocument(Report report, List<Task> tasks, ReportTemplate template, String operator);
}
