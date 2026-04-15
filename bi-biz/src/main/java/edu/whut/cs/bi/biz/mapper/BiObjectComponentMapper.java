package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.BiObjectComponent;

/**
 * 构件与对象关联Mapper接口
 */
public interface BiObjectComponentMapper {
    /**
     * 查询关联
     */
    public BiObjectComponent selectBiObjectComponentById(Long id);

    /**
     * 查询关联列表
     */
    public List<BiObjectComponent> selectBiObjectComponentList(BiObjectComponent biObjectComponent);

    /**
     * 新增关联
     */
    public int insertBiObjectComponent(BiObjectComponent biObjectComponent);

    /**
     * 修改关联
     */
    public int updateBiObjectComponent(BiObjectComponent biObjectComponent);

    /**
     * 删除关联
     */
    public int deleteBiObjectComponentById(Long id);

    /**
     * 批量删除关联
     */
    public int deleteBiObjectComponentByIds(String[] ids);

    /**
     * 按构件ID删除关联
     */
    public int deleteBiObjectComponentByComponentId(Long componentId);
}
