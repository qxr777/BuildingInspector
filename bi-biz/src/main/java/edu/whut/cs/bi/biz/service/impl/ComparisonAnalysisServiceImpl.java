package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author:wanzheng
 * @Date:2025/9/16 21:00
 * @Description:
 **/
@Slf4j
@Service
public class ComparisonAnalysisServiceImpl implements ComparisonAnalysisService {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IBiEvaluationService biEvaluationService;

    @Resource
    private IPropertyService propertyService;

    @Resource
    private IBuildingService buildingService;
    @Autowired
    private ProjectServiceImpl projectServiceImpl;

    @Override
    public void generateComparisonAnalysisTable(XWPFDocument document, XWPFParagraph targetParagraph,
                                                Task currentTask, String bridgeName, boolean isSingleBridege) {
        try {
            log.info("开始生成比较分析表格，当前任务ID: {}", currentTask.getId());

            if (targetParagraph == null) {
                log.warn("目标段落为null，无法生成表格");
                return;
            }

            // 1. 查询当前任务和前一年任务
            if (currentTask == null || currentTask.getProject() == null) {
                log.error("无法获取当前任务信息或项目信息");
                return;
            }

            Integer currentYear = currentTask.getProject().getYear();
            Long buildingId = currentTask.getBuildingId();

            log.info("当前任务年份: {}, 建筑ID: {}", currentYear, buildingId);

            // 查询该建筑的所有任务
            Task queryTask = new Task();
            queryTask.setBuildingId(buildingId);
            List<Task> allTasks = taskMapper.selectTaskList(queryTask, null);

            // 查找前一年的任务
            Task previousYearTask = null;
            Integer previousYear = currentYear - 1;
            for (Task task : allTasks) {
                if (task.getProject() != null && previousYear.equals(task.getProject().getYear())) {
                    previousYearTask = task;
                    log.info("找到前一年任务: 年份{}, 任务ID: {}", previousYear, task.getId());
                    break;
                }
            }

            // 2. 查询两年的评定结果
            BiEvaluation currentEvaluation = biEvaluationService.selectBiEvaluationByTaskId(currentTask.getId());
            BiEvaluation previousEvaluation = null;
            if (previousYearTask != null) {
                previousEvaluation = biEvaluationService.selectBiEvaluationByTaskId(previousYearTask.getId());
            }
            // 11.10 修改 ， 如果上次 检查记录 数据库中没有记录对应任务 ， 可以查询excel 中的数据 组成部分信息。
            if (previousEvaluation == null) {
                Building building = buildingService.selectBuildingById(currentTask.getBuildingId());
                Property property = propertyService.selectPropertyById(building.getRootPropertyId());
                List<Property> properties = propertyService.selectPropertyList(property);
                String lastCheckDateStr = properties.stream().filter(a -> a.getName().equals("最近评定日期")).map(a -> a.getValue()).toList().get(0);
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date date = simpleDateFormat.parse(lastCheckDateStr);
                ZoneId zoneId = ZoneId.of("Asia/Shanghai");
                previousYear = date.toInstant().atZone(zoneId).toLocalDate().getYear();

                String lastSysLevelStr = properties.stream().filter(a -> a.getName().equals("桥梁技术状况")).map(a -> a.getValue()).toList().get(0);
                previousEvaluation = new BiEvaluation();
                previousEvaluation.setSystemLevel(lastSysLevelStr != null && lastCheckDateStr.length() >= 2 ? lastSysLevelStr.charAt(0) - '0' : null);
            }

            // 3. 生成表格
            if (isSingleBridege) {
                generateSingleBridgeComparisonTable(document, targetParagraph, bridgeName,
                        currentYear, currentEvaluation,
                        previousYear, previousEvaluation);
            } else {
                generateComparisonTable(document, targetParagraph, bridgeName,
                        currentYear, currentEvaluation,
                        previousYear, previousEvaluation);
            }

            log.info("比较分析表格生成完成");

        } catch (Exception e) {
            log.error("生成比较分析表格失败", e);
        }
    }

