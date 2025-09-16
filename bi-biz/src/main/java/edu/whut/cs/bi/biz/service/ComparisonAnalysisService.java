package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Task;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

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
     * @param currentTask   当前任务ID
     * @param bridgeName      桥梁名称
     */
    void generateComparisonAnalysisTable(XWPFDocument document, XWPFParagraph targetParagraph,
                                         Task currentTask, String bridgeName);
}