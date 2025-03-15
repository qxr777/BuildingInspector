package edu.whut.cs.bm.biz.service.impl;

import java.util.List;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bm.biz.mapper.IndexMapper;
import edu.whut.cs.bm.biz.domain.Index;
import edu.whut.cs.bm.biz.service.IIndexService;
import com.ruoyi.common.core.text.Convert;

/**
 * 监测指标Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-10
 */
@Service
public class IndexServiceImpl implements IIndexService 
{
    @Autowired
    private IndexMapper indexMapper;

    /**
     * 查询监测指标
     * 
     * @param id 监测指标ID
     * @return 监测指标
     */
    @Override
    public Index selectIndexById(Long id)
    {
        return indexMapper.selectIndexById(id);
    }

    /**
     * 查询监测指标列表
     * 
     * @param index 监测指标
     * @return 监测指标
     */
    @Override
    public List<Index> selectIndexList(Index index)
    {
        return indexMapper.selectIndexList(index);
    }

    /**
     * 新增监测指标
     * 
     * @param index 监测指标
     * @return 结果
     */
    @Override
    public int insertIndex(Index index)
    {
        index.setCreateTime(DateUtils.getNowDate());
        return indexMapper.insertIndex(index);
    }

    /**
     * 修改监测指标
     * 
     * @param index 监测指标
     * @return 结果
     */
    @Override
    public int updateIndex(Index index)
    {
        index.setUpdateTime(DateUtils.getNowDate());
        return indexMapper.updateIndex(index);
    }

    /**
     * 删除监测指标对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteIndexByIds(String ids)
    {
        return indexMapper.deleteIndexByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除监测指标信息
     * 
     * @param id 监测指标ID
     * @return 结果
     */
    @Override
    public int deleteIndexById(Long id)
    {
        return indexMapper.deleteIndexById(id);
    }

    @Override
    public List<Index> selectIndexsByObjectId(Long objectId) {
        List<Index> objectIndexes = indexMapper.selectIndexesByObjectId(objectId);
        List<Index> indexes = indexMapper.selectIndexList(new Index());
        for (Index index : indexes) {
            for (Index objectIndex : objectIndexes) {
                if (index.getId().longValue() == objectIndex.getId().longValue()) {
                    index.setFlag(true);
                    break;
                }
            }
        }
        return indexes;
    }
}
