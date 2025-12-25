package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.vo.DiseaseComparisonData;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 病害对比表格工具类
 * 用于生成病害变化情况分析表格
 *
 * @author wanzheng
 */
public class DiseaseComparisonTableUtils {

    /**
     * 创建病害对比表格
     *
     * @param document      Word文档
     * @param data          对比数据列表
     * @param cursor        插入位置游标
     * @param tableCounter  表格计数器
     * @param chapterNum    章节号
     * @param subChapterNum 子章节号
     * @param bridgeName    桥梁名称
     * @return 表格书签名
     */
    public static String createDiseaseComparisonTable(XWPFDocument document, List<DiseaseComparisonData> data,
                                                      XmlCursor cursor, AtomicInteger tableCounter,
                                                      Integer chapterNum, Integer subChapterNum, String bridgeName) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        // 计算行数：1行表头 + 1行子表头 + 数据行数
        int totalRows = 2 + data.size();
        int totalCols = 11; // 桥梁名称(1) + 部位(2) + 构件(1) + 病害种类(1) + 2023(2) + 2024(2) + 发展情况(1) + 备注(1)

        // 创建表格标题
        String titleText = bridgeName + "病害汇总统计表";
        String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(document, titleText, cursor,
                chapterNum, tableCounter);

        // 创建基础表格
        XWPFTable table;
        if (cursor != null) {
            table = document.insertNewTbl(cursor);
            cursor.toNextToken();

            // 初始化表格结构 - 确保每行都是准确的11列
            XWPFTableRow row0 = table.getRow(0);
            // 第一个单元格已存在，只需添加剩余的10个
            for (int i = 1; i < totalCols; i++) {
                row0.addNewTableCell();
            }

            // 创建其余行 - 每行都创建完整的11列
            for (int i = 1; i < totalRows; i++) {
                XWPFTableRow newRow = table.createRow();
            }
        } else {
            table = document.createTable(totalRows, totalCols);
        }

        // 创建表头
        createTableHeader(table,data);

        // 填充数据
        fillTableData(table, data);

        // 应用合并
        applyTableMerges(table, data);

        // 设置表格样式
        setTableStyle(table);

