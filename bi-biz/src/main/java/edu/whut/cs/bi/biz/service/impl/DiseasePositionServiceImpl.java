package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import edu.whut.cs.bi.biz.domain.DiseasePosition;
import edu.whut.cs.bi.biz.mapper.DiseasePositionMapper;
import edu.whut.cs.bi.biz.service.IDiseasePositionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 病害位置字典 Service 实现
 */
@Service
public class DiseasePositionServiceImpl implements IDiseasePositionService {

    @Resource
    private DiseasePositionMapper diseasePositionMapper;

    @Override
    public List<DiseasePosition> selectDiseasePositionList(DiseasePosition diseasePosition) {
        return diseasePositionMapper.selectDiseasePositionList(diseasePosition);
    }

    @Override
    public DiseasePosition selectDiseasePositionById(Long id) {
        return diseasePositionMapper.selectDiseasePositionById(id);
    }

    @Override
    public int insertDiseasePosition(DiseasePosition diseasePosition) {
        return diseasePositionMapper.insertDiseasePosition(diseasePosition);
    }

    @Override
    public int updateDiseasePosition(DiseasePosition diseasePosition) {
        return diseasePositionMapper.updateDiseasePosition(diseasePosition);
    }

    @Override
    public int deleteDiseasePositionByIds(String ids) {
        Long[] positionIds = Convert.toLongArray(ids);
        int rows = 0;
        for (Long id : positionIds) {
            rows += diseasePositionMapper.deleteDiseasePositionById(id);
        }
        return rows;
    }
}