    /**
     * 生成比较分析表格
     */
    private void generateComparisonTable(XWPFDocument document, XWPFParagraph targetParagraph,
                                         String bridgeName, Integer currentYear, BiEvaluation currentEvaluation,
                                         Integer previousYear, BiEvaluation previousEvaluation) {
        try {
            // 获取插入位置的游标
            XmlCursor cursor = targetParagraph.getCTP().newCursor();

            // 清空占位符段落的所有Run
            while (targetParagraph.getRuns().size() > 0) {
                targetParagraph.removeRun(0);
            }

            // 创建表格引用段落
            XWPFParagraph tableRefPara;
            if (cursor != null) {
                tableRefPara = document.insertNewParagraph(cursor);
                cursor.toNextToken();
            } else {
                tableRefPara = document.createParagraph();
            }

            // 设置1.5倍行距
            CTPPr ppr1 = tableRefPara.getCTP().getPPr();
            if (ppr1 == null) {
                ppr1 = tableRefPara.getCTP().addNewPPr();
            }
            CTSpacing spacing = ppr1.isSetSpacing() ? ppr1.getSpacing() : ppr1.addNewSpacing();
            spacing.setLine(BigInteger.valueOf(360));

            // 创建第九章表格计数器
            AtomicInteger chapter9TableCounter = new AtomicInteger(1);
            String tableTitle = "近两次桥梁技术状况评分及评定等级对比分析表";

            // 使用现有方法创建表格标题
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(
                    document, tableTitle, cursor, 9, chapter9TableCounter);

            // 创建章节格式的表格引用域
            WordFieldUtils.createChapterTableReference(tableRefPara, tableBookmark,
                    "对比分析详情见表", "所示。");

            // 创建表格
            createComparisonTableWithData(document, cursor, bridgeName,
                    currentYear, currentEvaluation,
                    previousYear, previousEvaluation);

        } catch (Exception e) {
            log.error("生成比较分析表格失败", e);
            throw e;
        }
    }

    /**
     * 生成单桥比较分析表格
     */
    private void generateSingleBridgeComparisonTable(XWPFDocument document, XWPFParagraph targetParagraph,
                                                     String bridgeName, Integer currentYear, BiEvaluation currentEvaluation,
                                                     Integer previousYear, BiEvaluation previousEvaluation) {
        try {
            // 获取插入位置的游标
            XmlCursor cursor = targetParagraph.getCTP().newCursor();

            // 清空占位符段落的所有Run
            while (targetParagraph.getRuns().size() > 0) {
                targetParagraph.removeRun(0);
            }

            // 创建表格引用段落
            XWPFParagraph tableRefPara;
            if (cursor != null) {
                tableRefPara = document.insertNewParagraph(cursor);
                cursor.toNextToken();
            } else {
                tableRefPara = document.createParagraph();
            }

            // 设置1.5倍行距
            CTPPr ppr1 = tableRefPara.getCTP().getPPr();
            if (ppr1 == null) {
                ppr1 = tableRefPara.getCTP().addNewPPr();
            }
            CTSpacing spacing = ppr1.isSetSpacing() ? ppr1.getSpacing() : ppr1.addNewSpacing();
            spacing.setLine(BigInteger.valueOf(360));

            // 创建第九章表格计数器
            AtomicInteger chapter9TableCounter = new AtomicInteger(1);
            String tableTitle = "近两次桥梁技术状况评分及评定等级对比分析表";

            // 使用现有方法创建表格标题
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(
                    document, tableTitle, cursor, 9, chapter9TableCounter);

//            // 创建章节格式的表格引用域
//            WordFieldUtils.createChapterTableReference(tableRefPara, tableBookmark,
//                    "对比分析详情见表", "所示。");
            // 单桥改为 如下表
            XWPFRun prefixRun = tableRefPara.createRun();
            prefixRun.setText("对比分析详情如下表所示。");
            // 设置前缀字体格式
            CTRPr prefixRPr = prefixRun.getCTR().addNewRPr();
            prefixRPr.addNewRFonts().setAscii("宋体");
            prefixRPr.addNewRFonts().setEastAsia("宋体");
            prefixRPr.addNewSz().setVal(BigInteger.valueOf(24)); // 12号字体
            prefixRPr.addNewSzCs().setVal(BigInteger.valueOf(24));
            // 创建表格
            createComparisonTableWithData(document, cursor, bridgeName,
                    currentYear, currentEvaluation,
                    previousYear, previousEvaluation);

        } catch (Exception e) {
            log.error("生成比较分析表格失败", e);
            throw e;
        }
    }


