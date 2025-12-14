package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Task;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/9/16 20:59
 * @Description:
 **/
public interface ComparisonAnalysisService {

    /**
     * 生成第九章比较分析表格
     *
     * @param document        Word文档
     * @param targetParagraph 目标段落（占位符段落）
     * @param currentTask     当前任务ID
     * @param bridgeName      桥梁名称
     */
    void generateComparisonAnalysisTable(XWPFDocument document, XWPFParagraph targetParagraph,
                                         Task currentTask, String bridgeName, boolean isSingleBridege);

    /**
     * 生成第九章比较分析表格（多桥版本）
     *
     * @param document        Word文档
     * @param targetParagraph 目标段落（占位符段落）
     * @param currentYearTasks 当前年度的所有子桥任务列表
     */
    void generateMultiBridgeComparisonAnalysisTable(XWPFDocument document, XWPFParagraph targetParagraph,
                                                   List<Task> currentYearTasks);
}