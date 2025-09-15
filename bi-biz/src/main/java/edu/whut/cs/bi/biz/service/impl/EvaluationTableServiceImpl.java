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
     * @param document Word文档
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
     * @param document Word文档
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
     * @param document Word文档
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

            // 确保每行有足够的列
            for (int i = 0; i < totalRows; i++) {
                XWPFTableRow row = table.getRow(i);
                while (row.getTableCells().size() < 15) {
                    row.createCell();
                }
            }

            // 设置表格基本样式（不包括边框）
            setupComplexTableBasicStyle(table);

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
     * 填充复杂表头（两行表头带合并）
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
                "桥梁分项工程", "", "", "", "",  // 第4-8列：第4列显示文本，5-8列为空（将被合并）
                "桥梁分部工程", "", "", "", "",  // 第9-13列：第9列显示文本，10-13列为空（将被合并）
                "桥梁总体", ""  // 第14-15列：第14列显示文本，第15列为空（将被合并）
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

        // 最后处理单元格合并
//        setupHeaderMerges(table);
    }

    /**
     * 设置表头单元格内容
     *
     * @param cell 单元格
     * @param text 文本内容
     */
    private void setHeaderCellContent(XWPFTableCell cell, String text) {
        // 清除默认内容
        if (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }

        XWPFParagraph para = cell.addParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(9);
        run.setFontFamily("宋体");

        // 设置单元格垂直居中
        CTTc ctTc = cell.getCTTc();
        CTTcPr tcPr = ctTc.isSetTcPr() ? ctTc.getTcPr() : ctTc.addNewTcPr();
        CTVerticalJc vJc = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vJc.setVal(STVerticalJc.CENTER);

        // 设置单元格边框
        CTTcBorders borders = tcPr.addNewTcBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
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
     * 设置表头合并
     *
     * @param table 表格对象
     */
    private void setupHeaderMerges(XWPFTable table) {
        // 垂直合并：部位、部件类别i、评价部件（第0、1、2列跨两行）
        mergeCellsVertically(table, 0, 0, 1); // 部位列（第1列）
        mergeCellsVertically(table, 1, 0, 1); // 部件类别i列（第2列）
        mergeCellsVertically(table, 2, 0, 1); // 评价部件列（第3列）

        // 水平合并：桥梁分项工程（第1行，第4-8列，即索引3-7）
        mergeCellsHorizontally(table, 0, 3, 7); // 第1行，第4列到第8列

        // 水平合并：桥梁分部工程（第1行，第9-13列，即索引8-12）
        mergeCellsHorizontally(table, 0, 8, 12); // 第1行，第9列到第13列

        // 水平合并：桥梁总体（第1行，第14-15列，即索引13-14）
        mergeCellsHorizontally(table, 0, 13, 14); // 第1行，第14列到第15列

        // 合并完成后，为所有单元格设置垂直居中对齐
        setVerticalAlignmentForAllCells(table);
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
     * 水平合并单元格（参考DiseaseComparisonTableUtils的安全实现）
     *
     * @param table    表格
     * @param row      行
     * @param startCol 起始列
     * @param endCol   结束列
     */
    private void mergeCellsHorizontally(XWPFTable table, int row, int startCol, int endCol) {
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
     * 填充复杂表格数据（带合并）
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

        // 记录每个结构的起始行，用于后续的部位列合并
        Map<String, Integer> structureStartRows = new HashMap<>();
        Map<String, Integer> structureRowCounts = new HashMap<>();

        for (int structIndex = 0; structIndex < structureTypes.length; structIndex++) {
            String structureType = structureTypes[structIndex];
            List<BiObject> components = structureData.get(structureType);

            if (components.isEmpty()) {
                continue; // 跳过没有构件的结构
            }

            int structureStartRow = currentRow;
            structureStartRows.put(structureType, structureStartRow);
            structureRowCounts.put(structureType, components.size());

            // 填充该结构下的所有构件
            for (int i = 0; i < components.size(); i++) {
                BiObject component = components.get(i);
                XWPFTableRow dataRow = currentRow < table.getNumberOfRows() ? table.getRow(currentRow) : table.createRow();

                // 填充构件数据
                fillComplexComponentRow(dataRow, component, conditionMap.get(component.getId()), i + 1,
                        structureStartRow, currentRow, components.size(), structureType);

                currentRow++;
            }

            // 在第一行添加结构汇总信息
            XWPFTableRow firstRow = table.getRow(structureStartRow);
            fillComplexStructureSummary(firstRow, structureType, evalCodes[structIndex], structureScores[structIndex],
                    structureLevels[structIndex], structureWeights[structIndex], components.size());
        }

        // 处理部位列的垂直合并
        setupDataMerges(table, structureStartRows, structureRowCounts);

        // 在第一行数据行添加全桥汇总信息
        XWPFTableRow firstDataRow = table.getRow(2); // 第三行是第一行数据
        fillComplexBridgeOverall(table, firstDataRow, evaluation, structureStartRows, structureRowCounts);

        // 数据填充和合并完成后，再次确保所有单元格垂直居中
        setVerticalAlignmentForAllCells(table);
    }

    /**
     * 填充复杂构件行数据
     */
    private void fillComplexComponentRow(XWPFTableRow row, BiObject component, Condition condition, int index,
                                         int structureStartRow, int currentRow, int structureSize, String structureType) {
        String[] cellValues = new String[15];

        // 部位（只在第一行显示，其他行为空，后续会被合并）
        cellValues[0] = (currentRow == structureStartRow) ? structureType : "";

        // 部件类别
        cellValues[1] = String.valueOf(index);

        // 评价部件
        cellValues[2] = component.getName();

        // 权重标准值
        cellValues[3] = component.getStandardWeight() != null ? String.format("%.2f", component.getStandardWeight()) : "/";

        // 折算权重值
        cellValues[4] = component.getWeight() != null ? String.format("%.2f", component.getWeight()) : "/";

        if (condition != null) {
            // 技术状况评分
            cellValues[5] = condition.getScore() != null ? String.format("%.1f", condition.getScore()) : "/";

            // 主要部件技术状况等级
            cellValues[6] = condition.getLevel() != null ? condition.getLevel() + "类" : "/";

            // 加权得分
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

        // 后面的列先置空，在汇总方法中填充
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
     * 填充复杂结构汇总信息
     */
    private void fillComplexStructureSummary(XWPFTableRow row, String structureType, String evalCode,
                                             BigDecimal score, Integer level, BigDecimal weight, int componentCount) {
        // 权重（第9列，索引8）
        setCellContent(row.getCell(8), weight != null ? String.format("%.1f", weight) : "/");

        // 评价项目（第10列，索引9）
        setCellContent(row.getCell(9), evalCode);

        // 技术状况评分（第11列，索引10）
        setCellContent(row.getCell(10), score != null ? String.format("%.1f", score) : "/");

        // 技术状况等级（第12列，索引11）
        setCellContent(row.getCell(11), level != null ? level + "类" : "/");

        // 加权得分（第13列，索引12）
        if (score != null && weight != null) {
            BigDecimal weightedScore = score.multiply(weight);
            setCellContent(row.getCell(12), String.format("%.1f", weightedScore));
        } else {
            setCellContent(row.getCell(12), "/");
        }
    }

    /**
     * 设置数据区的合并
     */
    private void setupDataMerges(XWPFTable table, Map<String, Integer> structureStartRows, Map<String, Integer> structureRowCounts) {
        // 合并部位列（第1列）
        for (Map.Entry<String, Integer> entry : structureStartRows.entrySet()) {
            int startRow = entry.getValue();
            int rowCount = structureRowCounts.get(entry.getKey());
            if (rowCount > 1) {
                mergeCellsVertically(table, 0, startRow, startRow + rowCount - 1);
            }
        }
    }

    /**
     * 填充复杂全桥汇总信息
     */
    private void fillComplexBridgeOverall(XWPFTable table, XWPFTableRow row, BiEvaluation evaluation,
                                          Map<String, Integer> structureStartRows, Map<String, Integer> structureRowCounts) {
        // 技术状况评分Dr（第14列，索引13）
        setCellContent(row.getCell(13), evaluation.getSystemScore() != null ?
                String.format("%.1f", evaluation.getSystemScore()) : "/");

        // 技术状况等级Dj（第15列，索引14）
        setCellContent(row.getCell(14), evaluation.getSystemLevel() != null ?
                evaluation.getSystemLevel() + "类" : "/");

        // 计算需要合并的总行数，用于垂直合并全桥汇总列
        int totalDataRows = structureRowCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalDataRows > 1) {
            // 合并全桥汇总列（第14、15列，索引13、14）
            int firstDataRow = structureStartRows.values().stream().mapToInt(Integer::intValue).min().orElse(2);
            mergeCellsVertically(table, 13, firstDataRow, firstDataRow + totalDataRows - 1); // Dr列（第14列，索引13）
            mergeCellsVertically(table, 14, firstDataRow, firstDataRow + totalDataRows - 1); // Dj列（第15列，索引14）
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