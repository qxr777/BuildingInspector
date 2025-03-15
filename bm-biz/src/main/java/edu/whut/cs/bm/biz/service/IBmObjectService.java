package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.BmObject;
import com.ruoyi.common.core.domain.Ztree;
import edu.whut.cs.bm.biz.domain.Evaluation;

/**
 * 监测对象Service接口
 * 
 * @author qixin
 * @date 2021-08-10
 */
public interface IBmObjectService 
{
    /**
     * 查询监测对象
     * 
     * @param id 监测对象ID
     * @return 监测对象
     */
    public BmObject selectBmObjectById(Long id);

    /**
     * 查询监测对象列表
     * 
     * @param bmObject 监测对象
     * @return 监测对象集合
     */
    public List<BmObject> selectBmObjectList(BmObject bmObject);

    /**
     * 新增监测对象
     * 
     * @param bmObject 监测对象
     * @return 结果
     */
    public int insertBmObject(BmObject bmObject);

    /**
     * 修改监测对象
     * 
     * @param bmObject 监测对象
     * @return 结果
     */
    public int updateBmObject(BmObject bmObject);

    /**
     * 批量删除监测对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteBmObjectByIds(String ids);

    /**
     * 删除监测对象信息
     * 
     * @param id 监测对象ID
     * @return 结果
     */
    public int deleteBmObjectById(Long id);

    /**
     * 查询监测对象树列表
     * 
     * @return 所有监测对象信息
     */
    public List<Ztree> selectBmObjectTree();

    public void insertObjectIndex(Long objectId, Long[] indexIds);

    public void insertObjectAssign(Long objectId, Long[] indexIds);

    /**
     * 评估监测对象的健康状况
     * @param objectId
     * @return  评估结果
     */
    Evaluation evaluate(Long objectId);

    /**
     * 得到祖级对象名层次
     * @param objectId
     * @return
     */
    String getAncestorNames(Long objectId);

}
