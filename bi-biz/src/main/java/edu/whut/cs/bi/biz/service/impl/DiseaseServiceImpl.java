package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
        Long diseaseTypeId = disease.getDiseaseTypeId();


        disease.setDiseaseType(diseaseTypeMapper.selectDiseaseTypeById(diseaseTypeId));

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
        List<Disease> diseases = diseaseMapper.selectDiseaseList(disease);

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
