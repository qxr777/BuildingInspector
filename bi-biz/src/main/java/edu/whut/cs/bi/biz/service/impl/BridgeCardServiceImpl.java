package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.enums.DisposalSuggestionEnums;
import edu.whut.cs.bi.biz.domain.enums.ReportTemplateTypes;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 桥梁卡片生成服务实现类
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class BridgeCardServiceImpl implements IBridgeCardService {

    @Autowired
    private IBuildingService buildingService;

    @Autowired
    private IPropertyService propertyService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private AttachmentService attachmentService;

    @javax.annotation.Resource
    private IBiEvaluationService biEvaluationService;


    /**
     * 生成桥梁卡片Word文档
     */
    @Override
    public XWPFDocument generateBridgeCardDocument(Long buildingId, ReportTemplateTypes templateType) {
        try {
            // 加载模板文件
            Resource resource = new ClassPathResource("word.biz/桥梁模板.docx");
            XWPFDocument document = new XWPFDocument(resource.getInputStream());

            // 1. 获取建筑物信息
            Building building = buildingService.selectBuildingById(buildingId);

            // 处理桥梁卡片数据
            // 这里 是 导出 word卡片 的接口，不是导出报告，因此 没有确定的task，传 null

            processBridgeCardData(document, building, templateType, null);

            return document;
        } catch (Exception e) {
            log.error("生成桥梁卡片文档失败", e);
            throw new RuntimeException("生成桥梁卡片文档失败：" + e.getMessage());
        }
    }

    /**
     * 处理桥梁卡片表格数据替换
     */
    @Override
    public void processBridgeCardData(XWPFDocument document, Building building, ReportTemplateTypes templateType, Task task) {
        try {
            // 1. 获取建筑信息
            if (building == null) {
                throw new RuntimeException("未找到对应的建筑信息");
            }

            // 2. 获取属性信息
            Property property = propertyService.selectPropertyById(building.getRootPropertyId());
            if (property == null) {
                throw new RuntimeException("未找到对应的属性信息");
            }

            List<Property> properties = propertyService.selectPropertyList(property);
//
//            // 3. 处理结构体系特殊属性
//            processStructureSystemProperty(properties);
//
//            // 4. 处理图片附件
//            processImageAttachments(document, building.getId());
//
//            // 5. 替换表格中的占位符
//            replacePlaceholdersInTables(document, properties);
//
//            // 6. 替换剩余的占位符
//            replaceRemainingPlaceholders(document);
            // 改为excel导入 property

            // 处理 特殊字段
            processSpecialProp(building, properties, templateType, task);
            // 处理图片
            processImageAttachments(document, building.getId());
            // 将属性替换占位符
            replacePlaceholdersInTables(document, properties);
            // 如果是单桥模板 ， 需要 顺带 处理 桥梁概况自动 生成的 文本
            if (templateType != null && ReportTemplateTypes.isSigleBridge(templateType.getType())) {
                // 处理特殊的 概况上部结构  字段。
                Property propertyOverviewUpperStructure = new Property();
                propertyOverviewUpperStructure.setName("概况上部结构");
                if (templateType.getType().equals(ReportTemplateTypes.ARCH_BRIDGE.getType())) {
                    propertyOverviewUpperStructure.setValue(properties.stream().filter(a -> a.getName().equals("拱桥主桥上部构造结构形式")).map(a -> a.getValue()).toList().get(0));
                } else if (templateType.getType().equals(ReportTemplateTypes.BEAM_BRIDGE.getType())) {
                    propertyOverviewUpperStructure.setValue(properties.stream().filter(a -> a.getName().equals("梁桥主桥上部构造结构形式")).map(a -> a.getValue()).toList().get(0));
                } else {
                    propertyOverviewUpperStructure.setValue("");
                }
                properties.add(propertyOverviewUpperStructure);
                // 将桥梁概况 text 中的 properties 中 占位符 替换。
                for (Property prop : properties) {
                    String propertyName = prop.getName();
                    String placeholder = "${" + propertyName + "}";
                    String value = prop.getValue() != null ? prop.getValue() : "";
                    replaceTextInParagraphs(document.getParagraphs(), placeholder, value, "正文文本，本次指向桥梁概况", 12);
                }
            }
            // 替换 无效的 占位符。
            replaceRemainingPlaceholders(document);
        } catch (Exception e) {
            log.error("处理桥梁卡片数据失败", e);
            throw new RuntimeException("处理桥梁卡片数据失败：" + e.getMessage());
        }
    }

    /**
     * 替换段落列表中的文本
     *
     * @param paragraphs 段落列表
     * @param oldText    要替换的文本
     * @param newText    新文本
     * @param location   位置描述（用于日志）
     */
    private void replaceTextInParagraphs(List<XWPFParagraph> paragraphs,
                                         String oldText,
                                         String newText,
                                         String location, double fontSize) {
        for (XWPFParagraph p : paragraphs) {
            // 1. 拼完整文本
            StringBuilder full = new StringBuilder();
            for (XWPFRun r : p.getRuns()) {
                String t = r.getText(0);
                if (t != null) full.append(t);
            }
            String raw = full.toString();
            if (!raw.contains(oldText)) continue;

            // 删除前留一个样式样本
            ReportRunsStyle runsStyle = null;
            // 2. 删除旧 run
            for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                if (i == p.getRuns().size() - 1) {
                    runsStyle = ReportRunsStyle.snapshot(p.getRuns().get(i));
                }
                p.removeRun(i);
            }

            // 3. 一次性写入新文本（支持换行）
            String replaced = raw.replace(oldText, newText);
            XWPFRun cleanRun = p.createRun();
            if (runsStyle != null) {
                runsStyle.apply(cleanRun);
            }
            setTextWithLineBreaks(cleanRun, replaced);

            log.debug("在{}中替换占位符: {} -> {}", location, oldText, newText);
        }
    }

    /* 可选：把源 run 的字体、粗体、颜色等拷过来 */
    private void cloneStyle(XWPFRun target, XWPFRun source) {
        target.setFontFamily(source.getFontFamily());
        target.setFontSize(source.getFontSizeAsDouble());
        target.setBold(source.isBold());
        target.setItalic(source.isItalic());
        target.setColor(source.getColor());
    }

    /**
     * 设置Run的文本，支持处理换行符
     * <p>
     * 如果文本中包含换行符（\n或\r\n），会将文本分行，并在每行之间插入换行符。
     * 这样可以在Word中正确显示用户在前端textarea中输入的多行文本。
     * </p>
     *
     * @param run  XWPFRun对象
     * @param text 要设置的文本（可能包含换行符）
     */
    private void setTextWithLineBreaks(XWPFRun run, String text) {
        if (text == null || text.isEmpty()) {
            run.setText("", 0);
            return;
        }

        // 分割文本（支持Windows和Unix风格的换行符）
        String[] lines = text.split("\\r?\\n");

        if (lines.length == 1) {
            // 没有换行符，直接设置
            run.setText(text, 0);
        } else {
            // 有换行符，需要分行处理
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    // 第一行使用setText设置到run的位置0
                    run.setText(lines[i], 0);
                } else {
                    // 后续行先添加换行符，再添加文本
                    run.addBreak();
                    run.setText(lines[i]);
                }
            }
        }
    }


    /**
     * 处理 桥梁名称 和 功能类型 等特别字段。
     */
    private void processSpecialProp(Building building, List<Property> properties, ReportTemplateTypes templateType, Task task) {
        // 没有桥梁名称属性，从数据库拿
        Property propertyBuilding = new Property();
        propertyBuilding.setName("桥梁名称");
        propertyBuilding.setValue(building.getName());
        properties.add(propertyBuilding);
        // 最后的生成报告年月日
        Date date = new Date();
        Property propertyGenerateDate = new Property();
        propertyGenerateDate.setName("生成报告年月日");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日");
        propertyGenerateDate.setValue(simpleDateFormat.format(date));
        properties.add(propertyGenerateDate);
        // 如果是单桥 判断结构体系
        if (templateType != null && !templateType.equals(ReportTemplateTypes.COMBINED_BRIDGE)) {
            Property propertyStructure = new Property();
            String value = templateType.getDesc();
            propertyStructure.setValue(value);
            propertyStructure.setName("结构体系");

            // 主梁 或 主拱圈 需要根据桥梁类型填写
            String mainBridgeUpperStructureForm = properties.stream().filter(a -> a.getName().equals("主桥上部构造结构形式")).map(a -> a.getValue()).toList().get(0);
            Property propertyMainBridgeUpperStructureForm = new Property();
            propertyMainBridgeUpperStructureForm.setValue(mainBridgeUpperStructureForm);
            if (templateType.equals(ReportTemplateTypes.BEAM_BRIDGE)) {
                propertyMainBridgeUpperStructureForm.setName("梁桥主桥上部构造结构形式");
            } else if (templateType.equals(ReportTemplateTypes.ARCH_BRIDGE)) {
                propertyMainBridgeUpperStructureForm.setName("拱桥主桥上部构造结构形式");
            }
            properties.add(propertyStructure);
            properties.add(propertyMainBridgeUpperStructureForm);
        }
        // task 可能为 null ， 这个方法 在 很多地方都引用了，有的没有task 信息。
        if (task != null) {
            // 处理当前评定 等级 和 当前 处置建议 当前评定日期。
            BiEvaluation biEvaluation = biEvaluationService.selectBiEvaluationByTaskId(task.getId());
            Property propertyCurSysLevel = new Property();
            propertyCurSysLevel.setName("当前评定结果");
            propertyCurSysLevel.setValue(biEvaluation.getSystemLevel() + "类");

            Property propertyCurAdance = new Property();
            propertyCurAdance.setName("当前处治对策");
            propertyCurAdance.setValue(DisposalSuggestionEnums.getContentByLevel(biEvaluation.getSystemLevel()));

            simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String dateStr = simpleDateFormat.format(date);
            Property propertyCurCheckDate = new Property();
            propertyCurCheckDate.setName("当前评定日期");
            propertyCurCheckDate.setValue(dateStr);

            properties.add(propertyCurSysLevel);
            properties.add(propertyCurAdance);
            properties.add(propertyCurCheckDate);
        }

        // 处理 上次 处置建议 ， 如果 存在
        String lastSysLevel = properties.stream().filter(a -> a.getName().equals("桥梁技术状况")).map(a -> a.getValue()).toList().get(0);
        if (lastSysLevel != null && lastSysLevel.length() >= 2) {
            int lastSysLevelInt = lastSysLevel.charAt(0) - '0';
            Property propertyLastAdance = new Property();
            propertyLastAdance.setName("上一次处治对策");
            propertyLastAdance.setValue(DisposalSuggestionEnums.getContentByLevel(lastSysLevelInt));
            properties.add(propertyLastAdance);
        }
        // 桥梁功能类型 ， 需要进行判断转换。
        for (int i = 0; i < properties.size(); i++) {
            Property prop = properties.get(i);
            if (prop.getName().equals("是否公铁两用桥梁")) {
                String curValue = prop.getValue();
                prop.setValue(curValue.equals("否") ? "公路" : "公铁两用桥");
            }
            if (prop.getName().equals("跨径组合")) {
                String curValue = prop.getValue();
                prop.setValue(curValue.replace('*', 'x'));
            }
        }
    }

    /**
     * 处理结构体系特殊属性
     */
    private void processStructureSystemProperty(List<Property> properties) {
        long structureSystemId = 0L;
        Property structureSystemProperty = null;

        for (Property property : properties) {
            if ("结构体系".equals(property.getName())) {
                structureSystemId = property.getId();
                structureSystemProperty = property;
                break;
            }
        }

        if (structureSystemProperty != null) {
            structureSystemProperty.setValue("");
            for (Property property : properties) {
                if (property.getParentId() != null && property.getParentId() == structureSystemId) {
                    structureSystemProperty.setValue(
                            structureSystemProperty.getValue() + "\n" + property.getName() + property.getValue());
                }
            }
        }
    }

    /**
     * 处理图片附件
     */
    private void processImageAttachments(XWPFDocument document, Long buildingId) {
        attachmentService.getAttachmentList(buildingId).stream()
                .map(attachment -> {
                    FileMap fileMap = fileMapService.selectFileMapById(attachment.getMinioId());
                    if (fileMap != null) {
                        fileMap.setOldName(attachment.getName());
                    }
                    return fileMap;
                })
                .forEach(fileMap -> {
                    if (fileMap != null) {
                        byte[] file = fileMapService.handleFileDownloadByNewName(fileMap.getNewName());
                        insertImageIntoTables(document, file, fileMap.getOldName());
                    }
                });
    }

    /**
     * 在表格中插入图片
     */
    private void insertImageIntoTables(XWPFDocument document, byte[] imageData, String imageName) {
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        insertImage(paragraph, imageData, imageName);
                    }
                }
            }
        }
    }

    /**
     * 替换表格中的占位符
     */
    private void replacePlaceholdersInTables(XWPFDocument document, List<Property> properties) {
        Map<String, Integer> propertyCounter = new HashMap<>();

        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        // 遍历所有属性进行替换
                        for (Property prop : properties) {
                            // 处理需要编号的特殊属性
//                            String propertyName = processSpecialPropertyName(prop, propertyCounter);
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
     * 处理特殊属性名称（需要编号的属性）
     * 11.11 修改 ， 暂时不需要。
     */
    private String processSpecialPropertyName(Property property, Map<String, Integer> propertyCounter) {
        String originalName = property.getName();

        // 需要编号的属性列表
        Set<String> specialProperties = new HashSet<>(Arrays.asList(
                "检测类别", "评定时间", "评定结果", "处治对策", "下次检测时间"
        ));

        if (specialProperties.contains(originalName)) {
            Integer count = propertyCounter.get(originalName);
            if (count == null) {
                propertyCounter.put(originalName, 1);
                return originalName + "1";
            } else {
                count++;
                propertyCounter.put(originalName, count);
                return originalName + count;
            }
        }

        return originalName;
    }

    /**
     * 替换剩余的占位符
     */
    private void replaceRemainingPlaceholders(XWPFDocument document) {
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        replaceRemainingPlaceholders(paragraph);
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

    /**
     * 替换剩余占位符的方法
     */
    private void replaceRemainingPlaceholders(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text.contains("${")) {
            // 清除段落中的所有runs
            for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }

            // 创建新的run并设置替换后的文本
            XWPFRun newRun = paragraph.createRun();
            // 使用正则表达式替换所有剩余的${xxx}格式的占位符
            String replacedText = text.replaceAll("\\$\\{[^}]*\\}", "/");
            newRun.setText(replacedText);

            // 设置字体为宋体小五
            newRun.setFontFamily("宋体");
            newRun.setFontSize(9);
        }
    }

    /**
     * 插入图片的方法
     */
    private void insertImage(XWPFParagraph paragraph, byte[] imageData, String name) {
        try {
            // 解析图片名称
            String[] parts = name.split("_");
            if (parts.length < 2) {
                return; // 名称格式不正确，直接返回
            }

            String prefix = parts[0]; // 0 或 1
            String type = parts[1]; // front 或 side

            String placeholder = "";

            // 根据前缀和类型确定占位符
            if ("front".equals(type) || "newfront".equals(type)) {
                if ("0".equals(prefix)) {
                    placeholder = "${桥梁正面照}";
                } else if ("1".equals(prefix)) {
                    placeholder = "${桥梁正面照1}";
                }
            } else if ("side".equals(type) || "newside".equals(type)) {
                if ("0".equals(prefix)) {
                    placeholder = "${桥梁立面照}";
                } else if ("1".equals(prefix)) {
                    placeholder = "${桥梁立面照1}";
                }
            }

            // 如果没有找到匹配的占位符，直接返回
            if (placeholder.isEmpty()) {
                return;
            }

            // 如果段落包含对应的图片占位符
            String text = paragraph.getText();
            if (text.contains(placeholder)) {
                // 清除段落中的所有runs
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }
                // 转换为EMU单位（1厘米 = 360000 EMU）
                int widthEMU = 4 * 360000; // 4厘米
                int heightEMU = 3 * 360000; // 3厘米

                // 创建新的run并插入图片
                XWPFRun run = paragraph.createRun();
                run.addPicture(
                        new ByteArrayInputStream(imageData),
                        XWPFDocument.PICTURE_TYPE_JPEG,
                        "bridge.jpg",
                        widthEMU,
                        heightEMU);
            }
        } catch (Exception e) {
            log.error("插入图片失败", e);
        }
    }
}