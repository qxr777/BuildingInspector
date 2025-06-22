package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;

import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


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
    private IBuildingService buildingService;

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
            diseases = diseaseMapper.selectDiseaseListByBiObjectIds(biObjectIds);
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
        diseaseDetailMapper.deleteDiseaseDetailByDiseaseId(disease.getId());

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
                    diseaseDetailMapper.deleteDiseaseDetailByDiseaseId(disease.getId());
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
        diseaseDetailMapper.deleteDiseaseDetailByDiseaseId(disease.getId());

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
        diseaseDetailMapper.deleteDiseaseDetailByDiseaseIds(longArray);
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


    @Transactional
    @Override
    public void readExcel(MultipartFile file) throws IOException {

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表

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


                    // 新增建筑
                    Building building = new Building();
                    building.setName(buildingName);
                    List<Building> buildings = buildingMapper.selectBuildingList(building);
                    if (CollUtil.isEmpty(buildings) || buildings.size() == 0) {
                        // 默认组合桥
                        building.setStatus("0");
                        building.setArea("420100");
                        building.setLine("G70");
                        building.setIsLeaf("0");
                        buildingService.insertBuilding(building);
                    } else {
                        building = buildings.get(0);
                    }
                    Long parentBuildingId = building.getId();

                    building = new Building();
                    if (side == null || side.equals("/") || side.equals("") || side.equals("L-梁") || side.equals("左幅")) {
                        building.setName(buildingName + "-左幅");
                    } else {
                        building.setName(buildingName + "-" + side);
                    }

                    buildings = buildingMapper.selectBuildingList(building);
                    if (CollUtil.isEmpty(buildings) || buildings.size() == 0) {
                        // 默认组合桥
                        building.setStatus("0");
                        building.setArea("420100");
                        building.setLine("G70");
                        building.setIsLeaf("1");
                        building.setTemplateId(1L);
                        building.setParentId(parentBuildingId);
                        buildingService.insertBuilding(building);
                    } else {
                        building = buildings.get(0);
                    }

                    BiObject rootBiObject = biObjectMapper.selectBiObjectById(building.getRootObjectId());
                    BiObject query = new BiObject();
                    query.setParentId(rootBiObject.getId());
                    query.setName(component_2);
                    List<BiObject> biObjects = biObjectMapper.selectBiObjectListByName(query);
                    if (biObjects != null && biObjects.size() > 0) {
                        BiObject biObject = biObjects.get(0);
                        query.setName(component_3);
                        query.setParentId(biObject.getId());
                        biObjects = biObjectMapper.selectBiObjectList(query);
                        if (biObjects != null && biObjects.size() > 0) {
                            // 第四层节点

                            // 新增部件
                            Component component = handleDefectLocation(location);
                            String componentName = component.getName();
                            String code = component.getCode();

                            // 查询绑定的biObject对象
                            query.setName(componentName);
                            query.setParentId(biObjects.get(0).getId());
                            biObjects = biObjectMapper.selectBiObjectList(query);
                            biObject = biObjects.get(0);


                            component.setBiObjectId(biObject.getId());
                            component.setCode(null);
                            if (code != null && !code.equals("")) {
                                component.setName(code + "#" + componentName);
                            }
                            log.info(component.toString());
                            Component old = componentService.selectComponent(component);
                            if (old == null) {
                                component.setCode(code);
                                componentService.insertComponent(component);
                            }

                            Disease disease = new Disease();
                            disease.setPosition(location);
                            disease.setType(diseaseType);
                            disease.setComponentId(component.getId());
                            disease.setBuildingId(building.getId());
                            disease.setProjectId(11L);
                            disease.setBiObjectName(componentName);
                            disease.setBiObjectId(component.getBiObjectId());
                            disease.setRepairRecommendation(repairSuggestion);
                            if (defectLevel.equals("/")) {
                                disease.setLevel(1);
                            } else {
                                disease.setLevel(Integer.parseInt(defectLevel));
                            }

                            disease.setDescription(diseaseDescription);
                            disease.setQuantity(Integer.parseInt(diseaseNumber));

                            diseaseMapper.insertDisease(disease);

                            DiseaseDetail diseaseDetail = new DiseaseDetail();
                            diseaseDetail.setDiseaseId(disease.getId());
                            if (length != null && !length.equals("/")) {
                                BigDecimal decimal = new BigDecimal(length);
                                diseaseDetail.setLength(decimal);
                            }

                            diseaseDetailMapper.insertDiseaseDetail(diseaseDetail);
                        }
                    }
                });
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
                    return String.valueOf((int) cell.getNumericCellValue());
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
        if (location.matches(regex)) {
            String[] split = location.split("#");
            component.setName(split[1]);
            component.setCode(split[0]);
            return component;
        }

        switch (location) {
            case "R-1#台":
                component.setName("台身");
                component.setCode("R-1");
                break;
            case "L-0#台":
                component.setName("台身");
                component.setCode("L-0");
                break;
            case "R-0#台":
                component.setName("台身");
                component.setCode("R-0");
                break;
            case "R-梁":
                component.setName("T梁");
                component.setCode("R");
                break;
            case "L-梁":
                component.setName("T梁");
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
                component.setName("其他");
                break;
        }

        return component;
    }
}
