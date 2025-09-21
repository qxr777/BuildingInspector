package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.*;
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

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Override
    public void generateRegularInspectionTable(XWPFDocument document, String placeholder, Building building, Task task, Project project) throws Exception {
        log.info("开始生成定期检查记录表, placeholder: {}, buildingId: {}, taskId: {}, projectId: {}", placeholder, building.getId(), task.getId(), project.getId());

        // 查找占位符位置
        XWPFParagraph targetParagraph = findParagraphByPlaceholder(document, placeholder);
        if (targetParagraph == null) {
            log.warn("未找到占位符: {}", placeholder);
            return;
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
        createTableHeader(table, building, project, task);

        // 创建表格主体部分
        createTableBody(table, rootObject, allObjects, building.getId(), project.getId(), task);

        // 创建表格底部
        createTableFooter(table);
    }

    /**
     * 创建表格头部
     */
    private void createTableHeader(XWPFTable table, Building building, Project project, Task task) {
        // 定义列宽比例（总和为100）
        int[] columnWidths = {8, 8, 8, 8, 8, 8, 8, 8, 16, 20};
        int totalWidth = 9500; // 总宽度

        // 第一行：公路管理机构名称（横跨所有列）
        XWPFTableRow row1 = table.getRow(0);
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row1.getTableCells().size()) {
                row1.createCell();
            }
        }

        // 设置第一个单元格
        XWPFTableCell cell1 = row1.getCell(0);
        setCellText(cell1, "公路管理机构名称：");
        // 横向合并单元格
        cell1.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        // 设置单元格宽度
        setCellWidth(cell1, totalWidth * columnWidths[0] / 100);

        // 合并剩余单元格
        for (int i = 1; i < 10; i++) {
            XWPFTableCell mergedCell = row1.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
            setCellWidth(mergedCell, totalWidth * columnWidths[i] / 100);
        }

        // 第二行：基本信息行1
        XWPFTableRow row2 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row2.getTableCells().size()) {
                row2.createCell();
            }
            setCellWidth(row2.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置单元格内容
        createHeaderCell(row2, 0, "1 线路编号");
        createHeaderCell(row2, 1, "");
        createHeaderCell(row2, 2, "2 线路名称");
        createHeaderCell(row2, 3, "");
        createHeaderCell(row2, 4, "3 桥位桩号");
        createHeaderCell(row2, 5, "");

        // 第三行：基本信息行2
        XWPFTableRow row3 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row3.getTableCells().size()) {
                row3.createCell();
            }
            setCellWidth(row3.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row3, 0, "4 桥梁编号");
        createHeaderCell(row3, 1, "");
        createHeaderCell(row3, 2, "5 桥梁名称");
        createHeaderCell(row3, 3, building.getName() != null ? building.getName() : "");
        createHeaderCell(row3, 4, "6 被跨域通道名称");
        createHeaderCell(row3, 5, "");

        // 第四行：基本信息行3
        XWPFTableRow row4 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row4.getTableCells().size()) {
                row4.createCell();
            }
            setCellWidth(row4.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row4, 0, "7 桥梁全长(m)");
        createHeaderCell(row4, 1, "");
        createHeaderCell(row4, 2, "8 主跨结构");
        createHeaderCell(row4, 3, "");
        createHeaderCell(row4, 4, "9 最大跨径(m)");
        createHeaderCell(row4, 5, "");

        // 第五行：基本信息行4
        XWPFTableRow row5 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row5.getTableCells().size()) {
                row5.createCell();
            }
            setCellWidth(row5.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row5, 0, "10 管养单位");
        createHeaderCell(row5, 1, "");
        createHeaderCell(row5, 2, "11 建成时间");
        createHeaderCell(row5, 3, "");
        createHeaderCell(row5, 4, "12 上次修复养护时间");
        createHeaderCell(row5, 5, "");

        // 第六行：基本信息行5
        XWPFTableRow row6 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row6.getTableCells().size()) {
                row6.createCell();
            }
            setCellWidth(row6.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        createHeaderCell(row6, 0, "13 上次检查时间");
        createHeaderCell(row6, 1, "");
        createHeaderCell(row6, 2, "14 本次检查时间");
        createHeaderCell(row6, 3, "");
        createHeaderCell(row6, 4, "15 本次检查时气候及环境温度");
        createHeaderCell(row6, 5, "");

        // 第七行：表头行
        XWPFTableRow row7 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row7.getTableCells().size()) {
                row7.createCell();
            }
            setCellWidth(row7.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置表头单元格
        createHeaderCell(row7, 0, "序号");
        createHeaderCell(row7, 1, "16 部位");
        createHeaderCell(row7, 2, "17 部件名称");
        createHeaderCell(row7, 3, "18\n评分");

        // 创建"19 缺损"列，合并5个单元格
        XWPFTableCell cell19 = row7.getCell(4);
        cell19.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        XWPFParagraph para19 = cell19.getParagraphs().get(0);
        para19.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run19 = para19.createRun();
        run19.setText("19 缺损");
        run19.setBold(true);
        run19.setFontSize(9);

        // 合并后续4个单元格
        for (int i = 0; i < 4; i++) {
            XWPFTableCell mergedCell = row7.getCell(5 + i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }

        // 设置最后两列
        createHeaderCell(row7, 9, "20 养护建议\n(维修范围、方式、时间)");
        createHeaderCell(row7, 9, "21 是否需要专项检查");

        // 第八行：缺损子表头
        XWPFTableRow row8 = table.createRow();
        // 确保创建所有单元格
        for (int i = 0; i < 10; i++) {
            if (i >= row8.getTableCells().size()) {
                row8.createCell();
            }
            setCellWidth(row8.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置前4列与上一行垂直合并
        for (int i = 0; i < 4; i++) {
            XWPFTableCell cell = row8.getCell(i);
            cell.getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.CONTINUE);
        }

        // 设置缺损子表头
        createHeaderCell(row8, 4, "类型");
        createHeaderCell(row8, 5, "位置");
        createHeaderCell(row8, 6, "范围");
        createHeaderCell(row8, 7, "照片");
        createHeaderCell(row8, 8, "最不利构件");

        // 设置最后一列与上一行垂直合并
        XWPFTableCell lastCell = row8.getCell(9);
        lastCell.getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.CONTINUE);
    }

    /**
     * 创建表格主体部分
     */
    private void createTableBody(XWPFTable table, BiObject rootObject, List<BiObject> allObjects, Long buildingId, Long projectId, Task task) {
        // 定义列宽比例（总和为100）- 与表头保持一致
        int[] columnWidths = {8, 8, 8, 8, 8, 8, 8, 8, 16, 20};
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

                // 确保创建足够的单元格
                for (int i = 0; i < 10; i++) {
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

                // 其他单元格暂时留空
                setCellText(row.getCell(5), "/"); // 位置
                setCellText(row.getCell(6), "/"); // 范围
                setCellText(row.getCell(7), "/"); // 照片
                setCellText(row.getCell(8), ""); // 最不利构件
                setCellText(row.getCell(9), ""); // 养护建议/是否需要专项检查

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
     * 批量获取构件病害类型
     * 将第四层节点的病害类型汇总到第三层节点
     *
     * @param level3Ids 第三层节点ID列表
     * @param buildingId 建筑物ID
     * @param projectId 项目ID
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
        // 定义列宽比例（总和为100）- 与表头保持一致
        int[] columnWidths = {8, 8, 8, 8, 8, 8, 8, 8, 16, 20};
        int totalWidth = 9500; // 总宽度

        // 创建底部行1
        XWPFTableRow footerRow1 = table.createRow();

        // 确保创建足够的单元格
        for (int i = 0; i < 10; i++) {
            if (i >= footerRow1.getTableCells().size()) {
                footerRow1.createCell();
            }
            // 设置单元格宽度
            setCellWidth(footerRow1.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置"22 桥梁技术状况评定等级"单元格
        XWPFTableCell cell22 = footerRow1.getCell(0);
        cell22.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        setCellText(cell22, "22 桥梁技术状况评定等级");

        // 合并后续单元格
        for (int i = 1; i < 3; i++) {
            XWPFTableCell mergedCell = footerRow1.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }

        // 设置"23 全桥清洁状况"单元格
        XWPFTableCell cell23 = footerRow1.getCell(3);
        cell23.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        setCellText(cell23, "23 全桥清洁状况");

        // 合并后续单元格
        for (int i = 4; i < 7; i++) {
            XWPFTableCell mergedCell = footerRow1.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }

        // 设置"24 预防及修复养护状况"单元格
        XWPFTableCell cell24 = footerRow1.getCell(7);
        cell24.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        setCellText(cell24, "24 预防及修复养护状况");

        // 合并后续单元格
        for (int i = 8; i < 10; i++) {
            XWPFTableCell mergedCell = footerRow1.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }

        // 创建底部行2
        XWPFTableRow footerRow2 = table.createRow();

        // 确保创建足够的单元格
        for (int i = 0; i < 10; i++) {
            if (i >= footerRow2.getTableCells().size()) {
                footerRow2.createCell();
            }
            // 设置单元格宽度
            setCellWidth(footerRow2.getCell(i), totalWidth * columnWidths[i] / 100);
        }

        // 设置"25 记录人"单元格
        XWPFTableCell cell25 = footerRow2.getCell(0);
        cell25.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        setCellText(cell25, "25 记录人");

        // 合并后续单元格
        for (int i = 1; i < 3; i++) {
            XWPFTableCell mergedCell = footerRow2.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }

        // 设置"26 负责人"单元格
        XWPFTableCell cell26 = footerRow2.getCell(3);
        cell26.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        setCellText(cell26, "26 负责人");

        // 合并后续单元格
        for (int i = 4; i < 7; i++) {
            XWPFTableCell mergedCell = footerRow2.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }

        // 设置"27 下次检查时间"单元格
        XWPFTableCell cell27 = footerRow2.getCell(7);
        cell27.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.RESTART);
        setCellText(cell27, "27 下次检查时间");

        // 合并后续单元格
        for (int i = 8; i < 10; i++) {
            XWPFTableCell mergedCell = footerRow2.getCell(i);
            mergedCell.getCTTc().addNewTcPr().addNewHMerge().setVal(STMerge.CONTINUE);
        }
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

        // 设置单元格文本
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
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
}