    /**
     * 创建带数据的比较表格
     */
    private void createComparisonTableWithData(XWPFDocument document, XmlCursor cursor,
                                               String bridgeName, Integer currentYear, BiEvaluation currentEvaluation,
                                               Integer previousYear, BiEvaluation previousEvaluation) {
        try {
            // 使用XmlCursor在指定位置插入表格
            XWPFTable table = document.insertNewTbl(cursor);

            // 创建表格：1行表头 + 2行数据（如果有前一年数据）或1行数据
            int dataRows = 2;
            int totalRows = 1 + dataRows; // 1行表头 + 数据行

            // 确保表格有足够的行和列（8列）
            while (table.getNumberOfRows() < totalRows) {
                table.createRow();
            }

            // 确保每行有8列
            for (int i = 0; i < totalRows; i++) {
                XWPFTableRow row = table.getRow(i);
                while (row.getTableCells().size() < 8) {
                    row.createCell();
                }
            }

            // 设置表格基本样式
            setupTableBasicStyle(table);

            // 设置列宽
            setComparisonTableColumnWidths(table);

            // 填充表头
            fillComparisonTableHeader(table);

            // 填充数据
            fillComparisonTableData(table, bridgeName, currentYear, currentEvaluation,
                    previousYear, previousEvaluation);

            log.info("比较分析表格创建完成，共{}行", totalRows);

        } catch (Exception e) {
            log.error("创建比较分析表格失败", e);
            throw e;
        }
    }

    /**
     * 设置表格基本样式
     */
    private void setupTableBasicStyle(XWPFTable table) {
        // 设置表格边框
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // 设置表格边框
        CTTblBorders borders = tblPr.addNewTblBorders();
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);

