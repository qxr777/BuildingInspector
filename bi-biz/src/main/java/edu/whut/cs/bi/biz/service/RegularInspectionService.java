package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/9/21 21:05
 * @Description:
 **/
public interface RegularInspectionService {

    /**
     * 生成定期检查记录表
     *
     * @param document    Word文档
     * @param placeholder 占位符
     * @param tasks        任务ID
     * @throws Exception 异常
     */
    void generateRegularInspectionTable(XWPFDocument document, String placeholder, List<Task> tasks) throws Exception;

    /**
     * 填充单桥 的定期检查记录表
     */
    void fillSingleBridgeRegularInspectionTable(XWPFDocument document, Building building, Task task, Project project, ReportTemplateTypes templateType);
}
