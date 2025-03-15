package edu.whut.cs.bm.biz.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bm.biz.mapper.AlertMapper;
import edu.whut.cs.bm.biz.domain.Alert;
import edu.whut.cs.bm.biz.service.IAlertService;
import com.ruoyi.common.core.text.Convert;

/**
 * 预警信息Service业务层处理
 * 
 * @author qixin
 * @date 2021-08-13
 */
@Service
public class AlertServiceImpl implements IAlertService 
{
    @Autowired
    private AlertMapper alertMapper;

    /**
     * 查询预警信息
     * 
     * @param id 预警信息ID
     * @return 预警信息
     */
    @Override
    public Alert selectAlertById(Long id)
    {
        return alertMapper.selectAlertById(id);
    }

    /**
     * 查询预警信息列表
     * 
     * @param alert 预警信息
     * @return 预警信息
     */
    @Override
    public List<Alert> selectAlertList(Alert alert)
    {
        return alertMapper.selectAlertList(alert);
    }

    /**
     * 新增预警信息
     * 
     * @param alert 预警信息
     * @return 结果
     */
    @Override
    public int insertAlert(Alert alert)
    {
        alert.setCreateTime(DateUtils.getNowDate());
        return alertMapper.insertAlert(alert);
    }

    /**
     * 修改预警信息
     * 
     * @param alert 预警信息
     * @return 结果
     */
    @Override
    public int updateAlert(Alert alert)
    {
        alert.setUpdateTime(DateUtils.getNowDate());
        return alertMapper.updateAlert(alert);
    }

    /**
     * 删除预警信息对象
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteAlertByIds(String ids)
    {
        return alertMapper.deleteAlertByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除预警信息信息
     * 
     * @param id 预警信息ID
     * @return 结果
     */
    @Override
    public int deleteAlertById(Long id)
    {
        return alertMapper.deleteAlertById(id);
    }
}
