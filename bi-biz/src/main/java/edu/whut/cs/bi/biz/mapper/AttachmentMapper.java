package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Attachment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Attachment 数据访问接口
 */
public interface AttachmentMapper {
    public List<Attachment> selectAll();

    public Attachment selectById(Long id);

    public List<Attachment> selectBySubjectId(Long id);

    public Long getMinioId(Long id);

    public String[] selectMinioIdsByIds(String[] ids);

    public String[] selectThumbMinioIdsByIds(String[] ids);

    public int insert(Attachment attachment);

    public int update(Attachment attachment);

    public int deleteById(Long id);

    public int deleteByIds(String[] ids);

    @Select("select * from bi_attachment where subject_id = #{id}")
    @Results({
            @Result(property = "id", column = "id", id = true),
            @Result(property = "minioId", column = "minio_id"),
            @Result(property = "thumbMinioId", column = "thumb_minio_id")
    })
    public List<Attachment> selectBySubjectListById(Long id);

    List<Attachment> selectBySubjectIds(@Param("subjectIds") List<Long> subjectIds);

    List<Attachment> selectAttachmentByMinio(@Param("minios") List<Long> minios);

    /**
     * 按离线 UUID 查询附件（用于病害照片 ZIP 批量上传的幂等去重）。
     *
     * @param offlineUuid 附件自身的离线 UUID
     * @return 附件
     */
    Attachment selectByOfflineUuid(@Param("offlineUuid") String offlineUuid);
}