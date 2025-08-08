package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
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
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
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
            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                List<BiObject> biObjects = biObjectMapper.selectBiObjectAndChildrenThreeLevel(building.getRootObjectId());
                addComponent(sheet, biObjects, componentMap);

                components = componentService.selectComponentList(new Component());
                Map<String, List<Component>> newComponentMap = components.stream().collect(Collectors.groupingBy(Component::getName));
                componentMap = newComponentMap;

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                Subject subject = ThreadContext.getSubject();
                for (int i = 1; i < sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;


                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                        ThreadContext.bind(subject);

                        String component_3 = getCellValueAsString(row.getCell(0));
                        String componentCode = getCellValueAsString(row.getCell(1));
                        String diseaseType = getCellValueAsString(row.getCell(2));
                        String position = getCellValueAsString(row.getCell(3));
                        String diseaseDescription = getCellValueAsString(row.getCell(4));
                        String scale = getCellValueAsString(row.getCell(5));
                        String photoName = getCellValueAsString(row.getCell(11));
                        String diseaseNumber = getCellValueAsString(row.getCell(13));

                        BiObject biObject3 = biObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

                        if (biObject3 == null) {
                            log.info("未找到对应的部件：{}", component_3);
                            biObject3 = biObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                            if (biObject3 == null)
                                throw new RuntimeException("未找到对应的部件：" + component_3);
                        }

                        List<Component> componentList = newComponentMap.get(componentCode + "#" + component_3);
                        BiObject finalBiObject1 = biObject3;
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
                        disease.setBiObjectId(biObject3.getId());
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

    private void addComponent(Sheet sheet, List<BiObject> biObjects, Map<String, List<Component>> componentMap) {
        Set<Component> componentSet = new HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String component_3 = getCellValueAsString(row.getCell(0));
            String componentCode = getCellValueAsString(row.getCell(1));

            BiObject biObject3 = biObjects.stream().filter(biObject -> biObject.getName().equals(component_3)).findFirst().orElse(null);

            if (biObject3 == null) {
                log.info("未找到对应的部件：{}", component_3);
                biObject3 = biObjects.stream().filter(biObject -> biObject.getName().equals("其他")).findFirst().orElse(null);
                if (biObject3 == null)
                    throw new RuntimeException("未找到对应的部件：" + component_3);
            }


            // 新增部件
            Component component = new Component();
            component.setCode(componentCode);
            component.setName(componentCode + "#" + component_3);

            List<Component> oldComponents = componentMap.get(component.getName());

            if (oldComponents != null && oldComponents.size() > 0) {
                BiObject finalBiObject = biObject3;
                Component old = oldComponents.stream().filter(oldComponent -> oldComponent.getBiObjectId() == finalBiObject.getId()).findFirst().orElse(null);

                if (old != null) {
                    continue;
                }
            }

            component.setBiObjectId(biObject3.getId());
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
}