        // 设置表格居中
        CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJcTable.CENTER);

        // 设置表格宽度
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(10000)); // 设置适合纵向布局的宽度
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格固定布局
        CTTblLayoutType tblLayout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        tblLayout.setType(STTblLayoutType.FIXED);

        /* ===== 11.11 新增：固定行高 0.8 cm ===== */
        int twips = (int) Math.round(0.8 * 567);          // 0.8 cm → 454 twips
        for (XWPFTableRow row : table.getRows()) {
            CTTrPr trPr = row.getCtRow().getTrPr();
            if (trPr == null) trPr = row.getCtRow().addNewTrPr();
            CTHeight ht = trPr.sizeOfTrHeightArray() > 0   // 复用或新建
                    ? trPr.getTrHeightArray(0)
                    : trPr.addNewTrHeight();
            ht.setVal(BigInteger.valueOf(twips));
            ht.setHRule(STHeightRule.AT_LEAST);                // 关键：11.12 修改 最小高度 。
        }
    }

    /**
     * 设置比较表格的列宽
     */
    private void setComparisonTableColumnWidths(XWPFTable table) {
        try {
            // 定义每列的宽度（单位：twips）- 8列
            int[] columnWidths = {
                    800,   // 年份
                    1500,  // 桥梁名称/部位
                    1200,  // 上部结构得分
                    1200,  // 下部结构得分
                    1200,  // 桥面系得分
                    1000,  // 总得分
                    1000,  // 评定等级
                    1300   // 总体技术状况等级
            };

            // 设置每一行的每一列的宽度
            for (XWPFTableRow row : table.getRows()) {
                for (int i = 0; i < Math.min(columnWidths.length, row.getTableCells().size()); i++) {
                    XWPFTableCell cell = row.getCell(i);
                    CTTcPr tcPr = cell.getCTTc().getTcPr();
                    if (tcPr == null) {
                        tcPr = cell.getCTTc().addNewTcPr();
                    }

                    CTTblWidth cellWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                    cellWidth.setW(BigInteger.valueOf(columnWidths[i]));
                    cellWidth.setType(STTblWidth.DXA);
                }
            }

            log.info("比较表格列宽设置完成");
        } catch (Exception e) {
            log.error("设置比较表格列宽失败", e);
            throw e;
        }
    }

    /**
     * 填充比较表格表头
     */
    private void fillComparisonTableHeader(XWPFTable table) {
        XWPFTableRow headerRow = table.getRow(0);

        String[] headers = {
                "年份", "桥梁名称/部位", "上部结构得分", "下部结构得分",
                "桥面系得分", "总得分", "评定等级", "总体技术状况等级"
        };

        for (int i = 0; i < headers.length && i < headerRow.getTableCells().size(); i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            setHeaderCellContent(cell, headers[i]);
        }
    }

    /**
     * 填充比较表格数据
     */
    private void fillComparisonTableData(XWPFTable table, String bridgeName,
                                         Integer currentYear, BiEvaluation currentEvaluation,
                                         Integer previousYear, BiEvaluation previousEvaluation) {
        int currentRowIndex = 1;

        // 填充前一年数据（如果存在）
        if (previousYear != null) {
            XWPFTableRow previousRow = table.getRow(currentRowIndex);
            if (previousEvaluation != null) {
                fillEvaluationDataRow(previousRow, previousYear.toString(), bridgeName, previousEvaluation);
            } else {
                // 如果前一年没有评定数据，可以填充部分数据 （11.10 修改）
                // 所以 previousEvaluation 基本 不会 为null 。
                // 因为 甲方 的桥梁信息 excel 包含了上一次的部分数据。
                fillEvaluationDataRow(previousRow, previousYear.toString(), bridgeName, null);
            }
            currentRowIndex++;
        }

        // 填充当前年数据
        if (currentRowIndex < table.getNumberOfRows()) {
            XWPFTableRow currentRow = table.getRow(currentRowIndex);
            if (currentEvaluation != null) {
                fillEvaluationDataRow(currentRow, currentYear.toString(), bridgeName, currentEvaluation);
            } else {
                // 如果当前年没有评定数据，也用"/"填充
                fillEvaluationDataRow(currentRow, currentYear.toString(), bridgeName, null);
            }
        }
    }

    /**
     * 填充单行评定数据
     */
    private void fillEvaluationDataRow(XWPFTableRow row, String year, String bridgeName, BiEvaluation evaluation) {
        String[] cellValues = new String[8];

        cellValues[0] = year;
        cellValues[1] = bridgeName;

        if (evaluation != null) {
            cellValues[2] = evaluation.getSuperstructureScore() == null ? "/" : formatScore(evaluation.getSuperstructureScore()); // 上部结构得分
            cellValues[3] = evaluation.getSubstructureScore() == null ? "/" : formatScore(evaluation.getSubstructureScore());   // 下部结构得分
            cellValues[4] = evaluation.getDeckSystemScore() == null ? "/" : formatScore(evaluation.getDeckSystemScore());     // 桥面系得分
            cellValues[5] = evaluation.getSystemScore() == null ? "/" : formatScore(evaluation.getSystemScore());         // 总得分
            cellValues[6] = evaluation.getSystemLevel() == null ? "/" : formatLevel(evaluation.getSystemLevel());         // 评定等级
            cellValues[7] = evaluation.getSystemLevel() == null ? "/" : formatLevel(evaluation.getSystemLevel());         // 总体技术状况等级
        } else {
            // 没有评定数据时用"/"填充
            for (int i = 2; i < 8; i++) {
                cellValues[i] = "/";
            }
        }

        // 设置单元格内容
        for (int i = 0; i < cellValues.length && i < row.getTableCells().size(); i++) {
            XWPFTableCell cell = row.getCell(i);
            setDataCellContent(cell, cellValues[i]);
        }
    }

    /**
     * 格式化分数显示
     */
    private String formatScore(BigDecimal score) {
        if (score == null) {
            return "/";
        }
        return String.format("%.1f", score);
    }

    /**
     * 格式化等级显示
     */
    private String formatLevel(Integer level) {
        if (level == null) {
            return "/";
        }
        return level + "类";
    }

    /**
     * 设置表头单元格内容
     */
    private void setHeaderCellContent(XWPFTableCell cell, String text) {
        if (text == null) text = "";

        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);

        // 设置字体
        run.setFontFamily("宋体");
        run.setFontSize(10);
        run.setBold(true);

        // 设置对齐方式
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 设置单元格垂直居中
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr == null) {
            tcPr = cell.getCTTc().addNewTcPr();
        }
        CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vAlign.setVal(STVerticalJc.CENTER);
    }

    /**
     * 设置数据单元格内容
     */
    private void setDataCellContent(XWPFTableCell cell, String text) {
        if (text == null) text = "";

        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);

        // 设置字体
        run.setFontFamily("宋体");
        run.setFontSize(10);
        run.setBold(false);

        // 设置对齐方式
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 设置单元格垂直居中
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr == null) {
            tcPr = cell.getCTTc().addNewTcPr();
        }
        CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vAlign.setVal(STVerticalJc.CENTER);
    }
}