        return tableBookmark;
    }

    /**
     * 创建表格表头
     */
    private static void createTableHeader(XWPFTable table,List<DiseaseComparisonData> data) {
        // 第一行主表头
        XWPFTableRow headerRow = table.getRow(0);
        setCellText(headerRow.getCell(0), "桥梁名称", true, true);
        setCellText(headerRow.getCell(1), "部位", true, true);
        setCellText(headerRow.getCell(2), "", true, true);
        setCellText(headerRow.getCell(3), "构件", true, true);
        setCellText(headerRow.getCell(4), "病害种类", true, true);
        setCellText(headerRow.getCell(5), String.valueOf(data.get(0).getLastYear()), true, true);
        setCellText(headerRow.getCell(6), "", true, true);
        setCellText(headerRow.getCell(7), String.valueOf(data.get(0).getCurrentYear()), true, true);
        setCellText(headerRow.getCell(8), "", true, true);
        setCellText(headerRow.getCell(9), "发展情况", true, true);
        setCellText(headerRow.getCell(10), "备注", true, true);

        // 第二行子表头
        XWPFTableRow subHeaderRow = table.getRow(1);
        setCellText(subHeaderRow.getCell(0), "", true, true);
        setCellText(subHeaderRow.getCell(1), "", true, true);
        setCellText(subHeaderRow.getCell(2), "", true, true);
        setCellText(subHeaderRow.getCell(3), "", true, true);
        setCellText(subHeaderRow.getCell(4), "", true, true);
        setCellText(subHeaderRow.getCell(5), "数量", true, true);
        setCellText(subHeaderRow.getCell(6), "病害程度", true, true);
        setCellText(subHeaderRow.getCell(7), "数量", true, true);
        setCellText(subHeaderRow.getCell(8), "病害程度", true, true);
        setCellText(subHeaderRow.getCell(9), "", true, true);
        setCellText(subHeaderRow.getCell(10), "", true, true);

        // 设置表头行高
        setHeaderRowHeights(headerRow, subHeaderRow);

        // 应用表头合并
        applyHeaderMerges(table);
    }

    /**
     * 设置表头行高
     */
    private static void setHeaderRowHeights(XWPFTableRow headerRow, XWPFTableRow subHeaderRow) {
        // 设置主表头行高 (约1.5厘米)
        setRowHeight(headerRow, 900);

        // 设置子表头行高 (约1.0厘米)
        setRowHeight(subHeaderRow, 600);
    }

    /**
     * 设置行高的通用方法
     */
    private static void setRowHeight(XWPFTableRow row, int heightInTwips) {
        try {
            row.setHeight(heightInTwips);
        } catch (Exception e) {
            // 如果setHeight方法失败，尝试使用底层API
            CTTrPr trPr = row.getCtRow().getTrPr();
            if (trPr == null) {
                trPr = row.getCtRow().addNewTrPr();
            }

            // 直接添加新的高度设置
            CTHeight height = trPr.addNewTrHeight();
            height.setVal(BigInteger.valueOf(heightInTwips));
            height.setHRule(STHeightRule.EXACT);
        }
    }

    /**
     * 应用表头合并
     */
    private static void applyHeaderMerges(XWPFTable table) {
        // 部位 (第1-2列) 横向合并
        mergeHorizontalCells(table, 0, 1, 2);

        mergeHorizontalCells(table,1,1,2);

        // 2023 (第5-6列) 横向合并
        mergeHorizontalCells(table, 0, 5, 6);

        // 2024 (第7-8列) 横向合并
        mergeHorizontalCells(table, 0, 7, 8);


        // 纵向合并
        mergeVerticalCells(table,1, 0, 1);
        mergeVerticalCells(table, 0, 0, 1);  // 桥梁名称
        mergeVerticalCells(table, 3, 0, 1);  // 构件
        mergeVerticalCells(table, 4, 0, 1);  // 病害种类
        mergeVerticalCells(table, 9, 0, 1);  // 发展情况
        mergeVerticalCells(table, 10, 0, 1); // 备注

    }

    /**
     * 填充表格数据
     */
    private static void fillTableData(XWPFTable table, List<DiseaseComparisonData> data) {
        for (int i = 0; i < data.size(); i++) {
            DiseaseComparisonData item = data.get(i);
            XWPFTableRow row = table.getRow(i + 2); // 前两行是表头

            setCellText(row.getCell(0), item.getBridgeName(), false, true);
            setCellText(row.getCell(1), item.getPosition1(), false, true);
            setCellText(row.getCell(2), item.getPosition2(), false, true);
            setCellText(row.getCell(3), item.getComponent(), false, true);
            setCellText(row.getCell(4), item.getDiseaseType(), false, true);
            setCellText(row.getCell(5), item.getQuantity2023() != null ? item.getQuantity2023().toString() : "", false, true);
            setCellText(row.getCell(6), item.getSeverity2023() != null ? item.getSeverity2023() : "", false, true);
            setCellText(row.getCell(7), item.getQuantity2024() != null ? item.getQuantity2024().toString() : "", false, true);
            setCellText(row.getCell(8), item.getSeverity2024() != null ? item.getSeverity2024() : "", false, true);
            setCellText(row.getCell(9), item.getDevelopmentStatus() != null ? item.getDevelopmentStatus() : "", false, true);
            setCellText(row.getCell(10), item.getRemarks() != null ? item.getRemarks() : "", false, true); // 添加备注列

            // 设置数据行高度 (约1.0厘米) - 适中
            setRowHeight(row, 600);
        }
    }

    /**
     * 应用数据行合并
     */
    private static void applyTableMerges(XWPFTable table, List<DiseaseComparisonData> data) {
        int startRow = 2; // 数据从第3行开始（索引为2）

        for (int i = 0; i < data.size(); i++) {
            DiseaseComparisonData item = data.get(i);
            int currentRow = startRow + i;

            // 桥梁名称列合并
            if (item.isFirstInBridge() && item.getBridgeRowSpan() > 1) {
                mergeVerticalCells(table, 0, currentRow, currentRow + item.getBridgeRowSpan() - 1);
            }

            // 部位1列合并
            if (item.isFirstInPosition1() && item.getPosition1RowSpan() > 1) {
                mergeVerticalCells(table, 1, currentRow, currentRow + item.getPosition1RowSpan() - 1);
            }

            // 部位2列合并
            if (item.isFirstInPosition2() && item.getPosition2RowSpan() > 1) {
                mergeVerticalCells(table, 2, currentRow, currentRow + item.getPosition2RowSpan() - 1);
            }

            // 构件列合并
            if (item.isFirstInComponent() && item.getComponentRowSpan() > 1) {
                mergeVerticalCells(table, 3, currentRow, currentRow + item.getComponentRowSpan() - 1);
            }
        }
    }

    /**
     * 设置单元格文本
     */
    private static void setCellText(XWPFTableCell cell, String text, boolean isHeader, boolean isCenter) {
        if (text == null) text = "";

        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);

        // 设置字体
        run.setFontFamily("宋体");
        run.setFontSize(9);
        run.setBold(isHeader);

        // 设置对齐方式
        if (isCenter) {
            paragraph.setAlignment(ParagraphAlignment.CENTER);
        } else {
            paragraph.setAlignment(ParagraphAlignment.LEFT);
        }

        // 设置单元格垂直对齐
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr == null) {
            tcPr = cell.getCTTc().addNewTcPr();
        }
        CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vAlign.setVal(STVerticalJc.CENTER);
    }

    /**
     * 横向合并单元格
     */
    private static void mergeHorizontalCells(XWPFTable table, int row, int startCol, int endCol) {
        XWPFTableRow tableRow = table.getRow(row);
        for (int col = startCol; col <= endCol; col++) {
            XWPFTableCell cell = tableRow.getCell(col);
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }

            CTHMerge hMerge = tcPr.isSetHMerge() ? tcPr.getHMerge() : tcPr.addNewHMerge();
            if (col == startCol) {
                hMerge.setVal(STMerge.RESTART);
            } else {
                hMerge.setVal(STMerge.CONTINUE);
            }
        }
    }

    /**
     * 纵向合并单元格
     */
    private static void mergeVerticalCells(XWPFTable table, int col, int startRow, int endRow) {
        for (int row = startRow; row <= endRow; row++) {
            XWPFTableCell cell = table.getRow(row).getCell(col);
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }

            CTVMerge vMerge = tcPr.isSetVMerge() ? tcPr.getVMerge() : tcPr.addNewVMerge();
            if (row == startRow) {
                vMerge.setVal(STMerge.RESTART);
            } else {
                vMerge.setVal(STMerge.CONTINUE);
            }
        }
    }

    /**
     * 设置表格样式
     */
    private static void setTableStyle(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // 设置表格宽度 - 横向页面可用宽度更大
        // 横向A4页面宽度约16838 twips，减去左右边距(1440*2)，可用宽度约13958 twips
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(13500)); // 使用更大的宽度适配横向页面
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格边框
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        setBorderStyle(borders.addNewTop());
        setBorderStyle(borders.addNewBottom());
        setBorderStyle(borders.addNewLeft());
        setBorderStyle(borders.addNewRight());
        setBorderStyle(borders.addNewInsideH());
        setBorderStyle(borders.addNewInsideV());

        // 设置列宽
        setColumnWidths(table);
    }

    /**
     * 设置边框样式
     */
    private static void setBorderStyle(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor("000000");
    }

    /**
     * 设置列宽 - 适配横向页面
     */
    private static void setColumnWidths(XWPFTable table) {
        // 列宽分配（单位：twips，1英寸=1440 twips）- 适用于横向页面
        // 总宽度约13500 twips，11列合理分配
        // 桥梁名称、部位1、部位2、构件、病害种类各约1000-1200
        // 数量列较窄约600，程度列约1400，发展情况和备注较宽约1600-1800
        int[] colWidths = {
                1100,  // 列0: 桥梁名称
                1000,  // 列1: 部位1
                1000,  // 列2: 部位2
                1000,  // 列3: 构件
                1200,  // 列4: 病害种类
                600,   // 列5: 2023数量
                1400,  // 列6: 2023病害程度
                600,   // 列7: 2024数量
                1400,  // 列8: 2024病害程度
                1800,  // 列9: 发展情况
                1400   // 列10: 备注
        };

        for (XWPFTableRow row : table.getRows()) {
            for (int i = 0; i < row.getTableCells().size() && i < colWidths.length; i++) {
                XWPFTableCell cell = row.getCell(i);
                CTTcPr tcPr = cell.getCTTc().getTcPr();
                if (tcPr == null) {
                    tcPr = cell.getCTTc().addNewTcPr();
                }
                CTTblWidth cellWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                cellWidth.setW(BigInteger.valueOf(colWidths[i]));
                cellWidth.setType(STTblWidth.DXA);
            }
        }
    }
}