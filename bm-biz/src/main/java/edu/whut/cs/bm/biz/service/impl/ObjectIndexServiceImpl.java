package edu.whut.cs.bm.biz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bm.biz.mapper.ObjectIndexMapper;
import edu.whut.cs.bm.biz.domain.ObjectIndex;
import edu.whut.cs.bm.biz.service.IObjectIndexService;
import com.ruoyi.common.core.text.Convert;

/**
 * 监测对象评估Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-11
 */
@Service
public class ObjectIndexServiceImpl implements IObjectIndexService 
{
    @Autowired
    private ObjectIndexMapper objectIndexMapper;

    /**
     * 查询监测对象评估
     * 
     * @param id 监测对象评估ID
     * @return 监测对象评估
     */
    @Override
    public ObjectIndex selectObjectIndexById(Long id)
    {
        return objectIndexMapper.selectObjectIndexById(id);
    }

    /**
     * 查询监测对象评估列表
     * 
     * @param objectIndex 监测对象评估
     * @return 监测对象评估
     */
    @Override
    public List<ObjectIndex> selectObjectIndexList(ObjectIndex objectIndex)
    {
        return objectIndexMapper.selectObjectIndexList(objectIndex);
    }

    /**
     * 新增监测对象评估
     * 
     * @param objectIndex 监测对象评估
     * @return 结果
     */
    @Override
    public int insertObjectIndex(ObjectIndex objectIndex)
    {
        return objectIndexMapper.insertObjectIndex(objectIndex);
    }

    /**
     * 修改监测对象评估
     * 
     * @param objectIndex 监测对象评估
     * @return 结果
     */
    @Override
    public int updateObjectIndex(ObjectIndex objectIndex)
    {
        return objectIndexMapper.updateObjectIndex(objectIndex);
    }

    /**
     * 删除监测对象评估对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteObjectIndexByIds(String ids)
    {
        return objectIndexMapper.deleteObjectIndexByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除监测对象评估信息
     * 
     * @param id 监测对象评估ID
     * @return 结果
     */
    @Override
    public int deleteObjectIndexById(Long id)
    {
        return objectIndexMapper.deleteObjectIndexById(id);
    }
}
