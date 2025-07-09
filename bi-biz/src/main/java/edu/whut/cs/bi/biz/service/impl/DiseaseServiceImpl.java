package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import edu.whut.cs.bi.biz.domain.temp.DiseaseReport;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * 病害Service业务层处理
 *
 */
@Service
@Slf4j
public class DiseaseServiceImpl implements IDiseaseService
{
    @Resource
    private DiseaseMapper diseaseMapper;

    @Resource
    private DiseaseTypeMapper diseaseTypeMapper;

    @Resource
    private IComponentService componentService;

    @Resource
    private BiObjectMapper biObjectMapper;

    @Resource
    private IFileMapService fileMapService;

    @Resource
    private AttachmentService attachmentService;

    @Resource
    private DiseaseDetailMapper diseaseDetailMapper;

    @Resource
    private DiseaseController diseaseController;

    @Resource
    private BuildingMapper buildingMapper;

    @Resource
    private IDiseaseTypeService diseaseTypeService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ITaskService taskService;

    @Resource
    private IProjectService projectService;

    @Autowired
    private ComponentMapper componentMapper;

    @Resource
    private IBuildingService buildingService;

    @Resource
    private ProjectMapper projectMapper;

    @Autowired
    private TaskMapper taskMapper;

    /**
     * 查询病害
     *
     * @param id 病害ID
     * @return 病害
     */
    @Override
    public Disease selectDiseaseById(Long id)
    {
        Disease disease = diseaseMapper.selectDiseaseById(id);
        // 关联查询其它属性
        Long componentId = disease.getComponentId();
        Long biObjectId = disease.getBiObjectId();
        if (biObjectId != null) {
            disease.setBiObject(biObjectMapper.selectBiObjectById(biObjectId));
        }
        if (componentId != null) {
            disease.setComponent(componentService.selectComponentById(componentId));
        }

        DiseaseDetail diseaseDetail = new DiseaseDetail();
        diseaseDetail.setDiseaseId(id);
        List<DiseaseDetail> diseaseDetails = diseaseDetailMapper.selectDiseaseDetailList(diseaseDetail);
        disease.setDiseaseDetails(diseaseDetails);

//        List<Map<String, Object>> diseaseImage = diseaseController.getDiseaseImage(disease.getId());


        return disease;
    }

    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害
     */
    @Override
    public List<Disease> selectDiseaseList(Disease disease)
    {
        // 这里是病害列表只有 biObjectId 一个查询条件
        Long biObjectId = disease.getBiObjectId();
        List<Disease> diseases;

        if (biObjectId != null) {
            List<Long> biObjectIds = new ArrayList<>();
            biObjectIds.add(biObjectId);
            List<BiObject> biObjects = biObjectMapper.selectChildrenById(biObjectId);
            biObjectIds.addAll(biObjects.stream().map(BiObject::getId).collect(Collectors.toList()));
            PageUtils.startPage();
            diseases = diseaseMapper.selectDiseaseListByBiObjectIds(biObjectIds, disease.getProjectId());
        } else {
            PageUtils.startPage();
            diseases = diseaseMapper.selectDiseaseList(disease);
        }

        diseases.forEach(ds -> {
            Long componentId = ds.getComponentId();

            if (componentId != null) {
                Component component = componentService.selectComponentById(componentId);
                BiObject parent = biObjectMapper.selectDirectParentById(component.getBiObjectId());

                if (parent != null) {
                    component.setParentObjectName(parent.getName());
                    BiObject grandBiObject = biObjectMapper.selectBiObjectById(parent.getParentId());
                    if (grandBiObject != null) {
                        component.setGrandObjectName(grandBiObject.getName());
                    }
                }
                ds.setComponent(component);
            }

            DiseaseDetail diseaseDetail = new DiseaseDetail();
            diseaseDetail.setDiseaseId(ds.getId());
            List<DiseaseDetail> diseaseDetails = diseaseDetailMapper.selectDiseaseDetailList(diseaseDetail);
            ds.setDiseaseDetails(diseaseDetails);

            List<String> images = new ArrayList<>();
            List<String> ADImgs = new ArrayList<>();
            List<Map<String, Object>> diseaseImage = diseaseController.getDiseaseImage(ds.getId());
            if (CollUtil.isNotEmpty(diseaseImage)) {
                diseaseImage.forEach(di -> {
                    Integer type = (Integer) di.get("type");
                    if (type.equals(7)) {
                        ADImgs.add((String) di.get("url"));
                    } else {
                        images.add((String) di.get("url"));
                    }
                });

                ds.setImages(images);
                ds.setADImgs(ADImgs);
            }
        });
        return diseases;
    }

    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害
     */
    @Override
    public List<Disease> selectDiseaseListForApi(Disease disease)
    {

        List<Disease> diseases = diseaseMapper.selectDiseaseList(disease);

        diseases.forEach(ds -> {
            Long componentId = ds.getComponentId();

            if (componentId != null) {
                Component component = componentService.selectComponentById(componentId);
                BiObject parent = biObjectMapper.selectDirectParentById(component.getBiObjectId());

                if (parent != null) {
                    component.setParentObjectName(parent.getName());
                    BiObject grandBiObject = biObjectMapper.selectBiObjectById(parent.getParentId());
                    if (grandBiObject != null) {
                        component.setGrandObjectName(grandBiObject.getName());
                    }
                }
                ds.setComponent(component);
            }

            DiseaseDetail diseaseDetail = new DiseaseDetail();
            diseaseDetail.setDiseaseId(ds.getId());
            List<DiseaseDetail> diseaseDetails = diseaseDetailMapper.selectDiseaseDetailList(diseaseDetail);
            ds.setDiseaseDetails(diseaseDetails);

            List<String> images = new ArrayList<>();
            List<String> ADImgs = new ArrayList<>();
            List<Map<String, Object>> diseaseImage = diseaseController.getDiseaseImage(ds.getId());
            if (CollUtil.isNotEmpty(diseaseImage)) {
                diseaseImage.forEach(di -> {
                    Integer type = (Integer) di.get("type");
                    if (type.equals(7)) {
                        ADImgs.add((String) di.get("url"));
                    } else {
                        images.add((String) di.get("url"));
                    }
                });

                ds.setImages(images);
                ds.setADImgs(ADImgs);
            }
        });
        return diseases;
    }

