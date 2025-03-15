package edu.whut.cs.bm.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bm.biz.data.converter.BaseConverter;
import edu.whut.cs.bm.biz.data.converter.ConverterFactory;
import edu.whut.cs.bm.biz.domain.Index;
import edu.whut.cs.bm.biz.domain.IndexData;
import edu.whut.cs.bm.biz.domain.ObjectIndex;
import edu.whut.cs.bm.biz.mapper.IndexDataMapper;
import edu.whut.cs.bm.biz.mapper.IndexMapper;
import edu.whut.cs.bm.biz.mapper.ObjectIndexMapper;
import edu.whut.cs.bm.biz.service.IAlertRuleService;
import edu.whut.cs.bm.biz.service.IIndexDataService;
import edu.whut.cs.bm.biz.vo.CheckVo;
import edu.whut.cs.bm.biz.vo.IndexDataVo;
import edu.whut.cs.bm.common.constant.BizConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 监测数据Service业务层处理
 *
 * @author qixin
 * @date 2021-08-10
 */
@Service
@Slf4j
public class IndexDataServiceImpl implements IIndexDataService
{
    @Autowired
    private IndexDataMapper indexDataMapper;

    @Autowired
    private ObjectIndexMapper objectIndexMapper;

    @Autowired
    private IndexMapper indexMapper;

    @Autowired
    private IAlertRuleService alertRuleService;

    /**
     * 查询监测数据
     *
     * @param id 监测数据ID
     * @return 监测数据
     */
    @Override
    public IndexData selectIndexDataById(Long id)
    {
        return indexDataMapper.selectIndexDataById(id);
    }

    /**
     * 查询监测数据列表
     *
     * @param indexData 监测数据
     * @return 监测数据
     */
    @Override
    public List<IndexData> selectIndexDataList(IndexData indexData)
    {
        return indexDataMapper.selectIndexDataList(indexData);
    }

    /**
     * 新增监测数据
     *
     * @param indexData 监测数据
     * @return 结果
     */
    @Override
    public int insertIndexData(IndexData indexData)
    {
        indexData.setValueStr(indexData.joinValueStr());
//        indexData.setCreateTime(DateUtils.getNowDate());
        return indexDataMapper.insertIndexData(indexData);
    }

    @Override
    public int insertIndexData(String measurement, String value, int createType) {
        ObjectIndex queryObjectIndex = new ObjectIndex();
        queryObjectIndex.setMeasurement(measurement);
        List<ObjectIndex> objectIndexList = objectIndexMapper.selectObjectIndexList(queryObjectIndex);

        return batchSaveIndexData(objectIndexList, value, createType);
    }

    @Override
    public int insertIndexData(Long objectId, Long indexId, String value, int createType) {
        ObjectIndex queryObjectIndex = new ObjectIndex();
        queryObjectIndex.setObjectId(objectId);
        queryObjectIndex.setIndexId(indexId);
        List<ObjectIndex> objectIndexList = objectIndexMapper.selectObjectIndexList(queryObjectIndex);

        return batchSaveIndexData(objectIndexList, value, createType);
    }

