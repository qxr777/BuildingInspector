package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Disease;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 病害Mapper接口
 *
 * @author: chenwenqi
 * @date: 2025-04-09
 */
@Mapper
public interface DiseaseMapper {
    /**
     * 查询病害
     *
     * @param id 病害ID
     * @return 病害
     */
    Disease selectDiseaseById(Long id);

    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害集合
     */
     List<Disease> selectDiseaseList(Disease disease);

    /**
     * 新增病害
     *
     * @param disease 病害
     * @return 结果
     */
    int insertDisease(Disease disease);


    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
     int updateDisease(Disease disease);

    /**
     * 删除病害
     *
     * @param id 病害ID
     * @return 结果
     */
     int deleteDiseaseById(Long id);


    /**
     * 批量删除病害
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
     int deleteDiseaseByIds(String[] ids);
}