    /**
     * 新增病害
     *
     * @param disease 病害
     * @return 结果
     */
    @Override
    @Transactional
    public Integer insertDisease(Disease disease) {
        disease.setCreateTime(DateUtils.getNowDate());
        if(disease.getType() == null || disease.getType().equals("")) {
            Long diseaseTypeId = disease.getDiseaseTypeId();
            DiseaseType diseaseType = diseaseTypeMapper.selectDiseaseTypeById(diseaseTypeId);
            disease.setType(diseaseType.getName());
        }

        // 新增部件
        // 判断数据库是否存在相应构件
        BiObject biObject = biObjectMapper.selectBiObjectById(disease.getBiObjectId());
        Component component = disease.getComponent();

        component.setBiObjectId(disease.getBiObjectId());

        Component old = componentService.selectComponent(component);
        if (old == null) {
            if (disease.getBiObjectName() ==  null ||  disease.getBiObjectName().equals("")) {
                component.setName(biObject.getName() + "#" + component.getCode());
            } else {
                component.setName(disease.getBiObjectName() + "#" + component.getCode());
            }

            componentService.insertComponent(component);
            disease.setComponentId(component.getId());
        } else {
            disease.setComponentId(old.getId());
        }

        if (disease.getBiObjectName() ==  null ||  disease.getBiObjectName().equals("")) {
            disease.setBiObjectName(biObject.getName());
        }

        Integer result = diseaseMapper.insertDisease(disease);

        // 添加病害详情
        List<DiseaseDetail> diseaseDetails = disease.getDiseaseDetails();
        diseaseDetails.forEach(diseaseDetail -> diseaseDetail.setDiseaseId(disease.getId()));
        diseaseDetailMapper.insertDiseaseDetails(diseaseDetails);


        return result;
    }

    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
    @Override
    @Transactional
    public int updateDisease(Disease disease) {
        Disease old = diseaseMapper.selectDiseaseById(disease.getId());
        if (old.getDiseaseTypeId().equals(disease.getDiseaseTypeId())) {
            DiseaseType diseaseType = diseaseTypeMapper.selectDiseaseTypeById(disease.getDiseaseTypeId());
            if (!diseaseType.getName().equals("其他")) {
                disease.setType(diseaseType.getName());
            }
        }

        disease.setUpdateTime(DateUtils.getNowDate());

        // 更新部件信息
        Component component = componentService.selectComponentById(disease.getComponentId());
        if (disease.getBiObjectName() != null || !disease.getBiObjectName().equals("")) {
            component.setName(disease.getBiObjectName() + "#" + disease.getComponent().getCode());
        }
        component.setCode(disease.getComponent().getCode());
        component.setUpdateTime(DateUtils.getNowDate());
        component.setUpdateBy(ShiroUtils.getLoginName());
        componentService.updateComponent(component);

        // 删除病害详情
        diseaseDetailMapper.deleteDiseaseDetailById(disease.getId());

        // 新增病害详情
        List<DiseaseDetail> diseaseDetails = disease.getDiseaseDetails();
        diseaseDetails.forEach(diseaseDetail -> diseaseDetail.setDiseaseId(disease.getId()));
        diseaseDetailMapper.insertDiseaseDetails(diseaseDetails);

        return diseaseMapper.updateDisease(disease);
    }

