package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.Ztree;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import edu.whut.cs.bi.biz.service.IDiseaseTypeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 病害类型 业务层处理
 *
 * @author chenwenqi
 */
@Service
public class DiseaseTypeServiceImpl implements IDiseaseTypeService
{
    @Resource
    private DiseaseTypeMapper diseaseTypeMapper;

    @Resource
    private DiseaseScaleMapper diseaseScaleMapper;

    @Resource
    private TODiseaseTypeMapper toDiseaseTypeMapper;

    /**
     * 根据条件分页查询病害类型
     *
     * @param diseaseType 病害类型信息
     * @return 病害类型集合信息
     */
    @Override
    public List<DiseaseType> selectDiseaseTypeList(DiseaseType diseaseType)
    {
        return diseaseTypeMapper.selectDiseaseTypeList(diseaseType);
    }

    /**
     * 根据所有病害类型
     *
     * @return 病害类型集合信息
     */
    @Override
    public List<DiseaseType> selectDiseaseTypeAll()
    {
        return diseaseTypeMapper.selectDiseaseTypeAll();
    }

    /**
     * 根据病害类型查询病害类型标度
     *
     * @param code 病害类型
     * @return 病害类型标度集合信息
     */
    @Override
    public List<DiseaseScale> selectDiseaseScaleByCode(String code)
    {
        return diseaseScaleMapper.selectDiseaseScaleByTypeCode(code);
    }

    /**
     * 根据病害类型ID查询信息
     *
     * @param diseaseTypeId 病害类型ID
     * @return 病害类型
     */
    @Override
    public DiseaseType selectDiseaseTypeById(Long diseaseTypeId)
    {
        return diseaseTypeMapper.selectDiseaseTypeById(diseaseTypeId);
    }

    /**
     * 根据病害类型查询信息
     *
     * @param code 病害类型编码
     * @return 病害类型
     */
    @Override
    public DiseaseType selectDiseaseTypeByCode(String code)
    {
        return diseaseTypeMapper.selectDiseaseTypeByCode(code);
    }

    /**
     * 批量删除病害类型
     *
     * @param ids 需要删除的数据
     */
    @Override
    public void deleteDiseaseTypeByIds(String ids)
    {
        Long[] diseaseTypeIds = Convert.toLongArray(ids);
        for (Long diseaseTypeId : diseaseTypeIds)
        {
            DiseaseType diseaseType = selectDiseaseTypeById(diseaseTypeId);
            if (diseaseScaleMapper.countDiseaseScaleByTypeCode(diseaseType.getCode()) > 0)
            {
                throw new ServiceException(String.format("%1$s已分配,不能删除", diseaseType.getName()));
            }
            diseaseTypeMapper.deleteDiseaseTypeById(diseaseTypeId);
        }
    }

    /**
     * 新增保存病害类型信息
     *
     * @param diseaseType 病害类型信息
     * @return 结果
     */
    @Override
    public int insertDiseaseType(DiseaseType diseaseType)
    {
        return diseaseTypeMapper.insertDiseaseType(diseaseType);
    }

