package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiEvaluation;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Condition;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.EvaluationTableService;
import edu.whut.cs.bi.biz.service.IConditionService;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.deepoove.poi.util.TableTools;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 第八章技术状况评定表格生成服务实现
 *
 * @author
 */
@Slf4j
@Service
public class EvaluationTableServiceImpl implements EvaluationTableService {

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private IConditionService conditionService;

    @Override
    public void generateEvaluationTableAfterParagraph(XWPFDocument document, XWPFParagraph afterParagraph, Building building, BiEvaluation evaluation, String bridgeName) {
        try {
            log.info("开始在指定位置生成第八章技术状况评定表格");

            if (afterParagraph == null) {
                log.warn("插入位置段落为null，无法生成表格");
                return;
            }

            // 获取四句话段落在文档中的位置索引
            int paragraphIndex = getParagraphIndex(document, afterParagraph);
            if (paragraphIndex == -1) {
                log.warn("无法找到四句话段落在文档中的位置");
                return;
            }

            // 在四句话段落后创建第一个分节符（结束当前分节）
            addSectionBreakAfterParagraph(document, afterParagraph);

            // 在四句话段落后立即创建横向分节符，并获取新插入的段落
            XWPFParagraph landscapeParagraph = addLandscapeSectionBreakAfterParagraph(document, afterParagraph);

            // 从横向分节符段落后获取cursor位置
            XmlCursor cursor = landscapeParagraph.getCTP().newCursor();

            // 创建表格标题
            AtomicInteger tableCounter = new AtomicInteger(1);
            String tableTitle = bridgeName + "桥梁技术状况评定表";

            // 使用XmlCursor在指定位置创建表格标题
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(document, tableTitle, cursor, 8, tableCounter);

            // 获取第二层和第三层结构
            Map<String, List<BiObject>> structureData = collectStructureData(building.getRootObjectId(), evaluation.getId());

            // 使用XmlCursor在标题后插入表格
            createComplexEvaluationTableWithCursor(document, cursor, structureData, evaluation);

            // 在表格后创建纵向分节符，恢复纵向布局
            addPortraitSectionBreakAfterTable(document, afterParagraph);

            log.info("第八章技术状况评定表格生成完成");

        } catch (Exception e) {
            log.error("生成第八章技术状况评定表格失败", e);
            throw e;
        }
    }

