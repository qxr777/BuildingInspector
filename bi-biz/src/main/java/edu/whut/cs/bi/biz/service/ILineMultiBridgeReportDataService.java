package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.LineBridgeGroup;
import edu.whut.cs.bi.biz.domain.LineReportData;
import edu.whut.cs.bi.biz.domain.Task;

import java.util.List;

/**
 * 定期检查多桥报告数据Service接口。
 *
 * <p>多桥报告的填报数据分线路级（taskId 为空）与桥梁级（taskId 为具体任务）两层，
 * 与单桥报告的数据读写完全分开，互不影响。</p>
 *
 * @author wanzheng
 */
public interface ILineMultiBridgeReportDataService {
    /**
     * 查询报告下的全部填报数据，桥梁级数据的 taskId 已解码。
     */
    List<LineReportData> selectByReportId(Long reportId);

    /**
     * 查询指定子桥任务的填报数据。
     *
     * @param taskId 桥梁任务ID，传 null 查线路级数据
     */
    List<LineReportData> selectByTask(Long reportId, Long taskId);

    /**
     * 查询指定大桥分组的填报数据。
     */
    List<LineReportData> selectByGroup(Long reportId, String groupId);

    /**
     * 保存填报数据，按 reportId + taskId/groupId + key 判断新增或更新。
     */
    int saveBatch(Long reportId, List<LineReportData> dataList);

    int saveWordImports(Long reportId, List<LineReportData> dataList, java.util.Map<String, String> versions);

    /**
     * 读取填报页保存的大桥分组。未配置时返回空列表。
     */
    List<LineBridgeGroup> selectBridgeGroups(Long reportId);

    /**
     * 生成用的有效大桥列表：已保存分组按顺序在前，未编入的任务各自成一座大桥。
     */
    List<LineBridgeGroup> resolveEffectiveGroups(Long reportId, List<Task> tasks);
}
