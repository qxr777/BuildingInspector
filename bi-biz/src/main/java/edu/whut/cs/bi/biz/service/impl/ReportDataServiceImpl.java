package edu.whut.cs.bi.biz.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.ReportDataMapper;
import edu.whut.cs.bi.biz.domain.ReportData;
import com.ruoyi.common.core.text.Convert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;

/**
 * 报告数据Service业务层处理
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class ReportDataServiceImpl implements IReportDataService {
    @Autowired
    private ReportDataMapper reportDataMapper;

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

    /**
     * 查询报告数据
     *
     * @param id 报告数据ID
     * @return 报告数据
     */
    @Override
    public ReportData selectReportDataById(Long id) {
        return reportDataMapper.selectReportDataById(id);
    }

    /**
     * 查询报告数据列表
     *
     * @param reportData 报告数据
     * @return 报告数据
     */
    @Override
    public List<ReportData> selectReportDataList(ReportData reportData) {
        return reportDataMapper.selectReportDataList(reportData);
    }

    /**
     * 新增报告数据
     *
     * @param reportData 报告数据
     * @return 结果
     */
    @Override
    public int insertReportData(ReportData reportData) {
        return reportDataMapper.insertReportData(reportData);
    }

    /**
     * 修改报告数据
     *
     * @param reportData 报告数据
     * @return 结果
     */
    @Override
    public int updateReportData(ReportData reportData) {
        return reportDataMapper.updateReportData(reportData);
    }

    /**
     * 删除报告数据对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteReportDataByIds(String ids) {
        return reportDataMapper.deleteReportDataByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除报告数据信息
     *
     * @param id 报告数据ID
     * @return 结果
     */
    @Override
    public int deleteReportDataById(Long id) {
        return reportDataMapper.deleteReportDataById(id);
    }

    /**
     * 根据报告ID查询报告数据
     *
     * @param reportId 报告ID
     * @return 报告数据集合
     */
    @Override
    public List<ReportData> selectReportDataByReportId(Long reportId) {
        return reportDataMapper.selectReportDataByReportId(reportId);
    }

    /**
     * 批量新增报告数据
     *
     * @param list 报告数据列表
     * @return 结果
     */
    @Override
    public int batchInsertReportData(List<ReportData> list) {
        return reportDataMapper.batchInsertReportData(list);
    }

    /**
     * 根据报告ID删除报告数据
     *
     * @param reportId 报告ID
     * @return 结果
     */
    @Override
    public int deleteReportDataByReportId(Long reportId) {
        return reportDataMapper.deleteReportDataByReportId(reportId);
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
            log.error("插入图片失败", e);
        }
    }

    @Override
    public void exportPropertyWord(Long bid, HttpServletResponse response) {
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
            log.error("导出Word文档失败：", e);
            throw new RuntimeException("导出Word文档失败：" + e.getMessage());
        }
    }

    /**
     * 批量保存报告数据
     *
     * @param reportId 报告ID
     * @param dataList 数据列表
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveReportDataBatch(Long reportId, List<ReportData> dataList) {
        int result = 0;

        // 查询当前报告的所有数据
        List<ReportData> existingDataList = reportDataMapper.selectReportDataByReportId(reportId);
        Map<String, ReportData> existingDataMap = new HashMap<>();

        // 将现有数据按key放入Map中，便于快速查找
        for (ReportData existingData : existingDataList) {
            existingDataMap.put(existingData.getKey(), existingData);
        }

        // 处理新数据列表
        List<ReportData> toInsertList = new ArrayList<>();
        List<ReportData> toUpdateList = new ArrayList<>();
        List<Long> minioIdsToDelete = new ArrayList<>(); // 需要删除的MinIO文件ID列表

        for (ReportData newData : dataList) {
            // 确保设置了报告ID
            newData.setReportId(reportId);

            // 检查该key是否已存在
            ReportData existingData = existingDataMap.get(newData.getKey());

            if (existingData != null) {
                // 已存在，需要更新
                newData.setId(existingData.getId());

                // 如果是图片类型，检查是否有需要删除的MinIO文件
                if (existingData.getType() != null && existingData.getType() == 1 &&
                        existingData.getValue() != null && !existingData.getValue().isEmpty()) {

                    // 获取旧的MinIO ID列表
                    Set<String> oldMinioIds = new HashSet<>(Arrays.asList(existingData.getValue().split(",")));

                    // 获取新的MinIO ID列表
                    Set<String> newMinioIds = new HashSet<>();
                    if (newData.getValue() != null && !newData.getValue().isEmpty()) {
                        newMinioIds.addAll(Arrays.asList(newData.getValue().split(",")));
                    }

                    // 找出在旧列表中存在但在新列表中不存在的ID
                    for (String oldId : oldMinioIds) {
                        if (!oldId.isEmpty() && !newMinioIds.contains(oldId)) {
                            try {
                                // 添加到待删除列表
                                minioIdsToDelete.add(Long.parseLong(oldId));
                            } catch (NumberFormatException e) {
                                // 忽略无效的ID
                                log.error("无效的MinIO ID: " + oldId, e);
                            }
                        }
                    }
                }
                toUpdateList.add(newData);
            } else {
                // 不存在，需要新增
                toInsertList.add(newData);
            }
        }

        // 批量新增
        if (!toInsertList.isEmpty()) {
            result += reportDataMapper.batchInsertReportData(toInsertList);
        }

        // 批量更新
        for (ReportData data : toUpdateList) {
            result += reportDataMapper.updateReportData(data);
        }
        if (!minioIdsToDelete.isEmpty()) {
            try {
                // 调用FileMapService删除文件
                for (Long fileId : minioIdsToDelete) {
                    iFileMapService.deleteFileMapById(fileId);
                }
            } catch (Exception e) {
                // 记录错误但不影响主流程
                log.error("删除MinIO文件失败", e);
            }
        }

        return result;
    }
} 