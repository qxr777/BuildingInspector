package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.Index;

/**
 * 监测指标Service接口
 * 
 * @author qixin
 * @date 2021-08-10
 */
public interface IIndexService 
{
    /**
     * 查询监测指标
     * 
     * @param id 监测指标ID
     * @return 监测指标
     */
    public Index selectIndexById(Long id);

    /**
     * 查询监测指标列表
     * 
     * @param index 监测指标
     * @return 监测指标集合
     */
    public List<Index> selectIndexList(Index index);

    /**
     * 新增监测指标
     * 
     * @param index 监测指标
     * @return 结果
     */
    public int insertIndex(Index index);

    /**
     * 修改监测指标
     * 
     * @param index 监测指标
     * @return 结果
     */
    public int updateIndex(Index index);

    /**
     * 批量删除监测指标
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteIndexByIds(String ids);

    /**
     * 删除监测指标信息
     * 
     * @param id 监测指标ID
     * @return 结果
     */
    public int deleteIndexById(Long id);

    public List<Index> selectIndexsByObjectId(Long objectId);
}
