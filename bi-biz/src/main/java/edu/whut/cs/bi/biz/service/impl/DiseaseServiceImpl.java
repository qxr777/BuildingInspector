package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.*;

import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
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
    private ComponentMapper componentMapper;

    @Resource
    private BiObjectMapper biObjectMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private AttachmentService attachmentService;
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
            disease.setComponent(componentMapper.selectComponentById(componentId));
        }

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
            diseases = diseaseMapper.selectDiseaseListByBiObjectIds(biObjectIds);
        } else {
            diseases = diseaseMapper.selectDiseaseList(disease);
        }

        diseases.forEach(ds -> {
            Long componentId = ds.getComponentId();

            if (componentId != null) {
                ds.setComponent(componentMapper.selectComponentById(componentId));
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
    public int insertDisease(Disease disease) {
        disease.setCreateTime(DateUtils.getNowDate());
        Long diseaseTypeId = disease.getDiseaseTypeId();
        DiseaseType diseaseType = diseaseTypeMapper.selectDiseaseTypeById(diseaseTypeId);
        disease.setType(diseaseType.getName());
        return diseaseMapper.insertDisease(disease);
    }

    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
    @Override
    public int updateDisease(Disease disease) {
        disease.setUpdateTime(DateUtils.getNowDate());
        return diseaseMapper.updateDisease(disease);
    }

    /**
     * 批量删除病害
     *
     * @param ids 需要删除的病害主键
     * @return 结果
     */
    @Override
    public int deleteDiseaseByIds(String ids) {
        return diseaseMapper.deleteDiseaseByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除病害信息
     *
     * @param id 病害主键
     * @return 结果
     */
    @Override
    public int deleteDiseaseById(Long id) {
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

    @Override
    public void handleDiseaseAttachment(MultipartFile[] files,Long id) {
        if(files == null)return;
        Arrays.stream(files).forEach(e->{
            FileMap fileMap = fileMapService.handleFileUpload(e);
            Attachment attachment = new Attachment();
            attachment.setMinioId(Long.valueOf(fileMap.getId()));
            attachment.setName("disease_"+fileMap.getOldName());
            attachment.setSubjectId(id);
            attachment.setType(1);
            attachmentService.insertAttachment(attachment);
        });
    }

}
