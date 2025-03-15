package edu.whut.cs.bm.biz.mapper;

import java.util.Date;
import java.util.List;
import edu.whut.cs.bm.biz.domain.IndexData;
import edu.whut.cs.bm.biz.vo.IndexDataVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 监测数据Mapper接口
 * 
 * @author qixin
 * @date 2021-08-10
 */
public interface IndexDataMapper 
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

    /**
     * 修改监测数据
     * 
     * @param indexData 监测数据
     * @return 结果
     */
    public int updateIndexData(IndexData indexData);

    /**
     * 删除监测数据
     * 
     * @param id 监测数据ID
     * @return 结果
     */
    public int deleteIndexDataById(Long id);

    /**
     * 批量删除监测数据
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteIndexDataByIds(String[] ids);

    IndexData selectPreviousIndexData(@Param("objectId")Long objectId, @Param("indexId")Long indexId, @Param("period")Long period);


    List<IndexData> selectIndexDataListByIds(Long[] ids);

    List<IndexData> selectIndexDataListByObjectIdAndIndexId(@Param("objectId") Long objectId, @Param("indexId") Long indexId, @Param("startTime") Date startTime,@Param("endTime") Date endTime);
}
