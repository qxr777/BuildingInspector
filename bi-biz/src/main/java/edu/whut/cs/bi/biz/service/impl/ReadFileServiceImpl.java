package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysDictDataService;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ReadFileServiceImpl implements ReadFileService {

    @Resource
    private ITaskService taskService;

    @Resource
    private IComponentService componentService;

    @Resource
    private DiseaseTypeMapper diseaseTypeMapper;

    @Resource
    private BiObjectMapper biObjectMapper;

    @Resource
    private BuildingMapper buildingMapper;

    @Resource
    private IDiseaseTypeService diseaseTypeService;

    @Resource
    private DiseaseMapper diseaseMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private DiseaseDetailMapper diseaseDetailMapper;

    @Resource
    private IDiseaseService diseaseService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    @Qualifier("thumbPhotoExecutor")
    private Executor thumbPhotoExecutor;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Resource
    private IFileMapService fileMapService;

    @Override
    public void readCBMSDiseaseExcel(MultipartFile file, Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        List<Component> components = componentService.selectComponentList(new Component());
        Map<String, List<Component>> componentMap = components.stream().collect(Collectors.groupingBy(Component::getName));
        Set<Disease> diseaseSet = new ConcurrentHashSet<>();

        Building building = buildingMapper.selectBuildingById(task.getBuildingId());

        // 病害类型”其他“，当病害类型都不存在时，默认为其他 (5)
        DiseaseType otherDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-5");
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                List<BiObject> threeBiObjects = biObjectMapper.selectBiObjectAndChildrenThreeLevel(building.getRootObjectId());
                List<BiObject> allBiObjects = biObjectMapper.selectBiObjectAndChildren(building.getRootObjectId());

                addCBMSComponent(sheet, threeBiObjects, allBiObjects,  componentMap);

                components = componentService.selectComponentList(new Component());
                Map<String, List<Component>> newComponentMap = components.stream().collect(Collectors.groupingBy(Component::getName));
                componentMap = newComponentMap;

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                Subject subject = ThreadContext.getSubject();
                for (int i = 1; i < sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String component_3 = getCellValueAsString(row.getCell(0));
                    if (component_3 == null || component_3.equals("/") || component_3.equals("")) {
                        continue;
                    }

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                        ThreadContext.bind(subject);

                        String componentCode = getCellValueAsString(row.getCell(1));
                        String diseaseType = getCellValueAsString(row.getCell(2));
                        String position = getCellValueAsString(row.getCell(3));
                        String diseaseDescription = getCellValueAsString(row.getCell(4));
                        String scale = getCellValueAsString(row.getCell(5));
                        String photoName = getCellValueAsString(row.getCell(11));
                        String diseaseNumber = getCellValueAsString(row.getCell(13));


                        BiObject biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

                        if (biObject3 == null) {
                            log.info("未找到对应的部件：{}", component_3);
                            biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                            if (biObject3 == null)
                                throw new RuntimeException("未找到对应的部件：" + component_3);
                        }
                        BiObject finalBiObject = biObject3;
                        BiObject biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals("其他"))
                                .findFirst().orElse(null);

                        List<Component> componentList = newComponentMap.get(componentCode + "#" + component_3);
                        Component component = componentList.stream().filter(c -> c.getBiObjectId().equals(biObject4.getId())).findFirst().orElse(null);

                        Disease disease = new Disease();
                        disease.setPosition(position);
                        disease.setDescription(diseaseDescription);
                        if (scale == null || scale.equals("/") || scale.equals("")) {
                            disease.setLevel(1);
                        } else {
                            disease.setLevel((int) Double.parseDouble(scale));
                        }
                        if (diseaseNumber == null || diseaseNumber.equals("/") || diseaseNumber.equals("")) {
                            disease.setQuantity(1);
                        } else {
                            disease.setQuantity((int) Double.parseDouble(diseaseNumber));
                        }


                        // 病害类型
                        List<DiseaseType> diseaseTypes = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(biObject3.getId());

                        DiseaseType queryDiseaseType = null;
                        if (biObject3.getName().equals("其他")) {
                            queryDiseaseType = diseaseTypes.stream().filter(dt -> dt.getName().equals("其他")).findFirst().orElse(null);
                        } else {
                            queryDiseaseType = diseaseTypes.stream().filter(dt -> dt.getName().equals(diseaseType)).findFirst().orElse(null);
                        }

                        if (queryDiseaseType == null) {
                            queryDiseaseType = otherDiseaseType;
                        }

                        disease.setType(diseaseType);
                        disease.setDiseaseTypeId(queryDiseaseType.getId());

                        disease.setComponentId(component.getId());
                        disease.setBuildingId(building.getId());
                        disease.setProjectId(task.getProjectId());
                        disease.setBiObjectName(component_3);
                        disease.setTaskId(taskId);
                        disease.setParticipateAssess("1");
                        disease.setBiObjectId(biObject4.getId());
                        disease.setNature("非结构病害");
                        disease.setDevelopmentTrend("新增");

                        // 设置图片编码
                        List<String> splitPhotos = splitPhotoName(photoName);
                        disease.setImgNoExp(convertToDbFormat(splitPhotos));

                        diseaseSet.add(disease);
                    });
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (IOException e) {
            log.error("读取Excel文件时出错", e);
            throw new RuntimeException(e);
        }

        if (!diseaseSet.isEmpty()) {
            diseaseMapper.batchInsertDiseases(new ArrayList<>(diseaseSet));
        }
    }

    private void addCBMSComponent(Sheet sheet, List<BiObject> threeBiObjects, List<BiObject> allBiObjects, Map<String, List<Component>> componentMap) {
        Set<Component> componentSet = new HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String component_3 = getCellValueAsString(row.getCell(0));
            if (component_3 == null || component_3.equals("/") || component_3.equals("")) {
                continue;
            }

            String componentCode = getCellValueAsString(row.getCell(1));

            BiObject biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

            if (biObject3 == null) {
                log.info("未找到对应的部件：{}", component_3);
                biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                if (biObject3 == null)
                    throw new RuntimeException("未找到对应的部件：" + component_3);
            }

            BiObject finalBiObject = biObject3;
            BiObject biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals("其他"))
                    .findFirst().orElse(null);

            // 新增部件
            Component component = new Component();
            component.setCode(componentCode);
            component.setName(componentCode + "#" + component_3);

            List<Component> oldComponents = componentMap.get(component.getName());

            if (oldComponents != null && oldComponents.size() > 0) {
                Component old = oldComponents.stream().filter(oldComponent -> oldComponent.getBiObjectId().equals(biObject4.getId())).findFirst().orElse(null);

                if (old != null) {
                    continue;
                }
            }

            component.setBiObjectId(biObject4.getId());
            component.setCreateBy(ShiroUtils.getLoginName());
            component.setStatus("0");
            componentSet.add(component);
        }
        // 持久化
        for (Component component : componentSet) {
            componentService.insertComponent(component);
        }

    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue); // 去掉 .0
                    } else {
                        return String.valueOf(numericValue); // 保留小数
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static final Set<String> developmentTreadSet = new HashSet<>();

    static {
        developmentTreadSet.add("发展");
        developmentTreadSet.add("新增");
        developmentTreadSet.add("已维修");
        developmentTreadSet.add("稳定");
        developmentTreadSet.add("未找到");
    }

    @Override
    public void readDiseaseExcel(MultipartFile file, Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        List<Component> components = componentService.selectComponentList(new Component());
        Map<String, List<Component>> componentMap = components.stream().collect(Collectors.groupingBy(Component::getName));
        Set<Disease> diseaseSet = new ConcurrentHashSet<>();

        Building building = buildingMapper.selectBuildingById(task.getBuildingId());

        // 病害类型”其他“，当病害类型都不存在时，默认为其他 (5)
        DiseaseType otherDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-5");
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < 1; j++) {
//            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                List<BiObject> threeBiObjects = biObjectMapper.selectBiObjectAndChildrenThreeLevel(building.getRootObjectId());
                List<BiObject> allBiObjects = biObjectMapper.selectBiObjectAndChildren(building.getRootObjectId());

                addComponent(sheet, threeBiObjects, allBiObjects,  componentMap);

                components = componentService.selectComponentList(new Component());
                Map<String, List<Component>> newComponentMap = components.stream().collect(Collectors.groupingBy(Component::getName));
                componentMap = newComponentMap;

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                Subject subject = ThreadContext.getSubject();
                for (int i = 3; i < sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String component_3 = getCellValueAsString(row.getCell(3));
                    if (component_3 == null || component_3.equals("/") || component_3.equals("")) {
                        continue;
                    }

                    int finalI = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                        ThreadContext.bind(subject);

                        String position = getCellValueAsString(row.getCell(4));
                        String component_4 = position.split("#")[1];

                        String diseaseType = getCellValueAsString(row.getCell(5));
                        // 1处
                        String diseaseNumber = getCellValueAsString(row.getCell(6));
                        String units = "";

                        // 检查是否以非数字字符结尾
                        if (diseaseNumber.length() > 0 && !Character.isDigit(diseaseNumber.charAt(diseaseNumber.length() - 1))) {
                            // 带单位的情况：分离数字和单位
                            units = diseaseNumber.substring(diseaseNumber.length() - 1); // 单位为最后一个字符
                            diseaseNumber = diseaseNumber.substring(0, diseaseNumber.length() - 1); // 数字部分
                        } else {
                            // 纯数字的情况：单位为空
                            units = "处";
                        }

                        String length = getCellValueAsString(row.getCell(7));
                        String lengthUnits = getCellValueAsString(row.getCell(8));

                        String diseaseDescription = getCellValueAsString(row.getCell(9));
                        String repairSuggestion = getCellValueAsString(row.getCell(10));
                        String scale = getCellValueAsString(row.getCell(11));
                        String photoName = getCellValueAsString(row.getCell(12));
                        String developmentTrend = getCellValueAsString(row.getCell(13));
                        String remark = getCellValueAsString(row.getCell(14));

                        BiObject biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

                        if (biObject3 == null) {
                            biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                            if (biObject3 == null)
                                throw new RuntimeException("第"+(finalI +1)+"行数据未找到对应的部件：" + component_3);
                        }
                        BiObject finalBiObject = biObject3;
                        BiObject biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals(component_4))
                                .findFirst().orElse(null);

                        if (biObject4 == null) {
                            biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals("其他")).findFirst().orElse(null);
                            if (biObject4 == null)
                                throw new RuntimeException("第"+(finalI +1)+"行数据未找到对应的部件：" + component_4);
                        }

                        List<Component> componentList = newComponentMap.get(position);
                        BiObject finalBiObject1 = biObject4;
                        Component component = componentList.stream().filter(c -> c.getBiObjectId().equals(finalBiObject1.getId())).findFirst().orElse(null);

                        Disease disease = new Disease();
                        disease.setPosition(position);
                        disease.setParticipateAssess("1");
                        disease.setDescription(diseaseDescription);
                        if (scale == null || scale.equals("/") || scale.equals("")) {
                            disease.setLevel(1);
                        } else {
                            disease.setLevel((int) Double.parseDouble(scale));
                        }
                        if (diseaseNumber == null || diseaseNumber.equals("/") || diseaseNumber.equals("")) {
                            disease.setQuantity(1);
                        } else {
                            try {
                                disease.setQuantity((int) Double.parseDouble(diseaseNumber));
                            } catch (Exception e) {
                                throw new RuntimeException("第"+(finalI +1)+"行数据的数量：" + diseaseNumber + "不规范！");
                            }

                        }


                        // 病害类型
                        List<DiseaseType> diseaseTypes = new ArrayList<>();
                        List<DiseaseType> dts = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(biObject3.getId());
                        List<DiseaseType> childDiseaseTypes = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(biObject4.getId());

                        diseaseTypes.addAll(dts);
                        diseaseTypes.addAll(childDiseaseTypes);

                        DiseaseType queryDiseaseType = null;
                        if (biObject3.getName().equals("其他")) {
                            queryDiseaseType = diseaseTypes.stream().filter(dt -> dt.getName().equals("其他")).findFirst().orElse(null);
                        } else {
                            queryDiseaseType = diseaseTypes.stream().filter(dt -> dt.getName().equals(diseaseType)).findFirst().orElse(null);
                        }

                        if (queryDiseaseType == null) {
                            queryDiseaseType = otherDiseaseType;
                        }

                        disease.setType(diseaseType);
                        disease.setDiseaseTypeId(queryDiseaseType.getId());

                        disease.setComponentId(component.getId());
                        disease.setBuildingId(building.getId());
                        disease.setProjectId(task.getProjectId());
                        disease.setBiObjectName(biObject4.getName());
                        disease.setBiObjectId(biObject4.getId());
                        disease.setDescription(diseaseDescription);
                        disease.setTaskId(taskId);
                        disease.setNature("非结构病害");

                        if (developmentTrend != null && !developmentTrend.equals("") && developmentTreadSet.contains(developmentTrend)) {
                            disease.setDevelopmentTrend(developmentTrend);
                        }

                        disease.setRepairRecommendation(repairSuggestion);
                        disease.setUnits(units);

                        // 设置图片编码
                        List<String> splitPhotos = splitPhotoName(photoName);
                        disease.setImgNoExp(convertToDbFormat(splitPhotos));

                        disease.setRemark(disease.getRemark() + remark);

                        DiseaseDetail diseaseDetail = new DiseaseDetail();

                        if (lengthUnits != null && !lengthUnits.equals("/") && !lengthUnits.equals("")) {
                            if (length != null && !length.equals("/") && !length.equals("")) {
                                BigDecimal decimal = new BigDecimal(0);
                                try {
                                    decimal = new BigDecimal(length);
                                } catch (Exception e) {
                                    log.error("第"+(finalI +1)+"行数据转换长度出错length: ", length);
                                }
                                if (lengthUnits.equals("m")) {
                                    diseaseDetail.setLength1(decimal);
                                } else {
                                    diseaseDetail.setAreaIdentifier(2);
                                    diseaseDetail.setAreaLength(decimal);
                                    diseaseDetail.setAreaWidth(BigDecimal.valueOf(1));
                                }
                            }

                        }

                        List<DiseaseDetail> diseaseDetails = new ArrayList<>();
                        diseaseDetails.add(diseaseDetail);
                        disease.setDiseaseDetails(diseaseDetails);

                        diseaseSet.add(disease);
                    });
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (IOException e) {
            log.error("读取Excel文件时出错", e);
            throw new RuntimeException(e);
        }

        if (!diseaseSet.isEmpty()) {
            transactionTemplate.execute(status -> {
                diseaseMapper.batchInsertDiseases(new ArrayList<>(diseaseSet));

                List<DiseaseDetail> allDetails = diseaseSet.stream().flatMap(disease -> disease.getDiseaseDetails().stream().peek(detail -> detail.setDiseaseId(disease.getId()))).collect(Collectors.toList());
                if (!allDetails.isEmpty()) {
                    diseaseDetailMapper.insertDiseaseDetails(allDetails);
                }

                return true;
            });

        }
    }

    // 定义支持的分隔符（中文顿号和波浪线）
    private static final String SEPARATOR_DOT = "、";
    private static final String SEPARATOR_TILDE = "~";

    /**
     * 将photoName分割为字符串列表
     * @param photoName 原始字符串（如"1982、1983"或"1982~1985"）
     * @return 分割后的列表，若输入为空则返回空列表
     */
    public List<String> splitPhotoName(String photoName) {
        List<String> result = new ArrayList<>();
        if (photoName == null || photoName.trim().isEmpty()) {
            return result;
        }
        // 去除首尾空格，避免空元素
        String trimmedPhotoName = photoName.trim();

        // 1. 优先判断是否包含中文顿号分隔符
        if (trimmedPhotoName.contains(SEPARATOR_DOT)) {
            String[] parts = trimmedPhotoName.split(SEPARATOR_DOT);
            result.addAll(Arrays.asList(parts));
        }
        // 2. 再判断是否包含波浪线分隔符
        else if (trimmedPhotoName.contains(SEPARATOR_TILDE)) {
            String[] parts = trimmedPhotoName.split(SEPARATOR_TILDE);
            for (int i = Integer.valueOf(parts[0]);i <= Integer.valueOf(parts[1]);i++) {
                result.add(String.valueOf(i));
            }
        }
        // 3. 若没有分隔符（单个值），直接添加到列表
        else {
            result.add(trimmedPhotoName);
        }

        // 过滤空元素（避免分割后出现空字符串，如"1982、 、1983"）
        result.removeIf(part -> part == null || part.trim().isEmpty());
        return result;
    }

    /**
     * 将分割后的列表转为数据库支持的格式（示例：转为JSON字符串，适配MySQL的JSON类型）
     * @param photoList 分割后的列表
     * @return JSON格式字符串（如["1982","1983"]）
     */
    public static String convertToDbFormat(List<String> photoList) {
        if (photoList == null || photoList.isEmpty()) {
            return "[]"; // 空数组的JSON表示
        }
        // 手动拼接JSON（若项目有FastJSON/Jackson，可替换为工具类调用，避免手动拼接错误）
        StringBuilder jsonSb = new StringBuilder("[");
        for (int i = 0; i < photoList.size(); i++) {
            jsonSb.append("\"").append(photoList.get(i).trim()).append("\"");
            if (i != photoList.size() - 1) {
                jsonSb.append(",");
            }
        }
        jsonSb.append("]");
        return jsonSb.toString();
    }

    private void addComponent(Sheet sheet, List<BiObject> threeBiObjects, List<BiObject> allBiObjects, Map<String, List<Component>> componentMap) {
        Set<Component> componentSet = new HashSet<>();

        for (int i = 3; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String component_3 = getCellValueAsString(row.getCell(3));
            String position = getCellValueAsString(row.getCell(4));
            if (component_3 == null || component_3.equals("/") || component_3.equals("")) {
                continue;
            }

            String[] splitPosition = position.split("#");
            String componentCode = splitPosition[0];
            String component_4 = "";
            try {
                component_4 = splitPosition[1];
            } catch (Exception e) {
                throw new RuntimeException("第"+(i+1)+"行数据，病害位置格式错误（应为'code#xx'格式)：" + position + "，或者是excel文件格式错误，缺失部件列。");
            }

            BiObject biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

            if (biObject3 == null) {
                biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                if (biObject3 == null)
                    throw new RuntimeException("第"+(i+1)+"行数据未找到对应的部件：" + component_3);
            }

            BiObject finalBiObject = biObject3;
            String finalComponent_ = component_4;
            BiObject biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals(finalComponent_))
                    .findFirst().orElse(null);
            if (biObject4 == null) {
                biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals("其他"))
                        .findFirst().orElse(null);
            }

            // 新增部件
            Component component = new Component();
            component.setCode(componentCode);
            component.setName(position);

            List<Component> oldComponents = componentMap.get(component.getName());

            if (oldComponents != null && oldComponents.size() > 0) {
                BiObject finalBiObject1 = biObject4;
                Component old = oldComponents.stream().filter(oldComponent -> oldComponent.getBiObjectId().equals(finalBiObject1.getId())).findFirst().orElse(null);

                if (old != null) {
                    continue;
                }
            }

            component.setBiObjectId(biObject4.getId());
            component.setCreateBy(ShiroUtils.getLoginName());
            component.setStatus("0");
            componentSet.add(component);
        }
        // 持久化
        for (Component component : componentSet) {
            componentService.insertComponent(component);
        }

    }

    @Override
    @Transactional
    public List<String> uploadPictures(List<MultipartFile> photos, Long taskId) {
        // 防止
        Task task = taskService.selectTaskById(taskId);

        Disease disease =  new Disease();
        disease.setBuildingId(task.getBuildingId());
        disease.setProjectId(task.getProjectId());
        List<Disease> diseases = diseaseMapper.selectDiseaseList(disease);

        // 1. 收集所有 img -> diseaseId 的映射
        ObjectMapper mapper = new ObjectMapper();

        List<Map.Entry<String, Long>> allEntries = diseases.stream()
                .filter(dd -> dd.getImgNoExp() != null && !dd.getImgNoExp().trim().isEmpty())
                .flatMap(d -> {
                    try {
                        List<String> imgs = mapper.readValue(d.getImgNoExp(), new TypeReference<List<String>>() {});
                        return imgs.stream()
                                .filter(img -> img != null && !img.trim().isEmpty())
                                .map(img -> new AbstractMap.SimpleEntry<>(img.trim(), d.getId()));
                    } catch (JsonProcessingException e) {
                        // 如果 JSON 格式非法，可以选择记录日志或跳过
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        // 2. 统计每个 img 出现的次数
        Map<String, Long> imgCountMap = allEntries.stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.counting()));

        // 3. 过滤掉重复的 img，只保留唯一的
        Map<String, Long> imgToDiseaseMap = allEntries.stream()
                .filter(e -> imgCountMap.get(e.getKey()) == 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 定义无法匹配的图片列表
        List<String> unmatchedPhotos = new ArrayList<>();

        Map<Long, List<MultipartFile>> group = new HashMap<>();
        photos.forEach(photo -> {
            String photoName = photo.getOriginalFilename();
            if (photoName == null) {
                unmatchedPhotos.add("null"); // 或者跳过，视业务而定
                return;
            }

            int dotIndex = photoName.lastIndexOf('.');
            if (dotIndex <= 4) {
                unmatchedPhotos.add(photoName); // 格式不对
                return;
            }

            int lastUnderlineIndex = photoName.lastIndexOf("_");
            // 校验：_ 必须在后缀前，且 _ 后面有有效内容（避免如 "IMG_.JPG" 这种无效格式）
            if (lastUnderlineIndex == -1 || lastUnderlineIndex >= dotIndex - 1) {
                unmatchedPhotos.add(photoName); // 无 _ 或 _ 位置非法，不匹配
                return;
            }


            String code = photoName.substring(lastUnderlineIndex + 1, dotIndex);
            Long diseaseId = imgToDiseaseMap.get(code);

            if (diseaseId != null) {
                group.computeIfAbsent(diseaseId, k -> new ArrayList<>()).add(photo);
            } else {
                unmatchedPhotos.add(photoName);
            }
        });

        group.forEach((diseaseId, ps) -> {
            diseaseService.handleDiseaseAttachment(ps.toArray(new MultipartFile[0]), diseaseId, 1);
            Disease d = diseaseService.selectDiseaseById(diseaseId);
            if (d.getAttachmentCount() != null) {
                d.setAttachmentCount(d.getAttachmentCount() + ps.size());
            } else {
                d.setAttachmentCount(ps.size());
            }

            diseaseMapper.updateDisease(d);
        });

        return unmatchedPhotos;
    }

    @Resource
    private ISysDictDataService sysDictDataService;

    private static final Map<String, Long> BRIDGE_TYPE_MAP = new HashMap<>();

    static {
        BRIDGE_TYPE_MAP.put("梁式桥", 1L);
        BRIDGE_TYPE_MAP.put("箱形拱桥", 3L);
        BRIDGE_TYPE_MAP.put("双曲拱桥", 4L);
        BRIDGE_TYPE_MAP.put("板拱桥", 5L);
        BRIDGE_TYPE_MAP.put("刚架拱桥", 6L);
        BRIDGE_TYPE_MAP.put("桁架拱桥", 7L);
        BRIDGE_TYPE_MAP.put("钢-混凝土组合拱桥", 8L);
        BRIDGE_TYPE_MAP.put("预应力混凝土悬索桥", 9L);
        BRIDGE_TYPE_MAP.put("预应力混凝土斜拉桥", 10L);
        BRIDGE_TYPE_MAP.put("钢箱梁斜拉桥", 11L);
        BRIDGE_TYPE_MAP.put("肋拱桥", 12L);
        BRIDGE_TYPE_MAP.put("钢箱梁悬索桥", 13L);
        BRIDGE_TYPE_MAP.put("钢桁梁悬索桥", 14L);
        BRIDGE_TYPE_MAP.put("叠合梁悬索桥", 15L);
        BRIDGE_TYPE_MAP.put("钢桁梁斜拉桥", 16L);
        BRIDGE_TYPE_MAP.put("叠合梁斜拉桥", 17L);
    }

    @Resource
    private IBuildingService buildingService;

    @Override
    @Transactional
    public void ReadBuildingFile(MultipartFile file, Long projectId)  {
        List<Long> buildingList = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < 7; j++) {
//            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                for (int i = 1; i < sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String buildingName = getCellValueAsString(row.getCell(0));
                    String type = getCellValueAsString(row.getCell(1));
                    String fatherBuilding = getCellValueAsString(row.getCell(2));
                    String area = getCellValueAsString(row.getCell(3));
                    String line =  getCellValueAsString(row.getCell(4));
                    String template = getCellValueAsString(row.getCell(5));

                    Building building = new Building();

                    building.setName(buildingName);
                    SysDictData sysDictData = new SysDictData();
                    sysDictData.setDictType("bi_building_area");
                    sysDictData.setDictLabel(area);
                    List<SysDictData> areaCode = sysDictDataService.selectDictDataList(sysDictData);
                    if (areaCode == null || CollUtil.isEmpty(areaCode)) {
                        throw new RuntimeException("请检查字典数据: " +  area);
                    }
                    building.setArea(String.valueOf(areaCode.get(0).getDictValue()));

                    sysDictData.setDictType("bi_buildeing_line");
                    sysDictData.setDictLabel(line);
                    List<SysDictData> lineCode = sysDictDataService.selectDictDataList(sysDictData);
                    if (lineCode == null || CollUtil.isEmpty(lineCode)) {
                        throw new RuntimeException("请检查字典数据: " +  line);
                    }
                    building.setLine(String.valueOf(lineCode.get(0).getDictValue()));

                    List<Building> buildings = buildingMapper.selectBuildingList(building);
                    if (buildings == null || buildings.size() == 0) {
                        if (type.equals("组合桥")) {
                            building.setStatus("0");
                            building.setIsLeaf("0");
                        } else if (type.equals("桥幅")) {
                            building.setStatus("0");
                            building.setIsLeaf("1");
                            building.setTemplateId(BRIDGE_TYPE_MAP.get(template));
                            if (!fatherBuilding.equals(buildingName)) {
                                Building parent = new Building();
                                parent.setName(fatherBuilding);
                                parent.setIsLeaf("0");
                                parent.setArea(String.valueOf(areaCode.get(0).getDictValue()));
                                parent.setLine(String.valueOf(lineCode.get(0).getDictValue()));
                                List<Building> parentBuildings = buildingMapper.selectBuildingList(parent);
                                if (parentBuildings != null && !parentBuildings.isEmpty()) {
                                    building.setParentId(parentBuildings.get(0).getId());

//                                    throw new RuntimeException("请检查桥梁数据: " +  buildingName + " 父桥梁: " + fatherBuilding);
                                }
                            }
                        }

                        buildingService.insertBuilding(building);

                        if (type.equals("桥幅")) {
                            buildingList.add(building.getId());
                        }

                    } else {
                        if (type.equals("桥幅")) {
                            building = buildings.get(0);
                            if (!fatherBuilding.equals(buildingName)) {
                                Building parent = new Building();
                                parent.setName(fatherBuilding);
                                parent.setIsLeaf("0");
                                parent.setArea(String.valueOf(areaCode.get(0).getDictValue()));
                                parent.setLine(String.valueOf(lineCode.get(0).getDictValue()));
                                List<Building> parentBuildings = buildingMapper.selectBuildingList(parent);
                                if (parentBuildings != null && parentBuildings.size() > 0) {
                                    building.setParentId(parentBuildings.get(0).getId());
                                    buildingService.updateBuilding(building);
//                                    throw new RuntimeException("请检查桥梁数据: " +  buildingName + " 父桥梁: " + fatherBuilding);
                                }

                            }
                        }

                    }

                }
            }
        } catch (IOException e) {
            log.error("读取桥梁文件时出错", e);
            throw new RuntimeException(e);
        }
