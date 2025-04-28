package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Attachment;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

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

    @Select("select * from bi_attachment where subject_id = #{id}")
    @Results({
            @Result(property = "id", column = "id", id = true),
            @Result(property = "minioId", column = "minio_id"),
    })
    public List<Attachment> selectBySubjectListById(Long id);
}