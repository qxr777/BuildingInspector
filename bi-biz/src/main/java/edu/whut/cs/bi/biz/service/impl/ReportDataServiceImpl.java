package edu.whut.cs.bi.biz.service.impl;

import java.io.ByteArrayOutputStream;
import java.util.*;

import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.ReportDataMapper;
import com.ruoyi.common.core.text.Convert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    @Autowired
    IProjectService projectService;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private IBridgeCardService bridgeCardService;

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

            // 使用新的桥梁卡片服务生成文档
            XWPFDocument document = bridgeCardService.generateBridgeCardDocument(bid);

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
            
            // 添加病害数据的查询日志
            if (existingData.getKey() != null && existingData.getKey().contains("diseases")) {
                log.info("查询到已存在的病害数据 - id: {}, key: {}, value长度: {}", 
                        existingData.getId(),
                        existingData.getKey(),
                        existingData.getValue() != null ? existingData.getValue().length() : 0);
            }
        }

        // 处理新数据列表
        List<ReportData> toInsertList = new ArrayList<>();
        List<ReportData> toUpdateList = new ArrayList<>();
        List<Long> minioIdsToDelete = new ArrayList<>(); // 需要删除的MinIO文件ID列表

        for (ReportData newData : dataList) {
            // 确保设置了报告ID
            newData.setReportId(reportId);

            // 添加病害数据的详细日志
            if (newData.getKey() != null && newData.getKey().contains("diseases")) {
                log.info("准备保存病害数据 - key: {}, value长度: {}, value内容: {}", 
                        newData.getKey(), 
                        newData.getValue() != null ? newData.getValue().length() : 0,
                        newData.getValue());
            }

            // 检查该key是否已存在
            ReportData existingData = existingDataMap.get(newData.getKey());

            if (existingData != null) {
                // 已存在，需要更新
                newData.setId(existingData.getId());
                
                // 添加更新日志
                if (newData.getKey() != null && newData.getKey().contains("diseases")) {
                    log.info("病害数据将被更新 - 原有id: {}, 原有value长度: {}", 
                            existingData.getId(),
                            existingData.getValue() != null ? existingData.getValue().length() : 0);
                }

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
            log.info("准备批量新增 {} 条数据", toInsertList.size());
            result += reportDataMapper.batchInsertReportData(toInsertList);
            log.info("批量新增完成，影响行数: {}", result);
        }

        // 批量更新
        for (ReportData data : toUpdateList) {
            int updateResult = reportDataMapper.updateReportData(data);
            result += updateResult;
            
            // 添加病害数据更新结果日志
            if (data.getKey() != null && data.getKey().contains("diseases")) {
                log.info("病害数据更新完成 - id: {}, key: {}, 更新结果: {}, value长度: {}", 
                        data.getId(), 
                        data.getKey(), 
                        updateResult > 0 ? "成功" : "失败",
                        data.getValue() != null ? data.getValue().length() : 0);
            }
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

        log.info("保存报告数据完成 - reportId: {}, 总影响行数: {}", reportId, result);
        return result;
    }

    /**
     * 获取构件病害数据（用于病害选择器）
     *
     * @param report 报告ID
     * @return 构件病害数据列表
     */
    @Override
    public List<Map<String, Object>> getDiseaseComponentData(Report report) {
        long reportId = report.getId();
        long startTime = System.currentTimeMillis();

        try {

            // 1.获取报告关联的任务ID
            String taskIdsStr = report.getTaskIds();
            if (taskIdsStr == null || taskIdsStr.isEmpty()) {
                return new ArrayList<>();
            }
            // 只取第一个任务ID
            Long taskId = Long.parseLong(taskIdsStr.split(",")[0]);
            Task task = taskService.selectTaskById(taskId);
            // 2. 获取Building信息
            Building building = task.getBuilding();
            if (building == null) {
                log.error("未找到建筑物，buildingId: {}", taskId);
                return new ArrayList<>();
            }

            // 4. 获取BiObject层级结构
            Long subBridgeId = building.getRootObjectId();
            if (subBridgeId == null) {
                log.error("建筑物未关联BiObject，buildingId: {}", building.getId());
                return new ArrayList<>();
            }

            BiObject subBridge = biObjectMapper.selectBiObjectById(subBridgeId);
            if (subBridge == null) {
                log.error("未找到BiObject，biObjectId: {}", subBridgeId);
                return new ArrayList<>();
            }

            List<BiObject> allObjects = biObjectMapper.selectChildrenById(subBridge.getId());
            Map<Long, BiObject> objectMap = allObjects.stream()
                    .collect(HashMap::new, (map, obj) -> map.put(obj.getId(), obj), HashMap::putAll);

            objectMap.put(subBridge.getId(), subBridge);
            allObjects.add(subBridge);

            // 5. 获取第四层节点（构件层）
            List<BiObject> level4Objects = findLevel4Objects(allObjects, subBridge.getId());

            if (level4Objects.isEmpty()) {
                log.warn("未找到第四层构件节点，reportId: {}", reportId);
                return new ArrayList<>();
            }

            // 6. 提取所有第四层节点的ID
            List<Long> level4ObjectIds = level4Objects.stream()
                    .map(BiObject::getId)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            log.info("开始批量查询病害数据，构件数量: {}, reportId: {}", level4ObjectIds.size(), reportId);

            // 7. 批量查询所有构件的病害数据（一次查询替代N次查询）
            List<Disease> allDiseases = diseaseMapper.selectDiseaseComponentData(
                    level4ObjectIds, building.getId(), task.getProject().getYear());

            log.info("批量查询完成，病害记录数: {}, 耗时: {}ms", allDiseases.size(), System.currentTimeMillis() - startTime);

            // 8. 构建结果数据
            List<Map<String, Object>> result = new ArrayList<>();
            Set<String> uniqueKeys = new HashSet<>();

            for (Disease disease : allDiseases) {
                if (disease.getDiseaseType() != null) {
                    // 构造唯一键：构件ID + 病害类型ID
                    String uniqueKey = disease.getBiObjectId() + "_" + disease.getDiseaseTypeId();

                    if (!uniqueKeys.contains(uniqueKey)) {
                        uniqueKeys.add(uniqueKey);

                        // 从缓存的objectMap中获取构件名称
                        BiObject component = objectMap.get(disease.getBiObjectId());
                        String componentName = component != null ? component.getName() : "未知构件";

                        Map<String, Object> dataItem = new HashMap<>();
                        dataItem.put("componentId", disease.getBiObjectId());
                        dataItem.put("componentName", componentName);
                        dataItem.put("diseaseTypeId", disease.getDiseaseTypeId());
                        dataItem.put("diseaseTypeName", disease.getDiseaseType().getName());
                        result.add(dataItem);
                    }
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("获取构件病害数据成功，reportId: {}, 构件数: {}, 病害类型组合数: {}, 总耗时: {}ms",
                    reportId, level4Objects.size(), result.size(), totalTime);

            return result;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("获取构件病害数据失败，reportId: {}, 耗时: {}ms", reportId, totalTime, e);
            return new ArrayList<>();
        }
    }

    /**
     * 查找第四层节点
     */
    private List<BiObject> findLevel4Objects(List<BiObject> allObjects, Long rootObjectId) {
        // 构建父子关系映射
        Map<Long, List<BiObject>> childrenMap = allObjects.stream()
                .collect(HashMap::new,
                        (map, obj) -> map.computeIfAbsent(obj.getParentId(), k -> new ArrayList<>()).add(obj),
                        (map1, map2) -> {
                            for (Map.Entry<Long, List<BiObject>> entry : map2.entrySet()) {
                                map1.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                            }
                        });

        List<BiObject> level4Objects = new ArrayList<>();

        // 遍历根节点的子节点（第二层）
        List<BiObject> level2Objects = childrenMap.getOrDefault(rootObjectId, new ArrayList<>());
        for (BiObject level2 : level2Objects) {
            // 遍历第二层的子节点（第三层）
            List<BiObject> level3Objects = childrenMap.getOrDefault(level2.getId(), new ArrayList<>());
            for (BiObject level3 : level3Objects) {
                // 获取第三层的子节点（第四层）
                List<BiObject> level4 = childrenMap.getOrDefault(level3.getId(), new ArrayList<>());
                level4Objects.addAll(level4);
            }
        }

        return level4Objects;
    }
} 