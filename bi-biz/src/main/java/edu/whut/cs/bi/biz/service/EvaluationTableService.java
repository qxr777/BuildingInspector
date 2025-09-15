package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.Building;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * @Author:wanzheng
 * @Date:2025/9/13 17:06
 * @Description:
 **/
public interface EvaluationTableService {
    /**
     * 生成第八章技术状况评定表格
     *
     * @param document   Word文档
     * @param building   建筑物信息
     * @param evaluation 评定结果
     * @param bridgeName 桥梁名称
     */
    void generateEvaluationTableAfterParagraph(XWPFDocument document, XWPFParagraph afterParagraph, Building building, BiEvaluation evaluation, String bridgeName);
}
