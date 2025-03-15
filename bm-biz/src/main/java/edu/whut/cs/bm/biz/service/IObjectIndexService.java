package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.ObjectIndex;

/**
 * 监测对象评估Service接口
 * 
 * @author qixin
 * @date 2021-08-11
 */
public interface IObjectIndexService 
{
    /**
     * 查询监测对象评估
     * 
     * @param id 监测对象评估ID
     * @return 监测对象评估
     */
    public ObjectIndex selectObjectIndexById(Long id);

    /**
     * 查询监测对象评估列表
     * 
     * @param objectIndex 监测对象评估
     * @return 监测对象评估集合
     */
    public List<ObjectIndex> selectObjectIndexList(ObjectIndex objectIndex);

    /**
     * 新增监测对象评估
     * 
     * @param objectIndex 监测对象评估
     * @return 结果
     */
    public int insertObjectIndex(ObjectIndex objectIndex);

    /**
     * 修改监测对象评估
     * 
     * @param objectIndex 监测对象评估
     * @return 结果
     */
    public int updateObjectIndex(ObjectIndex objectIndex);

    /**
     * 批量删除监测对象评估
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteObjectIndexByIds(String ids);

    /**
     * 删除监测对象评估信息
     * 
     * @param id 监测对象评估ID
     * @return 结果
     */
    public int deleteObjectIndexById(Long id);
}
