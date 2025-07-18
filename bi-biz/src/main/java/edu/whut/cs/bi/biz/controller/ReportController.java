package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.io.FileInputStream;

/**
 * 报告导出 控制器
 */
@RestController
@RequestMapping("/biz/report")
public class ReportController extends BaseController {

  private int tableCounter = 1; // 添加表格计数器

  @Autowired
  private IPropertyService propertyService;

  @Autowired
  IBuildingService buildingService;

  @Autowired
  IFileMapService iFileMapService;

  @Autowired
  DiseaseController diseaseController;

  @Autowired
  AttachmentService attachmentService;

  @Autowired
  ITaskService taskService;

  @Autowired
  IDiseaseService diseaseService;

  @Autowired
  BuildingMapper buildingMapper;

  @Autowired
  BiObjectMapper biObjectMapper;

  @GetMapping("/export/{id}")
  public void exportTaskReport(@PathVariable("id") Long taskId, HttpServletResponse response) {
    try {
      // 1. 查询数据
      Task task = taskService.selectTaskById(taskId);
      Building building = buildingService.selectBuildingById(task.getBuildingId());
      Disease disease = new Disease();
      disease.setBuildingId(task.getBuildingId());
      disease.setProjectId(task.getProjectId());
      List<Disease> properties = diseaseService.selectDiseaseList(disease);
      List<BiObject> biObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());
      // System.out.println(building.getRootObjectId());
      // biObjects.forEach(e -> {
      // System.out.println(e.getId() + " " + e.getName());
      // });
      // 2. 找到树的根节点
      BiObject root = biObjectMapper.selectBiObjectById(building.getRootObjectId());

      // 3. 创建Word文档
      XWPFDocument doc = new XWPFDocument();

      // 4. 递归写入树结构和病害信息
      writeBiObjectTreeToWord(doc, root, biObjects, properties, "4.1", 1);
      // 5. 导出Word
      response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
      response.setHeader("Content-Disposition", "attachment; filename=report.docx");
      doc.write(response.getOutputStream());
      doc.close();
    } catch (Exception e) {
      logger.error("导出Word文档失败：", e);
      throw new RuntimeException("导出Word文档失败：" + e.getMessage());
    }
  }

  private void writeBiObjectTreeToWord(XWPFDocument doc, BiObject node, List<BiObject> allNodes,
      List<Disease> properties, String prefix, int level) {
    // 写目录标题
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

    // 设置大纲级别（模仿示例代码）
    CTPPr ppr = p.getCTP().getPPr();
    if (ppr == null)
      ppr = p.getCTP().addNewPPr();
    // Word 大纲级别从 0 开始，对应 Heading 1
    if (level <= 9) { // Word 支持的大纲级别通常到 9
      ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
    } else { // 超过 9 级，设置为正文级别
      ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(9));
    }

    XWPFRun run = p.createRun();
    run.setBold(true);
    run.setColor("000000"); // 黑色
    run.setFontSize(12 + Math.max(0, 4 - level)); // 层级越深字号越小
    run.setText(prefix + " " + node.getName());

    // 写病害信息
    List<Disease> nodeDiseases = properties.stream()
        .filter(d -> d.getBiObjectId() != null && d.getBiObjectId().equals(node.getId()))
        .collect(Collectors.toList());

    // 如果存在病害信息，则生成介绍段落和表格
    if (!nodeDiseases.isEmpty()) {
      // 创建介绍段落
      XWPFParagraph introPara = doc.createParagraph();

      // Part 1: 加粗的开头部分
      XWPFRun runBold = introPara.createRun();
      runBold.setText("经检查，" + node.getName() + " 主要病害为:");
      runBold.setBold(true);
      runBold.setFontSize(12); // 设置字号与后面一致

      // Part 2: 遍历病害，每个条目一行
      int summarySeqNum = 1;
      for (Disease d : nodeDiseases) {
        // 创建新的段落并设置首行缩进
        XWPFParagraph diseasePara = doc.createParagraph();
        CTPPr diseasePpr = diseasePara.getCTP().getPPr();
        if (diseasePpr == null) {
          diseasePpr = diseasePara.getCTP().addNewPPr();
        }
        CTInd ind = diseasePpr.isSetInd() ? diseasePpr.getInd() : diseasePpr.addNewInd();
        ind.setFirstLine(BigInteger.valueOf(480)); // 设置首行缩进为480（约2个字符）

        XWPFRun runItem = diseasePara.createRun();
        runItem.setText(summarySeqNum++ + "） "
            + (d.getComponent() != null ? d.getComponent().getName().split("（")[0] : "/") + " "
            + (d.getType() != null ? d.getType() : "/") + " "
            + (d.getQuantity() > 0 ? d.getQuantity() : "/") + " 处; "
            + (d.getDescription() != null ? d.getDescription() : "/") + "; ");
        runItem.setFontSize(12); // 设置字号
      }

      // Part 3: 表格引用部分
      XWPFParagraph tableRefPara = doc.createParagraph();

      // 设置1.5倍行距
      CTPPr ppr1 = tableRefPara.getCTP().getPPr();
      if (ppr1 == null) {
        ppr1 = tableRefPara.getCTP().addNewPPr();
      }
      CTSpacing spacing = ppr1.isSetSpacing() ? ppr1.getSpacing() : ppr1.addNewSpacing();
      spacing.setLine(BigInteger.valueOf(360)); // 1.5倍行距（240 * 1.5 = 360）

      String tableNumber = "4." + tableCounter++; // 生成表格编号
      XWPFRun runTableRef = tableRefPara.createRun();
      runTableRef.setText("具体检测结果见下表 " + tableNumber + ":");
      runTableRef.setFontSize(12); // 设置字号

      // 添加表格编号
      XWPFParagraph tableNumPara = doc.createParagraph();
      tableNumPara.setAlignment(ParagraphAlignment.CENTER); // 设置居中对齐
      XWPFRun runTableNum = tableNumPara.createRun();
      runTableNum.setText("表 " + tableNumber);
      runTableNum.setFontSize(12);
      runTableNum.setBold(true);

      // --- 原有的表格创建代码开始 --- //
      XWPFTable table = doc.createTable(1, 8); // 1 row (header), 8 columns

      // 设置表格边框
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

      // 设置表头
      XWPFTableRow headerRow = table.getRow(0);

      // 表头文本数组
      String[] headers = { "序号", "缺损位置", "缺损类型", "数量", "病害描述", "评定类别 (1~5)", "发展趋势", "照片" };

      // 设置表头样式（加粗 + 居中 + 不换行）
      Double[] num = { 1.24, 2.75, 2.28, 1.29, 4.43, 2.0, 2.0, 1.51 };

      CTTblLayoutType tblLayout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
      tblLayout.setType(STTblLayoutType.FIXED);

      // 3. 设置每列
      for (int i = 0; i < headers.length; i++) {
        XWPFTableCell cell = headerRow.getCell(i);

        // 清除内容（更安全的清除方式）
        for (int j = cell.getParagraphs().size() - 1; j >= 0; j--) {
          cell.removeParagraph(j);
        }

        // 设置文本样式
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run1 = paragraph.createRun();
        run1.setText(headers[i]);
        run1.setBold(true);
        run1.setFontSize(11); // 五号字

        // 设置列宽（关键修正）
        CTTc cttc = cell.getCTTc();
        CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();

        // 确保单元格宽度属性存在
        CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcW.setW(BigInteger.valueOf((int) Math.round(num[i] * 567)));
        tcW.setType(STTblWidth.DXA);

        // 防止内容换行（可选）
        tcPr.addNewNoWrap();
      }

      // 填充数据行
      int seqNum = 1;
      for (Disease d : nodeDiseases) {
        XWPFTableRow dataRow = table.createRow();

        // 为数据行的每个单元格设置相同的宽度
        for (int i = 0; i < headers.length; i++) {
          XWPFTableCell cell = dataRow.getCell(i);

          // 设置单元格宽度与表头一致
          CTTc cttc = cell.getCTTc();
          CTTcPr tcPr = cttc.isSetTcPr() ? cttc.getTcPr() : cttc.addNewTcPr();
          CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
          tcW.setW(BigInteger.valueOf((int) Math.round(num[i] * 567)));
          tcW.setType(STTblWidth.DXA);
          // 设置单元格内容居中
          XWPFParagraph cellP = cell.getParagraphs().get(0);
          cellP.setAlignment(ParagraphAlignment.CENTER);

          // 设置文本内容
          XWPFRun cellR = cellP.createRun();
          cellR.setFontSize(11);

          switch (i) {
            case 0:
              cellR.setText(String.valueOf(seqNum++));
              break;
            case 1:
              cellR.setText(d.getComponent() != null ? d.getComponent().getName() : "/");
              break;
            case 2:
              cellR.setText(d.getType() != null ? d.getType() : "/");
              break;
            case 3:
              cellR.setText(d.getQuantity() > 0 ? String.valueOf(d.getQuantity()) : "/");
              break;
            case 4:
              cellR.setText(d.getDescription() != null ? d.getDescription() : "/");
              break;
            case 5:
              cellR.setText(d.getLevel() > 0 ? String.valueOf(d.getLevel()) : "/");
              break;
            case 6:
              cellR.setText("/");
              break;
            case 7:
              cellR.setText("");
              break;
          }
        }
      }
      // 在表格下方插入病害图片（不用表格，每行两个图片+标题，图片和标题均用段落并排）
      if (!nodeDiseases.isEmpty()) {
        int imageSeq = 1;
        List<byte[]> imageDatas = new ArrayList<>();
        List<String> imageTitles = new ArrayList<>();
        for (Disease d : nodeDiseases) {
          List<Map<String, Object>> images = diseaseController.getDiseaseImage(d.getId());
          if (images != null) {
            for (Map<String, Object> img : images) {
              if (Boolean.TRUE.equals(img.get("isImage"))) {
                String url = (String) img.get("url");
                String newName = url.substring(url.lastIndexOf("/") + 1);
                byte[] imageData = iFileMapService.handleFileDownloadByNewName(newName);
                if (imageData != null && imageData.length > 0) {
                  imageDatas.add(imageData);
                  String desc = d.getDescription() != null ? d.getDescription() : "";
                  imageTitles.add("图" + tableNumber + "-" + imageSeq + "  " + desc);
                  imageSeq++;
                }
              }
            }
          }
        }
        int total = imageDatas.size();
        for (int i = 0; i < total; i += 2) {
          // 一行插入两个图片
          XWPFParagraph imageRow = doc.createParagraph();
          imageRow.setAlignment(ParagraphAlignment.CENTER);
          for (int j = 0; j < 2; j++) {
            if (i + j < total) {
              XWPFRun imageRun = imageRow.createRun();
              try {
                BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageDatas.get(i + j)));
                int maxWidth = (int) (6 * 37.8); // 6cm
                int maxHeight = (int) (5 * 37.8); // 5cm
                int width = originalImage.getWidth();
                int height = originalImage.getHeight();
                double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
                int newWidth = (int) (width * ratio);
                int newHeight = (int) (height * ratio);
                java.awt.Image scaled = originalImage.getScaledInstance(newWidth, newHeight,
                    java.awt.Image.SCALE_SMOOTH);
                BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                scaledImage.getGraphics().drawImage(scaled, 0, 0, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(scaledImage, "jpg", baos);
                byte[] scaledImageData = baos.toByteArray();
                imageRun.addPicture(
                    new ByteArrayInputStream(scaledImageData),
                    XWPFDocument.PICTURE_TYPE_JPEG,
                    "disease.jpg",
                    6 * 360000,
                    5 * 360000);
                imageRun.addTab(); // 图片之间加tab分隔
              } catch (Exception ex) {
                logger.error("插入病害图片失败", ex);
              }
            }
          }
          // 一行插入两个标题
          XWPFParagraph titleRow = doc.createParagraph();
          titleRow.setAlignment(ParagraphAlignment.CENTER);
          for (int j = 0; j < 2; j++) {
            if (i + j < total) {
              XWPFRun titleRun = titleRow.createRun();
              titleRun.setText(imageTitles.get(i + j));
              titleRun.setFontSize(10);
              titleRun.addTab();
            }
          }
        }
      }
    }

    // 递归写子节点
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

  private String getHeaderText(int index) {
    switch (index) {
      case 0:
        return "序号";
      case 1:
        return "缺损位置";
      case 2:
        return "缺损类型";
      case 3:
        return "数量";
      case 4:
        return "病害描述";
      case 5:
        return "评定类别 (1~5)";
      case 6:
        return "发展趋势";
      case 7:
        return "照片";
      default:
        return "";
    }
  }

  private Map<String, Object> buildTreeNode(Property current, List<Property> allProperties) {
    Map<String, Object> node = Map.of(
        "id", current.getId(),
        "name", current.getName(),
        "value", current.getValue());

    List<Property> children = allProperties.stream()
        .filter(p -> current.getId().equals(p.getParentId()))
        .collect(Collectors.toList());

    if (!children.isEmpty()) {
      List<Map<String, Object>> childNodes = children.stream()
          .map(child -> buildTreeNode(child, allProperties))
          .collect(Collectors.toList());
      return Map.of(
          "id", current.getId(),
          "name", current.getName(),
          "value", current.getValue(),
          "children", childNodes);
    }

    return node;
  }

  /**
   * 根据建筑ID导出属性树信息到Word文档
   */
  @GetMapping("/exportPropertyWord/{bid}")
  @Log(title = "导出属性Word", businessType = BusinessType.EXPORT)
  public void exportPropertyWord(@PathVariable("bid") Long bid, HttpServletResponse response) throws IOException {
    try {
      // 1. 根据buildingId查找对应的根属性节点
      Building building = buildingService.selectBuildingById(bid);
      if (building == null) {
        response.setStatus(404);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":404,\"msg\":\"未找到对应的建筑信息\"}");
        return;
      }

      Property property = propertyService.selectPropertyById(building.getRootPropertyId());
      if (property == null) {
        response.setStatus(404);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":404,\"msg\":\"未找到对应的属性信息\"}");
        return;
      }

      List<Property> properties = propertyService.selectPropertyList(property);

      Resource resource = new ClassPathResource("word.biz/桥梁模板.docx");
      XWPFDocument document = new XWPFDocument(resource.getInputStream());

      // 读取模板文件
      long t = 0L;
      Property temp = null;
      for (Property property1 : properties) {
        if (property1.getName().equals("结构体系")) {
          t = property1.getId();
          temp = property1;
        }
      }
      if (temp != null) {
        temp.setValue("");
        for (Property property1 : properties) {
          if (property1.getParentId() != null && property1.getParentId() == t) {
            temp.setValue(temp.getValue() + "\n" + property1.getName() + property1.getValue());
          }
        }
      }
      attachmentService.getAttachmentList(bid).stream().map(e -> {
        FileMap fileMap = iFileMapService.selectFileMapById(e.getMinioId());
        fileMap.setOldName(e.getName());
        return fileMap;
      })
          .forEach(e -> {
            byte[] file = iFileMapService.handleFileDownloadByNewName(e.getNewName());
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
      // 替换表格中的占位符
      Map<String, Integer> mp = new HashMap<>();
      for (XWPFTable table : document.getTables()) {
        for (XWPFTableRow row : table.getRows()) {
          for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
              // 遍历所有属性进行替换
              for (Property prop : properties) {
                if (prop.getName().equals("检测类别") || prop.getName().equals("评定时间") || prop.getName().equals("评定结果")
                    || prop.getName().equals("处治对策") || prop.getName().equals("下次检测时间")) {
                  if (mp.get(prop.getName()) == null) {
                    mp.put(prop.getName(), 1);
                    prop.setName(prop.getName() + "1");
                  } else {
                    mp.put(prop.getName(), mp.get(prop.getName()) + 1);
                    prop.setName(prop.getName() + mp.get(prop.getName()));
                  }
                }
                replacePlaceholder(p, "${" + prop.getName() + "}", prop.getValue() != null ? prop.getValue() : "/");
              }
            }
          }
        }
      }
      // 再次遍历替换剩余的占位符
      for (XWPFTable table : document.getTables()) {
        for (XWPFTableRow row : table.getRows()) {
          for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
              replaceRemainingPlaceholders(p);
            }
          }
        }
      }

      // 将XWPFDocument转换为MultipartFile
      String fileName = property.getName() + "文档信息" + ".docx";
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      document.write(baos);
      byte[] documentBytes = baos.toByteArray();

      MultipartFile multipartFile = new MockMultipartFile(
          fileName,
          fileName,
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          documentBytes);

      // 保存文件
      iFileMapService.handleFileUpload(multipartFile);

      // 设置响应头
      response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
      fileName = property.getName() + "桥梁基本状况卡.docx";
      response.setHeader("Content-Disposition", "attachment; filename=" +
          new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));

      // 输出文档
      document.write(response.getOutputStream());
      document.close();
    } catch (Exception e) {
      logger.error("导出Word文档失败：", e);
      throw new RuntimeException("导出Word文档失败：" + e.getMessage());
    }
  }

  // 替换单个占位符的方法
  private void replacePlaceholder(XWPFParagraph p, String placeholder, String value) {
    String text = p.getText();
    if (text.contains(placeholder)) {
      // 清除段落中的所有runs
      for (int i = p.getRuns().size() - 1; i >= 0; i--) {
        p.removeRun(i);
      }

      // 创建新的run并设置替换后的文本
      XWPFRun newRun = p.createRun();
      newRun.setText(text.replace(placeholder, value));

      // 设置字体为宋体小五
      newRun.setFontFamily("宋体");
      newRun.setFontSize(9);
    }
  }

  // 替换剩余占位符的方法
  private void replaceRemainingPlaceholders(XWPFParagraph p) {
    String text = p.getText();
    if (text.contains("${")) {
      // 清除段落中的所有runs
      for (int i = p.getRuns().size() - 1; i >= 0; i--) {
        p.removeRun(i);
      }

      // 创建新的run并设置替换后的文本
      XWPFRun newRun = p.createRun();
      // 使用正则表达式替换所有剩余的${xxx}格式的占位符
      String replacedText = text.replaceAll("\\$\\{[^}]*\\}", "/");
      newRun.setText(replacedText);

      // 设置字体为宋体小五
      newRun.setFontFamily("宋体");
      newRun.setFontSize(9);
    }
  }

  private void insertImage(XWPFParagraph p, byte[] imageData, String name) {
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
      if ("front".equals(type)) {
        if ("0".equals(prefix)) {
          placeholder = "${桥梁正面照}";
        } else if ("1".equals(prefix)) {
          placeholder = "${桥梁正面照1}";
        }
      } else if ("side".equals(type)) {
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
      String text = p.getText();
      if (text.contains(placeholder)) {
        // 清除段落中的所有runs
        for (int i = p.getRuns().size() - 1; i >= 0; i--) {
          p.removeRun(i);
        }

        // 读取原始图片
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));

        // 直接设置目标尺寸（像素）- 假设96dpi，1厘米约等于37.8像素
        int targetWidth = (int) (4 * 37.8); // 4厘米
        int targetHeight = (int) (3 * 37.8); // 3厘米

        // 创建缩放后的图片，直接拉伸到目标尺寸
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        scaledImage.createGraphics().drawImage(
            bufferedImage.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH),
            0, 0, null);

        // 将缩放后的图片转换为字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "jpg", baos);
        byte[] scaledImageData = baos.toByteArray();

        // 转换为EMU单位（1厘米 = 360000 EMU）
        int widthEMU = 4 * 360000; // 4厘米
        int heightEMU = 3 * 360000; // 3厘米

        // 创建新的run并插入图片
        XWPFRun run = p.createRun();
        run.addPicture(
            new ByteArrayInputStream(scaledImageData),
            XWPFDocument.PICTURE_TYPE_JPEG,
            "bridge.jpg",
            widthEMU,
            heightEMU);
      }
    } catch (Exception e) {
      logger.error("插入图片失败", e);
    }
  }
}