    /**
     * 批量删除病害
     *
     * @param ids 需要删除的病害主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteDiseaseByIds(String ids) {
        String[] strArray = Convert.toStrArray(ids);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2);

        List<CompletableFuture<Void>> futures = Arrays.stream(strArray)
                .map(id -> CompletableFuture.runAsync(() -> {
                    Disease disease = diseaseMapper.selectDiseaseById(Long.parseLong(id));
                    diseaseDetailMapper.deleteDiseaseDetailById(disease.getId());
                    deleteDeaseImage(disease);
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();

        return diseaseMapper.deleteDiseaseByIds(strArray);
    }

    private void deleteDeaseImage(Disease disease) {
        List<Attachment> attachmentList = attachmentService.getAttachmentList(disease.getId());
        for (Attachment value : attachmentList) {
            if (value.getName().startsWith("disease")) {
                attachmentService.deleteAttachmentById(value.getId());
            }
        }
    }

    /**
     * 删除病害信息
     *
     * @param id 病害主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteDiseaseById(Long id) {
        Disease disease = diseaseMapper.selectDiseaseById(id);
        diseaseDetailMapper.deleteDiseaseDetailById(disease.getId());

        return diseaseMapper.deleteDiseaseById(id);
    }

    /**
     * 批量删除病害
     *
     * @param ids 需要删除的病害主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDiseaseByDiseaseIds(String ids) {
        if(ids==null || ids.equals("")) {
            return 0;
        }
        String[] strArray = Convert.toStrArray(ids);
        Long[] longArray = Convert.toLongArray(ids);
        diseaseDetailMapper.deleteDiseaseDetailByIds(longArray);
        return diseaseMapper.deleteDiseaseByIds(strArray);
    }

    /**
     * 计算扣分
     *
     * @param maxScale 最大分值
     * @param scale    当前分值
     * @return 结果
     */
    @Override
    public int computeDeductPoints(int maxScale, int scale) {
        return switch (maxScale) {
            case 3 -> switch (scale) {
                case 1 -> 0;
                case 2 -> 20;
                case 3 -> 35;
                default -> throw new IllegalArgumentException("当 max_scale 为 3 时，scale 只能为 1、2 或 3");
            };
            case 4 -> switch (scale) {
                case 1 -> 0;
                case 2 -> 25;
                case 3 -> 40;
                case 4 -> 50;
                default -> throw new IllegalArgumentException("当 max_scale 为 4 时，scale 只能为 1、2、3 或 4");
            };
            case 5 -> switch (scale) {
                case 1 -> 0;
                case 2 -> 35;
                case 3 -> 45;
                case 4 -> 60;
                case 5 -> 100;
                default -> throw new IllegalArgumentException("当 max_scale 为 5 时，scale 只能为 1、2、3、4 或 5");
            };
            default -> throw new IllegalArgumentException("max_scale 只能为 3、4 或 5");
        };
    }

    /**
     * 处理病害附件
     *
     * @param files 文件
     * @param id    病害id
     */
    @Override
    public void handleDiseaseAttachment(MultipartFile[] files,Long id,int type) {
        if(files == null)return;
        Arrays.stream(files).forEach(e->{
            FileMap fileMap = fileMapService.handleFileUpload(e);
            Attachment attachment = new Attachment();
            attachment.setMinioId(Long.valueOf(fileMap.getId()));
            attachment.setName("disease_"+fileMap.getOldName());
            attachment.setSubjectId(id);
            attachment.setType(type);
            attachmentService.insertAttachment(attachment);
        });
    }

