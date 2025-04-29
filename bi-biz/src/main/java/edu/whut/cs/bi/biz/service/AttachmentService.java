package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Attachment;

import java.util.List;

/**
 * Attachment 服务接口
 *
 */
public interface AttachmentService  {
    // 可以在这里添加业务方法
    List<Attachment> getAttachmentList();
    Attachment getAttachmentBySubjectId(Long subjectId);
    Attachment getAttachmentById(Long id);
    int deleteAttachmentByIds(String ids);
    void deleteAttachmentById(Long id);
    void insertAttachment(Attachment attachment);
    void updateAttachment(Attachment attachment);

    List<Attachment> getAttachmentList(Long id);

}