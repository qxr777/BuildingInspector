
package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Standard;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.StandardMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.StandardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标准Service业务层处理
 *
 * @author ruoyi
 * @date 2025-03-23
 */
@Service
@Transactional
public class StandardServiceImpl implements StandardService
{
    @Autowired
    private StandardMapper standardMapper;
    // 这里 因为 不单单是操作 Attachment 表，所以需要注入AttachmentService
    @Autowired
    private AttachmentService attachmentService;

    // 标准 和 附件强关联 ，删除新增需要注意。


    /**
     * 查询标准
     *
     * @param id 标准主键
     * @return 标准
     */
    @Override
    public Standard selectStandardById(Long id)
    {
        return standardMapper.selectStandardById(id);
    }

    /**
     * 查询标准列表
     *
     * @param standard 标准
     * @return 标准
     */
    @Override
    public List<Standard> selectStandardList(Standard standard)
    {
        return standardMapper.selectStandardList(standard);
    }

    /**
     * 新增标准
     *
     * @param standard 标准
     * @return 新增项 id
     */
    @Override
    public int insertStandard(Standard standard)
    {
        standard.setCreateTime(DateUtils.getNowDate());
        standard.setCreateBy(ShiroUtils.getLoginName());
        // 由于 和附件 相互关联id ，插入后需要set 附件id, 这部分在controller 处理
        return standardMapper.insertStandard(standard);
    }

    public void setAttachmentId(Long AttachmentId,Long StandardId){
        standardMapper.setAttachmentId(AttachmentId,StandardId);
    }

    /**
     * 修改标准
     *
     * @param standard 标准
     * @return 结果
     */
    @Override
    public int updateStandard(Standard standard)
    {
        standard.setUpdateTime(DateUtils.getNowDate());
        standard.setUpdateBy(ShiroUtils.getLoginName());
        Attachment attachment = new Attachment();
        attachment.setType(5);
        // 同步更新 附件名 ， 暂时不支持更新文件。
        attachment.setName(standard.getName());
        attachmentService.updateAttachment(attachment);
        return standardMapper.updateStandard(standard);
    }

    /**
     * 批量删除标准
     *
     * @param ids 需要删除的标准主键
     * @return 结果
     */
    @Override
    public int deleteStandardByIds(String ids)
    {
        // 删除附件
        String[] idsArray = standardMapper.selectAttachmentIds(Convert.toStrArray(ids));
        attachmentService.deleteAttachmentByIds(String.join(",",idsArray));
        return standardMapper.deleteStandardByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除标准信息
     *
     * @param id 标准主键
     * @return 结果
     */
    @Override
    public int deleteStandardById(Long id)
    {
        // 由于前端封装的对象不带 attachmentId ，所以操作数据库得到 该Id
        Long attachmentId = standardMapper.selectAttachmentIdById(id);
        // 删除附件
        attachmentService.deleteAttachmentById(attachmentId);
        // 删除 标准文档
        return standardMapper.deleteStandardById(id);
    }

}

