package edu.whut.cs.bm.biz.service;

import java.util.List;
import edu.whut.cs.bm.biz.domain.Alert;

/**
 * 预警信息Service接口
 * 
 * @author qixin
 * @date 2021-08-13
 */
public interface IAlertService 
{
    /**
     * 查询预警信息
     * 
     * @param id 预警信息ID
     * @return 预警信息
     */
    public Alert selectAlertById(Long id);

    /**
     * 查询预警信息列表
     * 
     * @param alert 预警信息
     * @return 预警信息集合
     */
    public List<Alert> selectAlertList(Alert alert);

    /**
     * 新增预警信息
     * 
     * @param alert 预警信息
     * @return 结果
     */
    public int insertAlert(Alert alert);

    /**
     * 修改预警信息
     * 
     * @param alert 预警信息
     * @return 结果
     */
    public int updateAlert(Alert alert);

    /**
     * 批量删除预警信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteAlertByIds(String ids);

    /**
     * 删除预警信息信息
     * 
     * @param id 预警信息ID
     * @return 结果
     */
    public int deleteAlertById(Long id);
}
