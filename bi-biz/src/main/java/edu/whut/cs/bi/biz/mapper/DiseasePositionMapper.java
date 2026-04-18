package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.DiseasePosition;
import edu.whut.cs.bi.biz.domain.vo.TemplateDiseasePositionVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 病害位置字典 Mapper 接口
 *
 * @author QiXin
 * @date 2026/04/11
 */
public interface DiseasePositionMapper {

    /**
     * 查询全量病害位置列表（供 SQLite 导出等场景使用）
     */
    List<DiseasePosition> selectDiseasePositionList(DiseasePosition diseasePosition);

    /**
     * 根据 ID 查询病害位置
     */
    DiseasePosition selectDiseasePositionById(Long id);

    /**
     * 新增病害位置
     */
    int insertDiseasePosition(DiseasePosition diseasePosition);

    /**
     * 修改病害位置
     */
    int updateDiseasePosition(DiseasePosition diseasePosition);

    /**
     * 删除病害位置（逻辑删除）
     */
    int deleteDiseasePositionById(Long id);

    /**
     * 查询病害位置列表，包含是否已选信息
     *
     * @param templateObjectId 模板对象ID
     * @param diseasePosition 病害位置查询条件
     * @return 病害位置列表
     */
    List<TemplateDiseasePositionVO> selectTemplateDiseasePositionList(@Param("templateObjectId") Long templateObjectId,
                                                                      @Param("diseasePosition") DiseasePosition diseasePosition);
}
