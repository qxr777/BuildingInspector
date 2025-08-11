package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.domain.vo.Disease2ReportSummaryAiVO;
import edu.whut.cs.bi.biz.utils.Convert2VO;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ReportServiceImpl implements IReportService {

    private int tableCounter = 1;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IBuildingService buildingService;

    @Autowired
    private IDiseaseService diseaseService;

    @Autowired
    private BuildingMapper buildingMapper;

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private IPropertyService propertyService;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private AttachmentService attachmentService;

    @Value("${springAi_Rag.endpoint}")
    private String SpringAiUrl;

    @Override
    public XWPFDocument exportTaskReport(Long taskId) {
        // 1. 查询数据
        Task task = taskService.selectTaskById(taskId);
        Building building = buildingService.selectBuildingById(task.getBuildingId());
        Disease disease = new Disease();
        disease.setBuildingId(task.getBuildingId());
        disease.setProjectId(task.getProjectId());
        List<Disease> properties = diseaseService.selectDiseaseList(disease);
        List<BiObject> biObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());

        // 2. 找到树的根节点
        BiObject root = biObjectMapper.selectBiObjectById(building.getRootObjectId());

        // 3. 创建Word文档
        XWPFDocument doc = new XWPFDocument();

        // 4. 递归写入树结构和病害信息
        writeBiObjectTreeToWord(doc, root, biObjects, properties, "4.1", 1);

        return doc;
    }

    @Override
    public void writeBiObjectTreeToWord(XWPFDocument doc, BiObject node, List<BiObject> allNodes,
                                        List<Disease> properties, String prefix, int level) {
        // 写目录标题
        writeTitle(doc, node, prefix, level);

        // 写病害信息
        List<Disease> nodeDiseases = properties.stream()
                .filter(d -> d.getBiObjectId() != null && d.getBiObjectId().equals(node.getId()))
                .collect(Collectors.toList());

        // 如果存在病害信息，则生成介绍段落和表格
        if (!nodeDiseases.isEmpty()) {
            writeDiseaseSummary(doc, node, nodeDiseases);
            writeDiseaseTable(doc, nodeDiseases);
        }

        // 递归写子节点
        writeChildNodes(doc, node, allNodes, properties, prefix, level);
    }

    private void writeTitle(XWPFDocument doc, BiObject node, String prefix, int level) {
        XWPFParagraph p = doc.createParagraph();
        // 设置为标题样式
        if (level == 1) {
            p.setStyle("Heading1");
        } else if (level == 2) {
            p.setStyle("Heading2");
        } else if (level == 3) {
            p.setStyle("Heading3");
        } else {
            p.setStyle("Heading4");
        }

        // 设置大纲级别
        CTPPr ppr = p.getCTP().getPPr();
        if (ppr == null)
            ppr = p.getCTP().addNewPPr();
        if (level <= 9) {
            ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
        } else {
            ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(9));
        }

        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setColor("000000");
        run.setFontSize(12 + Math.max(0, 4 - level));
        run.setText(prefix + " " + node.getName());
    }

    private void writeDiseaseSummary(XWPFDocument doc, BiObject node, List<Disease> nodeDiseases) {
        // 创建介绍段落
        XWPFParagraph introPara = doc.createParagraph();

        // 加粗的开头部分
        XWPFRun runBold = introPara.createRun();
        runBold.setText("经检查，" + node.getName() + " 主要病害为:");
        runBold.setBold(true);
        runBold.setFontSize(12);

        // 遍历病害，每个条目一行
        int summarySeqNum = 1;
        for (Disease d : nodeDiseases) {
            writeDiseaseItem(doc, d, summarySeqNum++);
        }
    }

    private void writeDiseaseItem(XWPFDocument doc, Disease d, int seqNum) {
        XWPFParagraph diseasePara = doc.createParagraph();
        CTPPr diseasePpr = diseasePara.getCTP().getPPr();
        if (diseasePpr == null) {
            diseasePpr = diseasePara.getCTP().addNewPPr();
        }
        CTInd ind = diseasePpr.isSetInd() ? diseasePpr.getInd() : diseasePpr.addNewInd();
        ind.setFirstLine(BigInteger.valueOf(480));

        XWPFRun runItem = diseasePara.createRun();
        runItem.setText(seqNum + "） "
                + (d.getComponent() != null ? d.getComponent().getName().split("（")[0] : "/") + " "
                + (d.getType() != null ? d.getType() : "/") + " "
                + (d.getQuantity() > 0 ? d.getQuantity() : "/") + " 处; "
                + (d.getDescription() != null ? d.getDescription() : "/") + "; ");
        runItem.setFontSize(12);
    }

    private void writeDiseaseTable(XWPFDocument doc, List<Disease> nodeDiseases) {
        String tableNumber = "4." + tableCounter++;

        // 写表格引用
        writeTableReference(doc, tableNumber);

        // 写表格标题
        writeTableTitle(doc, tableNumber);

        // 创建和填充表格
        XWPFTable table = createTable(doc);
        fillTableData(table, nodeDiseases);
    }

    private void writeTableReference(XWPFDocument doc, String tableNumber) {
        XWPFParagraph tableRefPara = doc.createParagraph();
        CTPPr ppr1 = tableRefPara.getCTP().getPPr();
        if (ppr1 == null) {
            ppr1 = tableRefPara.getCTP().addNewPPr();
        }
        CTSpacing spacing = ppr1.isSetSpacing() ? ppr1.getSpacing() : ppr1.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(360));

        XWPFRun runTableRef = tableRefPara.createRun();
        runTableRef.setText("具体检测结果见下表 " + tableNumber + ":");
        runTableRef.setFontSize(12);
    }

    private void writeTableTitle(XWPFDocument doc, String tableNumber) {
        XWPFParagraph tableNumPara = doc.createParagraph();
        tableNumPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun runTableNum = tableNumPara.createRun();
        runTableNum.setText("表 " + tableNumber);
        runTableNum.setFontSize(12);
        runTableNum.setBold(true);
    }

    private XWPFTable createTable(XWPFDocument doc) {
        XWPFTable table = doc.createTable(1, 8);
        setupTableBorders(table);
        setupTableHeader(table);
        return table;
    }

    private void setupTableBorders(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null)
            tblPr = table.getCTTbl().addNewTblPr();
        CTTblBorders borders = tblPr.addNewTblBorders();
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);

        // 设置表格居中对齐
        CTJc jc = tblPr.isSetJc() ? tblPr.getJc() : tblPr.addNewJc();
        jc.setVal(STJc.CENTER);
    }

    private void setupTableHeader(XWPFTable table) {
        XWPFTableRow headerRow = table.getRow(0);
        String[] headers = {"序号", "缺损位置", "缺损类型", "数量", "病害描述", "评定类别 (1~5)", "发展趋势", "照片"};
        Double[] num = {1.24, 2.75, 2.28, 1.29, 4.43, 2.0, 2.0, 1.51};

        CTTblLayoutType tblLayout = table.getCTTbl().getTblPr().addNewTblLayout();
        tblLayout.setType(STTblLayoutType.FIXED);

        for (int i = 0; i < headers.length; i++) {
            setupHeaderCell(headerRow.getCell(i), headers[i], num[i]);
        }
    }

    private void setupHeaderCell(XWPFTableCell cell, String header, double width) {
        for (int j = cell.getParagraphs().size() - 1; j >= 0; j--) {
            cell.removeParagraph(j);
        }

        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setText(header);
        run.setBold(true);
        run.setFontSize(11);

        CTTc cttc = cell.getCTTc();
        CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();
        CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcW.setW(BigInteger.valueOf((int) Math.round(width * 567)));
        tcW.setType(STTblWidth.DXA);
        tcPr.addNewNoWrap();
    }

    private void fillTableData(XWPFTable table, List<Disease> nodeDiseases) {
        int seqNum = 1;
        for (Disease d : nodeDiseases) {
            XWPFTableRow dataRow = table.createRow();
            fillTableRow(dataRow, d, seqNum++);
        }
    }

    private void fillTableRow(XWPFTableRow row, Disease d, int seqNum) {
        String[] cellValues = {
                String.valueOf(seqNum),
                d.getComponent() != null ? d.getComponent().getName() : "/",
                d.getType() != null ? d.getType() : "/",
                d.getQuantity() > 0 ? String.valueOf(d.getQuantity()) : "/",
                d.getDescription() != null ? d.getDescription() : "/",
                d.getLevel() > 0 ? String.valueOf(d.getLevel()) : "/",
                "/",
                ""
        };

        for (int i = 0; i < cellValues.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            XWPFParagraph cellP = cell.getParagraphs().get(0);
            cellP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun cellR = cellP.createRun();
            cellR.setFontSize(11);
            cellR.setText(cellValues[i]);
        }
    }

    private void writeChildNodes(XWPFDocument doc, BiObject node, List<BiObject> allNodes,
                                 List<Disease> properties, String prefix, int level) {
        List<BiObject> children = allNodes.stream()
                .filter(obj -> node.getId().equals(obj.getParentId()))
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());
        int idx = 1;
        for (BiObject child : children) {
            writeBiObjectTreeToWord(doc, child, allNodes, properties, prefix + "." + idx, level + 1);
            idx++;
        }
    }

    @Override
    public XWPFDocument exportPropertyWord(Long buildingId) {
        try {
            // 1. 根据buildingId查找对应的根属性节点
            Building building = buildingService.selectBuildingById(buildingId);
            Property property = propertyService.selectPropertyById(building.getRootPropertyId());
            List<Property> properties = propertyService.selectPropertyList(property);

            // 2. 读取模板文件
            Resource resource = new ClassPathResource("word.biz/桥梁模板.docx");
            XWPFDocument document = new XWPFDocument(resource.getInputStream());

            // 3. 处理结构体系属性
            processStructureSystem(properties);

            // 4. 处理图片
            processImages(document, buildingId);

            // 5. 替换表格中的占位符
            replaceTablePlaceholders(document, properties);

            return document;
        } catch (Exception e) {
            throw new RuntimeException("导出Word文档失败：" + e.getMessage(), e);
        }
    }

    private void processStructureSystem(List<Property> properties) {
        Property structureSystem = properties.stream()
                .filter(p -> p.getName().equals("结构体系"))
                .findFirst()
                .orElse(null);

        if (structureSystem != null) {
            structureSystem.setValue("");
            properties.stream()
                    .filter(p -> p.getParentId() != null && p.getParentId().equals(structureSystem.getId()))
                    .forEach(p -> structureSystem.setValue(structureSystem.getValue() + "\n" + p.getName() + p.getValue()));
        }
    }

    private void processImages(XWPFDocument document, Long buildingId) {
        attachmentService.getAttachmentList(buildingId).stream()
                .map(e -> {
                    FileMap fileMap = fileMapService.selectFileMapById(e.getMinioId());
                    fileMap.setOldName(e.getName());
                    return fileMap;
                })
                .forEach(e -> {
                    byte[] file = fileMapService.handleFileDownloadByNewName(e.getNewName());
                    for (XWPFTable table : document.getTables()) {
                        for (XWPFTableRow row : table.getRows()) {
                            for (XWPFTableCell cell : row.getTableCells()) {
                                for (XWPFParagraph p : cell.getParagraphs()) {
                                    insertImage(p, file, e.getOldName());
                                }
                            }
                        }
                    }
                });
    }

    private void replaceTablePlaceholders(XWPFDocument document, List<Property> properties) {
        Map<String, Integer> mp = new HashMap<>();

        // 第一次遍历替换特定属性
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        for (Property prop : properties) {
                            if (isSpecialProperty(prop.getName())) {
                                String newName = getNewPropertyName(prop.getName(), mp);
                                replacePlaceholder(p, "${" + prop.getName() + "}", prop.getValue() != null ? prop.getValue() : "/");
                                prop.setName(newName);
                            }
                        }
                    }
                }
            }
        }

        // 第二次遍历替换剩余占位符
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        replaceRemainingPlaceholders(p);
                    }
                }
            }
        }
    }

    private boolean isSpecialProperty(String name) {
        return name.equals("检测类别") || name.equals("评定时间") || name.equals("评定结果")
                || name.equals("处治对策") || name.equals("下次检测时间");
    }

    private String getNewPropertyName(String name, Map<String, Integer> mp) {
        if (mp.get(name) == null) {
            mp.put(name, 1);
            return name + "1";
        } else {
            mp.put(name, mp.get(name) + 1);
            return name + mp.get(name);
        }
    }

    private void replacePlaceholder(XWPFParagraph p, String placeholder, String value) {
        String text = p.getText();
        if (text.contains(placeholder)) {
            for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                p.removeRun(i);
            }

            XWPFRun newRun = p.createRun();
            newRun.setText(text.replace(placeholder, value));
            newRun.setFontFamily("宋体");
            newRun.setFontSize(9);
        }
    }

    private void replaceRemainingPlaceholders(XWPFParagraph p) {
        String text = p.getText();
        if (text.contains("${")) {
            for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                p.removeRun(i);
            }

            XWPFRun newRun = p.createRun();
            String replacedText = text.replaceAll("\\$\\{[^}]*\\}", "/");
            newRun.setText(replacedText);
            newRun.setFontFamily("宋体");
            newRun.setFontSize(9);
        }
    }

    private void insertImage(XWPFParagraph p, byte[] imageData, String name) {
        try {
            String[] parts = name.split("_");
            if (parts.length < 2) {
                return;
            }

            String prefix = parts[0];
            String type = parts[1];
            String placeholder = getImagePlaceholder(prefix, type);

            if (placeholder.isEmpty()) {
                return;
            }

            String text = p.getText();
            if (text.contains(placeholder)) {
                for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                    p.removeRun(i);
                }

                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                int targetWidth = (int) (4 * 37.8);
                int targetHeight = (int) (3 * 37.8);

                BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                scaledImage.createGraphics().drawImage(
                        bufferedImage.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH),
                        0, 0, null);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(scaledImage, "jpg", baos);
                byte[] scaledImageData = baos.toByteArray();

                int widthEMU = 4 * 360000;
                int heightEMU = 3 * 360000;

                XWPFRun run = p.createRun();
                run.addPicture(
                        new ByteArrayInputStream(scaledImageData),
                        XWPFDocument.PICTURE_TYPE_JPEG,
                        "bridge.jpg",
                        widthEMU,
                        heightEMU);
            }
        } catch (Exception e) {
            throw new RuntimeException("插入图片失败", e);
        }
    }

    private String getImagePlaceholder(String prefix, String type) {
        if ("front".equals(type)) {
            return "0".equals(prefix) ? "${桥梁正面照}" : "${桥梁正面照1}";
        } else if ("side".equals(type)) {
            return "0".equals(prefix) ? "${桥梁立面照}" : "${桥梁立面照1}";
        }
        return "";
    }


    public String getDiseaseSummary(List<Disease> diseases) throws JsonProcessingException {
        // 瘦身
        List<Disease2ReportSummaryAiVO> less = Convert2VO.copyList(diseases, Disease2ReportSummaryAiVO.class);
        // 序列化为JSON字符串
        ObjectMapper mapper = new ObjectMapper();
        String diseasesJson = mapper.writeValueAsString(less);
        // 发送POST请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(diseasesJson, headers);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.postForObject(SpringAiUrl + "/api-ai" + "/diseaseSummary", request, String.class);
        return response;
    }
}