    private String convertValue(ObjectIndex objectIndex, String value) {
        try {
            BaseConverter converter = ConverterFactory.produce(objectIndex.getConverter());
            converter.setArgs(value);
            converter.setParams(objectIndex.getConvertParams());
            value = converter.convert() + "";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private int batchSaveIndexData(List<ObjectIndex> objectIndexList, String value, int createType) {
        int count = 0;
        IndexData indexData = new IndexData();
        for (ObjectIndex objectIndex : objectIndexList) {
            if (objectIndex.getConverter() != null && !objectIndex.getConverter().isEmpty()) {
                value = convertValue(objectIndex, value);  // 进行原始数据值的转换
            }
            indexData.setIndex(objectIndex.getIndex());
            switch (objectIndex.getIndex().getDataType().intValue()) {
                case BizConstants.INDEX_TYPE_DATA_TYPE_NUMERIC:
                    try{
                        // 根据监测指标的小数位数进行舍入
                        Index index = objectIndex.getIndex();
                        BigDecimal bd = BigDecimal.valueOf(Double.parseDouble(value));
                        bd = bd.setScale(index.getDecimalPlace(), RoundingMode.HALF_UP);
                        indexData.setNumericValue(bd);
                    } catch (NumberFormatException e) {
                        log.error(e.toString());
                        log.error("NominalValue: " + indexData.getNominalValue());
                        log.error("Measurement: " + objectIndex.getMeasurement());
                        continue;
                    }
                    break;
                case BizConstants.INDEX_TYPE_DATA_TYPE_BINARY:
                    if (value.equals("1")) {
                        indexData.setBinaryValue(1);
                    } else {
                        indexData.setBinaryValue(0);
                    }
                    break;
                case BizConstants.INDEX_TYPE_DATA_TYPE_NOMINAL:
                    indexData.setNominalValue(value);
                    break;
                case BizConstants.INDEX_TYPE_DATA_TYPE_ORDINAL:
                    indexData.setOrdinalValue(Integer.parseInt(value));
                    break;
                default:
            }
            indexData.setIndexDataType(objectIndex.getIndex().getDataType());
            indexData.setIndex(objectIndex.getIndex());
            indexData.setValueStr(indexData.joinValueStr());
            indexData.setIndexId(objectIndex.getIndexId());
            indexData.setObjectId(objectIndex.getObjectId());
            indexData.setCreateType(createType);
            indexData.setCreateTime(DateUtils.getNowDate());
            indexData.setMeasurement(objectIndex.getMeasurement());
            indexDataMapper.insertIndexData(indexData);
            objectIndex.setLastIndexDataId(indexData.getId());
            objectIndexMapper.updateObjectIndex(objectIndex);

            // 判断是否触发预警规则
            CheckVo theSeriousestVo = alertRuleService.check(indexData);
            if (theSeriousestVo != null) {
                indexData.setIsAlert(theSeriousestVo.getAlertLevel());
                indexData.setScore(theSeriousestVo.getScore());
            } else {
                indexData.setScore(BizConstants.ALERT_LEVEL_SCORE_RANGE[BizConstants.ALERT_LEVEL_SCORE_RANGE.length - 1]);
            }
            indexDataMapper.updateIndexData(indexData);
            count++;
        }
        return count;
    }

    /**
     * 修改监测数据
     *
     * @param indexData 监测数据
     * @return 结果
     */
    @Override
    public int updateIndexData(IndexData indexData)
    {
        indexData.setUpdateTime(DateUtils.getNowDate());
        indexData.setValueStr(indexData.joinValueStr());
        return indexDataMapper.updateIndexData(indexData);
    }

    /**
     * 删除监测数据对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteIndexDataByIds(String ids)
    {
        return indexDataMapper.deleteIndexDataByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除监测数据信息
     *
     * @param id 监测数据ID
     * @return 结果
     */
    @Override
    public int deleteIndexDataById(Long id)
    {
        return indexDataMapper.deleteIndexDataById(id);
    }

    @Override
    public List<IndexDataVo> selectIndexDataByIds(String ids, String startTime, String endTime) {
        if (ids == null && startTime == null && endTime == null) {
            return null;
        }
        Long[] idList = Convert.toLongArray(",",ids);
        //将id
        List<IndexDataVo> indexDataVoList = new ArrayList<>();
        List<IndexData> indexDataList = indexDataMapper.selectIndexDataListByIds(idList);
        //使用检测对象id以及检测指标id形成数据集
        int[][] simple = new int[10000][10000];
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        for (IndexData indexData : indexDataList) {
            //去重，将相同objectId和indexId相同的indexData进行去重
            Long objectId = indexData.getObjectId();
            Long indexId = indexData.getIndexId();
            if (simple[Math.toIntExact(objectId)][Math.toIntExact(indexId)] == 0) {
                simple[Math.toIntExact(objectId)][Math.toIntExact(indexId)] = 1;
            }else {
                continue;
            }
            Date startTimeDate = null;
            Date endTimeDate = null;
            try {
                startTimeDate = new SimpleDateFormat("yyyy-MM-dd").parse(startTime);
                endTimeDate = new SimpleDateFormat("yyyy-MM-dd").parse(endTime);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            Date endTimeDateResult = addAndSubtractDaysByGetTime(endTimeDate, 1);
            List<IndexData> indexDataListTemp = indexDataMapper.selectIndexDataListByObjectIdAndIndexId(objectId, indexId, startTimeDate, endTimeDateResult);
            //将数据整理成IndexDataVo，并形成最终的数据集
            IndexDataVo indexDataVo = new IndexDataVo();
            List<Long> dataList = new ArrayList<>();
            List<String> dateList = new ArrayList<>();
            for (IndexData data : indexDataListTemp) {
                //纵坐标数据
                Long value = data.getNumericValue().longValue();
                //横坐标数据
                String date = simpleDateFormat.format(data.getCreateTime());
                if (data.getNumericValue()  == null) {
                    continue;
                }
                dataList.add(value);
                dateList.add(date);
            }
            //给 indexDataVo赋值
            indexDataVo.setObjectName(indexData.getBmObject().getName());
            indexDataVo.setObjectId(indexData.getObjectId());
            indexDataVo.setUnit(indexData.getIndex().getUnit());
            indexDataVo.setIndexName(indexData.getIndex().getName());
            indexDataVo.setId(indexData.getId());
            Long[] value = new Long[dataList.size()];
            value= dataList.toArray(value);
            String[] date = new String[dateList.size()];
            date = dateList.toArray(date);
            indexDataVo.setData(value);
            indexDataVo.setDate(date);
            //放入列表
            indexDataVoList.add(indexDataVo);
        }
        return indexDataVoList;
    }

    public static Date addAndSubtractDaysByGetTime(Date dateTime/*待处理的日期*/, int n/*加减天数*/) {

        //日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        System.out.println(df.format(new Date(dateTime.getTime() + n * 24 * 60 * 60 * 1000L)));
        //注意这里一定要转换成Long类型，要不n超过25时会出现范围溢出，从而得不到想要的日期值
        return new Date(dateTime.getTime() + n * 24 * 60 * 60 * 1000L);
    }


}
