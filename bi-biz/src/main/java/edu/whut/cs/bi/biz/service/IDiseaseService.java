package edu.whut.cs.bi.biz.service;


import com.ruoyi.common.core.domain.Ztree;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Disease;

import java.util.List;

/**
 * 病害Service接口
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
public interface IDiseaseService {
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
    public int insertDisease(Disease disease);

    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
    public int updateDisease(Disease disease);

    /**
     * 批量删除病害
     *
     * @param ids 需要删除的病害主键集合
     * @return 结果
     */
    public int deleteDiseaseByIds(String ids);

    /**
     * 删除病害信息
     *
     * @param id 病害主键
     * @return 结果
     */
    public int deleteDiseaseById(Long id);
}
