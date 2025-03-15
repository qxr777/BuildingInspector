package edu.whut.cs.bm.biz.mapper;

import java.util.List;

import com.ruoyi.common.core.domain.entity.SysDept;
import edu.whut.cs.bm.biz.domain.BmObject;
import org.apache.ibatis.annotations.Param;

/**
 * 监测对象Mapper接口
 * 
 * @author qixin
 * @date 2021-08-10
 */
public interface BmObjectMapper 
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
     * 根据ID查询所有子对象
     *
     * @param objectId 对象ID
     * @return 对象列表
     */
    public List<BmObject> selectChildrenObjectById(Long objectId);

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
     * 修改子元素关系
     *
     * @param objects 子元素
     * @return 结果
     */
    public int updateObjectChildren(@Param("objects") List<BmObject> objects);

    /**
     * 删除监测对象
     * 
     * @param id 监测对象ID
     * @return 结果
     */
    public int deleteBmObjectById(Long id);

    /**
     * 批量删除监测对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteBmObjectByIds(String[] ids);
}
