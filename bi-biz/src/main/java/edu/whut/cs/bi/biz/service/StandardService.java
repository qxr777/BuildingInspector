//package edu.whut.cs.bi.biz.service;
//
//import edu.whut.cs.bi.biz.domain.Standard;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//import java.util.Map;
//
//public interface StandardService {
//     Standard selectById(Long id);
//     Standard selectByStandardNo(String standardNo);
//     List<Standard> selectByYearRange(Integer startYear, Integer endYear);
//     List<Standard> selectList(Map<String,Object> params);
//     void insert(Standard standard, String remark, String userId, MultipartFile file);
//     void update(Standard standard,String remark,String userId);
//     void deleteById(Long id);
//}





package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Standard;

import java.util.List;

/**
 * 标准Service接口
 *
 * @author ruoyi
 * @date 2025-03-23
 */
public interface StandardService
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
     * @return id
     */
    public int insertStandard(Standard standard);


    public void setAttachmentId(Long AttachmentId,Long StandardId);

    /**
     * 修改标准
     *
     * @param standard 标准
     * @return 结果
     */
    public int updateStandard(Standard standard);

    /**
     * 批量删除标准
     *
     * @param ids 需要删除的标准主键集合
     * @return 结果
     */
    public int deleteStandardByIds(String ids);

    /**
     * 删除标准信息
     *
     * @param id 标准主键
     * @return 结果
     */
    public int deleteStandardById(Long id);

}



