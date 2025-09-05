package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.IComponentService;
import edu.whut.cs.bi.biz.service.IDiseaseTypeService;
import edu.whut.cs.bi.biz.service.ITaskService;
import edu.whut.cs.bi.biz.service.ReadFileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
                        disease.setBiObjectId(biObject4.getId());
                        if (photoName != null && !photoName.equals("")) {
                            disease.setRemark("图片名称：" + photoName);
                        }

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
                    return String.valueOf(cell.getNumericCellValue());
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
                            units = "";
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
                                throw new RuntimeException("未找到对应的部件：" + component_3);
                        }
                        BiObject finalBiObject = biObject3;
                        BiObject biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals(component_4))
                                .findFirst().orElse(null);

                        if (biObject4 == null) {
                            biObject4 = allBiObjects.stream().filter(biObject -> biObject.getParentId().equals(finalBiObject.getId()) && biObject.getName().equals("其他")).findFirst().orElse(null);
                            if (biObject4 == null)
                                throw new RuntimeException("未找到对应的部件：" + component_4);
                        }

                        List<Component> componentList = newComponentMap.get(position);
                        BiObject finalBiObject1 = biObject4;
                        Component component = componentList.stream().filter(c -> c.getBiObjectId().equals(finalBiObject1.getId())).findFirst().orElse(null);

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
                        disease.setBiObjectName(component_3);
                        disease.setBiObjectId(biObject4.getId());
                        disease.setDescription(diseaseDescription);
                        disease.setTaskId(taskId);

                        if (developmentTrend != null && !developmentTrend.equals("") && developmentTreadSet.contains(developmentTrend)) {
                            disease.setDevelopmentTrend(developmentTrend);
                        }

                        disease.setRepairRecommendation(repairSuggestion);
                        disease.setUnits(units);

                        if (photoName != null && !photoName.equals("")) {
                            disease.setRemark("图片名称：" + photoName + " ");
                        }
                        disease.setRemark(disease.getRemark() + remark);

                        DiseaseDetail diseaseDetail = new DiseaseDetail();

                        if (lengthUnits != null && !lengthUnits.equals("/") && !lengthUnits.equals("")) {
                            if (length != null && !length.equals("/") && !length.equals("")) {
                                BigDecimal decimal = new BigDecimal(0);
                                try {
                                    decimal = new BigDecimal(length);
                                } catch (Exception e) {
                                    log.error("转换长度出错length: ", length);
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
                throw new RuntimeException("第"+(i+1)+"行数据，位置格式错误：" + position);
            }

            BiObject biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

            if (biObject3 == null) {
                biObject3 = threeBiObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                if (biObject3 == null)
                    throw new RuntimeException("未找到对应的部件：" + component_3);
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
}
