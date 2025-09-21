package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Task;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * @Author:wanzheng
 * @Date:2025/9/21 21:05
 * @Description:
 **/
public interface RegularInspectionService {

    /**
     * 生成定期检查记录表
     *
     * @param document Word文档
     * @param placeholder 占位符
     * @param building 建筑物ID
     * @param task 任务ID
     * @param project 项目ID
     * @throws Exception 异常
     */
    void generateRegularInspectionTable(XWPFDocument document, String placeholder, Building building, Task task, Project project) throws Exception;
}