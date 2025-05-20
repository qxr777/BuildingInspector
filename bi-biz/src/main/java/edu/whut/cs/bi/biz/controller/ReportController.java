package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IPropertyService;
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

  @Autowired
  private IPropertyService propertyService;

  @Autowired
  IBuildingService buildingService;

  @Autowired
  IFileMapService iFileMapService;

  @Autowired
  AttachmentService attachmentService;

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
      Property property = propertyService.selectPropertyById(building.getRootPropertyId());
      List<Property> properties = propertyService.selectPropertyList(property);

      // 读取模板文件
      Resource resource = new ClassPathResource("word.biz/桥梁模板.docx");
      XWPFDocument document = new XWPFDocument(resource.getInputStream());
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