    /**
     * 修改保存病害类型信息
     *
     * @param diseaseType 病害类型信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateDiseaseType(DiseaseType diseaseType)
    {
        return diseaseTypeMapper.updateDiseaseType(diseaseType);
    }

    /**
     * 校验病害类型称是否唯一
     *
     * @param diseaseType 病害类型
     * @return 结果
     */
    @Override
    public boolean checkDiseaseTypeUnique(DiseaseType diseaseType)
    {
        Long diseaseTypeId = StringUtils.isNull(diseaseType.getId()) ? -1L : diseaseType.getId();
        DiseaseType checked = diseaseTypeMapper.checkDiseaseTypeUnique(diseaseType.getCode());
        if (StringUtils.isNotNull(checked) && checked.getId().longValue() != diseaseTypeId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 查询病害类型树
     *
     * @param diseaseType 病害类型
     * @return 所有病害类型
     */
    @Override
    public List<Ztree> selectDiseaseTypeTree(DiseaseType diseaseType)
    {
        List<Ztree> ztrees = new ArrayList<Ztree>();
        List<DiseaseType> diseaseTypeList = diseaseTypeMapper.selectDiseaseTypeList(diseaseType);
        for (DiseaseType dt : diseaseTypeList)
        {
            if (UserConstants.DISEASE_NORMAL.equals(dt.getStatus()))
            {
                Ztree ztree = new Ztree();
                ztree.setId(diseaseType.getId());
                ztree.setName(diseaseType.getName());
                ztree.setTitle(diseaseType.getCode());
                ztrees.add(ztree);
            }
        }
        return ztrees;
    }

    /**
     * 读取json文件
     *
     * @param file
     * @param diseaseType
     * @return
     */
    @Transactional
    @Override
    public Boolean readJsonFile(MultipartFile file, DiseaseType diseaseType) {
        if (file.isEmpty()) {
            throw new ServiceException("文件不能为空");
        }

        // TODO 新增了一些参数，这里也要修改
        try {
            // 将文件内容转化成字符串
            String json = new String(file.getBytes(), "UTF-8");
            // 解析json数据
            JSONArray jsonArray = JSONUtil.parseArray(json,false);

            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                // 添加病害类型
                JSONObject type = jsonObject.getJSONObject("DiseaseType");
                String typeName = type.getStr("名称");
                String code = type.getStr("对应表序号");
                diseaseType.setName(typeName);
                diseaseType.setId(null);
                diseaseType.setCode(code);
                DiseaseType oldDiseaseType = diseaseTypeMapper.selectDiseaseTypeByCode(code);
                if (oldDiseaseType == null) {
                    diseaseTypeMapper.insertDiseaseType(diseaseType);
                } else {
                    diseaseType.setId(oldDiseaseType.getId());
                }

//                JSONArray bridgeComponentArray = jsonObject.getJSONArray("BridgeComponentID");
//                List<Long> componentIds = new ArrayList<>();
//                for (int j = 0; j < bridgeComponentArray.size(); j++) {
//                    componentIds.add(bridgeComponentArray.getLong(j));
//                }
//                toDiseaseTypeMapper.insertData(componentIds, diseaseType.getId());

                // 添加病害标度
                JSONArray scaleArray = jsonObject.getJSONArray("DiseaseScale");
                for (int j = 0; j < scaleArray.size(); j++) {
                    JSONObject scaleJson = scaleArray.getJSONObject(j);
                    DiseaseScale diseaseScale = new DiseaseScale();
                    diseaseScale.setTypeCode(diseaseType.getCode());
                    diseaseScale.setScale(scaleJson.getInt("Scale"));
                    diseaseScale.setQualitativeDescription(scaleJson.getStr("QualitativeDescription"));
                    diseaseScale.setQuantitativeDescription(scaleJson.getStr("QuantitativeDescription"));
                    diseaseScale.setCreateBy(ShiroUtils.getLoginName());
                    diseaseScaleMapper.insertDiseaseScale(diseaseScale);
                }

                // 更新bi_disease_type表的max_scale字段
                JSONObject scaleJson = scaleArray.getJSONObject(scaleArray.size() - 1);
                diseaseTypeMapper.updateMaxScale(diseaseType.getId(), scaleJson.getInt("Scale"));
            }

        } catch (IOException e) {
            throw new ServiceException("添加数据发生异常，" + e.getMessage());
        }

        return true;
    }

    @Override
    public List<DiseaseType> selectDiseaseTypeListByTemplateObjectId(Long biObjectId) {
        List<Long> diseaseTypeIds = toDiseaseTypeMapper.selectByTemplateObjectId(biObjectId);
        if (diseaseTypeIds != null && diseaseTypeIds.size() > 0) {
            return diseaseTypeMapper.selectDiseaseTypeListByIds(diseaseTypeIds);
        }

        return List.of();
    }
}