//        if (buildingList.size() > 0)
//            taskService.batchInsertTasks(projectId, buildingList);
    }


    int BATCH_SIZE = 50;

    @Override
    public void addThumbPhoto(List<Attachment> attachmentList) {
        if (attachmentList.isEmpty()) {
            log.info("没有需要处理的附件");
        }

        // 分批次处理
        List<List<Attachment>> batches = splitIntoBatches(attachmentList, BATCH_SIZE);

        // 用于存储异步任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<Attachment> batch : batches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processBatch(batch);
            }, thumbPhotoExecutor);
            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("处理缩略图任务时发生异常", e);
        }

        log.info("缩略图生成完成，共处理" + attachmentList.size() + "个附件");
    }

    /**
     * 处理单个批次的附件
     */
    private void processBatch(List<Attachment> batch) {
        for (Attachment attachment : batch) {
            try {
                processSingleAttachment(attachment);
            } catch (Exception e) {
                log.error("处理附件失败：attachmentId={}", attachment.getId(), e);
            }
        }
    }

    /**
     * 处理单个附件的缩略图生成
     */
    private void processSingleAttachment(Attachment attachment) {
        Long thumbMinioId = attachment.getThumbMinioId();
        if (thumbMinioId != null) {
            return;
        }

        Long minioId = attachment.getMinioId();
        FileMap fileMap = fileMapService.selectFileMapById(minioId);

        if (fileMap != null) {
            try {
                String newName = fileMap.getNewName();
                MultipartFile thumbnail = createThumbnail(newName, fileMap.getOldName(), 200, 200, 0.9f);

                // 保存缩略图并更新附件信息
                FileMap thumbFileMap = fileMapService.handleFileUpload(thumbnail);
                attachment.setThumbMinioId(Long.valueOf(thumbFileMap.getId()));
                attachmentService.updateAttachment(attachment);

                log.info("成功生成缩略图：fileId={}, thumbId={}", fileMap.getId(), thumbFileMap.getId());

            } catch (Exception e) {
                log.error("生成缩略图失败：fileId={}", fileMap.getId(), e);
            }
        }
    }

    /**
     * 将列表拆分成多个批次
     */
    private List<List<Attachment>> splitIntoBatches(List<Attachment> list, int batchSize) {
        List<List<Attachment>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    /**
     * 生成缩略图（原有方法保持不变）
     */
    public MultipartFile createThumbnail(String newName, String originalName, int width, int height, float quality) throws Exception {
        if (StringUtils.isEmpty(newName)) {
            throw new IllegalArgumentException("名称不能为空");
        }

        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucketName())
                .object(newName.substring(0, 2) + "/" + newName)
                .build())) {

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Thumbnails.of(inputStream)
                    .size(width, height)
                    .crop(Positions.CENTER)
                    .outputQuality(quality)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);

            byte[] thumbnailBytes = outputStream.toByteArray();

            return new MockMultipartFile(
                    originalName + "_thumbnail",
                    originalName + "_thumbnail.jpg",
                    "image/jpeg",
                    thumbnailBytes
            );
        }
    }
}
