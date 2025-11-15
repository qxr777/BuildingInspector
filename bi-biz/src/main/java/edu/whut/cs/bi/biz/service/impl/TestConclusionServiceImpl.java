package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.IBiEvaluationService;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import edu.whut.cs.bi.biz.service.IReportService;
import edu.whut.cs.bi.biz.service.TestConclusionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author:wanzheng
 * @Date:2025/9/16 22:14
 * @Description:
 **/
@Slf4j
@Service
public class TestConclusionServiceImpl implements TestConclusionService {

    @Autowired
    private IBiEvaluationService biEvaluationService;

    @Autowired
    private BiObjectMapper biObjectMapper;

    // 使用静态变量避免循环依赖问题
    private static Map<Long, String> diseaseSummaryCache = new HashMap<>();

    @Override
    public void handleTestConclusion(XWPFDocument document, XWPFParagraph targetParagraph,
                                     Task task, String bridgeName) {
        try {
            log.info("开始处理第十章检测结论, taskId: {}", task.getId());

            // 查询评定结果
            BiEvaluation evaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
            if (evaluation == null) {
                log.warn("未找到任务的评定结果: taskId={}", task.getId());
                // 清空占位符
                clearParagraphRuns(targetParagraph);
                XWPFRun run = targetParagraph.createRun();
                run.setText("未找到评定结果");
                return;
            }

            // 生成检测结论文本
            String conclusionText = generateTestConclusionText(evaluation, bridgeName);

            // 清空占位符段落并填充内容
            clearParagraphRuns(targetParagraph);

            // 设置段落格式
            setupParagraphFormat(targetParagraph);

            // 添加结论文本
            XWPFRun run = targetParagraph.createRun();
            run.setText("\t" + conclusionText);
            run.setFontSize(12);
            run.setFontFamily("宋体");

            log.info("第十章检测结论处理完成");

        } catch (Exception e) {
            log.error("处理第十章检测结论失败: taskId={}", task.getId(), e);
            throw e;
        }
    }

    @Override
    public void handleTestConclusionBridge(XWPFDocument document, XWPFParagraph targetParagraph,
                                           Task task, String bridgeName) {
        try {
            log.info("开始处理第十章检测结论桥梁详情, taskId: {}", task.getId());

            // 获取桥梁根节点
            Building building = task.getBuilding();
            if (building == null || building.getRootObjectId() == null) {
                log.warn("未找到桥梁信息: taskId={}", task.getId());
                return;
            }

            BiObject rootNode = biObjectMapper.selectBiObjectById(building.getRootObjectId());
            if (rootNode == null) {
                log.warn("未找到桥梁根节点: rootObjectId={}", building.getRootObjectId());
                return;
            }

            // 获取所有节点
            List<BiObject> allNodes = biObjectMapper.selectChildrenById(building.getRootObjectId());

            // 获取插入位置的游标
            XmlCursor cursor = targetParagraph.getCTP().newCursor();

            // 清空占位符段落
            clearParagraphRuns(targetParagraph);

            // 生成桥梁结构树（只到第二层，第二层开始添加内容）
            writeBridgeStructureTree(document, rootNode, allNodes, cursor, 1, bridgeName);

            log.info("第十章检测结论桥梁详情处理完成");

        } catch (Exception e) {
            log.error("处理第十章检测结论桥梁详情失败: taskId={}", task.getId(), e);
            throw e;
        }
    }

    /**
     * 生成检测结论文本
     */
    private String generateTestConclusionText(BiEvaluation evaluation, String bridgeName) {
        StringBuilder content = new StringBuilder();

        content.append("依据《公路桥梁技术状况评定标准》（JTG/T H21-2011）规定评定方法，")
                .append(bridgeName)
                .append("评定为")
                .append(evaluation.getSystemLevel())
                .append("类，全桥技术状况评定为")
                .append(evaluation.getSystemLevel())
                .append("类。");

        return content.toString();
    }

    /**
     * 写入桥梁结构树（参考第三章的writeBiObjectTreeToWord方法）
     */
    private void writeBridgeStructureTree(XWPFDocument document, BiObject node, List<BiObject> allNodes,
                                          XmlCursor cursor, int level,
                                          String bridgeName) {
        try {
            if (level > 2) {
                return; // 只处理到第三层
            }

            // 第一层：桥梁名称
            if (level == 1) {
                XWPFParagraph bridgePara = document.insertNewParagraph(cursor);
                cursor.toNextToken();

                // 设置为下一级标题样式
                bridgePara.setStyle("4"); // 根据实际需要调整标题级别
                bridgePara.setAlignment(ParagraphAlignment.LEFT);

                XWPFRun bridgeRun = bridgePara.createRun();
                bridgeRun.setText(bridgeName);
                bridgeRun.setBold(false);
                bridgeRun.setFontFamily("黑体");

                // 递归处理子节点（第二层）
                List<BiObject> secondLevelNodes = allNodes.stream()
                        .filter(obj -> node.getId().equals(obj.getParentId()))
                        .sorted((a, b) -> a.getName().compareTo(b.getName()))
                        .collect(Collectors.toList());

                for (BiObject secondNode : secondLevelNodes) {
                    if (("附属设施").equals(secondNode.getName())) {
                        continue;
                    }
                    writeBridgeStructureTree(document, secondNode, allNodes, cursor, level + 1, bridgeName);
                }
            }
            // 第二层：结构类型（上部结构、下部结构、桥面系）
            else if (level == 2) {
                XWPFParagraph structurePara = document.insertNewParagraph(cursor);
                cursor.toNextToken();

                // 设置为下下级标题样式
                structurePara.setStyle("5"); // 根据实际需要调整标题级别
                structurePara.setAlignment(ParagraphAlignment.LEFT);

                XWPFRun structureRun = structurePara.createRun();
                structureRun.setText(node.getName());
                structureRun.setBold(false);
                structureRun.setFontFamily("黑体");

                // 处理第三层节点
                List<BiObject> thirdLevelNodes = allNodes.stream()
                        .filter(obj -> node.getId().equals(obj.getParentId()))
                        .filter(obj -> !obj.getName().contains("其他")) // 过滤掉"其他"节点
                        .sorted((a, b) -> a.getName().compareTo(b.getName()))
                        .collect(Collectors.toList());

                int index = 1;
                for (BiObject thirdNode : thirdLevelNodes) {
                    writeThirdLevelContent(document, thirdNode, cursor, index);
                    index++;
                }
            }

        } catch (Exception e) {
            log.error("写入桥梁结构树失败: level={}, nodeName={}", level, node.getName(), e);
            throw e;
        }
    }

