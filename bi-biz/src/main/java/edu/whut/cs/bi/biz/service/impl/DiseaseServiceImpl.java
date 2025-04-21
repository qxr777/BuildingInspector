package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Disease;

import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
            List<BiObject> biObjects = biObjectMapper.selectChildrenById(biObjectId);
            List<Long> biObjectIds = biObjects.stream().map(BiObject::getId).collect(Collectors.toList());
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

}
