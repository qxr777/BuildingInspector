//package edu.whut.cs.bi.biz.mapper;
//
//import edu.whut.cs.bi.biz.domain.Standard;
//import org.apache.ibatis.annotations.Param;
//
//import java.util.List;
//import java.util.Map;
//
//public interface StandardMapper {
//    void insert(@Param("standard") Standard standard,@Param("remark") String remark,@Param("userId") String userId);
//    void update(@Param("standard") Standard standard,@Param("remark") String remark,@Param("userId") String userId);
//    void deleteById(Long id);
//    Standard selectById(Long id);
//
//    //  条件查询
//    Standard selectByStandardNo(String standardNo);
//    List<Standard> selectByYearRange(@Param("startYear") Integer startYear, @Param("endYear") Integer endYear);
//
//    // 分页查询（配合PageHelper）
//    List<Standard> selectList(Map<String,Object> params);
//}





package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Standard;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标准Mapper接口
 *
 * @author ruoyi
 * @date 2025-03-23
 */
public interface StandardMapper
{
    /**
     * 查询标准
     *
     * @param id 标准主键
     * @return 标准
     */
    public Standard selectStandardById(Long id);

    /**
     * 查询标准列表
     *
     * @param standard 标准
     * @return 标准集合
     */
    public List<Standard> selectStandardList(Standard standard);

    /**
     * 新增标准
     *
     * @param standard 标准
     * @return 结果
     */
    public int insertStandard(Standard standard);

    /**
     * 修改标准
     *
     * @param standard 标准
     * @return id
     */
    public int updateStandard(Standard standard);

    /**
     * 删除标准
     *
     * @param id 标准主键
     * @return 结果
     */
    public int deleteStandardById(Long id);

    /**
     * 批量删除标准
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteStandardByIds(String[] ids);

    public void setAttachmentId(@Param("AttachmentId") Long AttachmentId,@Param("StandardId") Long StandardId);

    public Long selectAttachmentIdById (Long id);

    public String[] selectAttachmentIds (String[] ids);
}
