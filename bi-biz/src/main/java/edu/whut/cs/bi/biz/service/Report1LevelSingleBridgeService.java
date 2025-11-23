package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Report;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;

public interface Report1LevelSingleBridgeService {
    public String generateReportDocument(Report report, Task task, ReportTemplateTypes templateType);
}
