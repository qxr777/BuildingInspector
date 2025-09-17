package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    /**
     * 生成桥梁卡片Word文档
     */
    @Override
    public XWPFDocument generateBridgeCardDocument(Long buildingId) {
        try {
            // 加载模板文件
            Resource resource = new ClassPathResource("word.biz/桥梁模板.docx");
            XWPFDocument document = new XWPFDocument(resource.getInputStream());

            // 1. 获取建筑物信息
            Building building = buildingService.selectBuildingById(buildingId);

            // 处理桥梁卡片数据
            processBridgeCardData(document, building);

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
    public void processBridgeCardData(XWPFDocument document, Building building) {
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

            // 3. 处理结构体系特殊属性
            processStructureSystemProperty(properties);

            // 4. 处理图片附件
            processImageAttachments(document, building.getId());

            // 5. 替换表格中的占位符
            replacePlaceholdersInTables(document, properties);

            // 6. 替换剩余的占位符
            replaceRemainingPlaceholders(document);

        } catch (Exception e) {
            log.error("处理桥梁卡片数据失败", e);
            throw new RuntimeException("处理桥梁卡片数据失败：" + e.getMessage());
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
                            String propertyName = processSpecialPropertyName(prop, propertyCounter);

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