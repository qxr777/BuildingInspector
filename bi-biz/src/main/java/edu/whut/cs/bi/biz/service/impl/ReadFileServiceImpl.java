package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysDictDataService;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.BatchBridgeCardImportResult;
import edu.whut.cs.bi.biz.domain.vo.BatchCbmsDiseaseImportResult;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.utils.DroneDiseaseImportParser;
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
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class ReadFileServiceImpl implements ReadFileService {

    @Resource
    private ITaskService taskService;

    @Resource
    private TaskMapper taskMapper;

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

    @Resource
    private IPropertyService propertyService;

    private static final Charset ZIP_UTF8_CHARSET = StandardCharsets.UTF_8;

    private static final Charset ZIP_GBK_CHARSET = Charset.forName("GBK");

    @Override
    public void readCBMSDiseaseExcel(MultipartFile file, Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        List<Component> components = componentService.selectComponentList(new Component());
        Map<String, List<Component>> componentMap = components.stream().collect(Collectors.groupingBy(Component::getName));
        Set<Disease> diseaseSet = new ConcurrentHashSet<>();

        Building building = buildingMapper.selectBuildingById(task.getBuildingId());

        String loginUser = ShiroUtils.getLoginName();

        // 病害类型”其他“，当病害类型都不存在时，默认为其他 (5)
        DiseaseType otherDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-5");
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                List<BiObject> threeBiObjects = biObjectMapper.selectBiObjectAndChildrenThreeLevel(building.getRootObjectId());
                List<BiObject> allBiObjects = biObjectMapper.selectBiObjectAndChildren(building.getRootObjectId());

                addCBMSComponent(sheet, threeBiObjects, allBiObjects, componentMap);

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
                        disease.setCreateBy(loginUser);
                        disease.setUpdateBy(loginUser);
                        disease.setCreateTime(DateUtils.getNowDate());
                        disease.setUpdateTime(DateUtils.getNowDate());
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
            List<Disease> diseaseList = new ArrayList<>(diseaseSet);
            diseaseMapper.batchInsertDiseases(diseaseList);
            diseaseMapper.fillLocalIdWithId(diseaseList.stream().map(Disease::getId).toList());
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
            component.setCreateBy(ShiroUtils.getLoginName());
            component.setUpdateBy(ShiroUtils.getLoginName());
            component.setCreateTime(DateUtils.getNowDate());
            component.setUpdateTime(DateUtils.getNowDate());
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
        Set<Disease> diseaseSet = new ConcurrentHashSet<>();

        Building building = buildingMapper.selectBuildingById(task.getBuildingId());
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < 1; j++) {
//            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                List<BiObject> allBiObjects = biObjectMapper.selectBiObjectAndChildren(building.getRootObjectId());

                validateJiaotouComponentLevels(sheet, building.getRootObjectId(), allBiObjects);

                List<Component> components = componentService.selectComponentList(new Component());
                Map<String, List<Component>> componentMap = components.stream()
                        .collect(Collectors.groupingBy(Component::getName));
                addComponent(sheet, building.getRootObjectId(), allBiObjects, componentMap);

                List<Component> newComponents = componentService.selectComponentList(new Component());
                Map<String, List<Component>> newComponentMap = newComponents.stream()
                        .collect(Collectors.groupingBy(Component::getName));

                String loginUser = ShiroUtils.getLoginName();
                // 病害类型“其他”，当病害类型不存在时默认为其他 (5)
                DiseaseType otherDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-5");

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                Subject subject = ThreadContext.getSubject();
                for (int i = 3; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String component_3 = getCellValueAsString(row.getCell(3));
                    if (component_3 == null || component_3.equals("/") || component_3.equals("")) {
                        continue;
                    }
                    String component_4 = getCellValueAsString(row.getCell(4));

                    int finalI = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                        ThreadContext.bind(subject);

                        String position = getCellValueAsString(row.getCell(5));
                        String componentCode = position.split("#")[0];
                        String diseaseType = getCellValueAsString(row.getCell(6));
                        // 1处
                        String diseaseNumber = getCellValueAsString(row.getCell(7));
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

                        String length = getCellValueAsString(row.getCell(8));
                        String lengthUnits = getCellValueAsString(row.getCell(9));

                        String diseaseDescription = getCellValueAsString(row.getCell(10));
                        String repairSuggestion = getCellValueAsString(row.getCell(11));
                        String scale = getCellValueAsString(row.getCell(12));
                        String photoName = getCellValueAsString(row.getCell(13));
                        String developmentTrend = getCellValueAsString(row.getCell(14));
                        String remark = getCellValueAsString(row.getCell(15));

                        JiaotouObjectPath objectPath = resolveJiaotouObjectPath(
                                component_3, component_4, building.getRootObjectId(), allBiObjects);
                        BiObject biObject3 = objectPath.getComponent3();
                        BiObject biObject4 = objectPath.getComponent4();

                        List<Component> componentList = newComponentMap.get(componentCode + "#" + component_4);
                        BiObject finalBiObject1 = biObject4;
                        Component component = componentList.stream().filter(c -> c.getBiObjectId().equals(finalBiObject1.getId())).findFirst().orElse(null);

                        Disease disease = new Disease();
                        disease.setPosition(position);
                        disease.setCreateBy(loginUser);
                        disease.setUpdateBy(loginUser);
                        disease.setCreateTime(DateUtils.getNowDate());
                        disease.setUpdateTime(DateUtils.getNowDate());
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
                                throw new RuntimeException("第" + (finalI + 1) + "行数据的数量：" + diseaseNumber + "不规范！");
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
                                    log.error("第" + (finalI + 1) + "行数据转换长度出错length: ", length);
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
                List<Disease> diseaseList = new ArrayList<>(diseaseSet);
                diseaseMapper.batchInsertDiseases(diseaseList);
                diseaseMapper.fillLocalIdWithId(diseaseList.stream().map(Disease::getId).toList());

                List<DiseaseDetail> allDetails = diseaseSet.stream().flatMap(disease -> disease.getDiseaseDetails().stream().peek(detail -> detail.setDiseaseId(disease.getId()))).collect(Collectors.toList());
                if (!allDetails.isEmpty()) {
                    diseaseDetailMapper.insertDiseaseDetails(allDetails);
                }

                return true;
            });

        }
    }

    @Override
    public void readDroneDiseaseExcel(MultipartFile file, Long taskId) {
        importDroneDiseaseWithPhotos(file, null, taskId);
    }

    @Override
    public List<String> importDroneDiseaseWithPhotos(MultipartFile excelFile, MultipartFile photoZip, Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
        Building building = buildingMapper.selectBuildingById(task.getBuildingId());
        if (building == null || building.getRootObjectId() == null) {
            throw new ServiceException("桥梁不存在或缺少对象树");
        }

        String loginUser = ShiroUtils.getLoginName();
        List<String> errors = new ArrayList<>();
        List<Disease> diseases = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(excelFile.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headerErrors = DroneDiseaseImportParser.validateHeaders(readDroneHeaderRow(sheet));
            if (!headerErrors.isEmpty()) {
                throw new ServiceException(String.join("\n", headerErrors));
            }

            // 只拉当前桥对象树/构件，避免全表 selectComponentList（此前约 2 分钟慢 SQL）
            List<BiObject> allBiObjects = biObjectMapper.selectChildrenById(building.getRootObjectId());
            Map<String, List<Component>> componentMap = componentService
                    .selectComponentsByBiObjectIdAndChildren(building.getRootObjectId()).stream()
                    .collect(Collectors.groupingBy(Component::getName));
            Map<Long, List<DiseaseType>> diseaseTypesByTemplateObjectId = new HashMap<>();

            for (int i = DroneDiseaseImportParser.FIRST_DATA_ROW_INDEX; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isDroneDiseaseRowBlank(row)) {
                    continue;
                }
                int excelRow = i + 1;
                String component3 = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_COMPONENT3));
                String component4 = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_COMPONENT4));
                String position = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_POSITION));
                String typeName = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_TYPE));

                JiaotouObjectPath path;
                try {
                    path = resolveJiaotouObjectPath(component3, component4, building.getRootObjectId(), allBiObjects);
                } catch (ServiceException exception) {
                    errors.add("第" + excelRow + "行：" + exception.getMessage());
                    continue;
                }
                if (isBlankTemplateCell(position) || !position.contains("#")) {
                    errors.add("第" + excelRow + "行：缺损位置要带#，#前是构件编号");
                    continue;
                }
                String componentCode = position.split("#", 2)[0].trim();
                if (componentCode.isEmpty()) {
                    errors.add("第" + excelRow + "行：缺损位置缺少构件编号");
                    continue;
                }

                DiseaseType diseaseType = resolveDroneDiseaseType(path, typeName, excelRow, errors, diseaseTypesByTemplateObjectId);
                if (diseaseType == null) {
                    continue;
                }

                Component component = findOrCreateDroneComponent(
                        componentMap, componentCode, component4, path.getComponent4(), loginUser);

                DroneDiseaseImportParser.Input input = new DroneDiseaseImportParser.Input();
                input.rowNumber = excelRow;
                input.diseaseType = diseaseType;
                input.participate = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_PARTICIPATE));
                input.quantity = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_QUANTITY));
                input.units = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_UNITS));
                input.level = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_LEVEL));
                input.crackFeature = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_CRACK));
                input.length = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_LENGTH));
                input.crackWidth = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_CRACK_WIDTH));
                input.heightDepth = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_HEIGHT));
                input.areaIdentifier = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_AREA_IDENTIFIER));
                input.areaLength = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_AREA_LENGTH));
                input.areaWidth = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_AREA_WIDTH));
                input.deformation = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_DEFORMATION));
                input.angle = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_ANGLE));
                input.ratio = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_RATIO));
                input.reference1Name = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REFERENCE1_NAME));
                input.reference1Start = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REFERENCE1_START));
                input.reference1End = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REFERENCE1_END));
                input.reference2Name = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REFERENCE2_NAME));
                input.reference2Start = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REFERENCE2_START));
                input.reference2End = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REFERENCE2_END));
                input.developmentTrend = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_TREND));
                input.nature = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_NATURE));
                input.description = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_DESCRIPTION));
                input.photo = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_PHOTO));
                input.remark = getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_REMARK));

                DroneDiseaseImportParser.Output output = DroneDiseaseImportParser.parse(input);
                if (!output.errors.isEmpty()) {
                    errors.addAll(output.errors);
                    continue;
                }

                Disease disease = new Disease();
                disease.setPosition(position);
                disease.setCreateBy(loginUser);
                disease.setUpdateBy(loginUser);
                disease.setCreateTime(DateUtils.getNowDate());
                disease.setUpdateTime(DateUtils.getNowDate());
                disease.setParticipateAssess(output.participateAssess);
                disease.setLevel(output.level);
                disease.setQuantity(output.quantity);
                disease.setUnits(output.units);
                disease.setCrackType(output.crackType);
                disease.setType(diseaseType.getName());
                disease.setDiseaseTypeId(diseaseType.getId());
                disease.setComponentId(component.getId());
                disease.setBuildingId(building.getId());
                disease.setProjectId(task.getProjectId());
                disease.setBiObjectName(path.getComponent4().getName());
                disease.setBiObjectId(path.getComponent4().getId());
                disease.setDescription(output.description);
                disease.setTaskId(taskId);
                disease.setNature(output.nature);
                disease.setDevelopmentTrend(output.developmentTrend);
                disease.setRemark(output.remark);
                disease.setImgNoExp(convertToDbFormat(splitPhotoName(output.photo)));
                disease.setDiseaseDetails(List.of(output.detail));
                diseases.add(disease);
            }
        } catch (IOException e) {
            log.error("读取无人机病害Excel失败", e);
            throw new ServiceException("读取Excel失败：" + e.getMessage());
        }

        if (!errors.isEmpty()) {
            int shown = Math.min(errors.size(), 30);
            String message = "无人机病害导入失败，共" + errors.size() + "处错误（已标出行号）：\n"
                    + String.join("\n", errors.subList(0, shown));
            if (errors.size() > shown) {
                message = message + "\n其余" + (errors.size() - shown) + "处未列出";
            }
            throw new ServiceException(message);
        }
        if (diseases.isEmpty()) {
            throw new ServiceException("没有可导入的病害");
        }

        Map<String, MultipartFile> photosByCode = Collections.emptyMap();
        if (photoZip != null && !photoZip.isEmpty()) {
            photosByCode = loadDronePhotosFromZip(photoZip);
        }

        Map<String, MultipartFile> finalPhotosByCode = photosByCode;
        List<String> photoHints = new ArrayList<>();
        transactionTemplate.execute(status -> {
            diseaseMapper.batchInsertDiseases(diseases);
            diseaseMapper.fillLocalIdWithId(diseases.stream().map(Disease::getId).toList());
            List<DiseaseDetail> details = diseases.stream()
                    .flatMap(disease -> disease.getDiseaseDetails().stream()
                            .peek(detail -> detail.setDiseaseId(disease.getId())))
                    .collect(Collectors.toList());
            if (!details.isEmpty()) {
                diseaseDetailMapper.insertDiseaseDetails(details);
            }

            if (!finalPhotosByCode.isEmpty()) {
                photoHints.addAll(attachDronePhotosInBatch(diseases, finalPhotosByCode));
            }
            return true;
        });
        return photoHints;
    }

    /**
     * 按本批 Excel 照片编号挂 ZIP 内图片（与 App 同批匹配，不依赖库内编号唯一）。
     */
    private List<String> attachDronePhotosInBatch(List<Disease> diseases, Map<String, MultipartFile> photosByCode) {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> usedCodes = new HashSet<>();
        List<String> hints = new ArrayList<>();

        for (Disease disease : diseases) {
            List<String> codes = parseDronePhotoCodes(disease.getImgNoExp(), mapper);
            if (codes.isEmpty()) {
                continue;
            }
            List<MultipartFile> matched = new ArrayList<>();
            for (String code : codes) {
                MultipartFile photo = photosByCode.get(code);
                if (photo != null) {
                    matched.add(photo);
                    usedCodes.add(code);
                } else {
                    hints.add("照片编号 " + code + " 在 ZIP 中不存在");
                }
            }
            if (matched.isEmpty()) {
                continue;
            }
            diseaseService.handleDiseaseAttachment(matched.toArray(new MultipartFile[0]), disease.getId(), 1);
            disease.setAttachmentCount(matched.size());
            diseaseMapper.updateDisease(disease);
        }

        for (Map.Entry<String, MultipartFile> entry : photosByCode.entrySet()) {
            if (!usedCodes.contains(entry.getKey())) {
                String name = entry.getValue().getOriginalFilename();
                hints.add((name != null ? name : entry.getKey()) + " 未在 Excel 中引用");
            }
        }
        return hints;
    }

    private List<String> parseDronePhotoCodes(String imgNoExp, ObjectMapper mapper) {
        if (imgNoExp == null || imgNoExp.trim().isEmpty() || "[]".equals(imgNoExp.trim())) {
            return Collections.emptyList();
        }
        try {
            List<String> imgs = mapper.readValue(imgNoExp, new TypeReference<List<String>>() {
            });
            return imgs.stream()
                    .filter(img -> img != null && !img.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private Map<String, MultipartFile> loadDronePhotosFromZip(MultipartFile zipFile) {
        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ServiceException("请上传ZIP格式的照片压缩包");
        }
        Map<String, MultipartFile> photosByCode = new LinkedHashMap<>();
        List<String> duplicateNames = new ArrayList<>();
        try {
            readDronePhotoZipEntries(zipFile, ZIP_UTF8_CHARSET, photosByCode, duplicateNames);
        } catch (IllegalArgumentException | IOException utf8Error) {
            photosByCode.clear();
            duplicateNames.clear();
            try {
                readDronePhotoZipEntries(zipFile, ZIP_GBK_CHARSET, photosByCode, duplicateNames);
            } catch (IOException gbkError) {
                throw new ServiceException("读取照片压缩包失败：" + gbkError.getMessage());
            }
        }
        if (photosByCode.isEmpty()) {
            throw new ServiceException("压缩包内没有可识别的照片文件（支持 jpg/jpeg/png）");
        }
        return photosByCode;
    }

    private void readDronePhotoZipEntries(MultipartFile zipFile,
                                          Charset charset,
                                          Map<String, MultipartFile> photosByCode,
                                          List<String> duplicateNames) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(zipFile.getInputStream(), charset)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace('\\', '/');
                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (fileName.startsWith(".") || fileName.isEmpty()) {
                    continue;
                }
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
                    continue;
                }

                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex <= 0) {
                    continue;
                }
                String code = fileName.substring(0, dotIndex).trim();
                if (code.isEmpty()) {
                    continue;
                }

                byte[] bytes = zipIn.readAllBytes();
                MockMultipartFile photo = new MockMultipartFile(
                        "photos",
                        fileName,
                        lower.endsWith(".png") ? "image/png" : "image/jpeg",
                        bytes);
                if (photosByCode.containsKey(code)) {
                    duplicateNames.add(fileName);
                } else {
                    photosByCode.put(code, photo);
                }
            }
        }
    }

    private List<String> readDroneHeaderRow(Sheet sheet) {
        Row header = sheet.getRow(DroneDiseaseImportParser.HEADER_ROW_INDEX);
        List<String> headers = new ArrayList<>();
        if (header == null) {
            return headers;
        }
        for (int i = 0; i < DroneDiseaseImportParser.EXPECTED_HEADERS.length; i++) {
            headers.add(getCellValueAsString(header.getCell(i)));
        }
        return headers;
    }

    private boolean isDroneDiseaseRowBlank(Row row) {
        return isBlankTemplateCell(getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_COMPONENT3)))
                && isBlankTemplateCell(getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_COMPONENT4)))
                && isBlankTemplateCell(getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_POSITION)))
                && isBlankTemplateCell(getCellValueAsString(row.getCell(DroneDiseaseImportParser.COL_TYPE)));
    }

    private DiseaseType resolveDroneDiseaseType(JiaotouObjectPath path,
                                                String typeName,
                                                int excelRow,
                                                List<String> errors,
                                                Map<Long, List<DiseaseType>> diseaseTypesByTemplateObjectId) {
        if (isBlankTemplateCell(typeName)) {
            errors.add("第" + excelRow + "行：缺损类型不能为空");
            return null;
        }
        Map<Long, DiseaseType> unique = new LinkedHashMap<>();
        for (DiseaseType diseaseType : loadDroneDiseaseTypes(path.getComponent3().getId(), diseaseTypesByTemplateObjectId)) {
            if (diseaseType.getId() != null) {
                unique.putIfAbsent(diseaseType.getId(), diseaseType);
            }
        }
        for (DiseaseType diseaseType : loadDroneDiseaseTypes(path.getComponent4().getId(), diseaseTypesByTemplateObjectId)) {
            if (diseaseType.getId() != null) {
                unique.putIfAbsent(diseaseType.getId(), diseaseType);
            }
        }
        List<DiseaseType> matched = unique.values().stream()
                .filter(diseaseType -> typeName.equals(diseaseType.getName()))
                .toList();
        if (matched.isEmpty()) {
            errors.add("第" + excelRow + "行：缺损类型“" + typeName + "”不在该构件的病害类型中");
            return null;
        }
        if (matched.size() > 1) {
            String codes = matched.stream().map(DiseaseType::getCode).collect(Collectors.joining("、"));
            errors.add("第" + excelRow + "行：该构件上有多个同名病害类型（" + codes + "），无法确定");
            return null;
        }
        return matched.get(0);
    }

    private List<DiseaseType> loadDroneDiseaseTypes(Long templateObjectId,
                                                    Map<Long, List<DiseaseType>> diseaseTypesByTemplateObjectId) {
        if (templateObjectId == null) {
            return Collections.emptyList();
        }
        return diseaseTypesByTemplateObjectId.computeIfAbsent(templateObjectId,
                id -> diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(id));
    }

    private Component findOrCreateDroneComponent(Map<String, List<Component>> componentMap,
                                                 String componentCode,
                                                 String component4Name,
                                                 BiObject biObject4,
                                                 String loginUser) {
        String componentName = componentCode + "#" + component4Name;
        List<Component> named = componentMap.get(componentName);
        if (named != null) {
            Component existing = named.stream()
                    .filter(item -> Objects.equals(biObject4.getId(), item.getBiObjectId()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        Component component = new Component();
        component.setCode(componentCode);
        component.setName(componentName);
        component.setBiObjectId(biObject4.getId());
        component.setStatus("0");
        component.setCreateBy(loginUser);
        component.setUpdateBy(loginUser);
        component.setCreateTime(DateUtils.getNowDate());
        component.setUpdateTime(DateUtils.getNowDate());
        componentService.insertComponent(component);
        componentMap.computeIfAbsent(componentName, key -> new ArrayList<>()).add(component);
        return component;
    }

    // 定义支持的分隔符
    private static final String SEPARATOR_DOT = "、";          // 中文顿号
    private static final String SEPARATOR_TILDE = "~";        // 波浪线
    private static final String SEPARATOR_SLASH = "/";        // 斜杠（新增）

    /**
     * 将photoName分割为字符串列表
     *
     * @param photoName 原始字符串（如"1982、1983"或"1982~1985"或"1982/1983）
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
        // 2. 判断是否包含斜杠分隔符（新增）
        else if (trimmedPhotoName.contains(SEPARATOR_SLASH)) {
            String[] parts = trimmedPhotoName.split(SEPARATOR_SLASH);
            result.addAll(Arrays.asList(parts));
        }
        // 3. 再判断是否包含波浪线分隔符
        else if (trimmedPhotoName.contains(SEPARATOR_TILDE)) {
            String[] parts = trimmedPhotoName.split(SEPARATOR_TILDE);
            for (int i = Integer.valueOf(parts[0]); i <= Integer.valueOf(parts[1]); i++) {
                result.add(String.valueOf(i));
            }
        }
        // 4. 若没有分隔符（单个值），直接添加到列表
        else {
            result.add(trimmedPhotoName);
        }

        // 过滤空元素（避免分割后出现空字符串，如"1982、 、1983"）
        result.removeIf(part -> part == null || part.trim().isEmpty());
        return result;
    }

    /**
     * 将分割后的列表转为数据库支持的格式（示例：转为JSON字符串，适配MySQL的JSON类型）
     *
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

    void validateJiaotouComponentLevels(Sheet sheet, Long rootObjectId, List<BiObject> allBiObjects) {
        List<String> errors = new ArrayList<>();

        for (int i = 3; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String component3 = getCellValueAsString(row.getCell(3));
            String component4 = getCellValueAsString(row.getCell(4));
            boolean component3Blank = isBlankTemplateCell(component3);
            boolean component4Blank = isBlankTemplateCell(component4);

            if (component3Blank && component4Blank) {
                continue;
            }
            if (component3Blank) {
                errors.add("第" + (i + 1) + "行：构件不能为空");
                continue;
            }
            if (component4Blank) {
                errors.add("第" + (i + 1) + "行：构件2不能为空");
                continue;
            }

            try {
                resolveJiaotouObjectPath(component3, component4, rootObjectId, allBiObjects);
            } catch (ServiceException e) {
                errors.add("第" + (i + 1) + "行：" + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new ServiceException("交投病害模板构件层级校验失败，共发现" + errors.size()
                    + "处问题：\n- " + String.join("\n- ", errors));
        }
    }

    private boolean isBlankTemplateCell(String value) {
        return value == null || value.isEmpty() || "/".equals(value);
    }

    private JiaotouObjectPath resolveJiaotouObjectPath(String component3Name,
                                                        String component4Name,
                                                        Long rootObjectId,
                                                        List<BiObject> allBiObjects) {
        Map<Long, BiObject> objectsById = allBiObjects.stream()
                .filter(object -> object.getId() != null)
                .collect(Collectors.toMap(BiObject::getId, object -> object, (left, right) -> left));

        List<BiObject> component3Candidates = allBiObjects.stream()
                .filter(object -> component3Name.equals(object.getName()))
                .filter(object -> isThirdLevelObject(object, rootObjectId, objectsById))
                .collect(Collectors.toList());

        if (component3Candidates.isEmpty()) {
            throw new ServiceException("构件“" + component3Name + "”不是当前桥梁对象树中的第三级部件");
        }

        List<JiaotouObjectPath> paths = new ArrayList<>();
        for (BiObject component3 : component3Candidates) {
            allBiObjects.stream()
                    .filter(object -> Objects.equals(object.getParentId(), component3.getId()))
                    .filter(object -> component4Name.equals(object.getName()))
                    .map(component4 -> new JiaotouObjectPath(component3, component4))
                    .forEach(paths::add);
        }

        if (paths.isEmpty()) {
            throw new ServiceException("构件2“" + component4Name + "”不是构件“"
                    + component3Name + "”的直接子部件");
        }
        if (paths.size() > 1) {
            throw new ServiceException("构件“" + component3Name + "”与构件2“"
                    + component4Name + "”在当前桥梁对象树中存在多个匹配关系");
        }
        return paths.get(0);
    }

    private boolean isThirdLevelObject(BiObject object, Long rootObjectId, Map<Long, BiObject> objectsById) {
        BiObject parent = objectsById.get(object.getParentId());
        return parent != null && Objects.equals(parent.getParentId(), rootObjectId);
    }

    private void addComponent(Sheet sheet, Long rootObjectId, List<BiObject> allBiObjects, Map<String, List<Component>> componentMap) {
        Set<Component> componentSet = new HashSet<>();

        for (int i = 3; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String component_3 = getCellValueAsString(row.getCell(3));
            if (component_3 == null || component_3.equals("/") || component_3.equals("")) {
                continue;
            }

            String position = getCellValueAsString(row.getCell(5));
            String[] splitPosition = position.split("#");
            if (splitPosition.length == 1) {
                throw new RuntimeException("第" + (i + 1) + "行数据，病害位置格式不正确：缺失 '# ' 符号");
            }
            String componentCode = splitPosition[0];
            String component_4 = getCellValueAsString(row.getCell(4));
            if (component_4 == null || component_4.equals("/") || component_4.equals("")) {
                throw new RuntimeException("第" + (i + 1) + "行数据，构件2出现错误或为空");
            }


            JiaotouObjectPath objectPath = resolveJiaotouObjectPath(
                    component_3, component_4, rootObjectId, allBiObjects);
            BiObject biObject4 = objectPath.getComponent4();

            // 新增部件
            Component component = new Component();
            component.setCode(componentCode);
            component.setName(componentCode + "#" + component_4);
            component.setCreateBy(ShiroUtils.getLoginName());
            component.setUpdateBy(ShiroUtils.getLoginName());
            component.setCreateTime(DateUtils.getNowDate());
            component.setUpdateTime(DateUtils.getNowDate());

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

    private static class JiaotouObjectPath {
        private final BiObject component3;
        private final BiObject component4;

        private JiaotouObjectPath(BiObject component3, BiObject component4) {
            this.component3 = component3;
            this.component4 = component4;
        }

        private BiObject getComponent3() {
            return component3;
        }

        private BiObject getComponent4() {
            return component4;
        }
    }

    @Override
    @Transactional
    public List<String> uploadPictures(List<MultipartFile> photos, Long taskId) {
        Map<String, Long> imgToDiseaseMap = buildUniquePhotoCodeToDiseaseMap(taskId);
        List<String> unmatchedPhotos = new ArrayList<>();
        Map<Long, List<MultipartFile>> group = new HashMap<>();

        photos.forEach(photo -> {
            String photoName = photo.getOriginalFilename();
            if (photoName == null) {
                unmatchedPhotos.add("null");
                return;
            }

            int dotIndex = photoName.lastIndexOf('.');
            if (dotIndex <= 4) {
                unmatchedPhotos.add(photoName);
                return;
            }

            int lastUnderlineIndex = photoName.lastIndexOf("_");
            if (lastUnderlineIndex == -1 || lastUnderlineIndex >= dotIndex - 1) {
                unmatchedPhotos.add(photoName);
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

        attachMatchedDiseasePhotos(group);
        return unmatchedPhotos;
    }

    @Override
    @Transactional
    public List<String> uploadPicturesFromZip(MultipartFile zipFile, Long taskId) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new ServiceException("请上传包含病害照片的ZIP压缩包");
        }
        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ServiceException("请上传ZIP格式的照片压缩包");
        }

        Map<String, Long> imgToDiseaseMap = buildUniquePhotoCodeToDiseaseMap(taskId);
        List<String> unmatchedPhotos = new ArrayList<>();
        Map<Long, List<MultipartFile>> group = new HashMap<>();

        try {
            extractDronePhotoZip(zipFile, ZIP_UTF8_CHARSET, imgToDiseaseMap, group, unmatchedPhotos);
        } catch (IllegalArgumentException | IOException utf8Error) {
            unmatchedPhotos.clear();
            group.clear();
            try {
                extractDronePhotoZip(zipFile, ZIP_GBK_CHARSET, imgToDiseaseMap, group, unmatchedPhotos);
            } catch (IOException gbkError) {
                throw new ServiceException("读取照片压缩包失败：" + gbkError.getMessage());
            }
        }

        if (group.isEmpty() && unmatchedPhotos.isEmpty()) {
            throw new ServiceException("压缩包内没有可识别的照片文件（支持 jpg/jpeg/png）");
        }

        attachMatchedDiseasePhotos(group);
        return unmatchedPhotos;
    }

    private Map<String, Long> buildUniquePhotoCodeToDiseaseMap(Long taskId) {
        Task task = taskService.selectTaskById(taskId);
        Disease disease = new Disease();
        disease.setBuildingId(task.getBuildingId());
        disease.setProjectId(task.getProjectId());
        List<Disease> diseases = diseaseMapper.selectDiseaseList(disease);

        ObjectMapper mapper = new ObjectMapper();
        List<Map.Entry<String, Long>> allEntries = diseases.stream()
                .filter(dd -> dd.getImgNoExp() != null && !dd.getImgNoExp().trim().isEmpty())
                .flatMap(d -> {
                    try {
                        List<String> imgs = mapper.readValue(d.getImgNoExp(), new TypeReference<List<String>>() {
                        });
                        return imgs.stream()
                                .filter(img -> img != null && !img.trim().isEmpty())
                                .map(img -> new AbstractMap.SimpleEntry<>(img.trim(), d.getId()));
                    } catch (JsonProcessingException e) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        Map<String, Long> imgCountMap = allEntries.stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.counting()));

        return allEntries.stream()
                .filter(e -> imgCountMap.get(e.getKey()) == 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void attachMatchedDiseasePhotos(Map<Long, List<MultipartFile>> group) {
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
    }

    private void extractDronePhotoZip(MultipartFile zipFile,
                                      Charset charset,
                                      Map<String, Long> imgToDiseaseMap,
                                      Map<Long, List<MultipartFile>> group,
                                      List<String> unmatchedPhotos) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(zipFile.getInputStream(), charset)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace('\\', '/');
                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (fileName.startsWith(".") || fileName.isEmpty()) {
                    continue;
                }
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
                    continue;
                }

                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex <= 0) {
                    unmatchedPhotos.add(fileName);
                    continue;
                }
                String code = fileName.substring(0, dotIndex).trim();
                if (code.isEmpty()) {
                    unmatchedPhotos.add(fileName);
                    continue;
                }

                byte[] bytes = zipIn.readAllBytes();
                MockMultipartFile photo = new MockMultipartFile(
                        "photos",
                        fileName,
                        lower.endsWith(".png") ? "image/png" : "image/jpeg",
                        bytes);

                Long diseaseId = imgToDiseaseMap.get(code);
                if (diseaseId != null) {
                    group.computeIfAbsent(diseaseId, key -> new ArrayList<>()).add(photo);
                } else {
                    unmatchedPhotos.add(fileName);
                }
            }
        }
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
    public int ReadBuildingFile(MultipartFile file, Long projectId) {
        List<Long> buildingList = new ArrayList<>();
        int importCount = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j);

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String buildingName = getCellValueAsString(row.getCell(0));
                    String type = getCellValueAsString(row.getCell(1));
                    String fatherBuilding = getCellValueAsString(row.getCell(2));
                    String area = getCellValueAsString(row.getCell(3));
                    String line = getCellValueAsString(row.getCell(4));
                    String template = getCellValueAsString(row.getCell(5));
                    int excelRowNum = i + 1;

                    if (StringUtils.isEmpty(buildingName) && StringUtils.isEmpty(type)
                            && StringUtils.isEmpty(fatherBuilding) && StringUtils.isEmpty(area)
                            && StringUtils.isEmpty(line) && StringUtils.isEmpty(template)) {
                        continue;
                    }

                    if (!"组合桥".equals(type) && !"桥幅".equals(type)) {
                        throw new RuntimeException("第" + excelRowNum + "行桥梁类型不支持：" + type
                                + "，桥梁：" + buildingName + "，仅支持：组合桥、桥幅");
                    }
                    if (StringUtils.isEmpty(area)) {
                        throw new RuntimeException("第" + excelRowNum + "行片区不能为空，桥梁：" + buildingName);
                    }
                    if (StringUtils.isEmpty(line)) {
                        throw new RuntimeException("第" + excelRowNum + "行线路不能为空，桥梁：" + buildingName);
                    }

                    Building building = new Building();

                    building.setName(buildingName);
                    SysDictData sysDictData = new SysDictData();
                    sysDictData.setDictType("bi_building_area");
                    sysDictData.setDictLabel(area);
                    List<SysDictData> areaCode = sysDictDataService.selectDictDataList(sysDictData);
                    if (areaCode == null || CollUtil.isEmpty(areaCode)) {
                        throw new RuntimeException("请检查字典数据: " + area);
                    }
                    building.setArea(String.valueOf(areaCode.get(0).getDictValue()));

                    sysDictData.setDictType("bi_buildeing_line");
                    sysDictData.setDictLabel(line);
                    List<SysDictData> lineCode = sysDictDataService.selectDictDataList(sysDictData);
                    if (lineCode == null || CollUtil.isEmpty(lineCode)) {
                        throw new RuntimeException("请检查字典数据: " + line);
                    }
                    building.setLine(String.valueOf(lineCode.get(0).getDictValue()));

                    List<Building> buildings = buildingMapper.selectBuildingList(building);
                    if (buildings == null || buildings.size() == 0) {
                        if (type.equals("组合桥")) {
                            building.setStatus("0");
                            building.setIsLeaf("0");
                        } else if (type.equals("桥幅")) {
                            Long templateId = BRIDGE_TYPE_MAP.get(template);
                            if (templateId == null) {
                                throw new RuntimeException("第" + excelRowNum + "行桥幅模板未匹配：" + template
                                        + "，桥梁：" + buildingName);
                            }
                            building.setStatus("0");
                            building.setIsLeaf("1");
                            building.setTemplateId(templateId);
                            if (StringUtils.isNotEmpty(fatherBuilding)
                                    && !fatherBuilding.equals(buildingName)) {
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

                        try {
                            buildingService.insertBuilding(building);
                        } catch (RuntimeException e) {
                            if ("该片区线路桥梁已存在".equals(e.getMessage())) {
                                throw new RuntimeException("第" + (i + 1) + "行桥梁已存在：" + buildingName
                                        + "，片区：" + area + "，线路：" + line);
                            }
                            throw e;
                        }

                        if (type.equals("桥幅")) {
                            buildingList.add(building.getId());
                        }
                        importCount++;

                    } else {
                        if (type.equals("桥幅")) {
                            building = buildings.get(0);
                            if (StringUtils.isNotEmpty(fatherBuilding)
                                    && !fatherBuilding.equals(buildingName)) {
                                Building parent = new Building();
                                parent.setName(fatherBuilding);
                                parent.setIsLeaf("0");
                                parent.setArea(String.valueOf(areaCode.get(0).getDictValue()));
                                parent.setLine(String.valueOf(lineCode.get(0).getDictValue()));
                                List<Building> parentBuildings = buildingMapper.selectBuildingList(parent);
                                if (parentBuildings != null && parentBuildings.size() > 0) {
                                    building.setParentId(parentBuildings.get(0).getId());
                                    try {
                                        buildingService.updateBuilding(building);
                                    } catch (RuntimeException e) {
                                        if ("该片区线路桥梁已存在".equals(e.getMessage())) {
                                            throw new RuntimeException("第" + (i + 1) + "行桥梁已存在：" + buildingName
                                                    + "，片区：" + area + "，线路：" + line);
                                        }
                                        throw e;
                                    }
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
        return importCount;
    }

    @Override
    @Transactional
    public int resumeBuildingFile(MultipartFile file) {
        int resumeCount = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j);

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        continue;
                    }

                    String buildingName = getCellValueAsString(row.getCell(0));
                    String type = getCellValueAsString(row.getCell(1));
                    String fatherBuilding = getCellValueAsString(row.getCell(2));
                    String area = getCellValueAsString(row.getCell(3));
                    String line = getCellValueAsString(row.getCell(4));
                    String template = getCellValueAsString(row.getCell(5));
                    int excelRowNum = i + 1;

                    if (StringUtils.isEmpty(buildingName) && StringUtils.isEmpty(type)
                            && StringUtils.isEmpty(fatherBuilding) && StringUtils.isEmpty(area)
                            && StringUtils.isEmpty(line) && StringUtils.isEmpty(template)) {
                        continue;
                    }

                    if (!"组合桥".equals(type) && !"桥幅".equals(type)) {
                        throw new RuntimeException("第" + excelRowNum + "行桥梁类型不支持：" + type
                                + "，桥梁：" + buildingName + "，仅支持：组合桥、桥幅");
                    }

                    SysDictData areaQuery = new SysDictData();
                    areaQuery.setDictType("bi_building_area");
                    areaQuery.setDictLabel(area);
                    List<SysDictData> areaCode = sysDictDataService.selectDictDataList(areaQuery);
                    if (areaCode == null || CollUtil.isEmpty(areaCode)) {
                        throw new RuntimeException("第" + excelRowNum + "行片区字典未匹配：" + area
                                + "，桥梁：" + buildingName);
                    }

                    SysDictData lineQuery = new SysDictData();
                    lineQuery.setDictType("bi_buildeing_line");
                    lineQuery.setDictLabel(line);
                    List<SysDictData> lineCode = sysDictDataService.selectDictDataList(lineQuery);
                    if (lineCode == null || CollUtil.isEmpty(lineCode)) {
                        throw new RuntimeException("第" + excelRowNum + "行线路字典未匹配：" + line
                                + "，桥梁：" + buildingName);
                    }
                    String areaValue = String.valueOf(areaCode.get(0).getDictValue());
                    String lineValue = String.valueOf(lineCode.get(0).getDictValue());

                    if ("组合桥".equals(type)) {
                        Building query = new Building();
                        query.setName(buildingName);
                        query.setArea(areaValue);
                        query.setLine(lineValue);
                        List<Building> matchedBuildings = buildingMapper.selectBuildingExactList(query);
                        if (CollUtil.isEmpty(matchedBuildings)) {
                            Building building = new Building();
                            building.setName(buildingName);
                            building.setStatus("0");
                            building.setIsLeaf("0");
                            building.setArea(areaValue);
                            building.setLine(lineValue);
                            resumeCount += buildingService.insertBuilding(building);
                            continue;
                        }
                        if (matchedBuildings.size() > 1) {
                            throw new RuntimeException("第" + excelRowNum + "行匹配到多座桥梁，无法自动修复为组合桥：" + buildingName
                                    + "，片区：" + area + "，线路：" + line);
                        }

                        Building building = matchedBuildings.get(0);
                        if (StringUtils.isNotEmpty(fatherBuilding) && !fatherBuilding.equals(buildingName)) {
                            Building parentQuery = new Building();
                            parentQuery.setName(fatherBuilding);
                            parentQuery.setIsLeaf("0");
                            parentQuery.setArea(areaValue);
                            parentQuery.setLine(lineValue);
                            List<Building> parentBuildings = buildingMapper.selectBuildingList(parentQuery).stream()
                                    .filter(item -> fatherBuilding.equals(item.getName()))
                                    .collect(Collectors.toList());
                            if (CollUtil.isEmpty(parentBuildings)) {
                                throw new RuntimeException("第" + excelRowNum + "行未找到父桥：" + fatherBuilding
                                        + "，组合桥：" + buildingName);
                            }
                            if (parentBuildings.size() > 1) {
                                throw new RuntimeException("第" + excelRowNum + "行匹配到多个父桥：" + fatherBuilding
                                        + "，组合桥：" + buildingName);
                            }
                            building.setParentId(parentBuildings.get(0).getId());
                        } else {
                            building.setParentId(null);
                        }

                        resumeCount += buildingService.repairCombinationBridgeRoot(building);
                        continue;
                    }

                    Long templateId = BRIDGE_TYPE_MAP.get(template);
                    if (templateId == null) {
                        throw new RuntimeException("第" + excelRowNum + "行桥幅模板未匹配：" + template
                                + "，桥梁：" + buildingName);
                    }

                    Building query = new Building();
                    query.setName(buildingName);
                    query.setArea(areaValue);
                    query.setLine(lineValue);
                    List<Building> matchedBuildings = buildingMapper.selectBuildingExactList(query).stream()
                            .filter(item -> "1".equals(item.getIsLeaf()))
                            .collect(Collectors.toList());
                    if (CollUtil.isEmpty(matchedBuildings)) {
                        Building building = new Building();
                        building.setName(buildingName);
                        building.setStatus("0");
                        building.setIsLeaf("1");
                        building.setArea(areaValue);
                        building.setLine(lineValue);
                        building.setTemplateId(templateId);
                        if (StringUtils.isNotEmpty(fatherBuilding) && !fatherBuilding.equals(buildingName)) {
                            Building parentQuery = new Building();
                            parentQuery.setName(fatherBuilding);
                            parentQuery.setIsLeaf("0");
                            parentQuery.setArea(areaValue);
                            parentQuery.setLine(lineValue);
                            List<Building> parentBuildings = buildingMapper.selectBuildingList(parentQuery);
                            if (parentBuildings != null && !parentBuildings.isEmpty()) {
                                building.setParentId(parentBuildings.get(0).getId());
                            }
                        }
                        resumeCount += buildingService.insertBuilding(building);
                        continue;
                    }
                    if (matchedBuildings.size() > 1) {
                        throw new RuntimeException("第" + excelRowNum + "行匹配到多座桥幅，无法自动修复：" + buildingName
                                + "，片区：" + area + "，线路：" + line);
                    }

                    Building building = matchedBuildings.get(0);
                    if (building.getRootObjectId() != null) {
                        continue;
                    }

                    if (StringUtils.isNotEmpty(fatherBuilding) && !fatherBuilding.equals(buildingName)) {
                        Building parentQuery = new Building();
                        parentQuery.setName(fatherBuilding);
                        parentQuery.setIsLeaf("0");
                        parentQuery.setArea(areaValue);
                        parentQuery.setLine(lineValue);
                        List<Building> parentBuildings = buildingMapper.selectBuildingList(parentQuery).stream()
                                .filter(item -> fatherBuilding.equals(item.getName()))
                                .collect(Collectors.toList());
                        if (CollUtil.isEmpty(parentBuildings)) {
                            throw new RuntimeException("第" + excelRowNum + "行未找到父桥：" + fatherBuilding
                                    + "，桥幅：" + buildingName);
                        }
                        if (parentBuildings.size() > 1) {
                            throw new RuntimeException("第" + excelRowNum + "行匹配到多个父桥：" + fatherBuilding
                                    + "，桥幅：" + buildingName);
                        }
                        building.setParentId(parentBuildings.get(0).getId());
                    } else {
                        building.setParentId(null);
                    }

                    building.setTemplateId(templateId);
                    resumeCount += buildingService.repairBridgeSpanObjectTree(building);
                }
            }
        } catch (IOException e) {
            log.error("读取桥梁修复文件时出错", e);
            throw new RuntimeException(e);
        }

        return resumeCount;
    }

    @Override
    public BatchBridgeCardImportResult batchImportBridgeCards(MultipartFile file, Long projectId) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename) || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ServiceException("请上传zip格式的桥梁卡片压缩包");
        }

        try {
            return batchImportBridgeCards(file, projectId, ZIP_UTF8_CHARSET);
        } catch (IllegalArgumentException e) {
            if (!isZipCharsetMalformed(e)) {
                throw e;
            }
            return batchImportBridgeCards(file, projectId, ZIP_GBK_CHARSET);
        }
    }

    private BatchBridgeCardImportResult batchImportBridgeCards(MultipartFile file, Long projectId, Charset charset) {
        BatchBridgeCardImportResult result = new BatchBridgeCardImportResult();
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), charset)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                String fileName = getZipEntryFileName(entry.getName());
                if (!isWordFile(fileName)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                byte[] wordBytes = readZipEntry(zipInputStream);
                log.info("开始批量导入桥梁卡片Word，fileName={}", fileName);
                importSingleBridgeCard(fileName, wordBytes, projectId, result);
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new ServiceException("读取桥梁卡片压缩包失败：" + e.getMessage());
        }
        return result;
    }

    private boolean isZipCharsetMalformed(IllegalArgumentException e) {
        return e.getCause() instanceof java.nio.charset.MalformedInputException
                || (e.getMessage() != null && e.getMessage().contains("malformed input"));
    }

    private void importSingleBridgeCard(String fileName, byte[] wordBytes, Long projectId, BatchBridgeCardImportResult result) {
        long startTime = System.currentTimeMillis();
        String bridgeName = extractBridgeNameFromCardFileName(fileName);
        if (StringUtils.isEmpty(bridgeName)) {
            result.addFailure(fileName, bridgeName, "无法从文件名解析桥梁名称");
            log.warn("批量导入桥梁卡片失败，fileName={}, reason=无法从文件名解析桥梁名称", fileName);
            return;
        }

        Building building = resolveBridgeCardBuilding(bridgeName, projectId);
        if (building == null) {
            result.addFailure(fileName, bridgeName, "未找到桥梁：" + bridgeName);
            log.warn("批量导入桥梁卡片失败，fileName={}, bridgeName={}, projectId={}, reason=未找到唯一桥梁",
                    fileName, bridgeName, projectId);
            return;
        }
        if (building.getRootPropertyId() != null) {
            result.addSkipped(fileName, bridgeName, building.getId(), "已存在桥梁卡片");
            log.info("跳过批量导入桥梁卡片，fileName={}, bridgeName={}, buildingId={}, rootPropertyId={}, reason=已存在桥梁卡片",
                    fileName, bridgeName, building.getId(), building.getRootPropertyId());
            return;
        }

        try {
            log.info("批量导入桥梁卡片匹配成功，fileName={}, bridgeName={}, buildingId={}",
                    fileName, bridgeName, building.getId());
            MultipartFile wordFile = new MockMultipartFile(
                    "file",
                    fileName,
                    getWordContentType(fileName),
                    wordBytes);
            Property property = new Property();
            Boolean imported = propertyService.readWordFile(wordFile, property, building.getId());
            if (imported == null || !imported) {
                result.addFailure(fileName, bridgeName, "桥梁卡片导入失败");
                log.warn("批量导入桥梁卡片失败，fileName={}, bridgeName={}, buildingId={}, cost={}ms, reason=readWordFile返回失败",
                        fileName, bridgeName, building.getId(), System.currentTimeMillis() - startTime);
                return;
            }
            result.addSuccess();
            log.info("批量导入桥梁卡片成功，fileName={}, bridgeName={}, buildingId={}, cost={}ms",
                    fileName, bridgeName, building.getId(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            result.addFailure(fileName, bridgeName, "桥梁卡片导入异常：" + e.getMessage());
            log.error("批量导入桥梁卡片异常，fileName={}, bridgeName={}, buildingId={}, cost={}ms",
                    fileName, bridgeName, building.getId(), System.currentTimeMillis() - startTime, e);
        }
    }

    private Building resolveBridgeCardBuilding(String bridgeName, Long projectId) {
        List<Building> buildings;
        if (projectId != null) {
            Task queryTask = new Task();
            queryTask.setProjectId(projectId);
            Building queryBuilding = new Building();
            queryBuilding.setName(bridgeName);
            queryTask.setBuilding(queryBuilding);
            List<Task> tasks = taskService.selectTaskList(queryTask);
            buildings = tasks == null ? Collections.emptyList() : tasks.stream()
                    .map(Task::getBuilding)
                    .filter(Objects::nonNull)
                    .filter(building -> bridgeName.equals(building.getName()))
                    .collect(Collectors.toList());
        } else {
            Building queryBuilding = new Building();
            queryBuilding.setName(bridgeName);
            buildings = buildingMapper.selectBuildingExactList(queryBuilding);
            if (buildings != null) {
                buildings = buildings.stream()
                        .filter(building -> bridgeName.equals(building.getName()))
                        .collect(Collectors.toList());
            }
        }

        if (CollUtil.isEmpty(buildings) || buildings.size() != 1) {
            return null;
        }
        return buildings.get(0);
    }

    private String getZipEntryFileName(String entryName) {
        String normalized = entryName == null ? "" : entryName.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private boolean isWordFile(String fileName) {
        if (StringUtils.isEmpty(fileName) || fileName.startsWith("~$")) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".docx") || lowerName.endsWith(".doc");
    }

    private byte[] readZipEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = zipInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private String extractBridgeNameFromCardFileName(String fileName) {
        String name = fileName;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }

        int separatorIndex = Math.max(
                Math.max(name.lastIndexOf('-'), name.lastIndexOf('－')),
                Math.max(name.lastIndexOf('—'), name.lastIndexOf('–')));
        if (separatorIndex >= 0 && separatorIndex < name.length() - 1) {
            name = name.substring(separatorIndex + 1);
        }
        return name.trim();
    }

    private String getWordContentType(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".doc")
                ? "application/msword"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    @Override
    public BatchCbmsDiseaseImportResult batchImportCBMSDiseases(MultipartFile file, Long projectId, String projectName) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename) || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ServiceException("请上传zip格式的CBMS病害压缩包");
        }
        if (projectId == null && StringUtils.isEmpty(projectName)) {
            throw new ServiceException("项目ID或项目名称不能为空");
        }

        try {
            return batchImportCBMSDiseases(file, projectId, projectName, ZIP_UTF8_CHARSET);
        } catch (IllegalArgumentException e) {
            if (!isZipCharsetMalformed(e)) {
                throw e;
            }
            return batchImportCBMSDiseases(file, projectId, projectName, ZIP_GBK_CHARSET);
        }
    }

    private BatchCbmsDiseaseImportResult batchImportCBMSDiseases(MultipartFile file, Long projectId, String projectName, Charset charset) {
        BatchCbmsDiseaseImportResult result = new BatchCbmsDiseaseImportResult();
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), charset)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                String fileName = getZipEntryFileName(entry.getName());
                if (!isExcelFile(fileName)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                byte[] excelBytes = readZipEntry(zipInputStream);
                log.info("开始批量导入CBMS病害Excel，fileName={}", fileName);
                importSingleCBMSDisease(fileName, excelBytes, projectId, projectName, result);
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new ServiceException("读取CBMS病害压缩包失败：" + e.getMessage());
        }
        return result;
    }

    private void importSingleCBMSDisease(String fileName, byte[] excelBytes, Long projectId, String projectName,
                                         BatchCbmsDiseaseImportResult result) {
        long startTime = System.currentTimeMillis();
        String bridgeName = extractBridgeNameFromCBMSDiseaseFileName(fileName);
        if (StringUtils.isEmpty(bridgeName)) {
            result.addFailure(fileName, bridgeName, "无法从文件名解析桥梁名称");
            log.warn("批量导入CBMS病害失败，fileName={}, reason=无法从文件名解析桥梁名称", fileName);
            return;
        }

        TaskMatchResult matchResult = resolveCBMSDiseaseTasks(bridgeName, projectId, projectName);
        if (CollUtil.isEmpty(matchResult.getTasks())) {
            result.addFailure(fileName, bridgeName, matchResult.getReason());
            log.warn("批量导入CBMS病害失败，fileName={}, bridgeName={}, projectId={}, projectName={}, reason={}",
                    fileName, bridgeName, projectId, projectName, matchResult.getReason());
            return;
        }

        for (Task task : matchResult.getTasks()) {
            importMatchedCBMSDiseaseTask(fileName, excelBytes, bridgeName, task, result, startTime);
        }
    }

    private void importMatchedCBMSDiseaseTask(String fileName, byte[] excelBytes, String bridgeName, Task task,
                                              BatchCbmsDiseaseImportResult result, long startTime) {
        Building building = task.getBuilding();
        if (hasExistingDiseases(task.getId())) {
            result.addSkipped(fileName, bridgeName, task.getId(), building == null ? null : building.getId(),
                    building == null ? null : building.getName(), "任务已存在病害信息");
            log.info("跳过批量导入CBMS病害，fileName={}, bridgeName={}, taskId={}, reason=任务已存在病害信息",
                    fileName, bridgeName, task.getId());
            return;
        }

        try {
            MultipartFile excelFile = new MockMultipartFile(
                    "file",
                    fileName,
                    getExcelContentType(fileName),
                    excelBytes);
            transactionTemplate.execute(status -> {
                readCBMSDiseaseExcel(excelFile, task.getId());
                return null;
            });
            Project project = task.getProject();
            result.addSuccess(fileName, bridgeName, task.getId(), building == null ? null : building.getId(),
                    building == null ? null : building.getName(), task.getProjectId(), project == null ? null : project.getName());
            log.info("批量导入CBMS病害成功，fileName={}, bridgeName={}, taskId={}, cost={}ms",
                    fileName, bridgeName, task.getId(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            result.addFailure(fileName, bridgeName, "CBMS病害导入异常：" + e.getMessage());
            log.error("批量导入CBMS病害异常，fileName={}, bridgeName={}, taskId={}, cost={}ms",
                    fileName, bridgeName, task.getId(), System.currentTimeMillis() - startTime, e);
        }
    }

    private TaskMatchResult resolveCBMSDiseaseTasks(String bridgeName, Long projectId, String projectName) {
        Task queryTask = new Task();
        queryTask.setProjectId(projectId);
        if (StringUtils.isNotEmpty(projectName)) {
            Project project = new Project();
            project.setName(projectName.trim());
            queryTask.setProject(project);
        }
        Building queryBuilding = new Building();
        queryBuilding.setName(bridgeName);
        queryTask.setBuilding(queryBuilding);

        List<Task> tasks = taskMapper.selectTaskList(queryTask, null);
        if (CollUtil.isEmpty(tasks)) {
            return TaskMatchResult.failure("未找到匹配任务：" + bridgeName);
        }

        List<Task> matchedTasks = tasks.stream()
                .filter(task -> projectMatches(task, projectId, projectName))
                .map(task -> new ScoredTask(task, scoreBridgeMatch(bridgeName, task.getBuilding())))
                .filter(scoredTask -> scoredTask.getScore() > 0)
                .sorted(Comparator.comparingInt(ScoredTask::getScore).reversed())
                .map(ScoredTask::getTask)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(matchedTasks)) {
            return TaskMatchResult.failure("未找到匹配任务：" + bridgeName);
        }
        return TaskMatchResult.success(matchedTasks);
    }

    private boolean hasExistingDiseases(Long taskId) {
        Disease query = new Disease();
        query.setTaskId(taskId);
        List<Disease> diseases = diseaseMapper.selectDiseaseList(query);
        return CollUtil.isNotEmpty(diseases);
    }

    private boolean projectMatches(Task task, Long projectId, String projectName) {
        if (projectId != null && !Objects.equals(projectId, task.getProjectId())) {
            return false;
        }
        if (StringUtils.isEmpty(projectName)) {
            return true;
        }
        Project project = task.getProject();
        String taskProjectName = project == null ? null : project.getName();
        return fuzzyContains(taskProjectName, projectName);
    }

    private int scoreBridgeMatch(String bridgeName, Building building) {
        if (building == null || StringUtils.isEmpty(building.getName())) {
            return 0;
        }
        String fileBridgeName = normalizeMatchName(bridgeName);
        String taskBridgeName = normalizeMatchName(building.getName());
        if (StringUtils.isEmpty(fileBridgeName) || StringUtils.isEmpty(taskBridgeName)) {
            return 0;
        }
        if (taskBridgeName.equals(fileBridgeName)) {
            return 1000;
        }
        if (taskBridgeName.startsWith(fileBridgeName)) {
            return 800 - Math.max(0, taskBridgeName.length() - fileBridgeName.length());
        }
        if (taskBridgeName.contains(fileBridgeName)) {
            return 600 - Math.max(0, taskBridgeName.length() - fileBridgeName.length());
        }
        if (fileBridgeName.contains(taskBridgeName)) {
            return 400 - Math.max(0, fileBridgeName.length() - taskBridgeName.length());
        }
        return 0;
    }

    private boolean fuzzyContains(String source, String target) {
        String normalizedSource = normalizeMatchName(source);
        String normalizedTarget = normalizeMatchName(target);
        return StringUtils.isNotEmpty(normalizedSource)
                && StringUtils.isNotEmpty(normalizedTarget)
                && (normalizedSource.contains(normalizedTarget) || normalizedTarget.contains(normalizedSource));
    }

    private boolean isExcelFile(String fileName) {
        return StringUtils.isNotEmpty(fileName)
                && !fileName.startsWith("~$")
                && fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private String extractBridgeNameFromCBMSDiseaseFileName(String fileName) {
        String name = fileName;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        for (String suffix : new String[]{"病害信息", "病害清单", "病害"}) {
            if (name.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }
        return trimSeparators(name);
    }

    private String trimSeparators(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("-") || result.endsWith("－") || result.endsWith("—")
                || result.endsWith("–") || result.endsWith("_") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private String normalizeMatchName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "")
                .replace("（", "(")
                .replace("）", ")")
                .trim();
    }

    private String getExcelContentType(String fileName) {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private static class TaskMatchResult {
        private final List<Task> tasks;
        private final String reason;

        private TaskMatchResult(List<Task> tasks, String reason) {
            this.tasks = tasks;
            this.reason = reason;
        }

        private static TaskMatchResult success(List<Task> tasks) {
            return new TaskMatchResult(tasks, null);
        }

        private static TaskMatchResult failure(String reason) {
            return new TaskMatchResult(Collections.emptyList(), reason);
        }

        private List<Task> getTasks() {
            return tasks;
        }

        private String getReason() {
            return reason;
        }
    }

    private static class ScoredTask {
        private final Task task;
        private final int score;

        private ScoredTask(Task task, int score) {
            this.task = task;
            this.score = score;
        }

        private Task getTask() {
            return task;
        }

        private int getScore() {
            return score;
        }
    }


    int BATCH_SIZE = 50;

    @Override
    public List<CompletableFuture<Void>> addThumbPhoto(List<Attachment> attachmentList) {
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

        return futures;
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
                MultipartFile thumbnail = createThumbnail(newName, fileMap.getOldName(), 1024, 768, 0.5f);

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

            // 获取原图尺寸
            BufferedImage originalImage = ImageIO.read(inputStream);
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // 判断是否为768*1024尺寸，若是则保持原尺寸，否则按指定尺寸调整
            Thumbnails.Builder<BufferedImage> thumbnailBuilder = Thumbnails.of(originalImage);
            if (originalWidth == 768 && originalHeight == 1024) {
                thumbnailBuilder.size(originalWidth, originalHeight);
            } else {
                thumbnailBuilder.size(width, height).crop(Positions.CENTER);
            }

            thumbnailBuilder.outputQuality(quality)
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
