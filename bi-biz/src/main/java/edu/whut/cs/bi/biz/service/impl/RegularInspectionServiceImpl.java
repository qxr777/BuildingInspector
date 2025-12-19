package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.ArchBridgeRecordTableComponentList;
import edu.whut.cs.bi.biz.domain.enums.BeamBridgeRecordTableComponentList;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ConditionMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.WordFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 定期检查记录表服务实现类
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class RegularInspectionServiceImpl implements RegularInspectionService {

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private ConditionMapper conditionMapper;

    @Autowired
    private IBiEvaluationService biEvaluationService;

    @Autowired
    private IPropertyService propertyService;

    @Override
    public void generateRegularInspectionTable(XWPFDocument document, String placeholder, List<Task> tasks) throws Exception {
        log.info("开始生成定期检查记录表, placeholder: {}", placeholder);

        // 查找占位符位置
        XWPFParagraph targetParagraph = findParagraphByPlaceholder(document, placeholder);
        if (targetParagraph == null) {
            log.warn("未找到占位符: {}", placeholder);
            return;
        }

        for (Task task : tasks) {
            Building building = task.getBuilding();
            Project project = task.getProject();

            if (building == null) {
                log.warn("任务未关联建筑物: taskId={}", task.getId());
                continue;
            }
            if (project == null) {
                log.warn("任务未关联项目: taskId={}", task.getId());
                continue;
            }
            // 获取插入位置的游标
            XmlCursor cursor = targetParagraph.getCTP().newCursor();

            // 获取桥梁结构树
            BiObject rootObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
            if (rootObject == null) {
                log.warn("未找到桥梁结构树: rootObjectId={}", building.getRootObjectId());
                return;
            }

            // 获取所有子节点
            List<BiObject> allObjects = biObjectMapper.selectChildrenById(rootObject.getId());

            // 创建表格计数器
            AtomicInteger chapter11TableCounter = new AtomicInteger(1);

            // 创建表格标题，使用WordFieldUtils
            String tableTitle = building.getName() + " 定期检查记录表";
            String tableBookmark = WordFieldUtils.createTableCaptionWithCounter(document, tableTitle, cursor, 11, chapter11TableCounter);

            // 创建表格
            XWPFTable table = document.insertNewTbl(cursor);
            cursor.toNextToken();

            // 设置表格样式
            setTableStyle(table);

            // 创建表格内容
            createTableContent(table, building, project, task, rootObject, allObjects);
        }
        // 清除原占位符段落
        clearParagraph(targetParagraph);

        log.info("定期检查记录表生成完成");
    }

    /**
     * 设置表格样式
     */
    private void setTableStyle(XWPFTable table) {
        // 设置表格边框
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }

        // 设置表格边框
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);

        // 设置表格宽度
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(9500)); // 表格宽度
        tblWidth.setType(STTblWidth.DXA);

        // 设置表格居中
        CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJcTable.CENTER);

        // 设置表格为固定宽度模式
        CTTblLayoutType tblLayout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        tblLayout.setType(STTblLayoutType.FIXED);
    }

    /**
     * 创建表格内容
     */
    private void createTableContent(XWPFTable table, Building building, Project project, Task task,
                                    BiObject rootObject, List<BiObject> allObjects) {
        // 创建表头部分
        createTableHeader(table, building);

        // 创建表格主体部分
        createTableBody(table, rootObject, allObjects, building.getId(), project.getId(), task);

        // 创建表格底部
        createTableFooter(table);
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
     * 创建表格头部
     */
    private void createTableHeader(XWPFTable table, Building building) {
        // 统一使用11列布局的宽度配置，保证所有行宽度一致
        int[] columnWidths = {8, 8, 8, 8, 8, 8, 8, 8, 8, 16, 20}; // 总和为100
        int totalWidth = 9500; // 总宽度

        // 第一行：公路管理机构名称（横跨所有列，左对齐）
        XWPFTableRow row1 = table.getRow(0);
        // 确保创建所有单元格（11列）
        for (int i = 0; i < 11; i++) {
            if (i >= row1.getTableCells().size()) {
                row1.createCell();
            }
            setCellWidth(row1.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置第一个单元格（左对齐）
        XWPFTableCell cell1 = row1.getCell(0);
        XWPFParagraph paragraph1 = cell1.getParagraphs().get(0);
        paragraph1.setAlignment(ParagraphAlignment.LEFT);

        // 设置段落行距为单倍行距
        paragraph1.setSpacingBetween(1.0);
        paragraph1.setSpacingAfter(0);
        paragraph1.setSpacingBefore(0);

        XWPFRun run1 = paragraph1.createRun();
        run1.setText("公路管理机构名称：");
        run1.setFontSize(9);
        run1.setFontFamily("宋体");

        // 横向合并所有11列
        mergeHorizontalCells(table, 0, 0, 10);

        // 第二行：基本信息行1
        XWPFTableRow row2 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row2.getTableCells().size()) {
                row2.createCell();
            }
            setCellWidth(row2.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置单元格内容
        createHeaderCell(row2, 0, "1线路编号");
        createHeaderCell(row2, 2, "");
        createHeaderCell(row2, 4, "2线路名称");
        createHeaderCell(row2, 6, "");
        createHeaderCell(row2, 8, "3桥位桩号");
        createHeaderCell(row2, 10, "");

        // 横向合并
        mergeHorizontalCells(table, 1, 0, 1);  // 1 线路编号
        mergeHorizontalCells(table, 1, 2, 3);  // 数据1
        mergeHorizontalCells(table, 1, 4, 5);  // 2 线路名称
        mergeHorizontalCells(table, 1, 6, 7);  // 数据2
        mergeHorizontalCells(table, 1, 8, 9);  // 3 桥位桩号
        // 数据3只占一列（第10列）

        // 第三行：基本信息行2
        XWPFTableRow row3 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row3.getTableCells().size()) {
                row3.createCell();
            }
            setCellWidth(row3.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row3, 0, "4桥梁编号");
        createHeaderCell(row3, 2, "");
        createHeaderCell(row3, 4, "5桥梁名称");
        createHeaderCell(row3, 6, building.getName() != null ? building.getName() : "");
        createHeaderCell(row3, 8, "6被跨域通道名称");
        createHeaderCell(row3, 10, "");

        mergeHorizontalCells(table, 2, 0, 1);  // 4 桥梁编号
        mergeHorizontalCells(table, 2, 2, 3);  // 数据1
        mergeHorizontalCells(table, 2, 4, 5);  // 5 桥梁名称
        mergeHorizontalCells(table, 2, 6, 7);  // 数据2
        mergeHorizontalCells(table, 2, 8, 9);  // 6 被跨域通道名称
        // 数据3只占一列（第10列）

        // 第四行：基本信息行3
        XWPFTableRow row4 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row4.getTableCells().size()) {
                row4.createCell();
            }
            setCellWidth(row4.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row4, 0, "7桥梁全长(m)");
        createHeaderCell(row4, 2, "");
        createHeaderCell(row4, 4, "8主跨结构");
        createHeaderCell(row4, 6, "");
        createHeaderCell(row4, 8, "9最大跨径(m)");
        createHeaderCell(row4, 10, "");

        mergeHorizontalCells(table, 3, 0, 1);
        mergeHorizontalCells(table, 3, 2, 3);
        mergeHorizontalCells(table, 3, 4, 5);
        mergeHorizontalCells(table, 3, 6, 7);
        mergeHorizontalCells(table, 3, 8, 9);

        // 第五行：基本信息行4
        XWPFTableRow row5 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row5.getTableCells().size()) {
                row5.createCell();
            }
            setCellWidth(row5.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row5, 0, "10管养单位");
        createHeaderCell(row5, 2, "");
        createHeaderCell(row5, 4, "11建成时间");
        createHeaderCell(row5, 6, "");
        createHeaderCell(row5, 8, "12上次修复养护时间");
        createHeaderCell(row5, 10, "");

        mergeHorizontalCells(table, 4, 0, 1);
        mergeHorizontalCells(table, 4, 2, 3);
        mergeHorizontalCells(table, 4, 4, 5);
        mergeHorizontalCells(table, 4, 6, 7);
        mergeHorizontalCells(table, 4, 8, 9);

        // 第六行：基本信息行5
        XWPFTableRow row6 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row6.getTableCells().size()) {
                row6.createCell();
            }
            setCellWidth(row6.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row6, 0, "13上次检查时间");
        createHeaderCell(row6, 2, "");
        createHeaderCell(row6, 4, "14本次检查时间");
        createHeaderCell(row6, 6, "");
        createHeaderCell(row6, 8, "15本次检查时气候及环境温度");
        createHeaderCell(row6, 10, "");

        mergeHorizontalCells(table, 5, 0, 1);
        mergeHorizontalCells(table, 5, 2, 3);
        mergeHorizontalCells(table, 5, 4, 5);
        mergeHorizontalCells(table, 5, 6, 7);
        mergeHorizontalCells(table, 5, 8, 9);

        // 第七行：表头行（11列）
        XWPFTableRow row7 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row7.getTableCells().size()) {
                row7.createCell();
            }
            setCellWidth(row7.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置表头单元格
        createHeaderCell(row7, 0, "序号");
        createHeaderCell(row7, 1, "16部位");
        createHeaderCell(row7, 2, "17部件名称");
        createHeaderCell(row7, 3, "18\n评分");
        createHeaderCell(row7, 4, "19缺损");
        createHeaderCell(row7, 9, "20养护建议\n(维修范围、方式、时间)");
        createHeaderCell(row7, 10, "21是否需要专项检查");

        // 横向合并"19 缺损"（第4-8列）
        mergeHorizontalCells(table, 6, 4, 8);

        // 第八行：缺损子表头（11列）
        XWPFTableRow row8 = table.createRow();
        for (int i = 0; i < 11; i++) {
            if (i >= row8.getTableCells().size()) {
                row8.createCell();
            }
            setCellWidth(row8.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置缺损子表头
        createHeaderCell(row8, 4, "类型");
        createHeaderCell(row8, 5, "位置");
        createHeaderCell(row8, 6, "范围");
        createHeaderCell(row8, 7, "照片");
        createHeaderCell(row8, 8, "最不利构件");

        // 纵向合并第7-8行的前4列和后2列
        mergeVerticalCells(table, 0, 6, 7);  // 序号
        mergeVerticalCells(table, 1, 6, 7);  // 16 部位
        mergeVerticalCells(table, 2, 6, 7);  // 17 部件名称
        mergeVerticalCells(table, 3, 6, 7);  // 18 评分
        mergeVerticalCells(table, 9, 6, 7);  // 20 养护建议
        mergeVerticalCells(table, 10, 6, 7); // 21 是否需要专项检查
    }

    /**
     * 创建表格主体部分
     */
    private void createTableBody(XWPFTable table, BiObject rootObject, List<BiObject> allObjects, Long buildingId, Long projectId, Task task) {
        // 定义列宽比例（11列）- 与表头保持一致
        int[] columnWidths = {8, 8, 8, 8, 8, 8, 8, 8, 8, 16, 20}; // 总和为100
        int totalWidth = 9500; // 总宽度

        // 获取第二层节点（部位）
        List<BiObject> level2Objects = allObjects.stream()
                .filter(obj -> obj.getParentId() != null && obj.getParentId().equals(rootObject.getId()))
                .collect(Collectors.toList());

        // 收集所有第三层节点的ID
        List<Long> allLevel3Ids = new ArrayList<>();
        Map<Long, BiObject> level3ObjectMap = new HashMap<>();
        Map<Long, BiObject> level2ForLevel3Map = new HashMap<>(); // 记录每个第三层节点对应的第二层节点

        // 遍历第二层节点（部位）
        for (BiObject level2Object : level2Objects) {
            // 获取第三层节点（部件）
            List<BiObject> level3Objects = allObjects.stream()
                    .filter(obj -> obj.getParentId() != null && obj.getParentId().equals(level2Object.getId()))
                    .collect(Collectors.toList());

            // 收集第三层节点ID
            for (BiObject level3Object : level3Objects) {
                allLevel3Ids.add(level3Object.getId());
                level3ObjectMap.put(level3Object.getId(), level3Object);
                level2ForLevel3Map.put(level3Object.getId(), level2Object);
            }
        }

        // 批量查询所有构件的评分
        Map<Long, String> componentScoreMap = batchGetComponentScores(allLevel3Ids, buildingId, projectId, task);

        // 批量查询所有构件的病害类型
        Map<Long, String> componentDiseaseTypesMap = batchGetComponentDiseaseTypes(allLevel3Ids, buildingId, projectId, allObjects);

        int rowIndex = 1; // 从1开始计数

        // 遍历第二层节点（部位）
        for (BiObject level2Object : level2Objects) {
            // 获取第三层节点（部件）
            List<BiObject> level3Objects = allObjects.stream()
                    .filter(obj -> obj.getParentId() != null && obj.getParentId().equals(level2Object.getId()))
                    .collect(Collectors.toList());

            // 如果没有第三层节点，跳过
            if (level3Objects.isEmpty()) {
                continue;
            }

            // 第一个第三层节点需要合并单元格显示第二层节点名称
            boolean isFirstLevel3 = true;
            int level2RowSpan = level3Objects.size(); // 第二层节点跨越的行数

            // 遍历第三层节点（部件）
            for (BiObject level3Object : level3Objects) {
                // 创建新行
                XWPFTableRow row = table.createRow();

                // 确保创建足够的单元格（11列）
                for (int i = 0; i < 11; i++) {
                    if (i >= row.getTableCells().size()) {
                        row.createCell();
                    }
                    // 设置单元格宽度
                    setCellWidth(row.getCell(i), totalWidth * columnWidths[i] / 100);
                }

                // 设置序号
                XWPFTableCell cellSeq = row.getCell(0);
                setCellText(cellSeq, String.valueOf(rowIndex));

                // 设置部位（第二层节点）
                if (isFirstLevel3) {
                    XWPFTableCell cellLevel2 = row.getCell(1);
                    setCellText(cellLevel2, level2Object.getName());

                    // 如果有多行，设置合并单元格
                    if (level2RowSpan > 1) {
                        CTVMerge vMerge = cellLevel2.getCTTc().addNewTcPr().addNewVMerge();
                        vMerge.setVal(STMerge.RESTART);
                    }
                } else {
                    // 非第一行，继续合并
                    XWPFTableCell cellLevel2 = row.getCell(1);
                    CTVMerge vMerge = cellLevel2.getCTTc().addNewTcPr().addNewVMerge();
                    vMerge.setVal(STMerge.CONTINUE);
                }

                // 设置部件名称（第三层节点）
                XWPFTableCell cellLevel3 = row.getCell(2);
                setCellText(cellLevel3, level3Object.getName());

                // 设置评分 - 从缓存中获取
                XWPFTableCell cellScore = row.getCell(3);
                String score = componentScoreMap.getOrDefault(level3Object.getId(), "");
                setCellText(cellScore, score);

                // 设置缺损类型 - 从缓存中获取
                XWPFTableCell cellDiseaseType = row.getCell(4);
                String diseaseTypes = componentDiseaseTypesMap.getOrDefault(level3Object.getId(), "");
                setCellText(cellDiseaseType, diseaseTypes);

                // 其他缺损相关单元格暂时留空
                setCellText(row.getCell(5), "/"); // 位置
                setCellText(row.getCell(6), "/"); // 范围
                setCellText(row.getCell(7), "/"); // 照片
                setCellText(row.getCell(8), ""); // 最不利构件

                // 养护建议和专项检查列
                setCellText(row.getCell(9), ""); // 养护建议
                setCellText(row.getCell(10), ""); // 是否需要专项检查

                isFirstLevel3 = false;
                rowIndex++;
            }
        }
    }

    /**
     * 批量获取构件评分
     */
    private Map<Long, String> batchGetComponentScores(List<Long> componentIds, Long buildingId, Long projectId, Task task) {
        Map<Long, String> resultMap = new HashMap<>();

        try {
            if (componentIds.isEmpty()) {
                return resultMap;
            }

            BiEvaluation biEvaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
            if (biEvaluation == null) {
                log.warn("未找到任务的评定结果: taskId={}", task.getId());
            }
            // 创建查询条件
            Condition queryParam = new Condition();
            queryParam.setBiEvaluationId(biEvaluation.getId());

            // 查询所有构件的评分
            List<Condition> allConditions = conditionMapper.selectConditionList(queryParam);

            // 按构件ID分组
            Map<Long, List<Condition>> conditionsByComponent = allConditions.stream()
                    .filter(c -> c.getBiObjectId() != null)
                    .collect(Collectors.groupingBy(Condition::getBiObjectId));

            // 为每个构件获取评分
            for (Long componentId : componentIds) {
                List<Condition> conditions = conditionsByComponent.get(componentId);
                if (conditions != null && !conditions.isEmpty()) {
                    // 取第一个评分
                    Condition firstCondition = conditions.get(0);
                    if (firstCondition.getScore() != null) {
                        resultMap.put(componentId, firstCondition.getScore().toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量获取构件评分失败: error={}", e.getMessage(), e);
        }

        return resultMap;
    }

    /**
     * 批量查询 biObject 的权重
     */
    private Map<Long, BigDecimal> batchGetObjectsWeight(List<Long> componentIds, Long buildingId, Long projectId, Task task) {
        Map<Long, BigDecimal> resultMap = new HashMap<>();
        try {
            if (componentIds.isEmpty()) {
                return resultMap;
            }

            BiEvaluation biEvaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
            if (biEvaluation == null) {
                log.warn("未找到任务的评定结果: taskId={}", task.getId());
            }
            // 创建查询条件
            Condition queryParam = new Condition();
            queryParam.setBiEvaluationId(biEvaluation.getId());

            // 查询所有构件的评分
            List<Condition> allConditions = conditionMapper.selectConditionList(queryParam);

            // 按构件ID分组
            Map<Long, List<Condition>> conditionsByComponent = allConditions.stream()
                    .filter(c -> c.getBiObjectId() != null)
                    .collect(Collectors.groupingBy(Condition::getBiObjectId));

            // 为每个构件获取评分
            for (Long componentId : componentIds) {
                List<Condition> conditions = conditionsByComponent.get(componentId);
                if (conditions != null && !conditions.isEmpty()) {
                    // 取第一个评分
                    Condition firstCondition = conditions.get(0);
                    if (firstCondition.getScore() != null) {
                        resultMap.put(componentId, firstCondition.getBiObject().getWeight());
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量获取构件评分失败: error={}", e.getMessage(), e);
        }

        return resultMap;
    }

    /**
     * 批量获取构件病害类型
     * 将第四层节点的病害类型汇总到第三层节点
     *
     * @param level3Ids  第三层节点ID列表
     * @param buildingId 建筑物ID
     * @param projectId  项目ID
     * @param allObjects 所有节点列表，包含了所有层级的节点
     * @return 第三层节点ID到病害类型名称的映射
     */
    private Map<Long, String> batchGetComponentDiseaseTypes(List<Long> level3Ids, Long buildingId, Long projectId, List<BiObject> allObjects) {
        Map<Long, String> resultMap = new HashMap<>();

        try {
            if (level3Ids.isEmpty() || allObjects == null || allObjects.isEmpty()) {
                return resultMap;
            }

            // 获取所有第三层节点的第四层子节点，直接从allObjects中筛选
            Map<Long, List<Long>> level3ToLevel4Map = new HashMap<>();
            List<Long> allLevel4Ids = new ArrayList<>();

            // 筛选出所有第四层节点（父节点ID在level3Ids中的节点）
            for (BiObject obj : allObjects) {
                if (obj.getParentId() != null && level3Ids.contains(obj.getParentId())) {
                    // 这是第四层节点
                    level3ToLevel4Map.computeIfAbsent(obj.getParentId(), k -> new ArrayList<>())
                            .add(obj.getId());
                    allLevel4Ids.add(obj.getId());
                }
            }

            // 创建查询条件
            Disease queryParam = new Disease();
            queryParam.setBuildingId(buildingId);
            queryParam.setProjectId(projectId);

            // 查询所有病害
            List<Disease> allDiseases = diseaseMapper.selectDiseaseList(queryParam);

            // 按构件ID（第四层）分组
            Map<Long, List<Disease>> diseasesByComponent = allDiseases.stream()
                    .filter(d -> d.getBiObjectId() != null && allLevel4Ids.contains(d.getBiObjectId()))
                    .collect(Collectors.groupingBy(Disease::getBiObjectId));

            // 为每个第三层节点汇总病害类型
            for (Long level3Id : level3Ids) {
                // 获取该第三层节点下的所有第四层节点
                List<Long> level4Ids = level3ToLevel4Map.getOrDefault(level3Id, Collections.emptyList());

                // 收集所有第四层节点的病害类型
                Set<String> diseaseTypeNames = new HashSet<>();

                for (Long level4Id : level4Ids) {
                    List<Disease> diseases = diseasesByComponent.getOrDefault(level4Id, Collections.emptyList());

                    for (Disease disease : diseases) {
                        DiseaseType diseaseType = disease.getDiseaseType();
                        if (diseaseType != null && diseaseType.getName() != null) {
                            diseaseTypeNames.add(diseaseType.getName());
                        }
                    }
                }

                // 用顿号连接病害类型名称
                if (!diseaseTypeNames.isEmpty()) {
                    resultMap.put(level3Id, String.join("、", diseaseTypeNames));
                } else {
                    resultMap.put(level3Id, "/");
                }
            }
        } catch (Exception e) {
            log.error("批量获取构件病害类型失败: error={}", e.getMessage(), e);
        }

        return resultMap;
    }


    /**
     * 根据占位符查找段落
     */
    private XWPFParagraph findParagraphByPlaceholder(XWPFDocument document, String placeholder) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (text != null && text.contains(placeholder)) {
                return paragraph;
            }
        }
        return null;
    }

    /**
     * 清除段落内容
     */
    private void clearParagraph(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return;
        }

        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }
    }

    /**
     * 创建表格底部
     */
    private void createTableFooter(XWPFTable table) {
        // 定义列宽比例（11列）- 与表头保持一致
        int[] columnWidths = {8, 8, 8, 8, 8, 8, 8, 8, 8, 16, 20}; // 总和为100
        int totalWidth = 9500; // 总宽度

        // 创建底部行1
        XWPFTableRow footerRow1 = table.createRow();
        int footerRow1Index = table.getRows().size() - 1; // 获取当前行索引

        // 确保创建足够的单元格（11列）
        for (int i = 0; i < 11; i++) {
            if (i >= footerRow1.getTableCells().size()) {
                footerRow1.createCell();
            }
            // 设置单元格宽度
            setCellWidth(footerRow1.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置单元格内容（按照6列的逻辑）
        createHeaderCell(footerRow1, 0, "22桥梁技术状况评定等级");
        createHeaderCell(footerRow1, 2, "");
        createHeaderCell(footerRow1, 4, "23全桥清洁状况");
        createHeaderCell(footerRow1, 6, "/");
        createHeaderCell(footerRow1, 8, "24预防及修复养护状况");
        createHeaderCell(footerRow1, 10, "/");

        // 横向合并
        mergeHorizontalCells(table, footerRow1Index, 0, 1);  // 22桥梁技术状况评定等级
        mergeHorizontalCells(table, footerRow1Index, 2, 3);  // 数据1
        mergeHorizontalCells(table, footerRow1Index, 4, 5);  // 23全桥清洁状况
        mergeHorizontalCells(table, footerRow1Index, 6, 7);  // 数据2
        mergeHorizontalCells(table, footerRow1Index, 8, 9);  // 24预防及修复养护状况
        // 数据3只占一列（第10列）

        // 创建底部行2
        XWPFTableRow footerRow2 = table.createRow();
        int footerRow2Index = table.getRows().size() - 1; // 获取当前行索引

        // 确保创建足够的单元格（11列）
        for (int i = 0; i < 11; i++) {
            if (i >= footerRow2.getTableCells().size()) {
                footerRow2.createCell();
            }
            // 设置单元格宽度
            setCellWidth(footerRow2.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置单元格内容（按照6列的逻辑）
        createHeaderCell(footerRow2, 0, "25记录人");
        createHeaderCell(footerRow2, 2, "");
        createHeaderCell(footerRow2, 4, "26负责人");
        createHeaderCell(footerRow2, 6, "");
        createHeaderCell(footerRow2, 8, "27下次检查时间");
        createHeaderCell(footerRow2, 10, "");

        // 横向合并
        mergeHorizontalCells(table, footerRow2Index, 0, 1);  // 25记录人
        mergeHorizontalCells(table, footerRow2Index, 2, 3);  // 数据1
        mergeHorizontalCells(table, footerRow2Index, 4, 5);  // 26负责人
        mergeHorizontalCells(table, footerRow2Index, 6, 7);  // 数据2
        mergeHorizontalCells(table, footerRow2Index, 8, 9);  // 27下次检查时间
        // 数据3只占一列（第10列）
    }

    /**
     * 创建表头单元格
     */
    private void createHeaderCell(XWPFTableRow row, int cellIndex, String text) {
        XWPFTableCell cell;
        if (cellIndex < row.getTableCells().size()) {
            cell = row.getCell(cellIndex);
        } else {
            cell = row.createCell();
        }

        // 清除现有内容
        for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
            cell.removeParagraph(i);
        }

        // 设置单元格文本
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 设置段落行距为单倍行距
        paragraph.setSpacingBetween(1.0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(9);
        run.setFontFamily("宋体");

        // 设置单元格垂直居中
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vAlign.setVal(STVerticalJc.CENTER);
    }

    /**
     * 设置单元格文本
     */
    private void setCellText(XWPFTableCell cell, String text) {
        // 清除现有内容
        for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
            cell.removeParagraph(i);
        }

        // 添加新段落
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        // 设置段落行距为单倍行距
        paragraph.setSpacingBetween(1.0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(9);
        run.setFontFamily("宋体");

        // 设置单元格垂直居中
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
        vAlign.setVal(STVerticalJc.CENTER);
    }

    /**
     * 设置单元格宽度
     */
    private void setCellWidth(XWPFTableCell cell, int width) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth tcWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcWidth.setW(BigInteger.valueOf(width));
        tcWidth.setType(STTblWidth.DXA);
    }

    @Override
    public void fillSingleBridgeRegularInspectionTable(XWPFDocument document, Building building, Task task, Project project, ReportTemplateTypes templateType) {
        if (null != templateType && (ReportTemplateTypes.is2LevelSigleBridge(templateType.getType()) || ReportTemplateTypes.is1LevelSigleBridge(templateType.getType()))) {
            // 拿到 建筑 部件树 的根节点 。
            BiObject rootObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
            if (rootObject == null) {
                log.warn("未找到桥梁结构树: rootObjectId={}", building.getRootObjectId());
                return;
            }
            List<Property> properties = new ArrayList<>();
            // 拿到 所有部件 对象。
            List<BiObject> allObjects = biObjectMapper.selectChildrenById(rootObject.getId());

            // 使用 所有部件对象查询 所有第三层结点的 评分 和 病害类型总结。

            // 获取第二层节点（部位）
            List<BiObject> level2Objects = allObjects.stream()
                    .filter(obj -> obj.getParentId() != null && obj.getParentId().equals(rootObject.getId()))
                    .collect(Collectors.toList());

            // 收集所有第三层节点的ID
            List<Long> allLevel3Ids = new ArrayList<>();
            Map<Long, BiObject> level3ObjectMap = new HashMap<>();
            Map<Long, BiObject> level2ForLevel3Map = new HashMap<>(); // 记录每个第三层节点对应的第二层节点

            // 遍历第二层节点（部位）
            for (BiObject level2Object : level2Objects) {
                // 获取第三层节点（部件）
                List<BiObject> level3Objects = allObjects.stream()
                        .filter(obj -> obj.getParentId() != null && obj.getParentId().equals(level2Object.getId()))
                        .collect(Collectors.toList());

                // 收集第三层节点ID
                for (BiObject level3Object : level3Objects) {
                    allLevel3Ids.add(level3Object.getId());
                    level3ObjectMap.put(level3Object.getId(), level3Object);
                    level2ForLevel3Map.put(level3Object.getId(), level2Object);
                }
            }
            //批量查询所有构件的权重
            Map<Long, BigDecimal> componentWeightMap = batchGetObjectsWeight(allLevel3Ids, building.getId(), project.getId(), task);
            // 批量查询所有构件的评分
            Map<Long, String> componentScoreMap = batchGetComponentScores(allLevel3Ids, building.getId(), project.getId(), task);
            // 批量查询所有构件的病害类型
            Map<Long, String> componentDiseaseTypesMap = batchGetComponentDiseaseTypes(allLevel3Ids, building.getId(), project.getId(), allObjects);

            List<String> recordComponentNameList = null;
            // 使用 enum 中的 list 查询固定格式 的 表格cell 应该填入的值。
            if (templateType.getType().equals(ReportTemplateTypes.LEVEL_2_BEAM_BRIDGE.getType()) || templateType.getType().equals(ReportTemplateTypes.LEVEL_1_BEAM_BRIDGE.getType())) {
                recordComponentNameList = BeamBridgeRecordTableComponentList.getComponentNameList();
            } else if (templateType.getType().equals(ReportTemplateTypes.LEVEL_2_ARCH_BRIDGE.getType())) {
                recordComponentNameList = ArchBridgeRecordTableComponentList.getComponentNameList();
            }

            // 表格 固定 名称 对应  biObject id
            Map<String, Long> idMap = new HashMap<>();
            for (String s : recordComponentNameList) {
                idMap.put(s, null);
            }
            // 不能从 allObjects 中查找  ， 因为 第三层 有的 和第四层 名称相同。
            for (BiObject object : level3ObjectMap.values()) {
                if (idMap.containsKey(object.getName())) {
                    idMap.put(object.getName(), object.getId());
                }
            }
            // 构造 占位符 name 和 value
            for (int i = 1; i <= recordComponentNameList.size(); i++) {
                String name = recordComponentNameList.get(i - 1);
                Long objectId = idMap.get(name);
                String scoreStr = "/";
                String typeStr = "/";
                if (null != objectId) {
                    scoreStr = componentWeightMap.get(objectId).compareTo(BigDecimal.ZERO) == 0 ? "/" : componentScoreMap.get(objectId);
                    typeStr = componentWeightMap.get(objectId).compareTo(BigDecimal.ZERO) == 0 ? "/" : componentDiseaseTypesMap.get(objectId);
                }
                Property propertyScore = new Property();
                propertyScore.setName("评分" + i);
                propertyScore.setValue(scoreStr);

                Property propertyType = new Property();
                propertyType.setName("类型" + i);
                propertyType.setValue(typeStr);

                properties.add(propertyScore);
                properties.add(propertyType);
            }
            // 处理 定期检查记录表特殊 的 属性。
            processSingleBridgeRecordTableSpecialProp(properties, building);
            // 将表格中的占位符 替换。
            replacePlaceholdersInTables(document, properties);
            //！！！ 注意 ， 这里考虑到 基本卡片的 最后 清除了 所有表格中的占位符 ，所以这里没有再次清除。
        }
    }

    private void replacePlaceholdersInTables(XWPFDocument document, List<Property> properties) {

        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        // 遍历所有属性进行替换
                        for (Property prop : properties) {
                            String propertyName = prop.getName();
                            String placeholder = "${" + propertyName + "}";
                            String value = prop.getValue() != null ? prop.getValue() : "/";
                            replacePlaceholder(paragraph, placeholder, value);
                        }
                    }
                }
            }
        }
    }

    /**
     * 替换单个占位符的方法
     */
    private void replacePlaceholder(XWPFParagraph paragraph, String placeholder, String value) {
        String text = paragraph.getText();
        if (text.contains(placeholder)) {
            // 清除段落中的所有runs
            for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }

            // 创建新的run并设置替换后的文本
            XWPFRun newRun = paragraph.createRun();
            newRun.setText(text.replace(placeholder, value));

            // 设置字体为宋体小五
            newRun.setFontFamily("宋体");
            newRun.setFontSize(9);
        }
    }

    private void processSingleBridgeRecordTableSpecialProp(List<Property> properties, Building building) {
        Property property = propertyService.selectPropertyById(building.getRootPropertyId());
        List<Property> allProperties = propertyService.selectPropertyList(property);
        // 处理 跨径组合中最大跨径
        String spanStr = allProperties.stream().filter(a -> a.getName().equals("跨径组合")).map(Property::getValue).toList().get(0);
        double maxSpan = getMaxSpan(spanStr);

        // 处理 生成报告的年份。
        LocalDateTime localDateTime = LocalDateTime.now();
        int curYear = localDateTime.getYear();

        // 处理 上次检查的年份。
        String lastCheckDateStr = allProperties.stream().filter(a -> a.getName().equals("最近评定日期")).map(Property::getValue).toList().get(0);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Integer lastYear = null;
        try {
            Date lastCheckDate = simpleDateFormat.parse(lastCheckDateStr);
            ZoneId zoneId = ZoneId.of("Asia/Shanghai");
            lastYear = lastCheckDate.toInstant().atZone(zoneId).toLocalDate().getYear();
        } catch (Exception e) {
            log.error("处理 上次检查的年份 ，解析日期字符串出错。");
            lastYear = curYear + 10;
        }
        Property propertyMaxSpan = new Property();
        propertyMaxSpan.setName("跨径组合中最大跨径");
        propertyMaxSpan.setValue(Double.toString(maxSpan));

        Property propertyCurYear = new Property();
        propertyCurYear.setName("生成报告的年份");
        propertyCurYear.setValue(Integer.toString(curYear));

        Property propertyLastYear = new Property();
        propertyLastYear.setName("最近评定日期的年份");
        propertyLastYear.setValue(Integer.toString(lastYear));

        properties.add(propertyMaxSpan);
        properties.add(propertyCurYear);
        properties.add(propertyLastYear);
    }

    /**
     * 提取跨径组合字符串中的最大跨径（支持整数和小数）
     *
     * @param spanStr 跨径组合（格式：n*x+...+数字+...，数字可含小数）
     * @return 最大跨径数值（double 类型，兼容小数）
     */
    public static double getMaxSpan(String spanStr) {
        // 正则：匹配 "n*x"（n和x支持小数）或单独数字（支持小数）
        Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)\\*(\\d+(\\.\\d+)?)|(\\d+(\\.\\d+)?)");
        Matcher matcher = pattern.matcher(spanStr.replaceAll("\\s+", "")); // 先去除所有空格（可选，增强兼容性）

        return matcher.results()
                .map(match -> {
                    // 匹配 "n*x" 取第3组（x），匹配单独数字取第5组
                    String span = match.group(3) != null ? match.group(3) : match.group(5);
                    return Double.parseDouble(span); // 用 double 接收，兼容小数
                })
                .max(Double::compareTo) // 小数版最大值对比
                .orElseThrow(() -> new IllegalArgumentException("无效的跨径组合：" + spanStr));
    }
}