    /**
     * 获取成因分析
     *
     * @param causeQuery
     * @return
     */
    @Override
    public String getCauseAnalysis(CauseQuery causeQuery) {
        String host = "47.94.205.90";
        int port = 8081;
        String url = "http://" + host + ":" + port + "/api-ai/disease/cause";

        causeQuery.setObjectId(null);
        try {
            String response = WebClient.create()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(causeQuery)) // 直接发送对象作为 JSON
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response;
        } catch (Exception e) {
            throw new RuntimeException("请求失败: " + e.getMessage());
        }
    }

    private void addComponent(Sheet sheet, Map<String, Building> buildingMap, Map<Long, BiObject> biObjectMap, Map<String, List<Component>> componentMap) {
        Set<Component> componentSet = new HashSet<>();

        for (int i = 3; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String buildingName = getCellValueAsString(row.getCell(1));
            String side = getCellValueAsString(row.getCell(3));
            String component_2 = getCellValueAsString(row.getCell(4));
            String component_3 = getCellValueAsString(row.getCell(5));
            String location = getCellValueAsString(row.getCell(6));

            Building building = null;
            if (side != null && !side.equals("")) {
                building = buildingMap.get(buildingName + '-' + side);
            } else {
                building = buildingMap.get(buildingName);
            }
            if (building == null) {
                // 跳过
                return;
            }

            BiObject rootBiObject = biObjectMap.get(building.getRootObjectId());
            List<BiObject> children = rootBiObject.getChildren();
            BiObject biObject2 = children.stream()
                    .filter(child -> rootBiObject.getId().equals(child.getParentId()) && component_2.equals(child.getName()))
                    .findFirst()
                    .orElse(null);
            if (biObject2 == null) {
                log.info("未找到对应的子对象, parentId:{}, name:{}", rootBiObject.getId(), component_2);
                return;
            }

            BiObject biObject3 = children.stream()
                    .filter(child -> biObject2.getId().equals(child.getParentId()) && component_3.equals(child.getName()))
                    .findFirst()
                    .orElse(null);
            if (biObject3 == null) {
                log.error("未找到对应的子对象, parentId:{}, name:{}", biObject2.getId(), component_3);
                return;
            }


            // 新增部件
            Component component = handleDefectLocation(location);
            String biObjectName = component.getName().split("#")[1];

            BiObject biObject4 = null;
            if (biObject3.getName().equals("其他")) {
                biObject4 = children.stream()
                        .filter(child -> biObject3.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                        .findFirst()
                        .orElse(null);
            } else {
                biObject4 = children.stream()
                        .filter(child -> biObject3.getId().equals(child.getParentId()) && biObjectName.equals(child.getName()))
                        .findFirst()
                        .orElse(null);
            }
            if (biObject4 == null) {
                log.error("未找到对应的子对象, parentId:{}, name:{}", biObject3.getId(), biObjectName);
                return;
            }

            List<Component> oldComponents = componentMap.get(component.getName());

            if (oldComponents != null && oldComponents.size() > 0) {
                Set<String> oldComponentRootObjectIds = oldComponents.stream()
                        .map(oldComponent -> oldComponent.getBiObject().getAncestors().split(",")[1])
                        .collect(Collectors.toSet());

                if (oldComponentRootObjectIds.contains(rootBiObject.getId())) {
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
//        componentMapper.batchAddComponents(componentSet);
    }

    @Override
    public void readDiseaseExcel(MultipartFile file, Long projectId) {

        List<Component> components = componentService.selectComponentList(new Component());
        Map<String, List<Component>> componentMap = components.stream()
                .collect(Collectors.groupingBy(Component::getName));
        Set<Disease> diseaseSet = new ConcurrentHashSet<>();

        // 病害类型”其他“，当病害类型都不存在时，默认为其他
        DiseaseType otherDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-0");

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < workbook.getNumberOfSheets(); j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表

                Set<String> buildingSet = new HashSet<>();
                for (int i = 3; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    // 用来查询building
                    String buildingName = getCellValueAsString(row.getCell(1));
                    String side = getCellValueAsString(row.getCell(3));
                    if (side != null && !side.equals("")) {
                        buildingSet.add(buildingName + '-' + side);
                    } else {
                        buildingSet.add(buildingName);
                    }
                }

                List<Building> buildings = buildingMapper.selectBuildingByNames(buildingSet);
                Map<String, Building> buildingMap = buildings.stream().collect(Collectors.toMap(Building::getName, Function.identity()));

                Set<Long> rootObjectIds = buildings.stream().map(building -> building.getRootObjectId()).collect(Collectors.toSet());
                List<BiObject> biObjects = biObjectMapper.selectBiObjects(rootObjectIds);
                Map<Long, BiObject> biObjectMap = biObjects.stream().collect(Collectors.toMap(BiObject::getId, Function.identity()));

                // 添加构件
                addComponent(sheet, buildingMap, biObjectMap, componentMap);

                components = componentService.selectComponentList(new Component());
                Map<String, List<Component>> newComponentMap = components.stream()
                        .collect(Collectors.groupingBy(Component::getName));
                componentMap = newComponentMap;

                // 新增病害
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                Subject subject = ThreadContext.getSubject();
                // 跳过表头行（前3行）
                for (int i = 3; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        // 解决多线程无上下文信息
                        ThreadContext.bind(subject);

                        String buildingName = getCellValueAsString(row.getCell(1));
                        String side = getCellValueAsString(row.getCell(3));
                        String component_2 = getCellValueAsString(row.getCell(4));
                        String component_3 = getCellValueAsString(row.getCell(5));
                        String location = getCellValueAsString(row.getCell(6));
                        String diseaseType = getCellValueAsString(row.getCell(7));
                        String diseaseNumber = getCellValueAsString(row.getCell(8));
                        String length = getCellValueAsString(row.getCell(9)); // 这里是暂存
                        String diseaseDescription = getCellValueAsString(row.getCell(11));
                        String defectLevel = getCellValueAsString(row.getCell(12));
                        String repairSuggestion = getCellValueAsString(row.getCell(13));

                        Building building = null;
                        if (side != null && !side.equals("")) {
                            building = buildingMap.get(buildingName + '-' + side);
                        } else {
                            building = buildingMap.get(buildingName);
                        }
                        if (building == null) {
                            // 跳过
                            return;
                        }

                        Component com = handleDefectLocation(location);
                        String biObjectName = com.getName().split("#")[1];

                        BiObject rootBiObject = biObjectMap.get(building.getRootObjectId());
                        List<BiObject> children = rootBiObject.getChildren();
                        BiObject biObject2 = children.stream()
                                .filter(child -> rootBiObject.getId().equals(child.getParentId()) && component_2.equals(child.getName()))
                                .findFirst()
                                .orElse(null);

                        BiObject biObject3 = children.stream()
                                .filter(child -> biObject2.getId().equals(child.getParentId()) && component_3.equals(child.getName()))
                                .findFirst()
                                .orElse(null);

                        BiObject biObject4 = null;
                        if (biObject3.getName().equals("其他")) {
                            biObject4 = children.stream()
                                    .filter(child -> biObject3.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                                    .findFirst()
                                    .orElse(null);
                        } else {
                            biObject4 = children.stream()
                                    .filter(child -> biObject3.getId().equals(child.getParentId()) && biObjectName.equals(child.getName()))
                                    .findFirst()
                                    .orElse(null);
                        }

                        List<Component> oldComponents = newComponentMap.get(com.getName());

                        BiObject finalBiObject = biObject4;
                        Component component = oldComponents.stream()
                                .filter(oldComponent -> oldComponent.getBiObjectId().equals(finalBiObject.getId())).findFirst().orElse(null);

                        Disease disease = new Disease();
                        disease.setPosition(location);

                        // 病害类型
                        List<DiseaseType> diseaseTypes = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(biObject4.getId());

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
                        disease.setProjectId(projectId);
                        disease.setBiObjectName(biObjectName);
                        disease.setBiObjectId(component.getBiObjectId());
                        disease.setRepairRecommendation(repairSuggestion);
                        if (defectLevel == null || defectLevel.equals("/") || defectLevel.equals("")) {
                            disease.setLevel(1);
                        } else {
                            disease.setLevel((int) Double.parseDouble(defectLevel));
                        }

                        disease.setDescription(diseaseDescription);
                        if (diseaseNumber == null || diseaseNumber.equals("/") || diseaseNumber.equals("")) {
                            disease.setQuantity(1);
                        } else {
                            disease.setQuantity((int) Double.parseDouble(diseaseNumber));
                        }

                        DiseaseDetail diseaseDetail = new DiseaseDetail();
                        diseaseDetail.setDiseaseId(disease.getId());
                        if (length != null && !length.equals("/") && !length.equals("")) {
                            BigDecimal decimal = new BigDecimal(length);
                            diseaseDetail.setLength1(decimal);
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
                diseaseMapper.batchInsertDiseases(diseaseSet);

                List<DiseaseDetail> allDetails = diseaseSet.stream()
                        .flatMap(disease -> disease.getDiseaseDetails().stream()
                                .peek(detail -> detail.setDiseaseId(disease.getId())))
                        .collect(Collectors.toList());
                if (!allDetails.isEmpty()) {
                    diseaseDetailMapper.insertDiseaseDetails(allDetails);
                }

                return true;
            });

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

    final String regex = "^.+#.+";

    private Component handleDefectLocation(String location) {
        Component component = new Component();

        switch (location) {
            case "R-1#台":
                component.setName("R-1#台身");
                component.setCode("R-1");
                break;
            case "L-0#台":
                component.setName("L-0#台身");
                component.setCode("L-0");
                break;
            case "R-0#台":
                component.setName("R-0#台身");
                component.setCode("R-0");
                break;
            case "R-梁":
                component.setName("R#T梁");
                component.setCode("R");
                break;
            case "L-梁":
                component.setName("L#T梁");
                component.setCode("L");
                break;
            case "通道内":
                component.setName("通道内");
                break;
            case "进水口":
                component.setName("出水口");
                break;
            case "出水口":
                component.setName("出水口");
                break;
            case "桥台":
                component.setName("桥身");
                break;
            case "其他":
                component.setName("其他");
                break;
            case "顶板":
                component.setName("实心板");
                break;
            default:
                if (location.matches(regex)) {
                    String[] split = location.split("#");
                    component.setName(location);
                    component.setCode(split[0]);
                    return component;
                }
        }

        return component;
    }

    private static final Map<String, Long> BRIDGE_TYPE_MAP = new HashMap<>();

    static {
        BRIDGE_TYPE_MAP.put("梁桥", 1L);
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


    @Override
    public Boolean readDiseaseZip(MultipartFile file) {
        List<Component> components = componentService.selectComponentList(new Component());
        Map<String, List<Component>> componentMap = components.stream()
                .collect(Collectors.groupingBy(Component::getName));

        Path tempDir = null;

        try {
            tempDir = extractToTempDirectory(file);

            String originalFilename = file.getOriginalFilename();
            String zipName = originalFilename.substring(0, originalFilename.lastIndexOf("."));
            Path jsonPath = tempDir.resolve(zipName).resolve("result.json");

//            String path = tempDir.toString() + "\\" + file.getOriginalFilename().split("\\.")[0];

            if (!Files.exists(jsonPath)) {
                log.error("result.json文件不存在于: " + jsonPath);
                throw new RuntimeException("result.json文件不存在");
            }

            String jsonContent = Files.readString(jsonPath);

            ObjectMapper objectMapper = new ObjectMapper();
            DiseaseReport diseaseReport = objectMapper.readValue(jsonContent, DiseaseReport.class);

            // 项目
            Project project = new Project();
            project.setName(diseaseReport.getProjectName());
            project.setYear(diseaseReport.getReportYear());

            List<Project> projects = projectMapper.selectProjectList(project,null, null);
            if (CollUtil.isEmpty(projects)) {
                // 新建项目
                project.setDeptId(101L);
                projectService.insertProject(project);
            } else {
                project = projects.get(0);
            }

            // 桥梁
            List<DiseaseReport.Building> buildings = diseaseReport.getBuilding();

            List<Building> buildingList = buildings.stream().map(building -> {
                Building bd = new Building();
                bd.setName(building.getBuildingName());
                if (building.getLineCode() != null)
                    bd.setLine(building.getLineCode());
                else
                    bd.setLine("S88");
                if (building.getZipCode() != null)
                    bd.setArea(building.getZipCode());
                else
                    bd.setArea("420581");

                List<Building> selectBuildings = buildingMapper.selectBuildingList(bd);
                if (CollUtil.isEmpty(selectBuildings)) {
                    bd.setIsLeaf("1");
                    bd.setStatus("0");

                    Long templateId = BRIDGE_TYPE_MAP.get(building.getBridgeType());
                    if (templateId == null) {
                        log.error("无法识别的桥梁类型: " + building.getBridgeType());
                        throw new RuntimeException("无法识别的桥梁类型");
                    }
                    bd.setTemplateId(templateId);
                    buildingService.insertBuilding(bd);
                } else {
                    bd = selectBuildings.get(0);
                }

                return bd;
            }).toList();
            Map<String, Building> buildingMap = buildingList.stream().collect(Collectors.toMap(Building::getName, Function.identity()));

            Set<Long> rootObjectIds = buildingList.stream().map(building -> building.getRootObjectId()).collect(Collectors.toSet());
            List<BiObject> biObjects = biObjectMapper.selectBiObjects(rootObjectIds);
            Map<Long, BiObject> biObjectMap = biObjects.stream().collect(Collectors.toMap(BiObject::getId, Function.identity()));

            // 项目绑定桥梁
            Task task = new Task();
            task.setProjectId(project.getId());
            List<Task> tasks = taskMapper.selectTaskList(task, null);
            List<Building> filterBuildings = buildingList.stream()
                    .filter(building -> !tasks.stream().anyMatch(t -> t.getBuildingId().equals(building.getId())))
                    .toList();

            taskService.batchInsertTasks(project.getId(), filterBuildings.stream().map(Building::getId).toList());

            for (DiseaseReport.Building building : buildings) {
                addComponent(building, buildingMap, biObjectMap, componentMap);

                addDiseases(building, buildingMap, biObjectMap, project, tempDir.resolve(zipName));
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tempDir != null) {
                Path finalTempDir = tempDir;
                transactionTemplate.execute(status -> {
                    try {
                        deleteDirectory(finalTempDir);
                    } catch (IOException e) {
                        log.error("清理临时目录时出错: " + e.getMessage());
                    }

                    return true;
                });

            }
        }
        return true;
    }

    private void addDiseases(DiseaseReport.Building DRbuilding, Map<String, Building> buildingMap,
                             Map<Long, BiObject> biObjectMap, Project project, Path path) {

        List<Component> components = componentService.selectComponentList(new Component());
        Map<String, List<Component>> componentMap = components.stream()
                .collect(Collectors.groupingBy(Component::getName));
        String buildingName = DRbuilding.getBuildingName();
        Set<Disease> diseaseSet = new ConcurrentHashSet<>();

        // 病害类型”其他“，当病害类型都不存在时，默认为其他
        DiseaseType otherDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-0");

        // 新增病害
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Subject subject = ThreadContext.getSubject();
        // 跳过表头行（前3行）
        for (DiseaseReport.Disease disease : DRbuilding.getDisease()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // 解决多线程无上下文信息
                ThreadContext.bind(subject);

                String biObject_2 = disease.getBiObject2();
                String biObject_3 = disease.getBiObject3();
                String biObject_4 = disease.getBiObject4();
                String position = disease.getPosition();
                String diseaseType = disease.getDiseaseType();
                Integer diseaseNumber = disease.getQuantity();
                String description = disease.getDescription();
                String developmentTrend = disease.getDevelopmentTrend();
                String image = disease.getImage();
                Integer defectLevel = disease.getLevel();


                Building building = buildingMap.get(buildingName);

                BiObject rootBiObject = biObjectMap.get(building.getRootObjectId());
                List<BiObject> children = rootBiObject.getChildren();
                BiObject biObject2 = children.stream()
                        .filter(child -> rootBiObject.getId().equals(child.getParentId()) && biObject_2.equals(child.getName()))
                        .findFirst()
                        .orElse(null);

                BiObject biObject3 = children.stream()
                        .filter(child -> biObject2.getId().equals(child.getParentId()) && biObject_3.equals(child.getName()))
                        .findFirst()
                        .orElse(null);
                if (biObject3 ==  null) {
                    log.error("未找到对应的子对象, parentId:{}, name:{}", biObject2.getId(), biObject_3);
                    biObject3 = children.stream()
                            .filter(child -> biObject2.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                            .findFirst()
                            .orElse(null);
                }


                BiObject finalBiObject3 = biObject3;
                BiObject biObject4 = null;
                if (biObject3.getName().equals("其他")) {
                    biObject4 = children.stream()
                            .filter(child -> finalBiObject3.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                            .findFirst()
                            .orElse(null);
                } else {
                    biObject4 = children.stream()
                            .filter(child -> finalBiObject3.getId().equals(child.getParentId()) && biObject_4.equals(child.getName()))
                            .findFirst()
                            .orElse(null);
                    if (biObject4 == null) {
                        biObject4 = children.stream()
                                .filter(child -> finalBiObject3.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                                .findFirst()
                                .orElse(null);
                    }
                }

                List<Component> oldComponents = componentMap.get(position);

                BiObject finalBiObject = biObject4;
                Component component = oldComponents.stream()
                        .filter(oldComponent -> oldComponent.getBiObjectId().equals(finalBiObject.getId())).findFirst().orElse(null);

                // 新增病害
                Disease newDisease = new Disease();
                newDisease.setPosition(position);

                // 病害类型
                List<DiseaseType> diseaseTypes = diseaseTypeService.selectDiseaseTypeListByTemplateObjectId(biObject4.getId());

                DiseaseType queryDiseaseType = null;
                if (biObject3.getName().equals("其他")) {
                    queryDiseaseType = diseaseTypes.stream().filter(dt -> dt.getName().equals("其他")).findFirst().orElse(null);
                } else {
                    queryDiseaseType = diseaseTypes.stream().filter(dt -> dt.getName().equals(diseaseType)).findFirst().orElse(null);
                }

                if (queryDiseaseType == null) {
                    queryDiseaseType = otherDiseaseType;
                }

                newDisease.setType(diseaseType);
                newDisease.setDiseaseTypeId(queryDiseaseType.getId());

                newDisease.setComponentId(component.getId());
                newDisease.setBuildingId(building.getId());
                newDisease.setProjectId(project.getId());
                newDisease.setBiObjectName(biObject_4);
                newDisease.setBiObjectId(biObject4.getId());
                if (defectLevel != null)
                    newDisease.setLevel(defectLevel);
                else
                    newDisease.setLevel(1);
                if (diseaseNumber != null)
                    newDisease.setQuantity(diseaseNumber);
                else
                    newDisease.setQuantity(1);
                newDisease.setDescription(description);
                newDisease.setDevelopmentTrend(developmentTrend);
                newDisease.setImages(List.of(image));
                diseaseSet.add(newDisease);
            });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!diseaseSet.isEmpty()) {
            transactionTemplate.execute(status -> {
                diseaseMapper.batchInsertDiseases(diseaseSet);

                for (Disease disease : diseaseSet) {
                    List<String> images = disease.getImages();

                    if (images != null && StringUtils.isNotEmpty(images.get(0))) {
                        MultipartFile file = convert(path, images.get(0));
                        handleDiseaseAttachment(new MultipartFile[]{file}, disease.getId(), 1);
                    }
                }


                return true;
            });

        }
    }

    public MultipartFile convert(Path path, String imageName) {
        // 构建完整的文件路径
        File imageFile = path.resolve(imageName).toFile();

        // 检查文件是否存在
        if (!imageFile.exists()) {
            try {
                throw new IOException("文件不存在: " + imageFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // 读取文件内容到字节数组

        byte[] bytes = null;
        try {
            FileInputStream inputStream = new FileInputStream(imageFile);
            bytes = inputStream.readAllBytes();
            inputStream.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 获取文件扩展名
        String originalFilename = imageFile.getName();
        String contentType = determineContentType(originalFilename);

        // 创建 MultipartFile 实例
        return new MockMultipartFile(
                "file", // 表单字段名
                originalFilename, // 原始文件名
                contentType, // 内容类型
                bytes // 文件内容
        );
    }

    private static String determineContentType(String filename) {
        if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (filename.toLowerCase().endsWith(".gif")) {
            return "image/gif";
        } else if (filename.toLowerCase().endsWith(".bmp")) {
            return "image/bmp";
        } else {
            return "application/octet-stream"; // 默认类型
        }
    }

    private void addComponent(DiseaseReport.Building building, Map<String, Building> buildingMap, Map<Long, BiObject> biObjectMap, Map<String, List<Component>> componentMap) {
        Set<Component> componentSet = new HashSet<>();
        List<DiseaseReport.Disease> diseases = building.getDisease();
        String buildingName = building.getBuildingName();
        Building bd = buildingMap.get(buildingName);
        BiObject rootBiObject = biObjectMap.get(bd.getRootObjectId());
        List<BiObject> children = rootBiObject.getChildren();

        for(DiseaseReport.Disease disease : diseases) {
            String biObject_2 = disease.getBiObject2();
            String biObject_3 = disease.getBiObject3();
            String biObject_4 = disease.getBiObject4();
            String position = disease.getPosition();

            BiObject biObject2 = children.stream()
                    .filter(child -> rootBiObject.getId().equals(child.getParentId()) && biObject_2.equals(child.getName()))
                    .findFirst()
                    .orElse(null);
            if (biObject2 == null) {
                log.info("未找到对应的子对象, parentId:{}, name:{}", rootBiObject.getId(), biObject_2);
                return;
            }

            BiObject biObject3 = children.stream()
                    .filter(child -> biObject2.getId().equals(child.getParentId()) && biObject_3.equals(child.getName()))
                    .findFirst()
                    .orElse(null);
            if (biObject3 == null) {
                log.error("未找到对应的子对象, parentId:{}, name:{}", biObject2.getId(), biObject_3);
                biObject3 = children.stream()
                        .filter(child -> biObject2.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                        .findFirst()
                        .orElse(null);
            }

            BiObject finalBiObject3 = biObject3;
            BiObject biObject4 = null;
            if (biObject3.getName().equals("其他")) {
                biObject4 = children.stream()
                        .filter(child -> finalBiObject3.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                        .findFirst()
                        .orElse(null);
            } else {
                biObject4 = children.stream()
                        .filter(child -> finalBiObject3.getId().equals(child.getParentId()) && biObject_4.equals(child.getName()))
                        .findFirst()
                        .orElse(null);
            }
            if (biObject4 == null) {
                log.error("未找到对应的子对象, parentId:{}, name:{}", biObject3.getId(), biObject_4);
                biObject4 = children.stream()
                        .filter(child -> finalBiObject3.getId().equals(child.getParentId()) && "其他".equals(child.getName()))
                        .findFirst()
                        .orElse(null);
            }

            Component component = new Component();
            if (position.matches(regex)) {
                String[] split = position.split("#");
                component.setName(position);
                component.setCode(split[0]);
            } else {
                component.setName(position);
                component.setCode("null");
            }

            List<Component> oldComponents = componentMap.get(component.getName());
            if (oldComponents != null && oldComponents.size() > 0) {
                Set<String> oldComponentRootObjectIds = oldComponents.stream()
                        .map(oldComponent -> oldComponent.getBiObject().getAncestors().split(",")[1])
                        .collect(Collectors.toSet());

                if (oldComponentRootObjectIds.contains(rootBiObject.getId())) {
                    continue;
                }

            }
            component.setBiObjectId(biObject4.getId());
            component.setCreateBy(ShiroUtils.getLoginName());
            component.setStatus("0");
            componentSet.add(component);
        };

        // 持久化
//        for (Component component : componentSet) {
//            componentService.insertComponent(component);
//        }
        componentMapper.batchAddComponents(componentSet);
    }


    /**
     * 从Zip文件中提取文件
     */
    public Path extractToTempDirectory(MultipartFile multipartFile) throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("zip-extract-");

        try (InputStream inputStream = multipartFile.getInputStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path filePath = tempDir.resolve(entry.getName());

                // 安全检查
                if (!filePath.normalize().startsWith(tempDir)) {
                    throw new IOException("恶意ZIP条目: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipInputStream, filePath);
                }
                zipInputStream.closeEntry();
            }
        }

        return tempDir;
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            System.err.println("无法删除文件: " + p);
                        }
                    });
        }
    }

}
