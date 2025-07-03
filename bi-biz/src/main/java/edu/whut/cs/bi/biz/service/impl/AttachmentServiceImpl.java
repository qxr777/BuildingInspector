package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Attachment 服务实现类
 *
 */
@Service
public class AttachmentServiceImpl  implements AttachmentService {
    @Autowired
    private AttachmentMapper attachmentMapper;

    // 附件和 文件强关联 ，在删除时需要注意。
    @Autowired
    private IFileMapService fileMapService;
    @Override
    public List<Attachment> getAttachmentList() {
        return attachmentMapper.selectAll();
    }

    @Override
    public List<Attachment> getAttachmentBySubjectId(Long subjectId) {
        return attachmentMapper.selectBySubjectId(subjectId);
    }

    @Override
    public Attachment getAttachmentById(Long id) {
        return attachmentMapper.selectById(id);
    }

    @Override
    public void deleteAttachmentById(Long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        // 删除附件时 删除 文件
        fileMapService.deleteFileMapById(attachment.getMinioId());
        attachmentMapper.deleteById(id);
    }

    @Override
    public void insertAttachment(Attachment attachment) {
        attachment.setCreateBy(ShiroUtils.getLoginName());
        attachment.setCreateTime(DateUtils.getNowDate());
        attachmentMapper.insert(attachment);
    }

    @Override
    public void updateAttachment(Attachment attachment) {
        attachment.setUpdateBy(ShiroUtils.getLoginName());
        attachment.setUpdateTime(DateUtils.getNowDate());
        attachmentMapper.update(attachment);
    }

    @Override
    public List<Attachment> getAttachmentList(Long id) {
        return attachmentMapper.selectBySubjectListById(id);
    }

    @Override
    public List<Attachment> getAttachmentBySubjectIds(List<Long> subjectIds) {
        return attachmentMapper.selectBySubjectIds(subjectIds);
    }

    @Override
    public List<Attachment> selectAttachmentByMinio(List<Long> minios) {
        return attachmentMapper.selectAttachmentByMinio(minios);
    }

    @Override
    public int deleteAttachmentByIds(String ids) {
        // 拿到 文件的 ids
        if(ids == null || ids.length() == 0){
            return 0;
        }
        String[] FileMapIds = attachmentMapper.selectMinioIdsByIds(Convert.toStrArray(ids));
        fileMapService.deleteFileMapByIds(String.join(",",FileMapIds));
        return attachmentMapper.deleteByIds(Convert.toStrArray(ids));
    }


}