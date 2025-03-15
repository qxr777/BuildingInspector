package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.IndexData;
import edu.whut.cs.bm.biz.vo.IndexDataVo;

/**
 * 监测数据Service接口
 *
 * @author qixin
 * @date 2021-08-10
 */
public interface IIndexDataService
{
    /**
     * 查询监测数据
     *
     * @param id 监测数据ID
     * @return 监测数据
     */
    public IndexData selectIndexDataById(Long id);

    /**
     * 查询监测数据列表
     *
     * @param indexData 监测数据
     * @return 监测数据集合
     */
    public List<IndexData> selectIndexDataList(IndexData indexData);

    /**
     * 新增监测数据
     *
     * @param indexData 监测数据
     * @return 结果
     */
    public int insertIndexData(IndexData indexData);

    public int insertIndexData(String measurement, String value, int createType);

    public int insertIndexData(Long objectId, Long indexId, String value, int createType);

    /**
     * 修改监测数据
     *
     * @param indexData 监测数据
     * @return 结果
     */
    public int updateIndexData(IndexData indexData);

    /**
     * 批量删除监测数据
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteIndexDataByIds(String ids);

    /**
     * 删除监测数据信息
     *
     * @param id 监测数据ID
     * @return 结果
     */
    public int deleteIndexDataById(Long id);


    /**
     * @param ids 需要可视化检测对象的id
     * @return 数据集用来生成图表
     */
    List<IndexDataVo> selectIndexDataByIds(String ids, String startTime, String endTime);
}
