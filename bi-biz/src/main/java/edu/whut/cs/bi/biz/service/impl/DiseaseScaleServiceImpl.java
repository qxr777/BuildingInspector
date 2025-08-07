package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;

import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.service.IDiseaseScaleService;

import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 病害标度 业务层处理
 *
 * @author chenwenqi
 */
@Service
public class DiseaseScaleServiceImpl implements IDiseaseScaleService
{
    @Resource
    private DiseaseScaleMapper diseaseScaleMapper;

    /**
     * 根据条件分页查询病害标度数据
     *
     * @param diseaseScale 病害标度数据信息
     * @return 病害标度数据集合信息
     */
    @Override
    public List<DiseaseScale> selectDiseaseScaleList(DiseaseScale diseaseScale)
    {
        String typeCode = diseaseScale.getTypeCode();
        if (StringUtils.isNotEmpty(typeCode) && typeCode.split("-").length > 2) {
            diseaseScale.setTypeCode(typeCode.substring(0, typeCode.lastIndexOf("-")));
        }
        return diseaseScaleMapper.selectDiseaseScaleList(diseaseScale);
    }

    /**
     * 根据病害标度数据ID查询信息
     *
     * @param id 病害标度数据ID
     * @return 病害标度数据
     */
    @Override
    public DiseaseScale selectDiseaseScaleById(Long id)
    {
        return diseaseScaleMapper.selectDiseaseScaleById(id);
    }

    /**
     * 批量删除病害标度数据
     *
     * @param ids 需要删除的数据
     */
    @Override
    public void deleteDiseaseScaleByIds(String ids)
    {
        Long[] diseaseScaleIds = Convert.toLongArray(ids);
        diseaseScaleMapper.deleteDiseaseScaleByIds(diseaseScaleIds);
    }

    /**
     * 新增保存病害标度数据信息
     *
     * @param diseaseScale 病害标度数据信息
     * @return 结果
     */
    @Override
    public int insertDiseaseScale(DiseaseScale diseaseScale)
    {
        return diseaseScaleMapper.insertDiseaseScale(diseaseScale);
    }

    /**
     * 修改保存病害标度数据信息
     *
     * @param diseaseScale 病害标度数据信息
     * @return 结果
     */
    @Override
    public int updateDiseaseScale(DiseaseScale diseaseScale)
    {
        return diseaseScaleMapper.updateDiseaseScale(diseaseScale);
    }
}
