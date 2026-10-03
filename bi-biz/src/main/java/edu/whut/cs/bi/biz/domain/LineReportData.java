package edu.whut.cs.bi.biz.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定期检查多桥报告的填报数据。
 *
 * <p>与单桥报告共用 bi_report_data 表。key 前缀：
 * {@code __task_{taskId}__} 为子桥任务数据，{@code __group_{groupId}__} 为大桥分组数据，
 * 无前缀且 key 以 line- 开头为线路级。taskId、groupId、buildingId 只在运行时使用。</p>
 *
 * @author wanzheng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LineReportData extends ReportData {
    private Long taskId;

    /** 大桥分组 ID，对应 {@link LineBridgeGroup#getId()}。 */
    private String groupId;

    private Long buildingId;
}
