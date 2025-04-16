package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Attachment;

import java.util.List;

/**
 * Attachment 数据访问接口

 */
public interface AttachmentMapper{
    public List<Attachment> selectAll();
    public Attachment selectById(Long id);
    public Attachment selectBySubjectId(Long id);
    public Long getMinioId (Long id);
    public String[] selectMinioIdsByIds (String[] ids);
    public int insert(Attachment attachment);
    public int update(Attachment attachment);
    public int deleteById(Long id);
    public int deleteByIds(String[] ids);


}