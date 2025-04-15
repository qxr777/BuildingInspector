package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;

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

  /**
   * 根据建筑ID导出属性树信息
   */
  @GetMapping("/exportPropertyTree/{bid}")
  @Log(title = "导出属性树", businessType = BusinessType.EXPORT)
  public AjaxResult exportPropertyTree(@PathVariable("bid") Long bid) {
    try {
      // 1. 根据buildingId查找对应的根属性节点
      Property rootProperty = propertyService.selectRootPropertyByBuildingId(bid);
      System.out.println(rootProperty.getId());
      if (rootProperty == null) {
        return AjaxResult.error("未找到该建筑的属性信息");
      }

      // 2. 获取完整的属性树
      List<Property> propertyTree = propertyService.selectPropertyTreeById(rootProperty.getId());

      // 3. 转换为层级结构
      Map<String, Object> result = convertToTreeStructure(propertyTree);

      return AjaxResult.success(result);
    } catch (Exception e) {
      logger.error("导出属性树失败：", e);
      return AjaxResult.error("导出失败：" + e.getMessage());
    }
  }

  /**
   * 将属性列表转换为层级结构
   */
  private Map<String, Object> convertToTreeStructure(List<Property> properties) {
    Property rootProperty = properties.stream()
        .filter(p -> p.getParentId() == null)
        .findFirst()
        .orElseThrow(() -> new RuntimeException("未找到根节点"));

    return buildTreeNode(rootProperty, properties);
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
      Property property = propertyService.selectPropertyById(building.getRootPropertyId());
      List<Property> properties = propertyService.selectPropertyList(property);

      // 创建Word文档
      XWPFDocument document = new XWPFDocument();

      // 创建表格
      XWPFTable table = document.createTable();

      // 设置表格样式
      CTTblPr tblPr = table.getCTTbl().getTblPr();
      CTTblWidth tblWidth = tblPr.addNewTblW();
      tblWidth.setW(BigInteger.valueOf(9000));
      tblWidth.setType(STTblWidth.DXA);

      // 创建表头
      XWPFTableRow headerRow = table.getRow(0);
      XWPFTableCell cell1 = headerRow.getCell(0);
      XWPFTableCell cell2 = headerRow.addNewTableCell();

      // 设置表头样式
      cell1.setColor("DEEAF6");
      cell2.setColor("DEEAF6");

      // 设置表头内容
      cell1.setText("属性名称");
      cell2.setText("属性值");

      // 填充数据
      for (Property prop : properties) {
        XWPFTableRow row = table.createRow();
        XWPFTableCell nameCell = row.getCell(0);
        XWPFTableCell valueCell = row.getCell(1);

        // 设置单元格样式
        setCellStyle(nameCell, 4500);
        setCellStyle(valueCell, 4500);

        // 设置内容
        nameCell.setText(prop.getName() != null ? prop.getName() : "");
        valueCell.setText(prop.getValue() != null ? prop.getValue() : "");
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
      response.setHeader("Content-Disposition", "attachment; filename=" +
          new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));

      // 输出文档
      response.getOutputStream().write(documentBytes);
      response.flushBuffer();
    } catch (Exception e) {
      response.reset();
      response.setContentType("application/json");
      response.setCharacterEncoding("utf-8");
      Map<String, String> error = new HashMap<>();
      error.put("msg", "导出失败：" + e.getMessage());
      response.getWriter().write(new ObjectMapper().writeValueAsString(error));
    }
  }

  /**
   * 设置单元格样式
   */
  private void setCellStyle(XWPFTableCell cell, int width) {
    // 设置单元格宽度
    CTTcPr tcPr = cell.getCTTc().addNewTcPr();
    CTTblWidth cellWidth = tcPr.addNewTcW();
    cellWidth.setW(BigInteger.valueOf(width));
    cellWidth.setType(STTblWidth.DXA);

    // 设置垂直对齐
    cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

    // 设置水平对齐和字体
    XWPFParagraph paragraph = cell.getParagraphs().get(0);
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = paragraph.createRun();
    run.setFontFamily("宋体");
    run.setFontSize(10);
  }

}