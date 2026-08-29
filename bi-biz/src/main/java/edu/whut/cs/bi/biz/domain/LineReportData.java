package edu.whut.cs.bi.biz.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定期检查多桥报告的填报数据。
 *
 * <p>与单桥报告共用 bi_report_data 表，桥梁归属编码在 key 前缀 __task_{taskId}__ 中，
 * taskId 与 buildingId 只在服务端运行时使用，不映射数据库列。taskId 为空表示线路级数据，
 * 即整份报告共用一份。</p>
 *
 * @author wanzheng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LineReportData extends ReportData {
    private Long taskId;

    private Long buildingId;
}