    /**
     * 写入第三层内容（构件级别）
     */
    private void writeThirdLevelContent(XWPFDocument document, BiObject node,
                                        XmlCursor cursor, int index) {
        try {

            // 创建内容段落
            XWPFParagraph contentPara = document.insertNewParagraph(cursor);
            cursor.toNextToken();

            // 设置段落格式
            setupParagraphFormat(contentPara);

            // 第一部分：编号和标题
            XWPFRun titleRun = contentPara.createRun();
            titleRun.setText("\t（" + index + "）" + node.getName());
            titleRun.setFontSize(12);
            titleRun.setBold(true);
            titleRun.setFontFamily("宋体");

            // 换行
            titleRun.addBreak();

            // 获取病害汇总（优先使用第三章缓存的结果）
            String diseaseSummary = getOrCreateDiseaseSummary(node.getId());

            if (diseaseSummary != null && !diseaseSummary.trim().isEmpty()) {
                // 第二部分：检查结果开头
                XWPFRun introRun = contentPara.createRun();
                introRun.setText("\t经检查，" + node.getName() + "主要病害为：");
                introRun.setFontSize(12);
                introRun.setBold(true);
                introRun.setFontFamily("宋体");
                // 如果有病害汇总（从第三章缓存获取），添加病害汇总
                String[] lines = diseaseSummary.split("\\r?\\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        XWPFRun diseaseRun = contentPara.createRun();
                        diseaseRun.addBreak();
                        diseaseRun.setText("\t" + line.trim());
                        diseaseRun.setFontSize(12);
                        diseaseRun.setFontFamily("宋体");
                    }
                }
            } else {
                // 没有缓存的病害汇总，说明第三章没有处理这个节点或没有病害
                XWPFRun noDiseaseRun = contentPara.createRun();
                noDiseaseRun.setText("\t经检查，" + node.getName() + "未见明显病害。");
                noDiseaseRun.setFontSize(12);
                noDiseaseRun.setFontFamily("宋体");
            }

        } catch (Exception e) {
            log.error("写入第三层内容失败: nodeName={}", node.getName(), e);
            throw e;
        }
    }

    /**
     * 获取病害汇总（优先使用第三章缓存的结果）
     */
    private String getOrCreateDiseaseSummary(Long nodeId) {
        try {
            // 先从缓存查找（第三章处理时已经缓存了结果）
            String cached = diseaseSummaryCache.get(nodeId);
            if (cached != null) {
                log.info("使用第三章缓存的病害汇总: nodeId={}", nodeId);
                return cached;
            }

            // 如果第三章没有处理这个节点（例如没有病害），返回空白或简单描述
            log.warn("第三章未缓存节点{}的病害汇总，可能该节点无病害", nodeId);
            return "";

        } catch (Exception e) {
            log.error("获取病害汇总失败: nodeId={}", nodeId, e);
            return "病害信息获取失败";
        }
    }

    /**
     * 清空段落中的所有Run
     */
    private void clearParagraphRuns(XWPFParagraph paragraph) {
        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }
    }

    /**
     * 设置段落格式
     */
    private void setupParagraphFormat(XWPFParagraph paragraph) {
        // 设置段落左对齐
        paragraph.setAlignment(ParagraphAlignment.LEFT);

        // 设置1.5倍行距
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr == null) {
            ppr = paragraph.getCTP().addNewPPr();
        }
        CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(360)); // 1.5倍行距

        // 设置缩进
        CTInd ind = ppr.isSetInd() ? ppr.getInd() : ppr.addNewInd();
        ind.setFirstLine(BigInteger.valueOf(0));
        ind.setLeft(BigInteger.valueOf(0));
    }

    /**
     * 缓存病害汇总数据（供外部调用，在第三章处理时缓存）
     */
    public void cacheDiseaseSummary(Long nodeId, String summary) {
        diseaseSummaryCache.put(nodeId, summary);
        log.info("缓存节点{}的病害汇总: {}", nodeId, summary);
    }

    /**
     * 清空病害汇总缓存
     */
    public void clearDiseaseSummaryCache() {
        diseaseSummaryCache.clear();
        log.info("清空病害汇总缓存");
    }

    @Override
    public Map<Long, String> getSummaryCache() {
        return this.diseaseSummaryCache;
    }
}