    /**
     * 获取段落在文档中的索引位置
     */
    private int getParagraphIndex(XWPFDocument document, XWPFParagraph targetParagraph) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).equals(targetParagraph)) {
                return i;
            }
        }
        return -1;
    }


    /**
     * 在四句话段落后创建第一个分节符
     *
     * @param document       Word文档
     * @param afterParagraph 四句话段落
     */
    private void addSectionBreakAfterParagraph(XWPFDocument document, XWPFParagraph afterParagraph) {
        try {
            // 在四句话段落中直接设置分节符
            CTP ctP = afterParagraph.getCTP();
            CTPPr pPr = ctP.isSetPPr() ? ctP.getPPr() : ctP.addNewPPr();

            // 如果已存在sectPr，先移除以避免冲突
            if (pPr.isSetSectPr()) {
                pPr.unsetSectPr();
            }

            // 创建分节符，使用NEXT_PAGE确保后续横向内容在新页面开始
            CTSectPr sectPr = pPr.addNewSectPr();
            CTSectType sectType = sectPr.addNewType();
            sectType.setVal(STSectionMark.NEXT_PAGE);

            log.info("在四句话段落后添加了第一个分节符");

        } catch (Exception e) {
            log.error("在四句话段落后添加分节符失败", e);
            throw e;
        }
    }

    /**
     * 在四句话段落后立即创建一个新段落并设置横向分节符
     *
     * @param document       Word文档
     * @param afterParagraph 四句话段落
     */
    private XWPFParagraph addLandscapeSectionBreakAfterParagraph(XWPFDocument document, XWPFParagraph afterParagraph) {
        try {
            // 使用XmlCursor在四句话段落后插入新段落
            XmlCursor cursor = afterParagraph.getCTP().newCursor();
            cursor.toEndToken();
            cursor.toNextToken();

            // 在指定位置插入新段落
            XWPFParagraph newParagraph = document.insertNewParagraph(cursor);
            newParagraph.setAlignment(ParagraphAlignment.LEFT);

            // 在新段落中设置横向分节符
            CTP ctP = newParagraph.getCTP();
            CTPPr pPr = ctP.isSetPPr() ? ctP.getPPr() : ctP.addNewPPr();

            // 创建分节符并设置横向
            CTSectPr sectPr = pPr.addNewSectPr();
            // 关键：不设置NEXT_PAGE，让横向设置在当前页面生效
            // 这样XmlCursor就能正确定位到横向区域

            // 创建页尺寸对象并设置横向
            CTPageSz pageSize = sectPr.addNewPgSz();
            pageSize.setOrient(STPageOrientation.LANDSCAPE);
            pageSize.setW(BigInteger.valueOf(16838)); // 设置页面宽度
            pageSize.setH(BigInteger.valueOf(11906)); // 设置页面高度

            // 设置适合横向布局的页边距
            CTPageMar pgMar = sectPr.addNewPgMar();
            pgMar.setTop(BigInteger.valueOf(1796)); // 3.17cm
            pgMar.setBottom(BigInteger.valueOf(1423)); // 2.51cm
            pgMar.setLeft(BigInteger.valueOf(1440)); // 2.54cm
            pgMar.setRight(BigInteger.valueOf(1440)); // 2.54cm

            log.info("在四句话段落后立即添加了横向分节符");

            return newParagraph;

        } catch (Exception e) {
            log.error("在四句话段落后添加横向分节符失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 在表格后创建纵向分节符，恢复纵向布局
     *
     * @param document       Word文档
     * @param afterParagraph 四句话段落（用于定位）
     */
    private void addPortraitSectionBreakAfterTable(XWPFDocument document, XWPFParagraph afterParagraph) {
        try {
            // 创建一个新段落来承载纵向分节符
            XWPFParagraph sectionParagraph = document.createParagraph();
            sectionParagraph.setAlignment(ParagraphAlignment.LEFT);

            // 在新段落中设置纵向分节符
            CTP ctP = sectionParagraph.getCTP();
            CTPPr pPr = ctP.isSetPPr() ? ctP.getPPr() : ctP.addNewPPr();

            // 创建分节符并设置纵向
            CTSectPr sectPr = pPr.addNewSectPr();
            CTSectType sectType = sectPr.addNewType();
            sectType.setVal(STSectionMark.CONTINUOUS);

            // 设置页面尺寸为纵向
            CTPageSz pageSize = sectPr.addNewPgSz();
            pageSize.setOrient(STPageOrientation.PORTRAIT);
            pageSize.setW(BigInteger.valueOf(11906)); // 21.0cm
            pageSize.setH(BigInteger.valueOf(16838)); // 29.7cm

            // 设置纵向页边距
            CTPageMar pgMar = sectPr.addNewPgMar();
            pgMar.setTop(BigInteger.valueOf(1440)); // 2.51cm
            pgMar.setBottom(BigInteger.valueOf(1440)); // 2.51cm
            pgMar.setLeft(BigInteger.valueOf(1796)); // 2.54cm
            pgMar.setRight(BigInteger.valueOf(1796)); // 2.54cm

            log.info("在表格后设置了纵向分节符");

        } catch (Exception e) {
            log.error("在表格后设置纵向分节符失败", e);
            throw e;
        }
    }


    /**
     * 收集结构数据（第二层和第三层节点）
     *
     * @param rootObjectId   根节点ID
     * @param biEvaluationId 评定ID
     * @return 结构数据映射
     */
    private Map<String, List<BiObject>> collectStructureData(Long rootObjectId, Long biEvaluationId) {
        Map<String, List<BiObject>> structureData = new LinkedHashMap<>();

        // 初始化三个主要结构
        structureData.put("上部结构", new ArrayList<>());
        structureData.put("下部结构", new ArrayList<>());
        structureData.put("桥面系", new ArrayList<>());

        // 直接查询根节点的子节点（第二层节点）
        List<BiObject> secondLevelNodes = biObjectMapper.selectChildrenByParentId(rootObjectId);

        // 为每个第二层节点，直接获取其子节点
        for (BiObject secondLevel : secondLevelNodes) {
            String structureType = determineStructureTypeFromName(secondLevel.getName());

            if (structureData.containsKey(structureType)) {
                // 直接查询该节点的子节点（第三层）
                List<BiObject> children = biObjectMapper.selectChildrenByParentId(secondLevel.getId())
                        .stream()
                        .filter(obj -> !obj.getName().contains("其他"))
                        .collect(Collectors.toList());

                structureData.get(structureType).addAll(children);
            }
        }

        log.info("收集到结构数据: 上部结构{}个, 下部结构{}个, 桥面系{}个",
                structureData.get("上部结构").size(),
                structureData.get("下部结构").size(),
                structureData.get("桥面系").size());

        return structureData;
    }

    /**
     * 根据节点名称判断结构类型
     */
    private String determineStructureTypeFromName(String name) {
        if (name.contains("上部")) {
            return "上部结构";
        } else if (name.contains("下部")) {
            return "下部结构";
        } else if (name.contains("桥面")) {
            return "桥面系";
        }
        return "其他";
    }

    /**
     * 使用XmlCursor在指定位置创建复杂合并表格
     *
     * @param document      Word文档
     * @param cursor        插入位置的XmlCursor
     * @param structureData 结构数据
     * @param evaluation    评定结果
     */
    private void createComplexEvaluationTableWithCursor(XWPFDocument document, XmlCursor cursor, Map<String, List<BiObject>> structureData, BiEvaluation evaluation) {
        try {
            // 计算表格行数：表头(2行) + 数据行
            int dataRows = 0;
            for (List<BiObject> components : structureData.values()) {
                dataRows += components.size();
            }
            int totalRows = 2 + dataRows; // 2行表头 + 数据行

            // 使用XmlCursor在指定位置插入表格
            XWPFTable table = document.insertNewTbl(cursor);

            // 确保表格有足够的行和列
            // 首先确保有足够的行
            while (table.getNumberOfRows() < totalRows) {
                table.createRow();
            }
            log.info("表格现有行数: {}", table.getNumberOfRows());

            // 确保每行有足够的列
            for (int i = 0; i < totalRows; i++) {
                XWPFTableRow row = table.getRow(i);
                while (row.getTableCells().size() < 15) {
                    row.createCell();
                }
                log.debug("第{}行列数: {}", i, row.getTableCells().size());
            }

            // 设置表格基本样式（不包括边框）
            setupComplexTableBasicStyle(table);

            // 设置列宽
            setColumnWidths(table);

            // 填充复杂表头（两行表头）
            fillComplexTableHeader(table);

            // 填充数据并处理合并
            fillComplexTableData(table, structureData, evaluation);

            log.info("复杂评定表格创建完成，共{}行", totalRows);

        } catch (Exception e) {
            log.error("使用XmlCursor在指定位置创建复杂评定表格失败", e);
            throw e;
        }
    }

    /**
     * 设置表格的列宽
     *
     * @param table 表格对象
     */
    private void setColumnWidths(XWPFTable table) {
        try {
            // 定义每列的宽度（单位：twips，1英寸=1440 twips）- 适用于横向页面
            int[] columnWidths = {
                    1000,  // 列0: 部位
                    800,   // 列1: 部件类别i
                    2000,  // 列2: 评价部件
                    1000,  // 列3: 权重标准值
                    1000,  // 列4: 折算权重值
                    1000,  // 列5: 技术状况评分
                    1200,  // 列6: 主要部件技术状况等级
                    1000,  // 列7: 加权得分
                    800,   // 列8: 权重
                    1000,  // 列9: 评价项目
                    1000,  // 列10: 技术状况评分
                    1000,  // 列11: 技术状况等级
                    1000,  // 列12: 加权得分
                    1000,  // 列13: 技术状况评分Dr
                    1200   // 列14: 技术状况等级Dj
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

            log.info("表格列宽设置完成");
        } catch (Exception e) {
            log.error("设置表格列宽失败", e);
            throw e;
        }
    }

    /**
     * 设置复杂表格基本样式（不包括边框）
     *
     * @param table 表格对象
     */
    private void setupComplexTableBasicStyle(XWPFTable table) {
        // 设置表格边框
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // 设置表格边框（表格级别）
        CTTblBorders borders = tblPr.addNewTblBorders();
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);

        CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJcTable.CENTER);

        // 设置表格宽度为100%以适应横向页面
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(15000)); // 设置更大宽度适应横向布局
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格固定布局
        CTTblLayoutType tblLayout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        tblLayout.setType(STTblLayoutType.FIXED);
    }


    /**
     * 填充复杂表头（两行表头带合并）- 修正版
     *
     * @param table 表格对象
     */
    private void fillComplexTableHeader(XWPFTable table) {
        // 第一行表头
        XWPFTableRow firstHeaderRow = table.getRow(0);
        // 第二行表头
        XWPFTableRow secondHeaderRow = table.getRow(1);

        // 第一行表头文本（15列）
        String[] firstRowHeaders = {
                "部位", "部件类别i", "评价部件",
                "桥梁分项工程", "", "", "", "",  // 第4-8列：第4列显示文本，5-8列为空
                "桥梁分部工程", "", "", "", "",  // 第9-13列：第9列显示文本，10-13列为空
                "桥梁总体", ""  // 第14-15列：第14列显示文本，第15列为空
        };

        // 第二行表头文本（15列）
        String[] secondRowHeaders = {
                "", "", "",  // 前3列为空（被第一行垂直合并占用）
                "权重标准值", "折算权重值", "技术状况评分", "主要部件技术状况等级", "加权得分",  // 第4-8列
                "权重", "评价项目", "技术状况评分", "技术状况等级", "加权得分",  // 第9-13列
                "技术状况评分Dr", "技术状况等级Dj"  // 第14-15列
        };

        // 设置第一行表头
        for (int i = 0; i < firstRowHeaders.length && i < 15; i++) {
            XWPFTableCell cell = firstHeaderRow.getCell(i);
            setHeaderCellContentSafely(cell, firstRowHeaders[i]);
        }

        // 设置第二行表头
        for (int i = 0; i < secondRowHeaders.length && i < 15; i++) {
            XWPFTableCell cell = secondHeaderRow.getCell(i);
            setHeaderCellContentSafely(cell, secondRowHeaders[i]);
        }

        // 处理表头合并
        setupHeaderMerges(table);
    }

    /**
     * 设置表头单元格内容（完全避免操作CTTcPr以保护合并属性）
     *
     * @param cell 单元格
     * @param text 文本内容
     */
    private void setHeaderCellContentSafely(XWPFTableCell cell, String text) {
        if (text == null) text = "";

        // 清除默认内容
        cell.removeParagraph(0);

        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);

        // 设置字体
        run.setFontFamily("宋体");
        run.setFontSize(9);
        run.setBold(true);

        // 设置对齐方式
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 不设置单元格级别的垂直对齐，避免操作CTTcPr
        // 垂直对齐将通过表格样式或合并后统一设置
    }

    /**
     * 设置表头合并 - 修正版
     *
     * @param table 表格对象
     */
    private void setupHeaderMerges(XWPFTable table) {
        try {
            log.info("开始设置表头合并");

            // 第一行水平合并
            // 桥梁分项工程：第0行第3-7列（索引3-7，共5列）
            mergeTableCellsHorizontally(table, 0, 3, 7);

            // 桥梁分部工程：第0行第8-12列（索引8-12，共5列）
            mergeTableCellsHorizontally(table, 0, 4, 8);

            // 桥梁总体：第0行第13-14列（索引13-14，共2列）
            mergeTableCellsHorizontally(table, 0, 5, 6);

            // 垂直合并：部位、部件类别i、评价部件（第0、1、2列跨两行）
            mergeCellsVertically(table, 0, 0, 1); // 部位列
            mergeCellsVertically(table, 1, 0, 1); // 部件类别i列
            mergeCellsVertically(table, 2, 0, 1); // 评价部件列

            // 合并完成后设置垂直居中
            setVerticalAlignmentForAllCells(table);

            log.info("表头合并完成");

        } catch (Exception e) {
            log.error("设置表头合并失败", e);
            throw e;
        }
    }

    /**
     * 为所有单元格设置垂直居中对齐（在合并完成后执行）
     *
     * @param table 表格对象
     */
    private void setVerticalAlignmentForAllCells(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                CTTcPr tcPr = cell.getCTTc().getTcPr();
                if (tcPr == null) {
                    tcPr = cell.getCTTc().addNewTcPr();
                }
                // 只设置垂直对齐，不影响其他属性
                if (!tcPr.isSetVAlign()) {
                    CTVerticalJc vAlign = tcPr.addNewVAlign();
                    vAlign.setVal(STVerticalJc.CENTER);
                }
            }
        }
    }

    /**
     * 垂直合并单元格（参考DiseaseComparisonTableUtils的安全实现）
     *
     * @param table    表格
     * @param col      列
     * @param startRow 起始行
     * @param endRow   结束行
     */
    private void mergeCellsVertically(XWPFTable table, int col, int startRow, int endRow) {
        try {
            log.debug("垂直合并第{}列，从第{}行到第{}行", col, startRow, endRow);

            // 安全检查
            if (table == null || col < 0 || startRow < 0 || endRow < startRow) {
                log.warn("垂直合并参数无效: col={}, startRow={}, endRow={}", col, startRow, endRow);
                return;
            }

            if (table.getNumberOfRows() <= endRow) {
                log.warn("表格行数不足，无法进行垂直合并: 表格行数={}, 需要行数={}", table.getNumberOfRows(), endRow + 1);
                return;
            }

            for (int row = startRow; row <= endRow; row++) {
                XWPFTableRow tableRow = table.getRow(row);
                if (tableRow == null || tableRow.getTableCells().size() <= col) {
                    log.warn("第{}行第{}列不存在，跳过垂直合并", row, col);
                    continue;
                }

                XWPFTableCell cell = tableRow.getCell(col);
                CTTcPr tcPr = cell.getCTTc().getTcPr();
                if (tcPr == null) {
                    tcPr = cell.getCTTc().addNewTcPr();
                }

                CTVMerge vMerge = tcPr.isSetVMerge() ? tcPr.getVMerge() : tcPr.addNewVMerge();
                if (row == startRow) {
                    vMerge.setVal(STMerge.RESTART);
                    log.debug("设置第{}行第{}列为垂直合并起始", row, col);
                } else {
                    vMerge.setVal(STMerge.CONTINUE);
                    log.debug("设置第{}行第{}列为垂直合并继续", row, col);
                }
            }

        } catch (Exception e) {
            log.error("垂直合并第{}列（第{}行到第{}行）失败", col, startRow, endRow, e);
            throw e;
        }
    }

    /**
     * 使用poi-tl TableTools进行水平合并
     *
     * @param table    表格
     * @param row      行
     * @param startCol 起始列
     * @param endCol   结束列
     */
    private void mergeTableCellsHorizontally(XWPFTable table, int row, int startCol, int endCol) {
        try {
            log.debug("使用TableTools水平合并第{}行，从第{}列到第{}列", row, startCol, endCol);

            // 安全检查
            if (table == null || row < 0 || startCol < 0 || endCol < startCol) {
                log.warn("TableTools合并参数无效: row={}, startCol={}, endCol={}", row, startCol, endCol);
                return;
            }

            if (table.getNumberOfRows() <= row) {
                log.warn("表格行数不足，无法进行TableTools合并: 表格行数={}, 需要行数={}", table.getNumberOfRows(), row + 1);
                return;
            }

            XWPFTableRow tableRow = table.getRow(row);
            if (tableRow == null) {
                log.warn("第{}行不存在，无法进行TableTools合并", row);
                return;
            }

            if (tableRow.getTableCells().size() <= endCol) {
                log.warn("第{}行列数不足，无法进行TableTools合并: 行列数={}, 需要列数={}", row, tableRow.getTableCells().size(), endCol + 1);
                return;
            }

            // 在合并前计算总宽度
            int totalWidth = 0;
            for (int i = startCol; i <= endCol; i++) {
                XWPFTableCell cell = tableRow.getCell(i);
                CTTcPr tcPr = cell.getCTTc().getTcPr();
                if (tcPr != null && tcPr.isSetTcW()) {
                    Object w = tcPr.getTcW().getW();
                    if (w != null) {
                        if (w instanceof BigInteger) {
                            totalWidth += ((BigInteger) w).intValue();
                        } else if (w instanceof Integer) {
                            totalWidth += (Integer) w;
                        } else if (w instanceof String) {
                            try {
                                totalWidth += Integer.parseInt((String) w);
                            } catch (NumberFormatException e) {
                                log.warn("无法解析单元格宽度: {}", w);
                            }
                        }
                    }
                }
            }

            // 如果总宽度为0（可能是因为没有设置宽度），使用默认宽度
            if (totalWidth == 0) {
                totalWidth = 1000 * (endCol - startCol + 1); // 每个单元格默认1000
            }

            // 使用poi-tl的TableTools进行合并
            TableTools.mergeCellsHorizonal(table, row, startCol, endCol);

            // 合并后设置单元格宽度为所有被合并单元格的宽度总和
            XWPFTableCell mergedCell = tableRow.getCell(startCol);
            CTTcPr tcPr = mergedCell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = mergedCell.getCTTc().addNewTcPr();
            }

            CTTblWidth tcWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
            tcWidth.setW(BigInteger.valueOf(totalWidth));
            tcWidth.setType(STTblWidth.DXA);

            log.debug("成功使用TableTools合并第{}行第{}列到第{}列，合并后宽度: {}", row, startCol, endCol, totalWidth);

        } catch (Exception e) {
            log.error("使用TableTools合并第{}行（第{}列到第{}列）失败", row, startCol, endCol, e);
            throw e;
        }
    }

    /**
     * 填充复杂表格数据（带合并）- 修正版
     *
     * @param table         表格对象
     * @param structureData 结构数据
     * @param evaluation    评定结果
     */
    private void fillComplexTableData(XWPFTable table, Map<String, List<BiObject>> structureData, BiEvaluation evaluation) {
        int currentRow = 2; // 从第三行开始填充数据（前两行是表头）

        // 查询所有condition数据
        List<Condition> allConditions = conditionService.selectConditionsByBiEvaluationId(evaluation.getId());
        Map<Long, Condition> conditionMap = allConditions.stream()
                .collect(Collectors.toMap(Condition::getBiObjectId, c -> c, (c1, c2) -> c1));

        // 处理三个主要结构
        String[] structureTypes = {"上部结构", "下部结构", "桥面系"};
        String[] evalCodes = {"SPCI", "SBCI", "BDCI"};
        BigDecimal[] structureScores = {evaluation.getSuperstructureScore(), evaluation.getSubstructureScore(), evaluation.getDeckSystemScore()};
        Integer[] structureLevels = {evaluation.getSuperstructureLevel(), evaluation.getSubstructureLevel(), evaluation.getDeckSystemLevel()};
        BigDecimal[] structureWeights = {new BigDecimal("0.4"), new BigDecimal("0.4"), new BigDecimal("0.2")};

        // 记录每个结构的起始行和行数
        Map<String, Integer> structureStartRows = new HashMap<>();
        Map<String, Integer> structureRowCounts = new HashMap<>();

        // 记录全局数据行范围
        int globalDataStartRow = currentRow;
        int totalDataRows = 0;

        for (int structIndex = 0; structIndex < structureTypes.length; structIndex++) {
            String structureType = structureTypes[structIndex];
            List<BiObject> components = structureData.get(structureType);

            if (components.isEmpty()) {
                continue;
            }

            int structureStartRow = currentRow;
            structureStartRows.put(structureType, structureStartRow);
            structureRowCounts.put(structureType, components.size());
            totalDataRows += components.size();

            // 填充该结构下的所有构件
            for (int i = 0; i < components.size(); i++) {
                BiObject component = components.get(i);
                XWPFTableRow dataRow = currentRow < table.getNumberOfRows() ? table.getRow(currentRow) : table.createRow();

                // 填充构件数据
                fillComplexComponentRow(dataRow, component, conditionMap.get(component.getId()), i + 1,
                        currentRow == structureStartRow, structureType);

                // 只在结构的第一行填充分部工程汇总信息
                if (currentRow == structureStartRow) {
                    fillComplexStructureSummary(dataRow, structureType, evalCodes[structIndex],
                            structureScores[structIndex], structureLevels[structIndex], structureWeights[structIndex]);
                }

                currentRow++;
            }
        }

        // 在第一个数据行填充全桥汇总信息
        if (totalDataRows > 0) {
            XWPFTableRow firstDataRow = table.getRow(globalDataStartRow);
            fillComplexBridgeOverall(firstDataRow, evaluation);
        }

        // 设置数据区域的合并
        setupDataMerges(table, structureStartRows, structureRowCounts, globalDataStartRow, totalDataRows);
    }

    /**
     * 填充复杂构件行数据 - 修正版
     */
    private void fillComplexComponentRow(XWPFTableRow row, BiObject component, Condition condition,
                                         int index, boolean isFirstRowOfStructure, String structureType) {
        String[] cellValues = new String[15];

        // 部位（只在结构的第一行显示）
        cellValues[0] = isFirstRowOfStructure ? structureType : "";

        // 部件类别
        cellValues[1] = String.valueOf(index);

        // 评价部件
        cellValues[2] = component.getName();

        // 桥梁分项工程列（第3-7列）
        cellValues[3] = component.getStandardWeight() != null ? String.format("%.2f", component.getStandardWeight()) : "/";
        if (component.getWeight() != null && component.getWeight().doubleValue() != 0) {
            cellValues[4] = String.format("%.2f", component.getWeight());
        } else {
            cellValues[4] = "/";
        }

        if (condition != null && component.getWeight().doubleValue() != 0) {
            cellValues[5] = condition.getScore() != null ? String.format("%.1f", condition.getScore()) : "/";
            cellValues[6] = condition.getLevel() != null ? condition.getLevel() + "类" : "/";

            if (condition.getScore() != null && component.getWeight() != null) {
                BigDecimal weightedScore = condition.getScore().multiply(component.getWeight());
                cellValues[7] = String.format("%.1f", weightedScore);
            } else {
                cellValues[7] = "/";
            }
        } else {
            cellValues[5] = "/";
            cellValues[6] = "/";
            cellValues[7] = "/";
        }

        // 桥梁分部工程列（第8-12列）和桥梁总体列（第13-14列）在各自的填充方法中处理
        for (int i = 8; i < 15; i++) {
            cellValues[i] = "";
        }

        // 设置单元格内容
        for (int i = 0; i < cellValues.length; i++) {
            XWPFTableCell cell = i < row.getTableCells().size() ? row.getCell(i) : row.addNewTableCell();
            setCellContent(cell, cellValues[i]);
        }
    }

    /**
     * 填充复杂结构汇总信息 - 修正版
     */
    private void fillComplexStructureSummary(XWPFTableRow row, String structureType, String evalCode,
                                             BigDecimal score, Integer level, BigDecimal weight) {
        // 桥梁分部工程列（第8-12列）
        setCellContent(row.getCell(8), weight != null ? String.format("%.1f", weight) : "/");
        setCellContent(row.getCell(9), evalCode);
        setCellContent(row.getCell(10), score != null ? String.format("%.1f", score) : "/");
        setCellContent(row.getCell(11), level != null ? level + "类" : "/");

        if (score != null && weight != null) {
            BigDecimal weightedScore = score.multiply(weight);
            setCellContent(row.getCell(12), String.format("%.1f", weightedScore));
        } else {
            setCellContent(row.getCell(12), "/");
        }
    }

    /**
     * 填充复杂全桥汇总信息 - 修正版
     */
    private void fillComplexBridgeOverall(XWPFTableRow row, BiEvaluation evaluation) {
        // 桥梁总体列（第13-14列）
        setCellContent(row.getCell(13), evaluation.getSystemScore() != null ?
                String.format("%.1f", evaluation.getSystemScore()) : "/");
        setCellContent(row.getCell(14), evaluation.getSystemLevel() != null ?
                evaluation.getSystemLevel() + "类" : "/");
    }

    /**
     * 设置数据区的合并 - 修正版
     */
    private void setupDataMerges(XWPFTable table, Map<String, Integer> structureStartRows,
                                 Map<String, Integer> structureRowCounts, int globalDataStartRow, int totalDataRows) {
        try {
            // 1. 合并部位列（第0列）- 按结构类型分别合并
            for (Map.Entry<String, Integer> entry : structureStartRows.entrySet()) {
                String structureType = entry.getKey();
                int startRow = entry.getValue();
                int rowCount = structureRowCounts.get(structureType);

                if (rowCount > 1) {
                    log.info("合并{}部位列，从第{}行到第{}行", structureType, startRow, startRow + rowCount - 1);
                    mergeCellsVertically(table, 0, startRow, startRow + rowCount - 1);
                }
            }

            // 2. 合并分部工程列（第8-12列）- 按结构类型分别合并
            for (Map.Entry<String, Integer> entry : structureStartRows.entrySet()) {
                String structureType = entry.getKey();
                int startRow = entry.getValue();
                int rowCount = structureRowCounts.get(structureType);

                if (rowCount > 1) {
                    log.info("合并{}分部工程列，从第{}行到第{}行", structureType, startRow, startRow + rowCount - 1);
                    for (int col = 8; col <= 12; col++) {
                        mergeCellsVertically(table, col, startRow, startRow + rowCount - 1);
                    }
                }
            }

            // 3. 合并全桥总体列（第13-14列）- 合并所有数据行
            if (totalDataRows > 1) {
                log.info("合并全桥总体列，从第{}行到第{}行", globalDataStartRow, globalDataStartRow + totalDataRows - 1);
                mergeCellsVertically(table, 13, globalDataStartRow, globalDataStartRow + totalDataRows - 1);
                mergeCellsVertically(table, 14, globalDataStartRow, globalDataStartRow + totalDataRows - 1);
            }

            log.info("数据区合并完成");
        } catch (Exception e) {
            log.error("设置数据区合并失败", e);
            throw e;
        }
    }

    /**
     * 设置单元格内容和样式（完全避免操作CTTcPr以保护合并属性）
     *
     * @param cell 单元格
     * @param text 文本内容
     */
    private void setCellContent(XWPFTableCell cell, String text) {
        if (text == null) text = "";

        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);

        // 设置字体
        run.setFontFamily("宋体");
        run.setFontSize(9);
        run.setBold(false);

        // 设置对齐方式
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 不设置单元格级别的垂直对齐，避免操作CTTcPr
        // 垂直对齐将通过表格样式或合并后统一设置
    }
}