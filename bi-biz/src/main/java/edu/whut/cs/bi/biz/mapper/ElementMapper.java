package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.Element;
import org.apache.ibatis.annotations.Param;

/**
 * 构件（bi_element，JTG/T 5230—2026 新标采集链）Mapper 接口
 */
public interface ElementMapper {

    /**
     * 查询构件列表（空条件 = 全量）
     */
    public List<Element> selectElementList(Element element);

    /**
     * 按所属部件节点 ID 集合批量查询未删除构件
     */
    public List<Element> selectElementsByBiObjectIds(@Param("biObjectIds") List<Long> biObjectIds);

    /**
     * 按离线 UUID 查询未删除构件（离线同步去重）
     */
    public Element selectByOfflineUuid(String offlineUuid);

    /**
     * 新增构件（离线同步）
     */
    public int insertElement(Element element);

    /**
     * 更新构件（离线同步）
     */
    public int updateElement(Element element);

    /**
     * 逻辑删除构件（del_flag='2'）
     */
    public int deleteElementById(Long id);
}
