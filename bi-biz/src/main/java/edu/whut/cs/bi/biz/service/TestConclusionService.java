package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Task;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * @Author:wanzheng
 * @Date:2025/9/16 22:13
 * @Description:
 **/
public interface TestConclusionService {

    /**
     * 处理第十章检测结论占位符
     *
     * @param document        Word文档
     * @param targetParagraph 目标段落（占位符段落）
     * @param task           任务信息
     * @param bridgeName     桥梁名称
     */
    void handleTestConclusion(XWPFDocument document, XWPFParagraph targetParagraph,
                              Task task, String bridgeName);

    /**
     * 处理第十章检测结论桥梁详情占位符
     *
     * @param document        Word文档
     * @param targetParagraph 目标段落（占位符段落）
     * @param task           任务信息
     * @param bridgeName     桥梁名称
     */
    void handleTestConclusionBridge(XWPFDocument document, XWPFParagraph targetParagraph,
                                    Task task, String bridgeName);

    /**
     * 缓存病害汇总数据（供外部调用，在第三章处理时缓存）
     */
    void cacheDiseaseSummary(Long nodeId, String summary);

    /**
     * 清空病害汇总缓存
     */
    void clearDiseaseSummaryCache();
}