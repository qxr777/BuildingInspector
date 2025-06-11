package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.controller.DiseaseController;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.*;

import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IComponentService;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
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
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();

        return diseaseMapper.deleteDiseaseByIds(strArray);
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

}
